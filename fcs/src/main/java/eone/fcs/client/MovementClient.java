package eone.fcs.client;

import eone.fcs.repository.MovSapRepository.MovsapRiga;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

/**
 * Registra movimenti MM su S/4HC tramite API_MATERIAL_DOCUMENT_SRV (OData V2).
 * Communication Scenario richiesto sul tenant: SAP_COM_0108.
 *
 * Replica la logica di ZFCS_MOV_SAP:
 *  - bwart 309 / 311       → movimento diretto (FORM movimento_merci)
 *  - bwart 551/552/561/562 → rettifica quantità con delta (FORM aggiusta_quantita)
 *
 * Credenziali lette da eone/ccee_config.properties (stessa convenzione di GoodsReceiptClient).
 */
public class MovementClient {

    private static final String API_PATH =
            "/sap/opu/odata/sap/API_MATERIAL_DOCUMENT_SRV/A_MaterialDocumentHeader";

    private final String baseUrl;
    private final String username;
    private final String password;
    private final HttpClient httpClient;

    public MovementClient() {
        Properties props = loadConfig();
        this.baseUrl  = props.getProperty("s4.base.url");
        this.username = props.getProperty("s4.username");
        this.password = props.getProperty("s4.password");
        // CookieManager ACCEPT_ALL: SAP valida il CSRF sulla stessa sessione
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
    }

    // -----------------------------------------------------------------------
    // Entry point pubblico
    // -----------------------------------------------------------------------

    /**
     * Esegue il movimento MM corrispondente alla riga di staging.
     * Sceglie automaticamente la logica in base al bwart.
     *
     * @param riga la riga letta da TABFCSMOVSAP
     * @return MovementResult con mblnr e mjahr
     * @throws MovementException in caso di errore SAP o di comunicazione
     */
    public MovementResult postMovement(MovsapRiga riga) {
        String bwart = riga.bwart == null ? "" : riga.bwart;

        switch (bwart) {
            case "309":
            case "311":
                return postMovimentoDiretto(riga);

            case "551":
            case "552":
            case "561":
            case "562":
                return postRettificaQuantita(riga);

            default:
                throw new MovementException("Tipo movimento non gestito: " + bwart);
        }
    }

    // -----------------------------------------------------------------------
    // FORM movimento_merci → bwart 309 / 311
    // -----------------------------------------------------------------------

    private MovementResult postMovimentoDiretto(MovsapRiga r) {
        // Validazione minima (coerente con HANDLE_REQUEST.abap)
        if (isEmpty(r.werks) || isEmpty(r.lgort) || isEmpty(r.matnr)) {
            throw new MovementException("Campi obbligatori mancanti per movimento diretto (werks/lgort/matnr)");
        }

        long nowMillis = java.time.Instant.now().toEpochMilli();
        String odataDate = "/Date(" + nowMillis + ")/";
        String headerText = "";
        String refDoc     = "ID:" + (r.movid != null ? r.movid.substring(0, 10) : "");

        // Determina i campi destinazione secondo la logica ABAP
        // bwart 309: werks_to/lgort_to = werks/lgort (stesso impianto/magazzino, cambia solo matnr)
        // bwart 311: werks_to/lgort_to espliciti
        String movePlant = isEmpty(r.werks_to) ? r.werks : r.werks_to;
        String moveSloc  = isEmpty(r.lgort_to) ? r.lgort : r.lgort_to;
        String moveMat   = isEmpty(r.matnr_to) ? r.matnr : r.matnr_to;
        String moveBatch = isEmpty(r.charg_to) ? r.charg : r.charg_to;

        String quantityStr = formatQuantity(r.menge);
        String itemText    = "ID FCS:" + (r.movid != null ? r.movid : "");

        String payload = buildPayload(
                odataDate, odataDate, headerText, refDoc,
                r.bwart, r.werks, r.lgort, r.matnr, r.charg,
                quantityStr, r.meins, itemText,
                movePlant, moveSloc, moveMat, moveBatch
        );

        return callSap(payload);
    }

    // -----------------------------------------------------------------------
    // FORM aggiusta_quantita → bwart 551/552/561/562
    // -----------------------------------------------------------------------

    private MovementResult postRettificaQuantita(MovsapRiga r) {
        if (isEmpty(r.werks) || isEmpty(r.lgort) || isEmpty(r.matnr)) {
            throw new MovementException("Campi obbligatori mancanti per rettifica quantità (werks/lgort/matnr)");
        }
        if (r.menge == null || r.menge_to == null) {
            throw new MovementException("menge e menge_to obbligatori per rettifica quantità");
        }

        // Logica ABAP: calcola bwart effettivo e quantità delta
        String bwartEffettivo;
        float delta;
        boolean isRottamazione = r.bwart.charAt(1) == '5'; // secondo char = '5' → 551/552

        if (r.menge > r.menge_to) {
            // Diminuzione
            bwartEffettivo = isRottamazione ? "551" : "562";
            delta = r.menge - r.menge_to;
        } else {
            // Aumento
            bwartEffettivo = isRottamazione ? "552" : "561";
            delta = r.menge_to - r.menge;
        }

        long nowMillis    = java.time.Instant.now().toEpochMilli();
        String odataDate  = "/Date(" + nowMillis + ")/";
        String headerText = "";
        String refDoc     = "ID:" + (r.movid != null ? r.movid.substring(0, 10) : "");
        String itemText   = "ID FCS:" + (r.movid != null ? r.movid : "");
        String quantityStr = formatQuantity(delta);

        // Rettifica: nessun campo _to (nessun trasferimento di magazzino/materiale)
        String payload = buildPayload(
                odataDate, odataDate, headerText, refDoc,
                bwartEffettivo, r.werks, r.lgort, r.matnr, r.charg,
                quantityStr, r.meins, itemText,
                null, null, null, null
        );

        return callSap(payload);
    }

