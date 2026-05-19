package eone.fcs.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Accesso centralizzato a ccee_config.properties.
 *
 * Tutte le classi che necessitano di proprietà di configurazione
 * (percorsi del bridge, credenziali DB, ecc.) devono usare questa
 * classe invece di rileggere il file autonomamente.
 *
 * Il file viene letto una sola volta al primo accesso (singleton lazy).
 */
public class FcsConfig {

    private static final Logger log = LoggerFactory.getLogger(FcsConfig.class);
    private static final String CONFIG_PATH = "eone/ccee_config.properties";

    private static FcsConfig instance;
    private final Properties props;

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private FcsConfig() {
        props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(CONFIG_PATH)) {
            if (is == null) {
                throw new IllegalStateException(
                    CONFIG_PATH + " non trovato nel classpath");
            }
            props.load(is);
            log.info("FcsConfig: configurazione caricata da {}", CONFIG_PATH);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Errore lettura " + CONFIG_PATH + ": " + e.getMessage(), e);
        }
    }

    public static synchronized FcsConfig getInstance() {
        if (instance == null) {
            instance = new FcsConfig();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Accesso alle proprietà
    // -------------------------------------------------------------------------

    /**
     * Restituisce il valore della proprietà, o il default se assente/vuota.
     */
    public String get(String key, String defaultValue) {
        String val = props.getProperty(key);
        return (val == null || val.isBlank()) ? defaultValue : val.trim();
    }

    /**
     * Restituisce il valore della proprietà, o null se assente/vuota.
     */
    public String get(String key) {
        return get(key, null);
    }

    // -------------------------------------------------------------------------
    // Proprietà tipizzate — bridge
    // -------------------------------------------------------------------------

    /**
     * Percorso assoluto del JAR fcs-wms-bridge sul filesystem del server.
     * Configurato in ccee_config.properties: bridge.jar.path
     */
    public String getBridgeJarPath() {
        return get("bridge.jar.path", "/opt/fcs/fcs-wms-bridge-1.0.0.jar");
    }

    /**
     * Percorso assoluto del config.properties del bridge sul filesystem del server.
     * Configurato in ccee_config.properties: bridge.config.path
     */
    public String getBridgeConfigPath() {
        return get("bridge.config.path", "/opt/fcs/config.properties");
    }
}
