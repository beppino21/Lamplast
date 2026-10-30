package lamplast.mbxa.logic.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Configurazione dell'applicazione, letta da un file properties ESTERNO al war
 * (credenziali, database, numerazione DDT, dati aziendali per la stampa).
 * <p>
 * Ricerca del file, nell'ordine:
 * <ol>
 *   <li>proprietà di sistema {@code -Dmbxa.config=C:/percorso/mbxa.properties}</li>
 *   <li>variabile d'ambiente {@code MBXA_CONFIG}</li>
 *   <li>{@code ${catalina.base}/conf/mbxa.properties} (Tomcat)</li>
 *   <li>{@code mbxa.properties} nel classpath (solo per sviluppo)</li>
 * </ol>
 * Il file viene riletto automaticamente se cambia (controllo sulla data di modifica).
 */
public final class AppConfig {

    /** Modo di numerazione dei DDT. */
    public enum ModoNumerazione {
        /** Numero SAP (GR/GI slip number, XABLN) letto da MIGO e inserito dall'operatore. */
        MANUALE,
        /** Serie propria dell'app: prefisso + progressivo per anno, assegnato all'emissione. */
        INTERNA
    }

    /** Controllo di sequenza sul numero inserito a mano (solo modo MANUALE). */
    public enum ControlloSequenza { NESSUNO, AVVISO, BLOCCO }

    private static final String FILE_NAME = "mbxa.properties";

    private static volatile AppConfig s_instance;
    private static volatile Path s_path;
    private static volatile long s_lastModified;

    private final Properties m_props;
    private final String m_source;

    private AppConfig(Properties props, String source) {
        m_props = props;
        m_source = source;
    }

    // ------------------------------------------------------------------------
    // accesso
    // ------------------------------------------------------------------------

