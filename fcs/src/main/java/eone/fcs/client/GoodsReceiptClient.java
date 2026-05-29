package eone.fcs.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eone.fcs.repository.EketRepository.EketRiga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/**
 * Client per la registrazione di Goods Receipt (Entrata Merci da OdA)
 * su SAP S/4HANA Cloud Public Edition tramite OData V2.
 *
 * API utilizzata : API_MATERIAL_DOCUMENT_SRV (OData V2)
 * Communication Scenario richiesto: SAP_COM_0108 (Material Document Integration)
 * Tipo movimento   : 101 (GR per OdA)
 * Riferimento doc. : B  (Goods movement for purchase order)
 * GoodsMovementCode: 01 (Goods Receipt for Purchase Order)
 *
 * Flusso della chiamata:
 *   1. GET  .../$metadata → X-CSRF-Token (fetch — endpoint standard S/4HC)
 *   2. POST .../A_MaterialDocumentHeader → crea il documento
 *
 * Configurazione (da ccee_config.properties):
 *   s4.base.url   = https://my434383-api.s4hana.cloud.sap
 *   s4.username   = COMM_USER_XXXX
 *   s4.password   = ****
 *
 * Logica batch:
 *   Se xchpf=true e in_charg non è null → il campo Batch viene popolato nel payload.
 *   Se xchpf=true e in_charg è null     → viene loggato un warning e la riga viene
 *                                          comunque inviata senza batch (SAP può
 *                                          generarlo automaticamente se configurato).
 *
 * Gestione errori:
 *   Qualsiasi risposta HTTP non 2xx, o errore di rete, lancia GoodsReceiptException.
 *   Il chiamante (EketResource.handleF) intercetta l'eccezione, marca lo stato 'E'
 *   e restituisce un messaggio di errore al WMS.
 */
public class GoodsReceiptClient {

    private static final Logger log = LoggerFactory.getLogger(GoodsReceiptClient.class);

    // OData V2 - Material Document
    private static final String SERVICE_PATH =
            "/sap/opu/odata/SAP/API_MATERIAL_DOCUMENT_SRV";
    private static final String ENTITY_SET   = "A_MaterialDocumentHeader";

    // GoodsMovementCode: 01 = Goods Receipt for Purchase Order
    private static final String GM_CODE      = "01";
    // GoodsMovementRefDocType: B = Goods movement for purchase order
    private static final String REF_DOC_TYPE = "B";
    // Tipo movimento
    private static final String MV_TYPE      = "101";

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String authHeader;
    private final HttpClient http;

    // -------------------------------------------------------------------------
    // Costruttore — legge le credenziali da ccee_config.properties
    // -------------------------------------------------------------------------

    public GoodsReceiptClient() {
        Properties props = loadConfig();
        String url      = props.getProperty("s4.base.url", "").replaceAll("/$", "");
        String username = props.getProperty("s4.username", "");
        String password = props.getProperty("s4.password", "");

        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            throw new GoodsReceiptException(
                "Configurazione S/4HC incompleta: verificare s4.base.url, s4.username, s4.password");
        }

        this.baseUrl    = url;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.http       = HttpClient.newBuilder()
                .cookieHandler(new java.net.CookieManager(
                        null, java.net.CookiePolicy.ACCEPT_ALL))
                .build();

