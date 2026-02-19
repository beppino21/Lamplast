package lamplast.utility.config;

/**
 * Configurazione centralizzata per l'accesso a SAP
 */
public class SapConfiguration {
    
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String client;
    
    public SapConfiguration() {
    	
        // TODO: Leggere da file properties o variabili ambiente
//        this.baseUrl = "https://my428121.s4hana.cloud.sap";
//        this.username = "BTP_USER_CONNECTION";
//        this.password = "Eonegroup_2026_gennaio";
//        this.client = "080";
        
        this.baseUrl = "https://my434383.s4hana.cloud.sap/"; 
        this.username = "COMM_USER_F46100";
        this.password = "Eone_Group_Febbraio2026";
        this.client = "100";
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public String getClient() {
        return client;
    }
    
    public String getBasicAuthHeader() {
        String credentials = username + ":" + password;
        return "Basic " + java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes());
    }
    
    public String getSalesOrderApiUrl() {
        return baseUrl + "/sap/opu/odata/SAP/API_SALES_ORDER_SRV/";
    }
}