    /** Istanza corrente; rilegge il file se è stato modificato. */
    public static synchronized AppConfig get() {
        try {
            Path p = findExternalFile();
            if (p != null) {
                long lm = Files.getLastModifiedTime(p).toMillis();
                if (s_instance == null || !p.equals(s_path) || lm != s_lastModified) {
                    Properties props = new Properties();
                    try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        props.load(r);
                    }
                    s_instance = new AppConfig(props, p.toString());
                    s_path = p;
                    s_lastModified = lm;
                }
                return s_instance;
            }
            if (s_instance == null) {
                Properties props = new Properties();
                try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(FILE_NAME)) {
                    if (in == null) {
                        throw new IllegalStateException("File di configurazione " + FILE_NAME
                            + " non trovato. Indicarlo con -Dmbxa.config=..., con la variabile MBXA_CONFIG"
                            + " oppure copiarlo in ${catalina.base}/conf/");
                    }
                    try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        props.load(r);
                    }
                }
                s_instance = new AppConfig(props, "classpath:" + FILE_NAME);
            }
            return s_instance;
        } catch (IOException e) {
            throw new IllegalStateException("Errore nella lettura della configurazione: " + e.getMessage(), e);
        }
    }

    /** Solo per i test: configurazione costruita da properties in memoria. */
    public static AppConfig of(Properties props) {
        return new AppConfig(props, "test");
    }

    private static Path findExternalFile() {
        List<String> candidates = new ArrayList<>();
        candidates.add(System.getProperty("mbxa.config"));
        candidates.add(System.getenv("MBXA_CONFIG"));
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null) candidates.add(catalinaBase + "/conf/" + FILE_NAME);
        for (String c : candidates) {
            if (c == null || c.isBlank()) continue;
            Path p = Paths.get(c.trim());
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    public String getSource() { return m_source; }

    // ------------------------------------------------------------------------
    // helper generici
    // ------------------------------------------------------------------------

    public String get(String key, String def) {
        String v = m_props.getProperty(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    public String require(String key) {
        String v = get(key, null);
        if (v == null) throw new IllegalStateException("Parametro obbligatorio mancante in " + m_source + ": " + key);
        return v;
    }

    public int getInt(String key, int def) {
        String v = get(key, null);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Parametro " + key + " non numerico: " + v);
        }
    }

    // ------------------------------------------------------------------------
    // SAP
    // ------------------------------------------------------------------------

    /** Es. https://my434383-api.s4hana.cloud.sap (host API, con "-api"). */
    public String getSapBaseUrl() {
        String u = require("sap.baseUrl");
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    /** Utente di comunicazione dell'arrangement SAP_COM_0108 (documenti materiale). */
    public String getSapUser() { return require("sap.user"); }
    public String getSapPassword() { return require("sap.password"); }

    /** Utente dell'arrangement SAP_COM_0009 (prodotto); se assente, lo stesso di sap.user. */
    public String getSapProductUser() { return get("sap.product.user", getSapUser()); }
    public String getSapProductPassword() { return get("sap.product.password", getSapPassword()); }

    public String getMaterialDocumentServicePath() {
        return get("sap.materialDocument.path", "/sap/opu/odata/sap/API_MATERIAL_DOCUMENT_SRV");
    }

    public String getProductServicePath() {
        return get("sap.product.path", "/sap/opu/odata/sap/API_PRODUCT_SRV");
    }

    /** Lingua delle descrizioni materiale (codice ISO a 2 lettere, es. IT). */
    public String getLinguaDescrizioni() { return get("ddt.lingua", "IT"); }

    public int getHttpTimeoutSeconds() { return getInt("sap.timeout.seconds", 30); }

    // ------------------------------------------------------------------------
    // Database
    // ------------------------------------------------------------------------

    /** Es. jdbc:postgresql://localhost:5432/ddt_trasf?currentSchema=ddt */
    public String getDbUrl() { return require("db.url"); }
    public String getDbUser() { return require("db.user"); }
    public String getDbPassword() { return require("db.password"); }

    // ------------------------------------------------------------------------
    // Numerazione DDT
    // ------------------------------------------------------------------------

    public ModoNumerazione getModoNumerazione() {
        String v = get("ddt.numerazione.modo", "MANUALE").toUpperCase();
        try {
            return ModoNumerazione.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ddt.numerazione.modo non valido: " + v + " (MANUALE o INTERNA)");
        }
    }

    public ControlloSequenza getControlloSequenza() {
        String v = get("ddt.numerazione.controllo.sequenza", "AVVISO").toUpperCase();
        try {
            return ControlloSequenza.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ddt.numerazione.controllo.sequenza non valido: " + v
                + " (NESSUNO, AVVISO o BLOCCO)");
        }
    }

    /** Codice della serie interna (modo INTERNA), es. TR. Non può essere "SAP". */
    public String getTipoNumerazioneInterna() {
        String t = get("ddt.numerazione.tipo", "TR").toUpperCase();
        if ("SAP".equals(t)) throw new IllegalStateException("ddt.numerazione.tipo non può essere SAP");
        return t;
    }

    /** Formato del numero interno: segnaposto {TIPO}, {ANNO}, {PROGRESSIVO} o {PROGRESSIVO:000000}. */
    public String getFormatoNumerazioneInterna() {
        return get("ddt.numerazione.formato", "{TIPO}{ANNO}/{PROGRESSIVO:000000}");
    }

    // ------------------------------------------------------------------------
    // Elenco documenti da acquisire
    // ------------------------------------------------------------------------

    public int getListaGiorni() { return getInt("ddt.lista.giorni", 30); }
    public int getListaMax() { return getInt("ddt.lista.max", 300); }

    // ------------------------------------------------------------------------
    // Stampa
    // ------------------------------------------------------------------------

    /**
     * Righe dei dati aziendali stampate in alto a destra (azienda.riga.1 ... azienda.riga.N).
     */
    public List<String> getRigheAzienda() {
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            String v = get("azienda.riga." + i, null);
            if (v != null) result.add(v);
        }
        return result;
    }

    /** Righe stampate sotto il logo (es. ragione sociale e capitale sociale). */
    public List<String> getRigheSottoLogo() {
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String v = get("azienda.logo.riga." + i, null);
            if (v != null) result.add(v);
        }
        return result;
    }

    /** Percorso del logo su disco; se assente si usa la risorsa interna /lamplast/mbxa/pdf/logo.jpg. */
    public String getLogoPath() { return get("pdf.logo", null); }

    /**
     * Alias delle unità di misura in stampa, es. "PC=NR,ST=NR,PZ=NR".
     */
    public Map<String, String> getAliasUnitaMisura() {
        Map<String, String> m = new LinkedHashMap<>();
        String v = get("pdf.um.alias", "");
        for (String pair : v.split(",")) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && !kv[0].isBlank()) m.put(kv[0].trim().toUpperCase(), kv[1].trim());
        }
        return m;
    }
}
