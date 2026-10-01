package lamplast.utility.config;

import java.util.Properties;

import lamplast.utility.config.SapSystemRegistry.SapSystemInfo;

/**
 * Configurazione centralizzata per l'accesso a UN sistema SAP e per il
 * mapping delle colonne Excel.
 * <p>
 * Le istanze si ottengono da {@link SapSystemRegistry#createConfiguration(String)}.
 * Ogni property viene cercata prima nella variante specifica del sistema e poi
 * in quella globale:
 * <pre>
 *   sap.baseUrl          → sap.system.&lt;ID&gt;.baseUrl
 *   sap.client           → sap.system.&lt;ID&gt;.client        (fallback: sap.client)
 *   odv.virtual.prefix   → sap.system.&lt;ID&gt;.odv.virtual.prefix (fallback: globale)
 *   excel.sheetName      → sap.system.&lt;ID&gt;.excel.sheetName    (fallback: globale)
 * </pre>
 * Per sicurezza, in modalità multi-sistema <b>baseUrl, username e password
 * NON hanno fallback</b> sulle property globali: vanno sempre dichiarate per
 * ciascun sistema, così non si rischia di usare le credenziali di un sistema
 * su un altro.
 */
public class SapConfiguration {

    // --- Identità sistema ---
    private final String  systemId;
    private final String  systemName;
    private final boolean productive;

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

    // Supporto lookup
    private final Properties props;
    private final String     sysPrefix;   // "sap.system.<ID>." oppure null in legacy
    private final boolean    legacyMode;

    /** Usare {@link SapSystemRegistry#createConfiguration(String)}. */
    SapConfiguration(Properties props, SapSystemInfo info, boolean legacyMode) {
        this.props      = props;
        this.legacyMode = legacyMode;
        this.sysPrefix  = legacyMode ? null : "sap.system." + info.getId() + ".";

        this.systemId   = info.getId();
        this.systemName = info.getName();
        this.productive = info.isProductive();

        // SAP — baseUrl/username/password senza fallback in multi-sistema
        this.baseUrl  = strict("sap.baseUrl");
        this.username = strict("sap.username");
        this.password = strict("sap.password");
        this.client   = get("sap.client", null);

        // URL ordini (con default nel caso mancassero)
        this.urlVa03  = get("sap.url.va03",  "/sap/bc/ui2/flp#SalesOrder-manage?SalesOrder=");
        this.urlFiori = get("sap.url.fiori", "/sap/bc/ui2/flp#SalesOrder-displayFactSheet?SalesOrder=");

        // Normalizzazione numerazione OdV
        this.odvVirtualPrefix = get("odv.virtual.prefix", "").trim();
        String offsetStr = get("odv.virtual.offset", "0").trim();
        this.odvVirtualOffset = offsetStr.isEmpty() ? 0L : Long.parseLong(offsetStr);

        // Cartella log stampa
        this.logPrintFolder = get("log.printFolder", "logprint");

        // Foglio Excel
        this.sheetName = get("excel.sheetName", "Export");

        // Colonne Excel
        this.colOrdine        = get("excel.col.ordine",        "Ordine");
        this.colPosizione     = get("excel.col.posizione",     "Pos.");
        this.colSchedulazione = get("excel.col.schedulazione", "Sch.");
        this.colMateriale     = get("excel.col.materiale",     "Materiale");
        this.colMaterialeText = get("excel.col.materialeText", "Text");
        this.colQuantita      = get("excel.col.quantita",      "Qtà");
        this.colDataProd      = get("excel.col.dataProd",      "Data prod.");

        // Modalità visualizzazione post-aggiornamento (default: sintetico)
        this.viewModeAfterUpdate = get("ui.viewMode.afterUpdate", "sintetico").trim().toLowerCase();

        validateSapConfig();
    }

    // -------------------------------------------------------
    // LOOKUP PROPERTY (specifica di sistema → globale → default)
    // -------------------------------------------------------

    /** "sap.baseUrl" → "sap.system.DEV.baseUrl"; "odv.x" → "sap.system.DEV.odv.x" */
    private String systemKey(String globalKey) {
        String k = globalKey.startsWith("sap.") ? globalKey.substring(4) : globalKey;
        return sysPrefix + k;
    }

    private String get(String globalKey, String def) {
        if (!legacyMode) {
            String v = props.getProperty(systemKey(globalKey));
            if (v != null) return v.trim();
        }
        String v = props.getProperty(globalKey);
        return v != null ? v.trim() : def;
    }

    private String strict(String globalKey) {
        String v = legacyMode ? props.getProperty(globalKey) : props.getProperty(systemKey(globalKey));
        return v != null ? v.trim() : null;
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
                "[" + systemId + "] baseUrl non deve terminare con '/': " + baseUrl);
        }
    }

    private void validateRequired(String key, String value) {
        if (value == null || value.isBlank()) {
            String shown = legacyMode ? key : systemKey(key);
            throw new IllegalStateException(
                "Proprietà obbligatoria mancante per il sistema " + systemId + ": " + shown);
        }
    }

    // -------------------------------------------------------
    // GETTER — Identità sistema
    // -------------------------------------------------------

    public String  getSystemId()   { return systemId; }
    public String  getSystemName() { return systemName; }
    public boolean isProductive()  { return productive; }
    public String  getHost()       { return SapSystemRegistry.hostOf(baseUrl); }

    /** Es. "TEST · LAMPLAST Sviluppo (my434383.s4hana.cloud.sap)" */
    public String getSystemLabel() {
        return (productive ? "PRODUZIONE" : "TEST") + " · " + systemName + " (" + getHost() + ")";
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
