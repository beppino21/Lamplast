package it.eone.coge;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class AppConfig {

    private final Properties props = new Properties();

    // --- S/4HC connessione ---
    public final String baseUrl;
    public final String username;
    public final String password;
    public final String language;

    // --- Parametri posting ---
    public final String companyCode;
    public final String documentType;       // es. UE
    public final String transitoryAccount;  // solo doc., il conto viene da GKONT nel CSV
    public final String companyCurrency;    // divisa societa' es. EUR
    public final String postingDate;        // YYYY-MM-DD, vuoto = oggi
    public final String createdByUser;

    // --- Modalita' ---
    public final boolean dryRun;

    // --- Filtro opzionale ---
    public final String filterKunnr;

    public AppConfig(String configPath) throws IOException {
        Path p = resolveConfigPath(configPath);
        System.out.println("[CONFIG] Caricamento da: " + p.toAbsolutePath());
        try (InputStream is = Files.newInputStream(p)) {
            props.load(is);
        }

        baseUrl          = required("s4hc.baseUrl").replaceAll("/$", "");
        username         = required("s4hc.username");
        password         = required("s4hc.password");
        language         = get("s4hc.language", "IT");

        companyCode      = required("posting.companyCode");
        documentType     = get("posting.documentType", "UE");
        transitoryAccount= get("posting.transitoryAccount", "");
        companyCurrency  = get("posting.companyCurrency", "EUR");
        postingDate      = get("posting.postingDate", "");
        createdByUser    = get("posting.createdByUser", username);

        dryRun           = Boolean.parseBoolean(get("posting.dryRun", "true"));
        filterKunnr      = get("filter.kunnr", "").trim();

        System.out.println("[CONFIG] dryRun=" + dryRun
                + "  companyCode=" + companyCode
                + "  documentType=" + documentType
                + "  companyCurrency=" + companyCurrency);
        if (!filterKunnr.isEmpty()) {
            System.out.println("[CONFIG] Filtro cliente: " + filterKunnr);
        }
    }

    private Path resolveConfigPath(String configArg) throws IOException {
        if (configArg != null && !configArg.isBlank()) {
            Path p = Paths.get(configArg);
            if (Files.exists(p)) return p;
            throw new IOException("File config non trovato: " + configArg);
        }
        // Accanto al JAR (usa toURI() per evitare /C:/... su Windows)
        Path jarDir = null;
        try {
            jarDir = Paths.get(
                    AppConfig.class.getProtectionDomain()
                                   .getCodeSource()
                                   .getLocation()
                                   .toURI()).getParent();
        } catch (Exception ignored) {}
        if (jarDir != null) {
            Path p = jarDir.resolve("config.properties");
            if (Files.exists(p)) return p;
        }
        // Directory corrente
        Path p = Paths.get("config.properties");
        if (Files.exists(p)) return p;

        throw new IOException("config.properties non trovato. "
                + "Usare --config=<percorso> oppure mettere il file "
                + "nella stessa directory del JAR.");
    }

    private String required(String key) throws IOException {
        String v = props.getProperty(key, "").trim();
        if (v.isEmpty()) throw new IOException("Parametro obbligatorio mancante: " + key);
        return v;
    }

    private String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue).trim();
    }
}
