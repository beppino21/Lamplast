package lamplast.utility.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneOffset;
import lamplast.utility.config.SapConfiguration;
import lamplast.utility.model.ScheduleLineData;
import lamplast.utility.service.SapAuthenticationService.SapAuthToken;

/**
 * Gestisce le operazioni CRUD sulle schedulazioni SAP.
 * Include il metodo dryRun() per la verifica preventiva senza modifiche.
 */
public class SapScheduleLineService {

    private final SapConfiguration        config;
    private final SapAuthenticationService authService;
    private final HttpClient               httpClient;

    public SapScheduleLineService(SapConfiguration config) {
        this.config      = config;
        this.authService = new SapAuthenticationService(config);
        this.httpClient  = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    }

    // -------------------------------------------------------
    // DRY RUN — verifica preventiva senza modifiche
    // -------------------------------------------------------

    /**
     * Verifica preventiva: interroga SAP in sola lettura e restituisce
     * una descrizione di cosa accadrà (o perché fallirà).
     * Non esegue nessuna modifica su SAP.
     */
    public SapDryRunResult dryRun(ScheduleLineData data) throws Exception {

        String validationError = data.validate();
        if (validationError != null) {
            return SapDryRunResult.error("Dati non validi: " + validationError);
        }

        String paddedItem  = String.format("%06d", data.getItemNumber());
        String paddedSched = String.format("%04d", Math.abs(data.getScheduleLine()));

        if (data.isInsert()) {
            return checkPositionExists(data, paddedItem);
        } else {
            return checkScheduleLineExists(data.getOrderNumber(), paddedItem, paddedSched, data);
        }
    }

    /**
     * Verifica esistenza della posizione ordine (per inserimento).
     * Controlla anche che il materiale nel file coincida con quello SAP.
     * GET A_SalesOrderItem(SalesOrder='X', SalesOrderItem='000010')
     */
    private SapDryRunResult checkPositionExists(ScheduleLineData data,
                                                String paddedItem) throws Exception {
        String url = config.getSalesOrderApiUrl()
            + "A_SalesOrderItem(SalesOrder='" + data.getOrderNumber() + "'"
            + ",SalesOrderItem='" + paddedItem + "')"
            + "?$select=SalesOrder,SalesOrderItem,Material"
            + "&sap-client=" + config.getClient();

        HttpResponse<String> resp = doGet(url);

        if (resp.statusCode() == 200) {
            // Controllo materiale — tollerante agli zeri iniziali
            String materialMismatch = checkMaterialMatch(resp.body(), data.getMaterial());
            if (materialMismatch != null) {
                return SapDryRunResult.error(
                    "Posizione " + paddedItem + " — " + materialMismatch);
            }
            return SapDryRunResult.ok(
                "Posizione " + paddedItem + " esistente — inserimento possibile");
        } else if (resp.statusCode() == 404) {
            return SapDryRunResult.error(
                "Posizione " + paddedItem + " NON trovata su SAP — inserimento impossibile");
        } else {
            return SapDryRunResult.warning(
                "Posizione " + paddedItem + " — risposta SAP inattesa: HTTP " + resp.statusCode());
        }
    }

    /**
     * Confronta il materiale del file con quello presente sulla posizione SAP.
     * Il confronto è numerico/tollerante: "100012" == "000100012".
     *
     * @return null se i materiali coincidono, stringa di errore altrimenti.
     */
    @SuppressWarnings("unchecked")
    private String checkMaterialMatch(String body, String materialFromFile) {
        if (materialFromFile == null || materialFromFile.isBlank()) {
            // Materiale non comunicato nel file: controllo non applicabile
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> json = mapper.readValue(body, java.util.Map.class);
            java.util.Map<String, Object> d    =
                (java.util.Map<String, Object>) json.get("d");
            if (d == null) return null; // non leggibile, non blocchiamo

            String sapMaterial  = String.valueOf(d.getOrDefault("Material", "")).trim();
            String fileMaterial = materialFromFile.trim();

            // Normalizzazione: rimuoviamo leading zeros e confrontiamo
            String sapNorm  = sapMaterial.replaceFirst("^0+(?!$)", "");
            String fileNorm = fileMaterial.replaceFirst("^0+(?!$)", "");

            if (sapNorm.equalsIgnoreCase(fileNorm)) return null; // OK

            return "Materiale non corrispondente: file='" + fileMaterial
                 + "' SAP='" + sapMaterial + "'";
        } catch (Exception e) {
            return null; // parsing fallito: non blocchiamo
        }
    }

