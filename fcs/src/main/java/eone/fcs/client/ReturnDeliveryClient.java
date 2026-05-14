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
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/**
 * Client per la registrazione di Resi da cliente su S/4HC tramite
 * API_OUTBOUND_DELIVERY_SRV (OData V2).
 *
 * Communication Scenario richiesto: SAP_COM_0106 (Outbound Delivery Integration)
 *
 * Flusso:
 *   1. GET  .../$metadata            → X-CSRF-Token
 *   2. POST .../A_OutbDeliveryHeader → crea consegna reso (tipo da config)
 *   3. POST .../PostGoodsIssue       → PGI della consegna (genera mblnr)
 *
 * Configurazione (da ccee_config.properties):
 *   s4.base.url        = https://myXXXXXX-api.s4hana.cloud.sap
 *   s4.username        = COMM_USER_XXXX
 *   s4.password        = ****
 *   reso.delivery.type = LR   ← TODO: aggiornare con tipo Z confermato dal cliente
 *
 * Gestione errori:
 *   Qualsiasi risposta HTTP non 2xx, o errore di rete, lancia ReturnDeliveryException.
 *   Il chiamante (EketResource.handleF) intercetta l'eccezione, marca wmsst='E'
 *   e restituisce HTTP 400 al WMS.
 */
public class ReturnDeliveryClient {

    private static final Logger log = LoggerFactory.getLogger(ReturnDeliveryClient.class);

    private static final String SERVICE_PATH  = "/sap/opu/odata/sap/API_OUTBOUND_DELIVERY_SRV;v=0002";
    private static final String ENTITY_HEADER = "A_OutbDeliveryHeader";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String authHeader;
    private final String deliveryTypeReso;
    private final HttpClient http;

    // -------------------------------------------------------------------------
    // Costruttore — legge le credenziali e il tipo consegna da ccee_config.properties
    // -------------------------------------------------------------------------

    public ReturnDeliveryClient() {
        Properties props = loadConfig();

        String url      = props.getProperty("s4.base.url", "").replaceAll("/$", "");
        String username = props.getProperty("s4.username", "");
        String password = props.getProperty("s4.password", "");

        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            throw new ReturnDeliveryException(
                "Configurazione S/4HC incompleta: verificare s4.base.url, s4.username, s4.password");
        }

        this.baseUrl          = url;
        this.authHeader       = "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.deliveryTypeReso = props.getProperty("reso.delivery.type", "LR");
        this.http             = HttpClient.newBuilder()
                .cookieHandler(new java.net.CookieManager(
                        null, java.net.CookiePolicy.ACCEPT_ALL))
                .build();

