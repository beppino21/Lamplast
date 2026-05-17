package eone.fcs.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Repository per la tracciatura delle chiamate REST in arrivo e delle
 * relative risposte su TABFCSRESTLOG.
 *
 * La PK è (zdatetime, prog):
 *   - zdatetime: timestamp della chiamata
 *   - prog: progressivo per distinguere chiamate con stesso timestamp,
 *           gestito tramite AtomicInteger statico (si azzera al riavvio Tomcat,
 *           sufficiente per garantire unicità in sessione).
 *
 * Stessa convenzione di connessione di EketRepository e MovSapRepository:
 * credenziali lette da eone/ccee_config.properties.
 */
public class RestLogRepository {

    private static final Logger log = LoggerFactory.getLogger(RestLogRepository.class);

    /**
     * Progressivo per la PK (zdatetime, prog).
     * Statico per essere condiviso tra tutte le istanze nella stessa JVM.
     * Si azzera al riavvio di Tomcat — accettabile per lo scopo del log.
     */
    private static final AtomicInteger progCounter = new AtomicInteger(0);

    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;

    // -------------------------------------------------------------------------
    // Costruttore
    // -------------------------------------------------------------------------

    public RestLogRepository() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("eone/ccee_config.properties")) {
            if (is == null) {
                throw new RepositoryException(
                    "ccee_config.properties non trovato nel classpath");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RepositoryException("Errore lettura ccee_config.properties", e);
        }

        this.dbUrl      = props.getProperty("db_url");
        this.dbUsername = props.getProperty("db_username");
        this.dbPassword = props.getProperty("db_password");

        if (dbUrl == null || dbUrl.isBlank()) {
            throw new RepositoryException("db_url non configurato in ccee_config.properties");
        }

        log.info("RestLogRepository inizializzato: url={}", dbUrl);
    }

    // -------------------------------------------------------------------------
    // Connessione
    // -------------------------------------------------------------------------

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
    }

    // -------------------------------------------------------------------------
    // Log chiamata
    // -------------------------------------------------------------------------

    /**
     * Registra una chiamata WS in TABFCSRESTLOG (senza id_eket né movid).
     * Usato da EketResource per le action che non hanno ancora un ID specifico
     * (H, C, D, T, U, I) e da MovSapResource per l'health check.
     */
    public void log(String httpMethod, String queryString, String response) {
        log(httpMethod, queryString, response, null, null);
    }

    /**
     * Registra una chiamata WS in TABFCSRESTLOG con id_eket e/o movid.
     *
     * @param httpMethod   metodo HTTP (es. "GET")
     * @param queryString  query string completa della chiamata
     * @param response     risposta restituita al WMS
     * @param idEket       ID dello scarico da FCSEKET (action F inbound), null se non applicabile
     * @param movid        ID del movimento da FCSMOVSAP (action M movement), null se non applicabile
     */
    public void log(String httpMethod, String queryString, String response,
                    String idEket, String movid) {
        LocalDateTime zdatetime = LocalDateTime.now();
        int prog = progCounter.incrementAndGet();

        queryString = truncate(queryString, 500);
        response    = truncate(response,    500);
        idEket      = truncate(idEket,       24);
        movid       = truncate(movid,        50);

        String sql = """
                INSERT INTO tabfcsrestlog
                    (tenant, zdatetime, prog, http_method, query_string, response,
                     id_eket, movid)
                VALUES
                    ('undefined', ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, zdatetime);
            ps.setInt   (2, prog);
            ps.setString(3, httpMethod);
            ps.setString(4, queryString);
            ps.setString(5, response);
            ps.setString(6, idEket);
            ps.setString(7, movid);
            ps.executeUpdate();
            log.debug("RestLog: prog={} idEket={} movid={} response={}", prog, idEket, movid, response);
        } catch (SQLException e) {
            log.error("Errore scrittura RestLog: {} — chiamata: {}", e.getMessage(), queryString);
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private String truncate(String s, int maxLength) {
        if (s == null) return null;
        return s.length() <= maxLength ? s : s.substring(0, maxLength);
    }
}