    // -----------------------------------------------------------------------
    // HTTP: CSRF fetch + POST
    // -----------------------------------------------------------------------

    private MovementResult callSap(String payload) {
        try {
            // Step 1: GET /$metadata per CSRF token + cookie di sessione
            String metadataUrl = baseUrl + "/sap/opu/odata/sap/API_MATERIAL_DOCUMENT_SRV/$metadata";
            HttpRequest csrfReq = HttpRequest.newBuilder()
                    .uri(URI.create(metadataUrl))
                    .header("Authorization", basicAuth())
                    .header("X-CSRF-Token", "Fetch")
                    .GET()
                    .build();

            HttpResponse<String> csrfResp = httpClient.send(csrfReq,
                    HttpResponse.BodyHandlers.ofString());

            String csrfToken = csrfResp.headers()
                    .firstValue("x-csrf-token")
                    .orElseThrow(() -> new MovementException("CSRF token non ricevuto da S/4HC"));

            // Step 2: POST movimento
            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + API_PATH))
                    .header("Authorization", basicAuth())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-CSRF-Token", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> postResp = httpClient.send(postReq,
                    HttpResponse.BodyHandlers.ofString());

            int status = postResp.statusCode();
            String body = postResp.body();

            if (status != 200 && status != 201) {
                throw new MovementException(
                        "S/4HC ha risposto HTTP " + status + " per movimento MM. Body: " + body);
            }

            return parseMovementResult(body);

        } catch (MovementException e) {
            throw e;
        } catch (Exception e) {
            throw new MovementException("Errore chiamata S/4HC movimento MM: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Payload JSON
    // -----------------------------------------------------------------------

    /**
     * Costruisce il payload JSON per API_MATERIAL_DOCUMENT_SRV.
     * I campi _to (movePlant, moveSloc, moveMat, moveBatch) sono opzionali:
     * se null non vengono inclusi (rettifiche quantità).
     */
    private String buildPayload(
            String documentDate, String postingDate,
            String headerText, String refDocNo,
            String moveType,
            String plant, String stgeLoc, String material, String batch,
            String quantity, String entryUom,
            String itemText,
            String movePlant, String moveSloc, String moveMat, String moveBatch) {

        StringBuilder item = new StringBuilder();
        item.append("{");
        item.append("\"GoodsMovementType\":\"").append(nvl(moveType)).append("\",");
        item.append("\"Plant\":\"").append(nvl(plant)).append("\",");
        item.append("\"StorageLocation\":\"").append(nvl(stgeLoc)).append("\",");
        item.append("\"Material\":\"").append(nvl(material)).append("\",");
        item.append("\"Batch\":\"").append(nvl(batch)).append("\",");
        item.append("\"QuantityInEntryUnit\":\"").append(nvl(quantity)).append("\",");
        item.append("\"EntryUnit\":\"").append(nvl(entryUom)).append("\",");
        item.append("\"GoodsMovementRefDocType\":\" \",");
        item.append("\"MaterialDocumentItemText\":\"").append(escapeJson(itemText)).append("\"");

        if (!isEmpty(movePlant)) {
            item.append(",\"IssuingOrReceivingPlant\":\"").append(nvl(movePlant)).append("\"");
        }
        if (!isEmpty(moveSloc)) {
            item.append(",\"IssuingOrReceivingStorageLoc\":\"").append(nvl(moveSloc)).append("\"");
        }
        if (!isEmpty(moveMat)) {
            item.append(",\"IssgOrRcvgMaterial\":\"").append(nvl(moveMat)).append("\"");
        }
        if (!isEmpty(moveBatch)) {
            item.append(",\"IssgOrRcvgBatch\":\"").append(nvl(moveBatch)).append("\"");
        }

        item.append("}");

        return "{"
                + "\"DocumentDate\":\"" + documentDate + "\","
                + "\"PostingDate\":\"" + postingDate + "\","
                + "\"GoodsMovementCode\":\"06\","
                + "\"ReferenceDocument\":\"" + escapeJson(refDocNo) + "\","
                + "\"to_MaterialDocumentItem\":[" + item + "]"
                + "}";
    }

    // -----------------------------------------------------------------------
    // Parsing risposta
    // -----------------------------------------------------------------------

    /**
     * Estrae MaterialDocumentYear e MaterialDocumentHeader (mblnr / mjahr)
     * dalla risposta JSON di S/4HC.
     */
    private MovementResult parseMovementResult(String json) {
        String mblnr = extractJsonField(json, "MaterialDocument");
        String mjahr  = extractJsonField(json, "MaterialDocumentYear");

        if (mblnr == null || mblnr.isBlank()) {
            throw new MovementException(
                    "S/4HC non ha restituito MaterialDocument. Body: " + json);
        }
        return new MovementResult(mblnr, mjahr != null ? mjahr : "");
    }

    /**
     * Estrazione minima di un campo stringa da JSON senza dipendenze esterne.
     * Sufficiente per i campi scalari della risposta S/4HC.
     */
    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String basicAuth() {
        String cred = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(cred.getBytes());
    }

    private String formatQuantity(Float q) {
        if (q == null) return "0.000";
        return String.format(Locale.US, "%.3f", q);
    }

    private String formatQuantity(float q) {
        return String.format(Locale.US, "%.3f", q);
    }

    private boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Properties loadConfig() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("eone/ccee_config.properties")) {
            if (is == null)
                throw new MovementException("ccee_config.properties non trovato nel classpath");
            Properties p = new Properties();
            p.load(is);
            return p;
        } catch (IOException e) {
            throw new MovementException("Errore lettura ccee_config.properties", e);
        }
    }
}
