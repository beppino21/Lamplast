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

    // --- URL visualizzazione ordini ---
    private final String urlVa03;
    private final String urlFiori;

    // --- Normalizzazione numerazione OdV ---
    private final String odvVirtualPrefix;
    private final long   odvVirtualOffset;

    // --- Log stampa ---
    private final String logPrintFolder;

    // --- Excel: foglio ---
    private final String sheetName;

    // --- Excel: nomi colonne ---
    private final String colOrdine;
    private final String colPosizione;
    private final String colSchedulazione;
    private final String colMateriale;
    private final String colMaterialeText;
    private final String colQuantita;
    private final String colDataProd;

    /**
     * Modalità visualizzazione griglia dopo "Aggiorna Ordini".
     * "sintetico" = solo errori + inserimenti + cancellazioni.
     * "completo"  = tutto.
     */
    private final String viewModeAfterUpdate;

    private static final String CONFIG_FILE = "config.properties";

    public SapConfiguration() {
        Properties props = loadProperties();

        // SAP
        this.baseUrl  = props.getProperty("sap.baseUrl");
        this.username = props.getProperty("sap.username");
        this.password = props.getProperty("sap.password");
        this.client   = props.getProperty("sap.client");

        // URL ordini (con default nel caso mancassero)
        this.urlVa03  = props.getProperty("sap.url.va03",
                            "/sap/bc/ui2/flp#SalesOrder-manage?SalesOrder=");
        this.urlFiori = props.getProperty("sap.url.fiori",
                            "/sap/bc/ui2/flp#SalesOrder-displayFactSheet?SalesOrder=");

        // Normalizzazione numerazione OdV
        this.odvVirtualPrefix = props.getProperty("odv.virtual.prefix", "").trim();
        String offsetStr = props.getProperty("odv.virtual.offset", "0").trim();
        this.odvVirtualOffset = offsetStr.isEmpty() ? 0L : Long.parseLong(offsetStr);

        // Cartella log stampa
        this.logPrintFolder = props.getProperty("log.printFolder", "logprint");

        // Foglio Excel
        this.sheetName = props.getProperty("excel.sheetName", "Export");

        // Colonne Excel
        this.colOrdine        = props.getProperty("excel.col.ordine",        "Ordine");
        this.colPosizione     = props.getProperty("excel.col.posizione",     "Pos.");
        this.colSchedulazione = props.getProperty("excel.col.schedulazione", "Sch.");
        this.colMateriale     = props.getProperty("excel.col.materiale",     "Materiale");
        this.colMaterialeText = props.getProperty("excel.col.materialeText", "Text");
        this.colQuantita      = props.getProperty("excel.col.quantita",      "Qtà");
        this.colDataProd      = props.getProperty("excel.col.dataProd",      "Data prod.");

        // Modalità visualizzazione post-aggiornamento (default: sintetico)
        this.viewModeAfterUpdate = props.getProperty(
                "ui.viewMode.afterUpdate", "sintetico").trim().toLowerCase();

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

    public String getSalesOrderApiUrl() {
        return baseUrl + "/sap/opu/odata/SAP/API_SALES_ORDER_SRV/";
    }

    /**
     * Servizio OData dedicato agli ordini "senza addebito" (SDDocumentCategory
     * = VBTYP = 'I', es. tipo ordine CBFD). API_SALES_ORDER_SRV NON copre
     * questa categoria documento (per progettazione SAP — vedi KBA 3621002 /
     * 2752419): serve questo servizio separato, comunication scenario
     * SAP_COM_0334.
     */
    public String getSalesOrderWithoutChargeApiUrl() {
        return baseUrl + "/sap/opu/odata/SAP/API_SALES_ORDER_WITHOUT_CHARGE_SRV/";
    }

    // -------------------------------------------------------
    // GETTER — Normalizzazione numerazione OdV
    // -------------------------------------------------------

    public String getOdvVirtualPrefix() { return odvVirtualPrefix; }
    public long   getOdvVirtualOffset() { return odvVirtualOffset; }

    /**
     * Se il numero d'ordine inizia con il prefisso virtuale configurato,
     * sottrae l'offset per ottenere il numero reale SAP4.
     * Es. "1130000042" con prefix="113" e offset=1130000000 → "42"
     * Se il prefisso non è configurato, restituisce il valore invariato.
     */
    public String normalizeOrderNumber(String raw) {
        if (raw == null || raw.isBlank() || odvVirtualPrefix.isEmpty()) return raw;
        if (raw.startsWith(odvVirtualPrefix)) {
            try {
                long num    = Long.parseLong(raw.trim());
                long result = num - odvVirtualOffset;
                if (result > 0) return String.valueOf(result);
            } catch (NumberFormatException ignored) {}
        }
        return raw;
    }

    // -------------------------------------------------------
    // GETTER — URL ordini
    // -------------------------------------------------------

    public String getFullUrlVa03(String salesOrder)  { return baseUrl + urlVa03  + salesOrder; }
    public String getFullUrlFiori(String salesOrder) { return baseUrl + urlFiori + salesOrder; }

    // -------------------------------------------------------
    // GETTER — Excel
    // -------------------------------------------------------

    public String getLogPrintFolder()   { return logPrintFolder; }
    public String getSheetName()        { return sheetName; }
    public String getColOrdine()        { return colOrdine; }
    public String getColPosizione()     { return colPosizione; }
    public String getColSchedulazione() { return colSchedulazione; }
    public String getColMateriale()     { return colMateriale; }
    public String getColMaterialeText() { return colMaterialeText; }
    public String getColQuantita()      { return colQuantita; }
    public String getColDataProd()      { return colDataProd; }

    // -------------------------------------------------------
    // GETTER — UI
    // -------------------------------------------------------

    /** true se la modalità di default è "sintetico" (solo errori/aggiunte/cancellazioni). */
    public boolean isViewModeSinteticoDefault() {
        return !"completo".equals(viewModeAfterUpdate);
    }
}
