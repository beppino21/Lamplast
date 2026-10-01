package lamplast.utility.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Elenco dei sistemi SAP S/4HANA Cloud configurati in config.properties.
 * <p>
 * <b>Formato multi-sistema</b>:
 * <pre>
 * sap.systems        = DEV, PRD
 * sap.system.default = DEV
 *
 * sap.system.DEV.name       = LAMPLAST Sviluppo
 * sap.system.DEV.productive = false
 * sap.system.DEV.baseUrl    = https://my434383.s4hana.cloud.sap
 * sap.system.DEV.username   = ...
 * sap.system.DEV.password   = ...
 * sap.system.DEV.client     = 100          (facoltativo: default = sap.client)
 * </pre>
 * Qualunque altra property (es. odv.virtual.prefix, sap.url.va03) può essere
 * ridefinita per singolo sistema con il prefisso "sap.system.&lt;ID&gt;." —
 * vedi {@link SapConfiguration}.
 * <p>
 * <b>Formato legacy</b> (sap.systems assente): viene creato un unico sistema
 * "DEFAULT" dalle property sap.baseUrl / sap.username / ... come prima.
 * <p>
 * <b>Posizione del file</b>: per default config.properties nel classpath.
 * Se è valorizzata la system property JVM <code>lamplast.utility.config</code>
 * oppure la variabile d'ambiente <code>LAMPLAST_UTILITY_CONFIG</code> con il
 * percorso di un file, viene letto quel file: in questo modo si possono
 * aggiungere/modificare sistemi senza rifare il deploy (basta riaprire la pagina).
 */
public class SapSystemRegistry {

    public static final String CONFIG_FILE       = "config.properties";
    public static final String EXT_CONFIG_SYSPROP = "lamplast.utility.config";
    public static final String EXT_CONFIG_ENV     = "LAMPLAST_UTILITY_CONFIG";
    public static final String LEGACY_SYSTEM_ID   = "DEFAULT";

    /** Descrittore "leggero" di un sistema, per combo e header. */
    public static class SapSystemInfo {
        private final String  id;
        private final String  name;
        private final boolean productive;
        private final String  baseUrl;

        SapSystemInfo(String id, String name, boolean productive, String baseUrl) {
            this.id         = id;
            this.name       = name;
            this.productive = productive;
            this.baseUrl    = baseUrl;
        }

        public String  getId()         { return id; }
        public String  getName()       { return name; }
        public boolean isProductive()  { return productive; }
        public String  getBaseUrl()    { return baseUrl; }

        /** Testo mostrato nella combo di selezione. */
        public String getComboText() {
            return (productive ? "🟢 PRODUZIONE — " : "🔴 TEST — ") + name;
        }
    }

    private final Properties                 props;
    private final String                     source;
    private final boolean                    legacyMode;
    private final Map<String, SapSystemInfo> systems = new LinkedHashMap<>();
    private final String                     defaultSystemId;

    public SapSystemRegistry() {
        this.props  = new Properties();
        this.source = loadProperties(props);

        String list = trim(props.getProperty("sap.systems"));
        this.legacyMode = list.isEmpty();

        if (legacyMode) {
            String name = trim(props.getProperty("sap.system.name"));
            if (name.isEmpty()) name = "Sistema unico (" + hostOf(props.getProperty("sap.baseUrl")) + ")";
            // Se non dichiarato, il sistema è considerato NON produttivo:
            // meglio un allarme di troppo che uno di meno.
            boolean prod = Boolean.parseBoolean(trim(props.getProperty("sap.productive", "false")));
            systems.put(LEGACY_SYSTEM_ID, new SapSystemInfo(
                LEGACY_SYSTEM_ID, name, prod, trim(props.getProperty("sap.baseUrl"))));
        } else {
            for (String raw : list.split("[,;]")) {
                String id = raw.trim();
                if (id.isEmpty() || systems.containsKey(id)) continue;
                String p    = "sap.system." + id + ".";
                String name = trim(props.getProperty(p + "name"));
                if (name.isEmpty()) name = id;
                boolean prod = Boolean.parseBoolean(trim(props.getProperty(p + "productive", "false")));
                systems.put(id, new SapSystemInfo(id, name, prod, trim(props.getProperty(p + "baseUrl"))));
            }
            if (systems.isEmpty()) {
                throw new IllegalStateException("sap.systems è valorizzato ma non contiene alcun ID valido");
            }
        }

        String def = trim(props.getProperty("sap.system.default"));
        this.defaultSystemId = systems.containsKey(def) ? def : systems.keySet().iterator().next();
    }

