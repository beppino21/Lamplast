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
 *
 * BUG FIX rispetto alla versione originale:
 *  1. L'URL non genera più double-slash (gestito in SapConfiguration).
 *  2. SalesOrderItem e ScheduleLine vengono zero-paddati secondo i
 *     MaxLength definiti nel metadata OData:
 *       SalesOrderItem → 6 cifre  (es. "000010")
 *       ScheduleLine   → 4 cifre  (es. "0010")
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
    // ENTRY POINT PUBBLICO
    // -------------------------------------------------------

    /**
     * Aggiorna o inserisce una schedulazione.
     */
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

    /**
     * Inserisce una nuova schedulazione (POST su A_SalesOrderScheduleLine).
     */
    private SapResponse insertScheduleLine(ScheduleLineData data,
                                           SapAuthToken auth) throws Exception {

        String sapDate      = convertToSapDate(data.getProductionDate());
        String cleanQty     = cleanQuantity(data.getQuantity());
        // BUG FIX #2: zero-padding su SalesOrderItem (MaxLength=6)
        String paddedItem   = String.format("%06d", data.getItemNumber());

        String payload = "{"
            + "\"SalesOrder\":\""               + data.getOrderNumber() + "\","
            + "\"SalesOrderItem\":\""           + paddedItem            + "\","
            + "\"RequestedDeliveryDate\":\""    + sapDate               + "\","
            + "\"ScheduleLineOrderQuantity\":\"" + cleanQty             + "\""
            + "}";

        // BUG FIX #1: getSalesOrderApiUrl() ora restituisce URL senza double-slash
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

        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        return parseSapResponse(response);
    }

    // -------------------------------------------------------
    // UPDATE (PATCH)
    // -------------------------------------------------------

    /**
     * Aggiorna una schedulazione esistente (PATCH su A_SalesOrderScheduleLine).
     */
    private SapResponse patchScheduleLine(ScheduleLineData data,
                                          SapAuthToken auth) throws Exception {

        String sapDate      = convertToSapDate(data.getProductionDate());
        String cleanQty     = cleanQuantity(data.getQuantity());
        // BUG FIX #2: zero-padding su SalesOrderItem (MaxLength=6)
        //             e su ScheduleLine (MaxLength=4)
        String paddedItem   = String.format("%06d", data.getItemNumber());
        String paddedSched  = String.format("%04d", data.getScheduleLine());

        String payload = "{"
            + "\"RequestedDeliveryDate\":\""     + sapDate  + "\","
            + "\"ScheduleLineOrderQuantity\":\"" + cleanQty + "\""
            + "}";

        // BUG FIX #1: URL senza double-slash
        // BUG FIX #2: chiavi con padding corretto
        String url = config.getSalesOrderApiUrl()
            + "A_SalesOrderScheduleLine("
            + "SalesOrder='"     + data.getOrderNumber() + "',"
            + "SalesOrderItem='" + paddedItem            + "',"
            + "ScheduleLine='"   + paddedSched           + "'"
            + ")?sap-client="    + config.getClient();

        System.out.println(">>> DEBUG URL PATCH: " + url); // ← aggiunta riga TEST        
        
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

        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        return parseSapResponse(response);
    }

    // -------------------------------------------------------
    // UTILITY
    // -------------------------------------------------------

    /**
     * Converte LocalDate in formato SAP OData v2: /Date(milliseconds)/
     */
    private String convertToSapDate(java.time.LocalDate date) {
        long millis = date.atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli();
        return "/Date(" + millis + ")/";
    }

    /**
     * Pulisce la quantità rimuovendo la parte decimale
     * (accetta sia punto che virgola come separatore decimale).
     */
    private String cleanQuantity(String quantity) {
        return quantity.split("\\.")[0].split("\\,")[0];
    }

    /**
     * Costruisce un SapResponse dall'HttpResponse ricevuto.
     */
    private SapResponse parseSapResponse(HttpResponse<String> httpResponse) {
        SapResponse sapResponse = new SapResponse(httpResponse.statusCode());
        sapResponse.parseHeaders(httpResponse.headers().map());
        sapResponse.parseBody(httpResponse.body());
        return sapResponse;
    }
}
