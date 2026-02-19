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
 * Gestisce le operazioni CRUD sulle schedulazioni SAP
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
    
    /**
     * Aggiorna o inserisce una schedulazione
     */
    public SapResponse updateScheduleLine(ScheduleLineData data) throws Exception {
        
        // Validazione
        String validationError = data.validate();
        if (validationError != null) {
            SapResponse response = new SapResponse(400);
            return response;
        }
        
        // Autenticazione
        SapAuthToken auth = authService.fetchCsrfToken();
        
        // Decisione INSERT vs UPDATE
        if (data.isInsert()) {
            return insertScheduleLine(data, auth);
        } else {
            return patchScheduleLine(data, auth);
        }
    }
    
    /**
     * Inserisce una nuova schedulazione (POST)
     */
    private SapResponse insertScheduleLine(ScheduleLineData data, 
                                           SapAuthToken auth) throws Exception {
        
        String sapDate = convertToSapDate(data.getProductionDate());
        String cleanQuantity = cleanQuantity(data.getQuantity());
        
        String payload = "{"
            + "\"SalesOrder\":\"" + data.getOrderNumber() + "\","
            + "\"SalesOrderItem\":\"" + data.getItemNumber() + "\","
            + "\"RequestedDeliveryDate\":\"" + sapDate + "\","
            + "\"ScheduleLineOrderQuantity\":\"" + cleanQuantity + "\""
            + "}";
        
        String url = config.getSalesOrderApiUrl() 
            + "A_SalesOrderScheduleLine?sap-client=" + config.getClient();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", config.getBasicAuthHeader())
            .header("x-csrf-token", auth.getCsrfToken())
            .header("Cookie", auth.getCookies())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        
        HttpResponse<String> response = httpClient.send(
            request, 
            HttpResponse.BodyHandlers.ofString()
        );
        
        return parseSapResponse(response);
    }
    
    /**
     * Aggiorna una schedulazione esistente (PATCH)
     */
    private SapResponse patchScheduleLine(ScheduleLineData data, 
                                          SapAuthToken auth) throws Exception {
        
        String sapDate = convertToSapDate(data.getProductionDate());
        String cleanQuantity = cleanQuantity(data.getQuantity());
        
        String payload = "{"
            + "\"RequestedDeliveryDate\":\"" + sapDate + "\","
            + "\"ScheduleLineOrderQuantity\":\"" + cleanQuantity + "\""
            + "}";
        
        String url = config.getSalesOrderApiUrl() 
            + "A_SalesOrderScheduleLine("
            + "SalesOrder='" + data.getOrderNumber() + "',"
            + "SalesOrderItem='" + data.getItemNumber() + "',"
            + "ScheduleLine='" + data.getScheduleLine() + "'"
            + ")?sap-client=" + config.getClient();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
            .header("Authorization", config.getBasicAuthHeader())
            .header("x-csrf-token", auth.getCsrfToken())
            .header("Cookie", auth.getCookies())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("If-Match", "*")
            .build();
        
        HttpResponse<String> response = httpClient.send(
            request, 
            HttpResponse.BodyHandlers.ofString()
        );
        
        return parseSapResponse(response);
    }
    
    /**
     * Converte LocalDate in formato SAP /Date(milliseconds)/
     */
    private String convertToSapDate(java.time.LocalDate date) {
        long millis = date.atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli();
        return "/Date(" + millis + ")/";
    }
    
    /**
     * Pulisce la quantità rimuovendo decimali
     */
    private String cleanQuantity(String quantity) {
        return quantity.split("\\.")[0].split("\\,")[0];
    }
    
    /**
     * Crea oggetto SapResponse dal HttpResponse
     */
    private SapResponse parseSapResponse(HttpResponse<String> httpResponse) {
        SapResponse sapResponse = new SapResponse(httpResponse.statusCode());
        sapResponse.parseHeaders(httpResponse.headers().map());
        sapResponse.parseBody(httpResponse.body());
        return sapResponse;
    }
}