package eone.fcs.repository;

import java.sql.*;
import java.time.*;
import java.util.UUID;

/**
 * Gestisce il ciclo di vita delle righe in TABFCSMOVSAP (staging movimenti MM)
 * e l'archiviazione in TABFCSMOVSAPHST dopo la conferma SAP.
 *
 * La connessione DB viene letta da eone/ccee_config.properties nel classpath,
 * coerentemente con EketRepository e DataRepository.
 */
public class MovSapRepository {

    // -----------------------------------------------------------------------
    // Connessione DB
    // -----------------------------------------------------------------------

    private Connection getConnection() throws SQLException {
        try {
            java.io.InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("eone/ccee_config.properties");
            java.util.Properties props = new java.util.Properties();
            props.load(is);
            String url  = props.getProperty("db.url");
            String user = props.getProperty("db.username");
            String pwd  = props.getProperty("db.password");
            return DriverManager.getConnection(url, user, pwd);
        } catch (Exception e) {
            throw new SQLException("Impossibile leggere ccee_config.properties: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // UPSERT staging (action M — popolamento)
    // -----------------------------------------------------------------------

    /**
     * INSERT ON CONFLICT UPDATE su TABFCSMOVSAP.
     * datum, uzeit, uname vengono sempre sovrascritti con i valori attuali.
     * wmsst viene impostato a NULL (movimento da elaborare).
     */
    public void upsertMovsap(MovsapRiga r) {
        String sql = """
            INSERT INTO tabfcsmovsap
              (movid, bwart, lifnr, kunnr, kostl, aufnr, prctr, sobkz,
               werks, lgort, matnr, charg, menge,
               werks_to, lgort_to, matnr_to, charg_to, menge_to,
               meins, wmsst, datum, uzeit, uname)
            VALUES
              (?::uuid, ?, ?, ?, ?, ?, ?, ?,
               ?, ?, ?, ?, ?,
               ?, ?, ?, ?, ?,
               ?, NULL, CURRENT_DATE, CURRENT_TIME, ?)
            ON CONFLICT (movid) DO UPDATE SET
               bwart    = EXCLUDED.bwart,
               lifnr    = EXCLUDED.lifnr,
               kunnr    = EXCLUDED.kunnr,
               kostl    = EXCLUDED.kostl,
               aufnr    = EXCLUDED.aufnr,
               prctr    = EXCLUDED.prctr,
               sobkz    = EXCLUDED.sobkz,
               werks    = EXCLUDED.werks,
               lgort    = EXCLUDED.lgort,
               matnr    = EXCLUDED.matnr,
               charg    = EXCLUDED.charg,
               menge    = EXCLUDED.menge,
               werks_to = EXCLUDED.werks_to,
               lgort_to = EXCLUDED.lgort_to,
               matnr_to = EXCLUDED.matnr_to,
               charg_to = EXCLUDED.charg_to,
               menge_to = EXCLUDED.menge_to,
               meins    = EXCLUDED.meins,
               wmsst    = NULL,
               datum    = EXCLUDED.datum,
               uzeit    = EXCLUDED.uzeit,
               uname    = EXCLUDED.uname
            """;

        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1,  r.movid);
            ps.setString(2,  r.bwart);
            ps.setString(3,  r.lifnr);
            ps.setString(4,  r.kunnr);
            ps.setString(5,  r.kostl);
            ps.setString(6,  r.aufnr);
            ps.setString(7,  r.prctr);
            ps.setString(8,  r.sobkz);
            ps.setString(9,  r.werks);
            ps.setString(10, r.lgort);
            ps.setString(11, r.matnr);
            ps.setString(12, r.charg);
            setFloatOrNull(ps, 13, r.menge);
            ps.setString(14, r.werks_to);
            ps.setString(15, r.lgort_to);
            ps.setString(16, r.matnr_to);
            ps.setString(17, r.charg_to);
            setFloatOrNull(ps, 18, r.menge_to);
            ps.setString(19, r.meins);
            ps.setString(20, r.uname);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RepositoryException("upsertMovsap fallito per movid=" + r.movid, e);
        }
    }

    // -----------------------------------------------------------------------
    // Lettura staging per elaborazione
    // -----------------------------------------------------------------------

    /**
     * Legge la riga di staging da TABFCSMOVSAP dato il movid.
     * Restituisce null se non trovata.
     */
    public MovsapRiga loadByMovid(String movid) {
        String sql = """
            SELECT movid::text, bwart, lifnr, kunnr, kostl, aufnr, prctr, sobkz,
                   werks, lgort, matnr, charg, menge,
                   werks_to, lgort_to, matnr_to, charg_to, menge_to,
                   meins, wmsst, datum, uzeit, uname
            FROM tabfcsmovsap
            WHERE movid = ?::uuid
            """;

        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, movid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return fromResultSet(rs);
            }
            return null;

        } catch (SQLException e) {
            throw new RepositoryException("loadByMovid fallito per movid=" + movid, e);
        }
    }

    // -----------------------------------------------------------------------
    // Aggiornamento wmsst
    // -----------------------------------------------------------------------

    /**
     * Imposta wmsst='2' (elaborato con successo) su TABFCSMOVSAP.
     */
    public void setWmsstSuccess(String movid) {
        setWmsst(movid, "2");
    }

    /**
     * Imposta wmsst='E' (errore elaborazione) su TABFCSMOVSAP.
     */
    public void setWmsstErrore(String movid) {
        setWmsst(movid, "E");
    }

    private void setWmsst(String movid, String stato) {
        String sql = "UPDATE tabfcsmovsap SET wmsst = ? WHERE movid = ?::uuid";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, stato);
            ps.setString(2, movid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("setWmsst(" + stato + ") fallito per movid=" + movid, e);
        }
    }

    // -----------------------------------------------------------------------
    // Archiviazione (transazione atomica: HST insert + live delete)
    // -----------------------------------------------------------------------

    /**
     * Archivia il movimento dopo conferma SAP:
     * 1. INSERT in TABFCSMOVSAPHST con mblnr e mjahr
     * 2. DELETE da TABFCSMOVSAP
     * Tutto in una singola transazione.
     */
    public void archiviaDopoMovimento(String movid, String mblnr, String mjahr) {
        String sqlHst = """
            INSERT INTO tabfcsmovsaphst
              (movid, bwart, lifnr, kunnr, kostl, aufnr, prctr, sobkz,
               werks, lgort, matnr, charg, menge,
               werks_to, lgort_to, matnr_to, charg_to, menge_to,
               meins, wmsst, datum, uzeit, uname,
               mblnr, mjahr)
            SELECT
               movid, bwart, lifnr, kunnr, kostl, aufnr, prctr, sobkz,
               werks, lgort, matnr, charg, menge,
               werks_to, lgort_to, matnr_to, charg_to, menge_to,
               meins, '2', datum, uzeit, uname,
               ?, ?
            FROM tabfcsmovsap
            WHERE movid = ?::uuid
            ON CONFLICT (movid) DO UPDATE SET
               mblnr = EXCLUDED.mblnr,
               mjahr = EXCLUDED.mjahr,
               wmsst = '2'
            """;

        String sqlDel = "DELETE FROM tabfcsmovsap WHERE movid = ?::uuid";

        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(sqlHst)) {
                    ps.setString(1, mblnr);
                    ps.setString(2, mjahr);
                    ps.setString(3, movid);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(sqlDel)) {
                    ps.setString(1, movid);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RepositoryException("archiviaDopoMovimento fallito per movid=" + movid, e);
        }
    }

    // -----------------------------------------------------------------------
    // DTO
    // -----------------------------------------------------------------------

    public static class MovsapRiga {
        public String movid;
        public String bwart;
        public String lifnr;
        public String kunnr;
        public String kostl;
        public String aufnr;
        public String prctr;
        public String sobkz;
        public String werks;
        public String lgort;
        public String matnr;
        public String charg;
        public Float  menge;
        public String werks_to;
        public String lgort_to;
        public String matnr_to;
        public String charg_to;
        public Float  menge_to;
        public String meins;
        public String wmsst;
        public LocalDate datum;
        public LocalTime uzeit;
        public String uname;
    }

    // -----------------------------------------------------------------------
    // Helpers privati
    // -----------------------------------------------------------------------

    private MovsapRiga fromResultSet(ResultSet rs) throws SQLException {
        MovsapRiga r = new MovsapRiga();
        r.movid    = rs.getString("movid");
        r.bwart    = rs.getString("bwart");
        r.lifnr    = rs.getString("lifnr");
        r.kunnr    = rs.getString("kunnr");
        r.kostl    = rs.getString("kostl");
        r.aufnr    = rs.getString("aufnr");
        r.prctr    = rs.getString("prctr");
        r.sobkz    = rs.getString("sobkz");
        r.werks    = rs.getString("werks");
        r.lgort    = rs.getString("lgort");
        r.matnr    = rs.getString("matnr");
        r.charg    = rs.getString("charg");
        r.menge    = toFloat(rs, "menge");
        r.werks_to = rs.getString("werks_to");
        r.lgort_to = rs.getString("lgort_to");
        r.matnr_to = rs.getString("matnr_to");
        r.charg_to = rs.getString("charg_to");
        r.menge_to = toFloat(rs, "menge_to");
        r.meins    = rs.getString("meins");
        r.wmsst    = rs.getString("wmsst");
        r.datum    = toLocalDate(rs, "datum");
        r.uzeit    = toLocalTime(rs, "uzeit");
        r.uname    = rs.getString("uname");
        return r;
    }

    private Float toFloat(ResultSet rs, String col) throws SQLException {
        Object v = rs.getObject(col);
        if (v == null) return null;
        return ((Number) v).floatValue();
    }

    private LocalDate toLocalDate(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    private LocalTime toLocalTime(ResultSet rs, String col) throws SQLException {
        Time t = rs.getTime(col);
        return t == null ? null : t.toLocalTime();
    }

    private void setFloatOrNull(PreparedStatement ps, int idx, Float v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.NUMERIC);
        else           ps.setFloat(idx, v);
    }
}
