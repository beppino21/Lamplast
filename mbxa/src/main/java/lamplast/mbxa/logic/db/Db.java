package lamplast.mbxa.logic.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import lamplast.mbxa.logic.config.AppConfig;

/**
 * Connessioni PostgreSQL e helper JDBC. Volumi e utenti sono pochi (intranet):
 * una connessione per operazione, senza pool.
 */
public final class Db {

    private Db() {}

    /** Operazione eseguita in una transazione. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection c) throws Exception;
    }

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[MBXA] Driver PostgreSQL non trovato nel classpath");
        }
    }

    public static Connection open() throws SQLException {
        AppConfig cfg = AppConfig.get();
        return DriverManager.getConnection(cfg.getDbUrl(), cfg.getDbUser(), cfg.getDbPassword());
    }

    /** Esegue {@code work} in una transazione: commit se va a buon fine, rollback altrimenti. */
    public static <T> T inTransaction(Work<T> work) throws Exception {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------------
    // parametri
    // ------------------------------------------------------------------------

    public static void setString(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null || v.isBlank()) ps.setNull(i, Types.VARCHAR);
        else ps.setString(i, v.trim());
    }

    public static void setInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER);
        else ps.setInt(i, v);
    }

    public static void setDecimal(PreparedStatement ps, int i, BigDecimal v) throws SQLException {
        if (v == null) ps.setNull(i, Types.NUMERIC);
        else ps.setBigDecimal(i, v);
    }

    public static void setDate(PreparedStatement ps, int i, LocalDate v) throws SQLException {
        if (v == null) ps.setNull(i, Types.DATE);
        else ps.setObject(i, v);
    }

    public static void setDateTime(PreparedStatement ps, int i, LocalDateTime v) throws SQLException {
        if (v == null) ps.setNull(i, Types.TIMESTAMP);
        else ps.setObject(i, v);
    }

    // ------------------------------------------------------------------------
    // lettura
    // ------------------------------------------------------------------------

    public static Integer getInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    public static LocalDate getDate(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDate.class);
    }

    public static LocalDateTime getDateTime(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDateTime.class);
    }

    /** Colonne timestamptz, convertite all'ora locale del server. */
    public static LocalDateTime getDateTimeTz(ResultSet rs, String col) throws SQLException {
        OffsetDateTime odt = rs.getObject(col, OffsetDateTime.class);
        return odt == null ? null : odt.atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime();
    }
}
