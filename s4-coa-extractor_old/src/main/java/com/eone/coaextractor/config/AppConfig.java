package com.eone.coaextractor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Carica e valida la configurazione da file .properties.
 *
 * Ordine di ricerca del file di configurazione:
 *   1. Argomento da linea di comando (es: java -jar app.jar /etc/coa/config.properties)
 *   2. Variabile d'ambiente COA_CONFIG_FILE
 *   3. config.properties nella directory corrente
 *   4. config.properties nel classpath (per sviluppo)
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private final Properties props;

    // --- Campi configurazione ---
    public final String s4BaseUrl;
    public final String s4Username;
    public final String s4Password;
    public final String companyCode;
    public final String chartOfAccounts;
    public final String language;
    public final int pageSize;
    public final int httpTimeoutConnect;
    public final int httpTimeoutRead;

    public final Path outputDirectory;
    public final String outputFilenamePattern;
    public final String outputSeparator;
    public final String outputCharset;
    public final boolean outputIncludeHeader;

    private AppConfig(Properties props) {
        this.props = props;

        // Connessione
        this.s4BaseUrl       = require("s4.base.url").replaceAll("/$", "");
        this.s4Username      = require("s4.username");
        this.s4Password      = require("s4.password");
        this.companyCode     = get("s4.company.code", "S001");
        this.chartOfAccounts = get("s4.chart.of.accounts", "");
        this.language        = get("s4.language", "IT").toUpperCase();
        this.pageSize        = getInt("s4.page.size", 500);
        this.httpTimeoutConnect = getInt("s4.http.timeout.connect", 30);
        this.httpTimeoutRead    = getInt("s4.http.timeout.read", 120);

        // Output
        String outDir = require("output.directory");
        this.outputDirectory       = Paths.get(outDir);
        this.outputFilenamePattern = get("output.filename", "coa_export_yyyyMMdd.csv");
        this.outputSeparator       = get("output.separator", ";");
        this.outputCharset         = get("output.charset", "UTF-8");
        this.outputIncludeHeader   = Boolean.parseBoolean(get("output.include.header", "true"));
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static AppConfig load(String[] args) {
        Path configPath = resolveConfigPath(args);
        Properties props = new Properties();

        if (configPath != null && Files.exists(configPath)) {
            log.info("Caricamento configurazione da: {}", configPath.toAbsolutePath());
            try (InputStream is = Files.newInputStream(configPath)) {
                props.load(is);
            } catch (IOException e) {
                throw new ConfigException("Impossibile leggere il file di configurazione: " + configPath, e);
            }
        } else {
            // Fallback classpath (utile in sviluppo con config.properties in src/main/resources)
            log.info("config.properties non trovato su filesystem, ricerca nel classpath...");
            try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (is == null) {
                    throw new ConfigException(
                        "File di configurazione non trovato. Specificare il percorso come argomento " +
                        "o impostare la variabile d'ambiente COA_CONFIG_FILE.");
                }
                props.load(is);
                log.info("Configurazione caricata dal classpath.");
            } catch (IOException e) {
                throw new ConfigException("Errore lettura configurazione dal classpath", e);
            }
        }

        return new AppConfig(props);
    }

    private static Path resolveConfigPath(String[] args) {
        // 1. Argomento CLI
        if (args != null && args.length > 0) {
            return Paths.get(args[0]);
        }
        // 2. Variabile d'ambiente
        String envPath = System.getenv("COA_CONFIG_FILE");
        if (envPath != null && !envPath.isBlank()) {
            return Paths.get(envPath);
        }
        // 3. Directory corrente
        Path local = Paths.get("config.properties");
        if (Files.exists(local)) {
            return local;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String require(String key) {
        String val = props.getProperty(key);
        if (val == null || val.isBlank()) {
            throw new ConfigException("Parametro obbligatorio mancante in config.properties: " + key);
        }
        return val.trim();
    }

    private String get(String key, String defaultValue) {
        String val = props.getProperty(key);
        return (val == null || val.isBlank()) ? defaultValue : val.trim();
    }

    private int getInt(String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            log.warn("Valore non valido per '{}': '{}'. Uso default: {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return "AppConfig{" +
               "s4BaseUrl='" + s4BaseUrl + '\'' +
               ", s4Username='" + s4Username + '\'' +
               ", companyCode='" + companyCode + '\'' +
               ", chartOfAccounts='" + chartOfAccounts + '\'' +
               ", language='" + language + '\'' +
               ", pageSize=" + pageSize +
               ", outputDirectory=" + outputDirectory +
               ", outputFilenamePattern='" + outputFilenamePattern + '\'' +
               '}';
    }
}