        log.info("ReturnDeliveryClient inizializzato: baseUrl={} user={} deliveryType={}",
                 url, username, deliveryTypeReso);
    }

    // -------------------------------------------------------------------------
    // API pubblica
    // -------------------------------------------------------------------------

    /**
     * Crea una consegna reso su S/4HC e ne esegue il PGI.
     *
     * @param uuid  bemid dello scarico (usato per il logging)
     * @param righe righe caricate da tabfcseket con kappl='V'
     * @return mblnr numero documento materiale generato dal PGI
     * @throws ReturnDeliveryException se la creazione consegna o il PGI falliscono
     */
    public String postReturnDelivery(String uuid, List<EketRiga> righe) {
        log.info("postReturnDelivery: avvio per uuid={} ({} righe) deliveryType={}",
                 uuid, righe.size(), deliveryTypeReso);

        // 1. CSRF token
        String csrfToken = fetchCsrfToken();
        log.debug("CSRF token ottenuto");

        // 2. Crea la consegna reso
        String deliveryDoc = createDelivery(csrfToken, righe, uuid);
        log.info("postReturnDelivery: consegna reso creata — deliveryDoc={}", deliveryDoc);

        // 3. PGI — genera il documento materiale
        String mblnr = postGoodsIssue(csrfToken, deliveryDoc, uuid);
        log.info("postReturnDelivery: PGI completato per uuid={} deliveryDoc={} mblnr={}",
                 uuid, deliveryDoc, mblnr);

        return mblnr;
    }

    // -------------------------------------------------------------------------
    // Step 1: CSRF token fetch
    // -------------------------------------------------------------------------

    private String fetchCsrfToken() {
        String url = baseUrl + SERVICE_PATH + "/$metadata";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", authHeader)
                    .header("X-CSRF-Token",  "Fetch")
                    .header("Accept",        "application/xml")
                    .header("sap-language",  "IT")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("CSRF fetch: HTTP {}", resp.statusCode());

            String token = resp.headers().firstValue("x-csrf-token").orElse(null);
            if (token == null || token.isBlank()) {
                throw new ReturnDeliveryException(
                    "CSRF token non restituito da S/4HC (HTTP " + resp.statusCode() + ")");
            }
            return token;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReturnDeliveryException("Errore fetch CSRF token: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Step 2: creazione consegna reso
    // -------------------------------------------------------------------------

    private String createDelivery(String csrfToken, List<EketRiga> righe, String uuid) {
        String payload = buildDeliveryPayload(righe);
        log.debug("Payload consegna reso:\n{}", payload);

        String url = baseUrl + SERVICE_PATH + "/" + ENTITY_HEADER;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization",  authHeader)
                    .header("X-CSRF-Token",   csrfToken)
                    .header("Content-Type",   "application/json")
                    .header("Accept",         "application/json")
                    .header("sap-language",   "IT")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("POST consegna reso: HTTP {} body={}",
                      resp.statusCode(), truncate(resp.body(), 500));

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new ReturnDeliveryException(
                    "S/4HC ha rifiutato la creazione consegna reso (HTTP " +
                    resp.statusCode() + "): " + extractSapError(resp.body(), payload));
            }

            return extractDeliveryDoc(resp.body());

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReturnDeliveryException(
                "Errore comunicazione S/4HC (creazione consegna): " + e.getMessage(), e);
        }
    }

    private String buildDeliveryPayload(List<EketRiga> righe) {
        try {
            EketRiga prima = righe.get(0);

            ObjectNode header = JSON.createObjectNode();
            header.put("ShippingPoint", nvl(prima.inWerks, prima.werks));

            ArrayNode items = JSON.createArrayNode();
            for (EketRiga r : righe) {
                ObjectNode item = JSON.createObjectNode();

                item.put("ReferenceSDDocument",     nvl(r.ebeln, "").trim());
                item.put("ReferenceSDDocumentItem", padPosnr(r.ebelp));

                String qty = r.inMenge != null
                        ? new java.math.BigDecimal(r.inMenge)
                              .setScale(3, java.math.RoundingMode.HALF_UP)
                              .toPlainString()
                        : "0.000";
                item.put("ActualDeliveryQuantity", qty);
                item.put("DeliveryQuantityUnit",   nvl(r.meins, ""));

                log.debug("buildDeliveryPayload: OdV={} pos={} matnr={} qty={}",
                          r.ebeln, r.ebelp, r.matnr, qty);
                items.add(item);
            }

            if (items.isEmpty()) {
                throw new ReturnDeliveryException(
                    "Nessuna posizione da inviare per la consegna reso");
            }

            header.set("to_DeliveryDocumentItem", items);
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(header);

        } catch (IOException e) {
            throw new ReturnDeliveryException(
                "Errore serializzazione payload consegna reso: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Step 3: PGI (Post Goods Issue)
    // -------------------------------------------------------------------------

    private String postGoodsIssue(String csrfToken, String deliveryDoc, String uuid) {
        String entityKey = "'" + deliveryDoc + "'";
        String url = baseUrl + SERVICE_PATH + "/" + ENTITY_HEADER +
                     "(" + entityKey + ")/PostGoodsIssue";

        log.info("postGoodsIssue: PGI per deliveryDoc={} uuid={}", deliveryDoc, uuid);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization",  authHeader)
                    .header("X-CSRF-Token",   csrfToken)
                    .header("Content-Type",   "application/json")
                    .header("Accept",         "application/json")
                    .header("sap-language",   "IT")
                    .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("PGI: HTTP {} body={}", resp.statusCode(), truncate(resp.body(), 500));

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new ReturnDeliveryException(
                    "S/4HC ha rifiutato il PGI per consegna " + deliveryDoc +
                    " (HTTP " + resp.statusCode() + "): " +
                    extractSapError(resp.body(), "{}"));
            }

            return extractMblnrFromPgi(resp.body(), deliveryDoc);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReturnDeliveryException(
                "Errore comunicazione S/4HC (PGI): " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Estrazione campi dalla risposta
    // -------------------------------------------------------------------------

    private String extractDeliveryDoc(String body) {
        try {
            JsonNode d = JSON.readTree(body).path("d");
            String doc = d.path("DeliveryDocument").asText(null);
            if (doc == null || doc.isBlank()) {
                throw new ReturnDeliveryException(
                    "Risposta S/4HC non contiene DeliveryDocument: " + body);
            }
            return doc.trim();
        } catch (IOException e) {
            throw new ReturnDeliveryException(
                "Impossibile parsare la risposta creazione consegna: " + e.getMessage(), e);
        }
    }

    private String extractMblnrFromPgi(String body, String deliveryDoc) {
        try {
            JsonNode d = JSON.readTree(body).path("d");
            String mblnr = d.path("GoodsIssueOrGoodsReceiptSlipNumber").asText(null);
            if (mblnr != null && !mblnr.isBlank() && !"0000000000".equals(mblnr.trim())) {
                log.info("PGI: mblnr estratto = {}", mblnr.trim());
                return mblnr.trim();
            }
            // Fallback: SAP non restituisce sempre mblnr nella risposta PGI
            log.warn("PGI: GoodsIssueOrGoodsReceiptSlipNumber non presente per " +
                     "deliveryDoc={} — uso numero consegna come mblnr di fallback", deliveryDoc);
            return deliveryDoc;
        } catch (IOException e) {
            throw new ReturnDeliveryException(
                "Impossibile parsare la risposta PGI: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Estrazione errore SAP
    // -------------------------------------------------------------------------

    private String extractSapError(String body, String payload) {
        if (body == null || body.isBlank()) return "(nessun dettaglio)";
        try {
            JsonNode msg = JSON.readTree(body).path("error").path("message").path("value");
            String sapMsg = (!msg.isMissingNode() && !msg.asText().isBlank())
                    ? msg.asText()
                    : truncate(body, 300);
            // DEBUG: include il payload — rimuovere dopo il go-live
            return sapMsg + "\n\n=== PAYLOAD INVIATO ===\n" + payload;
        } catch (Exception e) {
            return truncate(body, 300);
        }
    }

    // -------------------------------------------------------------------------
    // Caricamento configurazione
    // -------------------------------------------------------------------------

    private Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("eone/ccee_config.properties")) {
            if (is == null) {
                throw new ReturnDeliveryException(
                    "ccee_config.properties non trovato nel classpath");
            }
            props.load(is);
        } catch (IOException e) {
            throw new ReturnDeliveryException(
                "Errore lettura ccee_config.properties", e);
        }
        return props;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private String nvl(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private String padPosnr(String posnr) {
        if (posnr == null || posnr.isBlank()) return "000010";
        try { return String.format("%06d", Integer.parseInt(posnr.trim())); }
        catch (NumberFormatException e) { return posnr.trim(); }
    }

    private String truncate(String s, int max) {
        if (s == null) return "(null)";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