        log.info("GoodsReceiptClient inizializzato: baseUrl={} user={}", url, username);
    }

    // -------------------------------------------------------------------------
    // API pubblica
    // -------------------------------------------------------------------------

    /**
     * Registra un Goods Receipt su S/4HC a fronte delle righe fornite.
     *
     * Le righe vengono raggruppate per (ebeln, ebelp, etenr) — ogni combinazione
     * diventa una posizione del documento materiale. Se più righe EKET hanno lo
     * stesso OdA/posizione/schedulazione ma DDT o lotto diversi, vengono emesse
     * come posizioni separate nel documento materiale (SAP le tratta come split
     * di ricevimento).
     *
     * @param uuid  bemid dello scarico (usato solo per il logging)
     * @param righe righe caricate da tabfcsmseg / tabfcseket
     * @return mblnr numero documento materiale SAP (es. "5000000042")
     * @throws GoodsReceiptException se la chiamata fallisce o SAP risponde con errore
     */
    public String postGoodsReceipt(String uuid, List<EketRiga> righe) {
        log.info("postGoodsReceipt: avvio GR per uuid={} ({} righe)", uuid, righe.size());

        // 1. Ottieni il CSRF token
        String csrfToken = fetchCsrfToken();
        log.debug("CSRF token ottenuto");

        // 2. Costruisci il payload JSON
        String payload = buildPayload(righe);
        log.debug("Payload GR:\n{}", payload);

        // 3. POST per creare il documento materiale
        String mblnr = postDocument(csrfToken, payload);
        log.info("postGoodsReceipt: GR registrata per uuid={} → mblnr={}", uuid, mblnr);
        return mblnr;
    }

    // -------------------------------------------------------------------------
    // Step 1: CSRF token fetch
    // -------------------------------------------------------------------------

    /**
     * Esegue una GET con header "X-CSRF-Token: Fetch" per ottenere il token
     * da usare nella POST successiva.
     * SAP S/4HC richiede questo handshake per tutte le operazioni di scrittura.
     */
    private String fetchCsrfToken() {
        String url = baseUrl + SERVICE_PATH + "/$metadata";  // ← aggiunto
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization",  authHeader)
                    .header("X-CSRF-Token",   "Fetch")
                    .header("Accept",         "application/xml")
                    .header("sap-language",   "IT")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("CSRF fetch: HTTP {}", resp.statusCode());

            // Il token è nell'header della risposta
            String token = resp.headers().firstValue("x-csrf-token").orElse(null);
            if (token == null || token.isBlank()) {
                throw new GoodsReceiptException(
                    "CSRF token non restituito da S/4HC (HTTP " + resp.statusCode() + ")");
            }
            return token;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoodsReceiptException("Errore fetch CSRF token: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Step 2: costruzione payload
    // -------------------------------------------------------------------------

    /**
     * Costruisce il payload JSON OData V2 per la creazione del documento materiale.
     *
     * Struttura:
     * {
     *   "DocumentDate"    : "YYYY-MM-DD",
     *   "PostingDate"     : "YYYY-MM-DD",
     *   "GoodsMovementCode": "01",
     *   "ReferenceDocument": "<in_xblnr del primo DDT>",
     *   "to_MaterialDocumentItem": {
     *     "results": [
     *       {
     *         "Material"              : "<matnr>",
     *         "Plant"                 : "<in_werks o werks>",
     *         "StorageLocation"       : "<in_lgort o lgort>",
     *         "GoodsMovementType"     : "101",
     *         "GoodsMovementRefDocType": "B",
     *         "PurchaseOrder"         : "<ebeln>",
     *         "PurchaseOrderItem"     : "<ebelp (5 cifre)>",
     *         "QuantityInEntryUnit"   : <in_menge>,
     *         "EntryUnit"             : "<meins>",
     *         "Batch"                 : "<in_charg>",  // solo se xchpf=true
     *         "ExternalDeliveryNoteNumber": "<in_xblnr>" // DDT fornitore
     *       },
     *       ...
     *     ]
     *   }
     * }
     *
     * Nota: ReferenceDocument nell'header è il numero DDT globale (opzionale,
     * preso dal primo DDT trovato). Ogni posizione può avere il suo in_xblnr
     * in ExternalDeliveryNoteNumber.
     */
    private String buildPayload(List<EketRiga> righe) {
        try {
            String today = java.time.LocalDateTime.now().format(DATE_FMT);

            // Header del documento materiale
            ObjectNode header = JSON.createObjectNode();
            header.put("DocumentDate",      today);
            header.put("PostingDate",       today);
            header.put("GoodsMovementCode", GM_CODE);

            // ReferenceDocument = primo DDT non null (campo opzionale ma utile)
            righe.stream()
                 .map(r -> r.inXblnr)
                 .filter(x -> x != null && !x.isBlank())
                 .findFirst()
                 .ifPresent(ddt -> header.put("ReferenceDocument", ddt));

            // Posizioni
            ArrayNode items = JSON.createArrayNode();
            int itemCounter = 0;
            for (EketRiga r : righe) {
                itemCounter++;
                ObjectNode item = JSON.createObjectNode();

                item.put("Material",               nvl(r.matnr, ""));
                item.put("Plant",                  nvl(r.inWerks, r.werks));
                item.put("StorageLocation",        nvl(r.inLgort, r.lgort));
                item.put("GoodsMovementType",      MV_TYPE);
                item.put("GoodsMovementRefDocType", REF_DOC_TYPE);
                item.put("PurchaseOrder",           padEbeln(r.ebeln));
                item.put("PurchaseOrderItem",       padEbelp(r.ebelp));

                // Quantità — SAP OData V2 vuole stringa, non numero
                String qty = r.inMenge != null
                        ? new java.math.BigDecimal(r.inMenge)
                            .setScale(3, java.math.RoundingMode.HALF_UP)
                            .toPlainString()
                        : "0.000";
                item.put("QuantityInEntryUnit", qty);
                item.put("EntryUnit",           nvl(r.meins, ""));

                // DDT fornitore — portato in ReferenceDocument nell'header.
                // ExternalDeliveryNoteNumber non è supportato in scrittura dall'API.

                // Batch management
                if (Boolean.TRUE.equals(r.xchpf)) {
                    if (r.inCharg != null && !r.inCharg.isBlank()) {
                        item.put("Batch", r.inCharg);
                    } else {
                        log.warn("buildPayload: riga {}/{}/{} è a lotto (xchpf=true) " +
                                 "ma in_charg è null — la riga viene inviata senza batch",
                                 r.ebeln, r.ebelp, r.etenr);
                    }
                }

                // Numero item interno (non è la posizione OdA, è il counter OData)
                item.put("MaterialDocumentItemText",
                         "EKET " + r.ebeln + "/" + r.ebelp + "/" + r.etenr);

                log.debug("buildPayload: item {} — PO={} item={} matnr={} qty={} batch={}",
                          itemCounter, r.ebeln, r.ebelp, r.matnr, qty,
                          Boolean.TRUE.equals(r.xchpf) ? r.inCharg : "(no batch)");

                items.add(item);
            }

            if (itemCounter == 0) {
                throw new GoodsReceiptException("Nessuna posizione da inviare per la GR");
            }

            // to_MaterialDocumentItem come array diretto (non oggetto con "results")
            // come da payload di esempio ufficiali SAP
            header.set("to_MaterialDocumentItem", items);

            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(header);

        } catch (IOException e) {
            throw new GoodsReceiptException("Errore serializzazione payload GR: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Step 3: POST del documento
    // -------------------------------------------------------------------------

    /**
     * Esegue la POST OData e restituisce il mblnr del documento creato.
     * In caso di errore HTTP o risposta con messaggi di errore SAP, lancia
     * GoodsReceiptException con il dettaglio dell'errore SAP.
     */
    private String postDocument(String csrfToken, String payload) {
        String url = baseUrl + SERVICE_PATH + "/" + ENTITY_SET + "?sap-client=100";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization",  authHeader)
                    .header("X-CSRF-Token",   csrfToken)
                    .header("Content-Type",   "application/json")
                    .header("Accept",         "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("POST GR: HTTP {} body={}", resp.statusCode(),
                      resp.body() != null && resp.body().length() > 500
                          ? resp.body().substring(0, 500) + "..." : resp.body());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                String sapError = extractSapError(resp.body(), payload);
                throw new GoodsReceiptException(
                    "S/4HC ha rifiutato la GR (HTTP " + resp.statusCode() + "): " + sapError);
            }

            return extractMblnr(resp.body());

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoodsReceiptException("Errore comunicazione S/4HC: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Estrazione mblnr dalla risposta
    // -------------------------------------------------------------------------

    /**
     * Estrae il numero documento materiale (mblnr) dalla risposta OData.
     *
     * La risposta di una POST OData V2 a API_MATERIAL_DOCUMENT_SRV ha la struttura:
     * {
     *   "d": {
     *     "MaterialDocument"     : "5000000042",
     *     "MaterialDocumentYear" : "2025",
     *     ...
     *   }
     * }
     */
    private String extractMblnr(String responseBody) {
        try {
            JsonNode root = JSON.readTree(responseBody);
            JsonNode d    = root.path("d");
            String mblnr  = d.path("MaterialDocument").asText(null);
            String mjahr  = d.path("MaterialDocumentYear").asText(null);

            if (mblnr == null || mblnr.isBlank()) {
                throw new GoodsReceiptException(
                    "Risposta S/4HC non contiene MaterialDocument: " + responseBody);
            }

            log.info("GR creata: mblnr={} mjahr={}", mblnr, mjahr);
            return mblnr.trim();

        } catch (IOException e) {
            throw new GoodsReceiptException(
                "Impossibile parsare la risposta S/4HC: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Estrazione messaggio di errore SAP dalla risposta
    // -------------------------------------------------------------------------

    /**
     * Cerca di estrarre un messaggio leggibile dall'errore OData SAP.
     *
     * Struttura tipica di un errore OData:
     * {
     *   "error": {
     *     "code"   : "...",
     *     "message": { "value": "Messaggio di errore" },
     *     "innererror": { ... }
     *   }
     * }
     */
    private String extractSapError(String body, String payload) {
        String sapMsg;
        if (body == null || body.isBlank()) {
            sapMsg = "(nessun dettaglio)";
        } else {
            try {
                JsonNode root = JSON.readTree(body);
                JsonNode msg  = root.path("error").path("message").path("value");
                if (!msg.isMissingNode() && !msg.asText().isBlank()) {
                    sapMsg = msg.asText();
                } else {
                    sapMsg = body.length() > 300 ? body.substring(0, 300) + "..." : body;
                }
            } catch (Exception e) {
                sapMsg = body.length() > 300 ? body.substring(0, 300) + "..." : body;
            }
        }
        // DEBUG: include il payload inviato — rimuovere dopo il go-live
        return sapMsg + "\n\n=== PAYLOAD INVIATO ===\n" + payload;
    }

    // -------------------------------------------------------------------------
    // Caricamento configurazione
    // -------------------------------------------------------------------------

    private Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("eone/ccee_config.properties")) {
            if (is == null) {
                throw new GoodsReceiptException(
                    "ccee_config.properties non trovato nel classpath");
            }
            props.load(is);
        } catch (IOException e) {
            throw new GoodsReceiptException("Errore lettura ccee_config.properties", e);
        }
        return props;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /** Null-safe fallback: restituisce a se non null/blank, altrimenti b. */
    private String nvl(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    /**
     * EBELN in SAP è 10 caratteri — API_MATERIAL_DOCUMENT_SRV lo vuole senza
     * padding (non zero-padded a sinistra). Trimma eventuali spazi.
     */
    private String padEbeln(String ebeln) {
        return ebeln != null ? ebeln.trim() : "";
    }

    /**
     * EBELP (posizione OdA) in SAP è 5 caratteri numerici zero-padded.
     * Alcune sorgenti lo restituiscono come "10" o "00010" — normalizziamo
     * a 5 cifre (es. "00010") perché l'API V2 lo richiede in questo formato.
     */
    private String padEbelp(String ebelp) {
        if (ebelp == null || ebelp.isBlank()) return "00010";
        try {
            return String.format("%05d", Integer.parseInt(ebelp.trim()));
        } catch (NumberFormatException e) {
            return ebelp.trim(); // non numerico: lascia com'è
        }
    }
}
