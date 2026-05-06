package eone.fcs.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.Properties;

/**
 * Repository per le operazioni sul ciclo di vita dello scarico in tabfcseket.
 *
 * La connessione al DB viene letta da ccee_config.properties — lo stesso
 * file usato da CaptainCasa — così non duplichiamo le credenziali.
 *
 * Gestisce tutte le action del WMS RFID:
 *   C - assignBemid
 *   D - cancelByBemid / cancelByIdEket
 *   T - updateTestata
 *   U - updateRiga
 *   I - setWmsst
 *   F - existsBemidWithStatus, insertMsegHst
 */
public class EketRepository {

    private static final Logger log = LoggerFactory.getLogger(EketRepository.class);

    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;

    // -------------------------------------------------------------------------
    // Costruttore — legge le credenziali da ccee_config.properties
    // -------------------------------------------------------------------------

    public EketRepository() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("ccee_config.properties")) {
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

        log.info("EketRepository inizializzato: url={}", dbUrl);
    }

    // -------------------------------------------------------------------------
    // Connessione
    // -------------------------------------------------------------------------

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
    }

    // -------------------------------------------------------------------------
    // C - Associa bemid alla riga, wmsst=1
    // -------------------------------------------------------------------------

    /**
     * Associa il bemid (UUID) alla riga identificata da id_eket.
     * Solo se la riga è in stato 0 o ' ' (libera).
     *
     * @return numero di righe aggiornate (0 = riga non trovata o già in uso)
     */
    public int assignBemid(String idEket, String uuid) {
        String sql = """
                UPDATE public.tabfcseket
                SET bemid = ?, wmsst = '1'
                WHERE id_eket = ?
                AND (wmsst = '0' OR wmsst = ' ' OR wmsst IS NULL)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, idEket);
            int rows = ps.executeUpdate();
            log.debug("assignBemid idEket={} uuid={} rows={}", idEket, uuid, rows);
            return rows;
        } catch (SQLException e) {
            throw new RepositoryException("Errore assignBemid: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // D - Annulla scarico: azzera campi in_*, wmsst=0
    // -------------------------------------------------------------------------

    /**
     * Annulla tutte le righe con il bemid dato.
     * Non tocca righe con wmsst=3 (completate).
     */
    public int cancelByBemid(String uuid) {
        String sql = """
                UPDATE public.tabfcseket
                SET bemid          = NULL,
                    wmsst          = '0',
                    in_xblnr       = NULL,
                    in_bldat       = NULL,
                    in_traid       = NULL,
                    in_brgew_tot   = NULL,
                    in_brgew_row   = NULL,
                    in_ntgew_tot   = NULL,
                    in_ntgew_row   = NULL,
                    in_charg       = NULL,
                    in_menge       = NULL,
                    in_werks       = NULL,
                    in_lgort       = NULL,
                    in_colli_tot   = NULL,
                    in_colli_row   = NULL,
                    in_data_arrivo = NULL,
                    in_qtaxtag     = NULL
                WHERE bemid = ?
                AND wmsst NOT IN ('3', '0')
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            int rows = ps.executeUpdate();
            log.debug("cancelByBemid uuid={} rows={}", uuid, rows);
            return rows;
        } catch (SQLException e) {
            throw new RepositoryException("Errore cancelByBemid: " + e.getMessage(), e);
        }
    }

    /**
     * Annulla una riga specifica per id_eket.
     */
    public int cancelByIdEket(String idEket, String uuid) {
        String sql = """
                UPDATE public.tabfcseket
                SET bemid          = NULL,
                    wmsst          = '0',
                    in_xblnr       = NULL,
                    in_bldat       = NULL,
                    in_traid       = NULL,
                    in_brgew_tot   = NULL,
                    in_brgew_row   = NULL,
                    in_ntgew_tot   = NULL,
                    in_ntgew_row   = NULL,
                    in_charg       = NULL,
                    in_menge       = NULL,
                    in_werks       = NULL,
                    in_lgort       = NULL,
                    in_colli_tot   = NULL,
                    in_colli_row   = NULL,
                    in_data_arrivo = NULL,
                    in_qtaxtag     = NULL
                WHERE id_eket = ?
                AND wmsst NOT IN ('3', '0')
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idEket);
            int rows = ps.executeUpdate();
            log.debug("cancelByIdEket idEket={} rows={}", idEket, rows);
            return rows;
        } catch (SQLException e) {
            throw new RepositoryException("Errore cancelByIdEket: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // T - Aggiorna dati testata (targa, data arrivo)
    // -------------------------------------------------------------------------

    /**
     * Aggiorna targa e/o data arrivo su tutte le righe del bemid.
     * Stringa vuota = cancella il campo, null = non modificare.
     */
    public int updateTestata(String uuid, String targa, String dataArrivo) {
        StringBuilder sql = new StringBuilder(
                "UPDATE public.tabfcseket SET " +
                "datum = CURRENT_DATE, uzeit = CURRENT_TIME");

        if (targa != null) {
            sql.append(", in_traid = ?");
        }
        if (dataArrivo != null) {
            sql.append(", in_data_arrivo = ?");
        }
        sql.append(" WHERE bemid = ? AND wmsst NOT IN ('3', '0')");

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (targa != null) {
                ps.setString(idx++, targa.isEmpty() ? null : targa);
            }
            if (dataArrivo != null) {
                ps.setString(idx++, dataArrivo.isEmpty() ? null : dataArrivo);
            }
            ps.setString(idx, uuid);
            int rows = ps.executeUpdate();
            log.debug("updateTestata uuid={} rows={}", uuid, rows);
            return rows;
        } catch (SQLException e) {
            throw new RepositoryException("Errore updateTestata: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // U - Aggiorna dati di riga
    // -------------------------------------------------------------------------

    /**
     * Aggiorna i dati di scarico di una riga specifica identificata da id_eket.
     * Aggiorna solo i campi non null passati.
     * Stringa vuota = cancella il campo.
     * Non modifica righe con wmsst=0 o wmsst=3.
     */
    public int updateRiga(String idEket, String ddt, String dataDdt,
                          String inMenge, String inWerks, String inLgort,
                          String colliTot, String colliRow,
                          String pesoLordoTot, String pesoLordoRow,
                          String pesoNettoTot, String pesoNettoRow,
                          String qtaxtag, String inCharg) {

        String sql = """
                UPDATE public.tabfcseket SET
                    datum        = CURRENT_DATE,
                    uzeit        = CURRENT_TIME,
                    in_xblnr     = CASE WHEN ? THEN in_xblnr     ELSE ?::varchar  END,
                    in_bldat     = CASE WHEN ? THEN in_bldat     ELSE ?::date     END,
                    in_menge     = CASE WHEN ? THEN in_menge     ELSE ?::numeric  END,
                    in_werks     = CASE WHEN ? THEN werks        ELSE ?::varchar  END,
                    in_lgort     = CASE WHEN ? THEN in_lgort     ELSE ?::varchar  END,
                    in_colli_tot = CASE WHEN ? THEN in_colli_tot ELSE ?::integer  END,
                    in_colli_row = CASE WHEN ? THEN in_colli_row ELSE ?::integer  END,
                    in_brgew_tot = CASE WHEN ? THEN in_brgew_tot ELSE ?::numeric  END,
                    in_brgew_row = CASE WHEN ? THEN in_brgew_row ELSE ?::numeric  END,
                    in_ntgew_tot = CASE WHEN ? THEN in_ntgew_tot ELSE ?::numeric  END,
                    in_ntgew_row = CASE WHEN ? THEN in_ntgew_row ELSE ?::numeric  END,
                    in_qtaxtag   = CASE WHEN ? THEN in_qtaxtag   ELSE ?::numeric  END,
                    in_charg     = CASE WHEN ? THEN in_charg     ELSE ?::varchar  END
                WHERE id_eket = ?
                AND wmsst NOT IN ('0', '3')
                """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            // in_xblnr (ddt)
            ps.setBoolean(i++, ddt == null);
            ps.setString (i++, ddt);
            // in_bldat (dataDdt formato YYYYMMDD → ISO)
            ps.setBoolean(i++, dataDdt == null);
            ps.setString (i++, toIsoDate(dataDdt));
            // in_menge
            ps.setBoolean(i++, inMenge == null);
            ps.setString (i++, inMenge);
            // in_werks (se non passato usa werks della riga)
            ps.setBoolean(i++, inWerks == null);
            ps.setString (i++, inWerks);
            // in_lgort
            ps.setBoolean(i++, inLgort == null);
            ps.setString (i++, inLgort);
            // in_colli_tot
            ps.setBoolean(i++, colliTot == null);
            ps.setString (i++, colliTot);
            // in_colli_row
            ps.setBoolean(i++, colliRow == null);
            ps.setString (i++, colliRow);
            // in_brgew_tot
            ps.setBoolean(i++, pesoLordoTot == null);
            ps.setString (i++, pesoLordoTot);
            // in_brgew_row
            ps.setBoolean(i++, pesoLordoRow == null);
            ps.setString (i++, pesoLordoRow);
            // in_ntgew_tot
            ps.setBoolean(i++, pesoNettoTot == null);
            ps.setString (i++, pesoNettoTot);
            // in_ntgew_row
            ps.setBoolean(i++, pesoNettoRow == null);
            ps.setString (i++, pesoNettoRow);
            // in_qtaxtag
            ps.setBoolean(i++, qtaxtag == null);
            ps.setString (i++, qtaxtag);
            // in_charg
            ps.setBoolean(i++, inCharg == null);
            ps.setString (i++, inCharg);
            // WHERE
            ps.setString(i, idEket);

            int rows = ps.executeUpdate();
            log.debug("updateRiga idEket={} rows={}", idEket, rows);
            return rows;
        } catch (SQLException e) {
            throw new RepositoryException("Errore updateRiga: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // I / F - Aggiorna wmsst su tutte le righe del bemid
    // -------------------------------------------------------------------------

    /**
     * Imposta wmsst su tutte le righe del bemid.
     * Non tocca righe già completate (wmsst=3).
     */
    public int setWmsst(String uuid, String wmsst) {
        String sql = """
                UPDATE public.tabfcseket
                SET wmsst = ?
                WHERE bemid = ?
                AND wmsst <> '3'
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, wmsst);
            ps.setString(2, uuid);
            int rows = ps.executeUpdate();
            log.debug("setWmsst uuid={} wmsst={} rows={}", uuid, wmsst, rows);
            return rows;
        } catch (SQLException e) {
            throw new RepositoryException("Errore setWmsst: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // F - Verifica esistenza bemid con stato dato
    // -------------------------------------------------------------------------

    public boolean existsBemidWithStatus(String uuid, String wmsst) {
        String sql = """
                SELECT COUNT(*) FROM public.tabfcseket
                WHERE bemid = ? AND wmsst = ?
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, wmsst);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RepositoryException("Errore existsBemidWithStatus: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // F - Inserisce righe in tabfcsmseghst dopo creazione GR
    // -------------------------------------------------------------------------

    /**
     * Copia le righe dello scarico da tabfcseket a tabfcsmseghst
     * aggiungendo mblnr (numero doc. materiale SAP) e mjahr (anno esercizio).
     */
    public void insertMsegHst(String uuid, String mblnr) {
        String mjahr = String.valueOf(LocalDate.now().getYear());
        String sql = """
                INSERT INTO public.tabfcsmseghst
                    (tenant, ebeln, ebelp, etenr, in_charg, in_xblnr,
                     mblnr, mjahr, bemid, charg, datum, ernam, id_eket,
                     in_lgort, in_menge, in_werks, kappl, maktx, matnr,
                     meins, mtart, uzeit)
                SELECT
                    tenant, ebeln, ebelp, etenr, in_charg, in_xblnr,
                    ?, ?, bemid, charg, CURRENT_DATE, ernam, id_eket,
                    in_lgort, in_menge, in_werks, kappl, maktx, matnr,
                    meins, mtart, CURRENT_TIME
                FROM public.tabfcseket
                WHERE bemid = ?
                ON CONFLICT DO NOTHING
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mblnr);
            ps.setString(2, mjahr);
            ps.setString(3, uuid);
            int rows = ps.executeUpdate();
            log.debug("insertMsegHst uuid={} mblnr={} rows={}", uuid, mblnr, rows);
        } catch (SQLException e) {
            throw new RepositoryException("Errore insertMsegHst: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Converte data da formato ABAP YYYYMMDD a ISO YYYY-MM-DD.
     */
    private String toIsoDate(String abapDate) {
        if (abapDate == null || abapDate.trim().isEmpty()) return null;
        abapDate = abapDate.trim();
        if (abapDate.length() == 8) {
            return abapDate.substring(0, 4) + "-" +
                   abapDate.substring(4, 6) + "-" +
                   abapDate.substring(6, 8);
        }
        return abapDate;
    }
}
