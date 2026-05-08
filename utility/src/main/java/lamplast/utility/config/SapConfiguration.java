package lamplast.utility.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configurazione centralizzata per l'accesso a SAP e per il mapping
 * delle colonne Excel. Le proprietà vengono lette da config.properties
 * (nel classpath, es. src/main/resources/).
 */
public class SapConfiguration {

    // --- SAP ---
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String client;

    // --- Nomi colonne Excel ---
    private final String colOrdine;
    private final String colPosizione;
    private final String colSchedulazione;
    private final String colMateriale;
    private final String colMaterialeText;
    private final String colQuantita;
    private final String colDataProd;

    private static final String CONFIG_FILE = "config.properties";

    public SapConfiguration() {
        Properties props = loadProperties();

        // SAP
        this.baseUrl  = props.getProperty("sap.baseUrl");
        this.username = props.getProperty("sap.username");
        this.password = props.getProperty("sap.password");
        this.client   = props.getProperty("sap.client");

        // Colonne Excel (con valori di default nel caso mancassero)
        this.colOrdine        = props.getProperty("excel.col.ordine",        "Ordine");
        this.colPosizione     = props.getProperty("excel.col.posizione",     "Pos.");
        this.colSchedulazione = props.getProperty("excel.col.schedulazione", "Sch.");
        this.colMateriale     = props.getProperty("excel.col.materiale",     "Materiale");
        this.colMaterialeText = props.getProperty("excel.col.materialeText", "Text");
        this.colQuantita      = props.getProperty("excel.col.quantita",      "Qtà");
        this.colDataProd      = props.getProperty("excel.col.dataProd",      "Data prod.");

        validateSapConfig();
    }

    // -------------------------------------------------------
    // CARICAMENTO PROPERTIES
    // -------------------------------------------------------

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                                        .getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new IllegalStateException(
                    "File di configurazione non trovato nel classpath: "
                    + CONFIG_FILE
                    + " — verificare che sia in src/main/resources/");
            }
            props.load(is);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Errore nella lettura di " + CONFIG_FILE + ": " + e.getMessage(), e);
        }
        return props;
    }

    // -------------------------------------------------------
    // VALIDAZIONE
    // -------------------------------------------------------

    private void validateSapConfig() {
        validateRequired("sap.baseUrl",  baseUrl);
        validateRequired("sap.username", username);
        validateRequired("sap.password", password);
        validateRequired("sap.client",   client);

        if (baseUrl.endsWith("/")) {
            throw new IllegalStateException(
                "sap.baseUrl non deve terminare con '/': " + baseUrl);
        }
    }

    private void validateRequired(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Proprietà obbligatoria mancante in " + CONFIG_FILE + ": " + key);
        }
    }

    // -------------------------------------------------------
    // GETTER — SAP
    // -------------------------------------------------------

    public String getBaseUrl()   { return baseUrl; }
    public String getUsername()  { return username; }
    public String getPassword()  { return password; }
    public String getClient()    { return client; }

    public String getBasicAuthHeader() {
        String credentials = username + ":" + password;
        return "Basic " + java.util.Base64.getEncoder()
                                          .encodeToString(credentials.getBytes());
    }

    /**
     * URL base dell'API OData Sales Order.
     * Garantisce esattamente uno slash tra baseUrl e il path (no double-slash).
     */
    public String getSalesOrderApiUrl() {
        return baseUrl + "/sap/opu/odata/SAP/API_SALES_ORDER_SRV/";
    }

    // -------------------------------------------------------
    // GETTER — Colonne Excel
    // -------------------------------------------------------

    public String getColOrdine()        { return colOrdine; }
    public String getColPosizione()     { return colPosizione; }
    public String getColSchedulazione() { return colSchedulazione; }
    public String getColMateriale()     { return colMateriale; }
    public String getColMaterialeText() { return colMaterialeText; }
    public String getColQuantita()      { return colQuantita; }
    public String getColDataProd()      { return colDataProd; }
}
