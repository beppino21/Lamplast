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

    private final SapConfiguration config;
    private final SapAuthenticationService authService;
    private final HttpClient httpClient;

    public SapScheduleLineService(SapConfiguration config) {
        this.config = config;
        this.authService = new SapAuthenticationService(config);
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    }

    // -------------------------------------------------------
    // DRY RUN — verifica preventiva senza modifiche
    // -------------------------------------------------------

    /**
     * Verifica preventiva: interroga SAP in sola lettura e restituisce
     * una descrizione di cosa accadrà (o perché fallirà).
     *
     * Non esegue nessuna modifica su SAP.
     *
     * @return SapDryRunResult con esito e dettaglio leggibile
     */
    public SapDryRunResult dryRun(ScheduleLineData data) throws Exception {

        String validationError = data.validate();
        if (validationError != null) {
            return SapDryRunResult.error("Dati non validi: " + validationError);
        }

        String paddedItem  = String.format("%06d", data.getItemNumber());
        String paddedSched = String.format("%04d", Math.abs(data.getScheduleLine()));

        if (data.isInsert()) {
            // INSERIMENTO: verifica che la posizione ordine esista
            return checkPositionExists(data.getOrderNumber(), paddedItem);
        } else {
            // MODIFICA o ELIMINAZIONE: verifica che la schedulazione esista
            return checkScheduleLineExists(
                    data.getOrderNumber(), paddedItem, paddedSched, data);
        }
    }

    /**
     * Verifica esistenza della posizione ordine (per inserimento).
     * GET A_SalesOrderItem(SalesOrder='X',SalesOrderItem='000010')
     */
    private SapDryRunResult checkPositionExists(String order,
                                                 String paddedItem) throws Exception {
        String url = config.getSalesOrderApiUrl()
            + "A_SalesOrderItem(SalesOrder='" + order + "'"
            + ",SalesOrderItem='" + paddedItem + "')"
            + "?$select=SalesOrder,SalesOrderItem,Material"
            + "&sap-client=" + config.getClient();

        HttpResponse<String> resp = doGet(url);

        if (resp.statusCode() == 200) {
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
     * Verifica esistenza della schedulazione (per modifica/eliminazione).
     * GET A_SalesOrderScheduleLine(SalesOrder='X',SalesOrderItem='000010',ScheduleLine='0001')
     * Confronta anche quantità e data attuali vs nuove.
     */
    private SapDryRunResult checkScheduleLineExists(String order,
                                                     String paddedItem,
                                                     String paddedSched,
                                                     ScheduleLineData data) throws Exception {
        String url = config.getSalesOrderApiUrl()
            + "A_SalesOrderScheduleLine("
            + "SalesOrder='" + order + "',"
            + "SalesOrderItem='" + paddedItem + "',"
            + "ScheduleLine='" + paddedSched + "')"
            + "?$select=SalesOrder,SalesOrderItem,ScheduleLine,RequestedDeliveryDate,ScheduleLineOrderQuantity"
            + "&sap-client=" + config.getClient();

        HttpResponse<String> resp = doGet(url);

        boolean isElimination = isQuantityZero(data.getQuantity());

        if (resp.statusCode() == 200) {
            if (isElimination) {
                return SapDryRunResult.ok(
                    "Schedulazione " + paddedSched + " trovata — sarà eliminata (qty=0)");
            }
            // Estrai dati attuali per confronto
            String detail = extractCurrentValues(resp.body(), data);
            if ("NESSUNA_MODIFICA".equals(detail)) {
                return SapDryRunResult.warning("NESSUNA_MODIFICA");
            }
            if ("EVASA".equals(detail)) {
                return SapDryRunResult.warning("EVASA");
            }
            return SapDryRunResult.ok(
                "Schedulazione " + paddedSched + " trovata — " + detail);
        } else if (resp.statusCode() == 404) {
            return SapDryRunResult.error(
                "Schedulazione " + paddedSched + " NON trovata su SAP"
                + (isElimination ? " — nulla da eliminare" : " — modifica impossibile"));
        } else {
            return SapDryRunResult.warning(
                "Schedulazione " + paddedSched
                + " — risposta SAP inattesa: HTTP " + resp.statusCode());
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
            java.util.Map<String, Object> d    = (java.util.Map<String, Object>) json.get("d");
            if (d == null) return "dati attuali non leggibili";

            // --- Controlla quantità open (già evasa?) ---
            // Il campo OpenConfdDelivQtyInOrdQtyU è opzionale: presente solo
            // in alcune release SAP. Se assente, il check viene saltato.
            Object openQtyObj = d.get("OpenConfdDelivQtyInOrdQtyU");
            if (openQtyObj != null) {
                try {
                    double openQty = Double.parseDouble(
                            openQtyObj.toString().replace(",", "."));
                    if (openQty == 0.0) {
                        return "EVASA";
                    }
                } catch (Exception ignored) { }
            }

            // --- Confronto quantità (numerico, tollerante al separatore) ---
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

            if (!qtyChanged && !dateChanged) {
                return "NESSUNA_MODIFICA";
            }

            // --- Descrizione variazioni ---
            StringBuilder sb = new StringBuilder("modifica prevista:");
            if (qtyChanged) {
                sb.append(" Qtà ").append(fmt(currentQtyStr)).append("→").append(fmt(newQtyStr));
            }
            if (dateChanged) {
                sb.append(" Data ").append(currentDate).append("→").append(newDate);
            }
            return sb.toString();

        } catch (Exception e) {
            return "dati attuali non leggibili";
        }
    }

    /** Converte stringa numerica in Double normalizzato (gestisce punto e virgola). */
    private Double toDouble(String s) {
        if (s == null || s.isBlank() || s.equals("?")) return Double.NaN;
        try { return Double.parseDouble(s.replace(",", ".")); }
        catch (Exception e) { return Double.NaN; }
    }

    /** Formatta un numero rimuovendo zeri decimali inutili. */
    private String fmt(String s) {
        try {
            double v = Double.parseDouble(s.replace(",", "."));
            return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
        } catch (Exception e) { return s; }
    }

    /** Converte /Date(millis)/ in gg/mm/aaaa. */
    private String parseSapDate(String sapDate) {
        try {
            if (sapDate == null || !sapDate.startsWith("/Date(")) return sapDate;
            long millis = Long.parseLong(sapDate.replaceAll("[^0-9]", ""));
            return java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return sapDate;
        }
    }

    // -------------------------------------------------------
    // ENTRY POINT PUBBLICO — aggiornamento reale
    // -------------------------------------------------------

    public SapResponse updateScheduleLine(ScheduleLineData data) throws Exception {

        String validationError = data.validate();
        if (validationError != null) {
            return new SapResponse(400);
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
            + "\"SalesOrder\":\""               + data.getOrderNumber() + "\","
            + "\"SalesOrderItem\":\""           + paddedItem            + "\","
            + "\"RequestedDeliveryDate\":\""    + sapDate               + "\","
            + "\"ScheduleLineOrderQuantity\":\"" + cleanQty             + "\""
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

        return parseSapResponse(httpClient.send(request,
            HttpResponse.BodyHandlers.ofString()));
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

        System.out.println(">>> DEBUG URL PATCH: " + url);

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

        return parseSapResponse(httpClient.send(request,
            HttpResponse.BodyHandlers.ofString()));
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
        return quantity.split("\\.")[0].split("\\,")[0];
    }

    private boolean isQuantityZero(String qty) {
        if (qty == null || qty.isBlank()) return true;
        try { return Double.parseDouble(qty.replace(",", ".")) == 0; }
        catch (Exception e) { return false; }
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

        /** Icona per la colonna Esito nella griglia CC. */
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