    /**
     * Verifica esistenza della schedulazione (per modifica/eliminazione).
     * Prima controlla la coerenza del materiale sulla posizione (bloccante),
     * poi verifica schedulazione, quantità e data.
     */
    private SapDryRunResult checkScheduleLineExists(String order,
                                                    String paddedItem,
                                                    String paddedSched,
                                                    ScheduleLineData data) throws Exception {

        // --- Controllo materiale sulla posizione (GET A_SalesOrderItem) ---
        // Bloccante: se il materiale nel file non corrisponde a quello SAP,
        // i due sistemi sono disallineati e non possiamo procedere.
        if (data.getMaterial() != null && !data.getMaterial().isBlank()) {
            String itemUrl = config.getSalesOrderApiUrl()
                + "A_SalesOrderItem(SalesOrder='" + order + "'"
                + ",SalesOrderItem='" + paddedItem + "')"
                + "?$select=SalesOrder,SalesOrderItem,Material"
                + "&sap-client=" + config.getClient();

            HttpResponse<String> itemResp = doGet(itemUrl);

            if (itemResp.statusCode() == 200) {
                String materialMismatch = checkMaterialMatch(itemResp.body(), data.getMaterial());
                if (materialMismatch != null) {
                    return SapDryRunResult.error(
                        "Posizione " + paddedItem + " — " + materialMismatch
                        + " — operazione bloccata per disallineamento dati");
                }
            } else if (itemResp.statusCode() == 404) {
                return SapDryRunResult.error(
                    "Posizione " + paddedItem + " NON trovata su SAP — operazione impossibile");
            }
            // Altri status HTTP: non blocchiamo sul materiale, proseguiamo
        }

        // --- Verifica schedulazione ---
        String url = config.getSalesOrderApiUrl()
            + "A_SalesOrderScheduleLine("
            + "SalesOrder='" + order + "',"
            + "SalesOrderItem='" + paddedItem + "',"
            + "ScheduleLine='" + paddedSched + "')"
            + "?$select=SalesOrder,SalesOrderItem,ScheduleLine,RequestedDeliveryDate"
            + ",ScheduleLineOrderQuantity,DeliveredQtyInOrderQtyUnit,OpenConfdDelivQtyInOrdQtyUnit"
            + "&sap-client=" + config.getClient();

        HttpResponse<String> resp = doGet(url);

        boolean isElimination = isQuantityZero(data.getQuantity());

        if (resp.statusCode() == 200) {
            if (isElimination) {
                return SapDryRunResult.ok(
                    "Schedulazione " + paddedSched + " trovata — sarà eliminata (qty=0)");
            }
            String detail = extractCurrentValues(resp.body(), data);
            if ("NESSUNA_MODIFICA".equals(detail)) {
                return SapDryRunResult.warning("NESSUNA_MODIFICA");
            }
            if ("EVASA".equals(detail)) {
                return SapDryRunResult.warning("EVASA");
            }
            if (detail != null && detail.startsWith("BLOCCATA:")) {
                String cat = detail.substring("BLOCCATA:".length());
                return SapDryRunResult.warning("BLOCCATA:" + cat);
            }
            return SapDryRunResult.ok("Schedulazione " + paddedSched + " trovata — " + detail);

        } else if (resp.statusCode() == 404) {
            return SapDryRunResult.error(
                "Schedulazione " + paddedSched + " NON trovata su SAP"
                + (isElimination ? " — nulla da eliminare" : " — modifica impossibile"));
        } else {
            // Errore OData: estrai codice e messaggio
            String sapErrCode = "";
            String sapErrMsg  = resp.statusCode() + "";
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> errJson =
                    mapper.readValue(resp.body(), java.util.Map.class);
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> error =
                    (java.util.Map<String, Object>) errJson.get("error");
                if (error != null) {
                    sapErrCode = String.valueOf(error.getOrDefault("code", ""));
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> msg =
                        (java.util.Map<String, Object>) error.get("message");
                    if (msg != null)
                        sapErrMsg = String.valueOf(msg.getOrDefault("value", sapErrMsg));
                }
            } catch (Exception ignored) {}

            if (sapErrCode.contains("CM_MGW_RT/020")) {
                return SapDryRunResult.error(
                    "Schedulazione " + paddedSched
                    + " — tipo documento non supportato dall'API (codice: " + sapErrCode + ")");
            }
            return SapDryRunResult.warning(
                "Schedulazione " + paddedSched
                + " — risposta SAP inattesa: HTTP " + resp.statusCode()
                + (sapErrMsg.isEmpty() ? "" : " — " + sapErrMsg));
        }
    }

    /**
     * Estrae quantità e data attuali dalla risposta SAP e le confronta
     * con i valori nuovi, producendo un testo descrittivo per il dry-run.
     */
    @SuppressWarnings("unchecked")
    private String extractCurrentValues(String body, ScheduleLineData data) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> json = mapper.readValue(body, java.util.Map.class);
            java.util.Map<String, Object> d    =
                (java.util.Map<String, Object>) json.get("d");
            if (d == null) return "dati attuali non leggibili";

            // --- Controlla stato consegna ---
            Object deliveredQtyObj = d.get("DeliveredQtyInOrderQtyUnit");
            Object openQtyObj      = d.get("OpenConfdDelivQtyInOrdQtyUnit");

            if (deliveredQtyObj != null) {
                double deliveredQty = toDouble(deliveredQtyObj.toString());
                if (!Double.isNaN(deliveredQty) && deliveredQty > 0.0) {
                    double openQty = openQtyObj != null
                        ? toDouble(openQtyObj.toString()) : Double.NaN;
                    boolean fullyDelivered = !Double.isNaN(openQty) && openQty <= 0.0;
                    if (fullyDelivered) return "BLOCCATA:EVASA";

                    double newQty = toDouble(data.getQuantity() != null
                        ? data.getQuantity().replace(",", ".") : "0");
                    if (!Double.isNaN(newQty) && newQty < deliveredQty) {
                        return "BLOCCATA:QTA_SOTTO_CONSEGNATO:" + deliveredQty;
                    }
                }
            }

            // --- Confronto quantità ---
            String currentQtyStr = String.valueOf(d.getOrDefault("ScheduleLineOrderQuantity", "?"));
            String newQtyStr     = data.getQuantity() != null ? data.getQuantity() : "?";
            boolean qtyChanged   = !toDouble(currentQtyStr).equals(toDouble(newQtyStr));

            // --- Confronto data ---
            String currentDate = parseSapDate(
                    String.valueOf(d.getOrDefault("RequestedDeliveryDate", "?")));
            String newDate = data.getProductionDate() != null
                ? data.getProductionDate().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "?";
            boolean dateChanged = !currentDate.equals(newDate);

            if (!qtyChanged && !dateChanged) return "NESSUNA_MODIFICA";

            StringBuilder sb = new StringBuilder("modifica prevista:");
            if (qtyChanged) sb.append(" Qtà ").append(fmt(currentQtyStr)).append("→").append(fmt(newQtyStr));
            if (dateChanged) sb.append(" Data ").append(currentDate).append("→").append(newDate);
            return sb.toString();

        } catch (Exception e) {
            return "dati attuali non leggibili";
        }
    }

    // -------------------------------------------------------
    // ENTRY POINT PUBBLICO — aggiornamento reale
    // -------------------------------------------------------

    public SapResponse updateScheduleLine(ScheduleLineData data) throws Exception {

        String validationError = data.validate();
        if (validationError != null) {
            SapResponse r = new SapResponse(400);
            r.setSapMessage("Validazione: " + validationError);
            return r;
        }

        SapAuthToken auth = authService.fetchCsrfToken();

        if (data.isInsert()) {
            return insertScheduleLine(data, auth);
        } else {
            return patchScheduleLine(data, auth);
        }
    }

    // -------------------------------------------------------
    // INSERT (POST)
    // -------------------------------------------------------

    private SapResponse insertScheduleLine(ScheduleLineData data,
                                           SapAuthToken auth) throws Exception {

        String sapDate    = convertToSapDate(data.getProductionDate());
        String cleanQty   = cleanQuantity(data.getQuantity());
        String paddedItem = String.format("%06d", data.getItemNumber());

        String payload = "{"
            + "\"SalesOrder\":\""                + data.getOrderNumber() + "\","
            + "\"SalesOrderItem\":\""            + paddedItem            + "\","
            + "\"RequestedDeliveryDate\":\""     + sapDate               + "\","
            + "\"ScheduleLineOrderQuantity\":\"" + cleanQty              + "\""
            + "}";

        String url = config.getSalesOrderApiUrl()
            + "A_SalesOrderScheduleLine?sap-client=" + config.getClient();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization",  config.getBasicAuthHeader())
            .header("x-csrf-token",   auth.getCsrfToken())
            .header("Cookie",         auth.getCookies())
            .header("Content-Type",   "application/json")
            .header("Accept",         "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        return parseSapResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    // -------------------------------------------------------
    // UPDATE (PATCH)
    // -------------------------------------------------------

    private SapResponse patchScheduleLine(ScheduleLineData data,
                                          SapAuthToken auth) throws Exception {

        String sapDate     = convertToSapDate(data.getProductionDate());
        String cleanQty    = cleanQuantity(data.getQuantity());
        String paddedItem  = String.format("%06d", data.getItemNumber());
        String paddedSched = String.format("%04d", data.getScheduleLine());

        String payload = "{"
            + "\"RequestedDeliveryDate\":\""     + sapDate  + "\","
            + "\"ScheduleLineOrderQuantity\":\"" + cleanQty + "\""
            + "}";

        String url = config.getSalesOrderApiUrl()
            + "A_SalesOrderScheduleLine("
            + "SalesOrder='"     + data.getOrderNumber() + "',"
            + "SalesOrderItem='" + paddedItem            + "',"
            + "ScheduleLine='"   + paddedSched           + "'"
            + ")?sap-client="    + config.getClient();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
            .header("Authorization",  config.getBasicAuthHeader())
            .header("x-csrf-token",   auth.getCsrfToken())
            .header("Cookie",         auth.getCookies())
            .header("Content-Type",   "application/json")
            .header("Accept",         "application/json")
            .header("If-Match",       "*")
            .build();

        return parseSapResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    // -------------------------------------------------------
    // HTTP GET helper (per dry-run, senza CSRF)
    // -------------------------------------------------------

    private HttpResponse<String> doGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", config.getBasicAuthHeader())
            .header("Accept",        "application/json")
            .GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // -------------------------------------------------------
    // UTILITY
    // -------------------------------------------------------

    private String convertToSapDate(java.time.LocalDate date) {
        long millis = date.atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli();
        return "/Date(" + millis + ")/";
    }

    private String cleanQuantity(String quantity) {
        if (quantity == null || quantity.isBlank()) return "0";
        return quantity.replace(",", ".");
    }

    private boolean isQuantityZero(String qty) {
        if (qty == null || qty.isBlank()) return true;
        try { return Double.parseDouble(qty.replace(",", ".")) == 0; }
        catch (Exception e) { return false; }
    }

    private Double toDouble(String s) {
        if (s == null || s.isBlank() || s.equals("?")) return Double.NaN;
        try { return Double.parseDouble(s.replace(",", ".")); }
        catch (Exception e) { return Double.NaN; }
    }

    private String fmt(String s) {
        try {
            double v = Double.parseDouble(s.replace(",", "."));
            return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
        } catch (Exception e) { return s; }
    }

    private String parseSapDate(String sapDate) {
        try {
            if (sapDate == null || !sapDate.startsWith("/Date(")) return sapDate;
            long millis = Long.parseLong(sapDate.replaceAll("[^0-9]", ""));
            return java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) { return sapDate; }
    }

    private SapResponse parseSapResponse(HttpResponse<String> httpResponse) {
        SapResponse sapResponse = new SapResponse(httpResponse.statusCode());
        sapResponse.parseHeaders(httpResponse.headers().map());
        sapResponse.parseBody(httpResponse.body());
        return sapResponse;
    }

    // -------------------------------------------------------
    // INNER CLASS — risultato dry-run
    // -------------------------------------------------------

    public static class SapDryRunResult {

        public enum Stato { OK, WARNING, ERRORE }

        private final Stato  stato;
        private final String dettaglio;

        private SapDryRunResult(Stato stato, String dettaglio) {
            this.stato     = stato;
            this.dettaglio = dettaglio;
        }

        public static SapDryRunResult ok(String dettaglio) {
            return new SapDryRunResult(Stato.OK, dettaglio);
        }
        public static SapDryRunResult warning(String dettaglio) {
            return new SapDryRunResult(Stato.WARNING, dettaglio);
        }
        public static SapDryRunResult error(String dettaglio) {
            return new SapDryRunResult(Stato.ERRORE, dettaglio);
        }

        public Stato  getStato()     { return stato; }
        public String getDettaglio() { return dettaglio; }
        public boolean isOk()        { return stato == Stato.OK; }
        public boolean isError()     { return stato == Stato.ERRORE; }

        public String getEsitoIcona() {
            switch (stato) {
                case OK:      return "✅ " + dettaglio;
                case WARNING: return "⚠️ " + dettaglio;
                case ERRORE:  return "❌ " + dettaglio;
                default:      return dettaglio;
            }
        }
    }
}
