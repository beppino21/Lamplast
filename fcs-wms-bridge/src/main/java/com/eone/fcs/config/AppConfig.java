package com.eone.fcs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Carica e valida la configurazione da file .properties.
 *
 * Ordine di ricerca del file di configurazione:
 *   1. Argomento da linea di comando: java -jar app.jar /path/config.properties
 *   2. Variabile d'ambiente FCS_CONFIG_FILE
 *   3. config.properties nella directory corrente
 *   4. config.properties nel classpath (sviluppo)
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    // --- S/4HANA ---
    public final String s4BaseUrl;
    public final String s4Username;
    public final String s4Password;
    public final int    s4PageSize;
    public final int    s4TimeoutConnect;
    public final int    s4TimeoutRead;

    // --- PostgreSQL ---
    public final String dbUrl;
    public final String dbUsername;
    public final String dbPassword;
    public final String dbTenant;

    // --- Parametri estrazione S/4 ---
    public final String s4CompanyCode;
    public final String s4Language;
    public final String s4Plant;

    // --- Parametri resi da cliente ---
    /**
     * Valore kappl per le schedulazioni OdV di reso in tabfcseket.
     * config.properties: reso.kappl = V
     */
    public final String kapplReso;

    /**
     * Tipo consegna reso usato in ReturnDeliveryClient.
     * config.properties: reso.delivery.type = LR
     * TODO: aggiornare con il tipo Z confermato dal cliente.
     */
    public final String deliveryTypeReso;

    /**
     * Tipi OdV considerati "resi da cliente" — lista separata da virgola.
     * config.properties: reso.sales.order.types = RE,ZRE
     * Usato da SalesReturnClient per filtrare solo gli OdV di reso.
     * Default: RE (tipo reso standard SAP).
     */
    public final Set<String> salesOrderTypesReso;

    // --- Logging ---
    public final String logDirectory;

    private AppConfig(Properties p) {
        // S/4HANA
        this.s4BaseUrl        = require(p, "s4.base.url").replaceAll("/$", "");
        this.s4Username       = require(p, "s4.username");
        this.s4Password       = require(p, "s4.password");
        this.s4PageSize       = getInt(p, "s4.page.size", 500);
        this.s4TimeoutConnect = getInt(p, "s4.http.timeout.connect", 30);
        this.s4TimeoutRead    = getInt(p, "s4.http.timeout.read", 120);

        // PostgreSQL
        this.dbUrl      = require(p, "db.url");
        this.dbUsername = require(p, "db.username");
        this.dbPassword = require(p, "db.password");
        this.dbTenant   = get(p, "db.tenant", "FCS");

        // Parametri estrazione
        this.s4CompanyCode = get(p, "s4.company.code", "L001");
        this.s4Language    = get(p, "s4.language", "IT").toUpperCase();
        this.s4Plant       = get(p, "s4.plant", "");

        // Parametri resi
        this.kapplReso        = get(p, "reso.kappl",         "V");
        this.deliveryTypeReso = get(p, "reso.delivery.type", "LR");
        this.salesOrderTypesReso = parseSet(p, "reso.sales.order.types", "RE");

        // Logging
        this.logDirectory = get(p, "log.directory", "");
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
                throw new ConfigException(
                    "Impossibile leggere il file di configurazione: " + configPath, e);
            }
        } else {
            log.info("config.properties non trovato su filesystem, ricerca nel classpath...");
            try (InputStream is = AppConfig.class.getClassLoader()
                    .getResourceAsStream("config.properties")) {
                if (is == null) {
                    throw new ConfigException(
                        "File di configurazione non trovato. " +
                        "Specificare il percorso come argomento o impostare FCS_CONFIG_FILE.");
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
        if (args != null && args.length > 0) return Paths.get(args[0]);
        String env = System.getenv("FCS_CONFIG_FILE");
        if (env != null && !env.isBlank()) return Paths.get(env);
        Path local = Paths.get("config.properties");
        if (Files.exists(local)) return local;
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String require(Properties p, String key) {
        String val = p.getProperty(key);
        if (val == null || val.isBlank())
            throw new ConfigException("Parametro obbligatorio mancante: " + key);
        return val.trim();
    }

    private static String get(Properties p, String key, String def) {
        String val = p.getProperty(key);
        return (val == null || val.isBlank()) ? def : val.trim();
    }

    private static int getInt(Properties p, String key, int def) {
        String val = p.getProperty(key);
        if (val == null || val.isBlank()) return def;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) {
            log.warn("Valore non valido per '{}': '{}'. Uso default: {}", key, val, def);
            return def;
        }
    }

    /**
     * Legge una proprietà come lista separata da virgola e la converte in Set<String>.
     * Es. "RE,ZRE,ZRC" → {"RE", "ZRE", "ZRC"}
     */
    private static Set<String> parseSet(Properties p, String key, String def) {
        String val = get(p, key, def);
        Set<String> result = Arrays.stream(val.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        return Collections.unmodifiableSet(result);
    }

    @Override
    public String toString() {
        return "AppConfig{s4BaseUrl='" + s4BaseUrl + "', s4Username='" + s4Username +
               "', dbUrl='" + dbUrl + "', dbTenant='" + dbTenant +
               "', s4CompanyCode='" + s4CompanyCode + "', s4Language='" + s4Language +
               "', kapplReso='" + kapplReso + "', deliveryTypeReso='" + deliveryTypeReso +
               "', salesOrderTypesReso=" + salesOrderTypesReso + "'}";
    }
}
