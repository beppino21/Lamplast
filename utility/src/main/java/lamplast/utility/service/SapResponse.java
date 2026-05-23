package lamplast.utility.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SapResponse {

    private int     httpStatus;
    private boolean success;
    private boolean warning;
    private boolean frozen;     // SAP ha risposto OK ma la modifica non è stata applicata

    private String sapCode;
    private String sapMessage;  // messaggio sintetico (solo severity warning/error)
    private String transactionId;
    private String rawBody;

    /**
     * Per INSERT riusciti (HTTP 201): numero schedulazione assegnato da SAP
     * (campo d.ScheduleLine). Null se non presente o non applicabile.
     */
    private String createdScheduleLine;

    // =========================
    // COSTRUTTORI
    // =========================

    public SapResponse(int httpStatus) {
        this.httpStatus = httpStatus;
        this.success    = httpStatus >= 200 && httpStatus < 300;
    }

    // =========================
    // GETTER
    // =========================

    public int     getHttpStatus()           { return httpStatus; }
    public boolean isSuccess()               { return success; }
    public boolean isWarning()               { return warning; }
    public boolean isFrozen()                { return frozen; }
    public String  getSapCode()              { return sapCode; }
    public String  getSapMessage()           { return sapMessage; }
    public String  getTransactionId()        { return transactionId; }
    public String  getRawBody()              { return rawBody; }
    public String  getCreatedScheduleLine()  { return createdScheduleLine; }

    public void setFrozen(boolean frozen)       { this.frozen = frozen; }
    public void setSapMessage(String sapMessage){ this.sapMessage = sapMessage; }

    // =========================
    // PARSING BODY
    // =========================

    @SuppressWarnings("unchecked")
    public void parseBody(String body) {

        this.rawBody = body;

        if (body == null || body.isBlank() || body.startsWith("<")) return;

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

            Map<String, Object> json = mapper.readValue(body, Map.class);

            // --- CASO ERRORE ODATA ---
            if (json.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) json.get("error");
                this.success  = false;
                this.sapCode  = (String) error.get("code");

                Map<String, Object> message = (Map<String, Object>) error.get("message");
                if (message != null) {
                    this.sapMessage = (String) message.get("value");
                }

                Map<String, Object> inner = (Map<String, Object>) error.get("innererror");
                if (inner != null) {
                    this.transactionId = (String) inner.get("transactionid");
                }
            }

            // --- CASO SUCCESSO (2xx) con body "d" ---
            if (json.containsKey("d") && this.httpStatus >= 200 && this.httpStatus < 300) {
                this.success = true;

                // Per POST 201: estrai il numero schedulazione assegnato da SAP
                if (this.httpStatus == 201) {
                    Map<String, Object> d = (Map<String, Object>) json.get("d");
                    if (d != null) {
                        Object sl = d.get("ScheduleLine");
                        if (sl != null) {
                            // SAP restituisce "0001", "0002" ecc. — manteniamo il formato
                            this.createdScheduleLine = sl.toString();
                        }
                    }
                }
            }

        } catch (Exception e) {
            this.sapMessage = "Errore parsing risposta SAP: " + e.getMessage();
        }
    }

    // =========================
    // PARSING HEADERS
    // =========================

    @SuppressWarnings("unchecked")
    public void parseHeaders(Map<String, java.util.List<String>> headers) {

        if (headers == null) return;

        // sap-message: array JSON di messaggi OData
        if (headers.containsKey("sap-message")) {
            String rawMsg = headers.get("sap-message").get(0);

            // SLS_LORD/025 "SLINE_DATE not an input field" — rumore strutturale dell'API,
            // ignorato sempre (già documentato nelle versioni precedenti).
            boolean isKnownNoise = rawMsg.contains("SLS_LORD/025")
                                && rawMsg.contains("SLINE_DATE");

            if (!isKnownNoise) {
                String filtered = filterSapMessages(rawMsg);
                if (filtered != null && !filtered.isBlank()) {
                    if (this.httpStatus >= 200 && this.httpStatus < 300) {
                        this.warning    = true;
                        this.sapMessage = filtered;
                    } else {
                        // Su errore HTTP il messaggio integra quello del body
                        if (this.sapMessage == null) this.sapMessage = filtered;
                    }
                }
            }
        }

        // transactionid (a volte è header)
        if (headers.containsKey("transactionid")) {
            this.transactionId = headers.get("transactionid").get(0);
        }
    }

    // =========================
    // UTILITY — filtro messaggi
    // =========================

    /**
     * Estrae dal JSON array sap-message solo i messaggi con severity
     * "warning" o "error", ricorsivamente anche nei "details".
     * Restituisce una stringa multi-riga leggibile, o null se non ci sono
     * messaggi rilevanti.
     *
     * I messaggi "noise" noti vengono soppressi:
     *   - SLS_LORD/009  "Document is incomplete"  (wrapper generico)
     *   - SLS_LORD/099  "Consider the subsequent documents"  (info strutturale)
     *   - SLS_LORD/023  messaggi di credito/scaduto (fuori scope dell'integrazione)
     *   - V1/311        "Standard Order X has been saved"  (esito positivo, ridondante)
     *   - V1/399        "Date is in the past"  (warning prevedibile, non azionabile)
     */
    @SuppressWarnings("unchecked")
    private String filterSapMessages(String rawJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

            // Il header sap-message può essere un array o un singolo oggetto
            List<Map<String, Object>> msgs;
            String trimmed = rawJson.trim();
            if (trimmed.startsWith("[")) {
                msgs = mapper.readValue(trimmed, List.class);
            } else {
                msgs = new ArrayList<>();
                msgs.add(mapper.readValue(trimmed, Map.class));
            }

            List<String> relevant = new ArrayList<>();
            for (Map<String, Object> msg : msgs) {
                collectRelevant(msg, relevant);
            }

            return relevant.isEmpty() ? null : String.join(" | ", relevant);

        } catch (Exception e) {
            // Parsing fallito: restituisco il raw JSON troncato
            return rawJson.length() > 200 ? rawJson.substring(0, 200) + "…" : rawJson;
        }
    }

    /** Raccoglie ricorsivamente i messaggi rilevanti (warning/error, non noise). */
    @SuppressWarnings("unchecked")
    private void collectRelevant(Map<String, Object> msg, List<String> out) {
        if (msg == null) return;

        String code     = String.valueOf(msg.getOrDefault("code",     ""));
        String severity = String.valueOf(msg.getOrDefault("severity", ""));
        String text     = String.valueOf(msg.getOrDefault("message",  ""));

        boolean isRelevantSeverity = severity.equals("warning") || severity.equals("error");
        boolean isNoise = isKnownNoise(code, text);

        if (isRelevantSeverity && !isNoise && !text.isBlank()) {
            out.add("[" + code + "] " + text);
        }

        // Ricorsione sui details
        Object details = msg.get("details");
        if (details instanceof List) {
            for (Object d : (List<?>) details) {
                if (d instanceof Map) {
                    collectRelevant((Map<String, Object>) d, out);
                }
            }
        }
    }

    /**
     * Codici/testi da sopprimere perché ridondanti o fuori scope.
     */
    private boolean isKnownNoise(String code, String text) {
        if (code == null || text == null) return false;
        // Wrapper generico "Document is incomplete"
        if (code.equals("SLS_LORD/009")) return true;
        // Info strutturale "Consider subsequent documents"
        if (code.equals("SLS_LORD/099")) return true;
        // Messaggi di credito/scaduto (fuori scope dell'integrazione)
        if (code.equals("SLS_LORD/023")) return true;
        // Ordine salvato (esito positivo, ridondante)
        if (code.equals("V1/311"))       return true;
        // Data nel passato (prevedibile, non azionabile dall'integrazione)
        if (code.equals("V1/399"))       return true;
        // Rumore strutturale data schedulazione
        if (code.equals("SLS_LORD/025") && text.contains("SLINE_DATE")) return true;
        return false;
    }

    // =========================
    // UTIL
    // =========================

    /**
     * Messaggio leggibile per la colonna "Messaggio SAP" della griglia.
     * Priorità: sapMessage > "HTTP {status}" come fallback.
     * Tronca a maxLen caratteri per non rompere il layout.
     */
    public String getDisplayMessage(int maxLen) {
        if (sapMessage != null && !sapMessage.isBlank()) {
            return sapMessage.length() > maxLen
                ? sapMessage.substring(0, maxLen) + "…"
                : sapMessage;
        }
        if (!success) return "HTTP " + httpStatus;
        return "";
    }

    @Override
    public String toString() {
        return "SapResponse{httpStatus=" + httpStatus
             + ", success=" + success
             + ", warning=" + warning
             + ", sapCode='" + sapCode + '\''
             + ", sapMessage='" + sapMessage + '\''
             + ", createdScheduleLine='" + createdScheduleLine + '\''
             + ", transactionId='" + transactionId + '\'' + '}';
    }
}