    // -------------------------------------------------------
    // API
    // -------------------------------------------------------

    public List<SapSystemInfo> getSystems() {
        return Collections.unmodifiableList(new ArrayList<>(systems.values()));
    }

    public SapSystemInfo getSystem(String id) { return systems.get(id); }
    public String  getDefaultSystemId()       { return defaultSystemId; }
    public boolean isLegacyMode()             { return legacyMode; }
    /** Da dove è stata letta la configurazione (classpath o percorso file). */
    public String  getSource()                { return source; }

    /** Crea (e valida) la configurazione completa per il sistema indicato. */
    public SapConfiguration createConfiguration(String systemId) {
        SapSystemInfo info = systems.get(systemId);
        if (info == null) {
            throw new IllegalArgumentException("Sistema non configurato: " + systemId);
        }
        return new SapConfiguration(props, info, legacyMode);
    }

    // -------------------------------------------------------
    // CARICAMENTO
    // -------------------------------------------------------

    private static String loadProperties(Properties props) {
        String ext = trim(System.getProperty(EXT_CONFIG_SYSPROP));
        if (ext.isEmpty()) ext = trim(System.getenv(EXT_CONFIG_ENV));

        if (!ext.isEmpty()) {
            Path path = Paths.get(ext);
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("File di configurazione esterno non trovato: " + path
                    + " (impostato tramite " + EXT_CONFIG_SYSPROP + " / " + EXT_CONFIG_ENV + ")");
            }
            try (InputStream is = Files.newInputStream(path)) {
                loadAutoCharset(props, is);
            } catch (IOException e) {
                throw new IllegalStateException("Errore nella lettura di " + path + ": " + e.getMessage(), e);
            }
            return path.toAbsolutePath().toString();
        }

        try (InputStream is = SapSystemRegistry.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new IllegalStateException("File di configurazione non trovato nel classpath: "
                    + CONFIG_FILE + " — verificare che sia in src/main/resources/");
            }
            loadAutoCharset(props, is);
        } catch (IOException e) {
            throw new IllegalStateException("Errore nella lettura di " + CONFIG_FILE + ": " + e.getMessage(), e);
        }
        return "classpath:" + CONFIG_FILE;
    }

    /**
     * Carica le property riconoscendo la codifica del file.
     * <p>
     * Properties.load(InputStream) usa SEMPRE ISO-8859-1: un "Qtà" salvato
     * in UTF-8 (byte C3 A0) diventerebbe "QtÃ". Qui si prova prima UTF-8 in
     * modo rigoroso; se il file non è UTF-8 valido (es. salvato in
     * ISO-8859-1/Cp1252 con "è" = byte E8) si ripiega su ISO-8859-1.
     * Gli escape \\uXXXX funzionano in entrambi i casi.
     */
    private static void loadAutoCharset(Properties props, InputStream is) throws IOException {
        byte[] bytes = is.readAllBytes();
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException notUtf8) {
            text = new String(bytes, StandardCharsets.ISO_8859_1);
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);   // BOM
        props.load(new StringReader(text));
    }

    static String trim(String s) { return s == null ? "" : s.trim(); }

    /** Estrae l'host da un URL (per visualizzazione), o la stringa stessa se non parsabile. */
    public static String hostOf(String url) {
        if (url == null || url.isBlank()) return "?";
        try {
            String h = java.net.URI.create(url.trim()).getHost();
            return h != null ? h : url.trim();
        } catch (Exception e) {
            return url.trim();
        }
    }
}
