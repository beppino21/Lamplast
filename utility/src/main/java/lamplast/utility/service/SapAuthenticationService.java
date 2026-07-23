package lamplast.utility.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lamplast.utility.config.SapConfiguration;

/**
 * Gestisce l'autenticazione con SAP e il recupero del token CSRF
 */
public class SapAuthenticationService {
    
    private final SapConfiguration config;
    private final HttpClient httpClient;
    
    public SapAuthenticationService(SapConfiguration config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    }
    
    /**
     * Recupera token CSRF e cookie di sessione dal servizio Sales Order
     * standard (API_SALES_ORDER_SRV). Mantenuto per compatibilità.
     */
    public SapAuthToken fetchCsrfToken() throws Exception {
        return fetchCsrfToken(config.getSalesOrderApiUrl(), "A_SalesOrderItem");
    }

    /**
     * Recupera token CSRF e cookie di sessione da un servizio OData
     * specifico. Ogni servizio OData ha una propria sessione CSRF: il
     * token ottenuto da API_SALES_ORDER_SRV NON è valido per chiamate a
     * API_SALES_ORDER_WITHOUT_CHARGE_SRV (o viceversa) — va richiesto un
     * token per ciascun servizio realmente usato.
     *
     * @param serviceBaseUrl base URL del servizio OData (con "/" finale)
     * @param probeEntitySet entity set leggero su cui fare la GET di probe
     *                       (es. "A_SalesOrderItem" oppure
     *                       "A_SalesOrderWithoutChargeItem")
     */
    public SapAuthToken fetchCsrfToken(String serviceBaseUrl, String probeEntitySet) throws Exception {

        String url = serviceBaseUrl
            + probeEntitySet + "?$top=1&sap-client=" + config.getClient();
        
        System.out.println("URL--> " + url);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", config.getBasicAuthHeader())
            .header("x-csrf-token", "Fetch")
            .header("Accept", "application/json")
            .GET()
            .build();

        System.out.println("request--> " + url);
        
        HttpResponse<String> response = httpClient.send(
            request, 
            HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() >= 400) {
            throw new Exception(
                "Errore autenticazione SAP: " + response.statusCode()
            );
        }
        
        String csrfToken = response.headers()
            .firstValue("x-csrf-token")
            .orElse(null);
        
        if (csrfToken == null) {
            throw new Exception("Token CSRF non ricevuto da SAP");
        }
        
        String cookies = response.headers()
            .allValues("set-cookie")
            .stream()
            .map(c -> c.split(";", 2)[0])
            .reduce((a, b) -> a + "; " + b)
            .orElse("");
        
        return new SapAuthToken(csrfToken, cookies);
    }
    
    /**
     * Classe che contiene token e cookie di sessione
     */
    public static class SapAuthToken {
        private final String csrfToken;
        private final String cookies;
        
        public SapAuthToken(String csrfToken, String cookies) {
            this.csrfToken = csrfToken;
            this.cookies = cookies;
        }
        
        public String getCsrfToken() {
            return csrfToken;
        }
        
        public String getCookies() {
            return cookies;
        }
    }
}