package eone.fcs.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 *   F - loadRigheByBemid, upsertMseg, archiviaDopoGr, setWmsstErrore
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

        log.info("EketRepository inizializzato: url={}", dbUrl);
    }

    // -------------------------------------------------------------------------
    // Connessione
    // -------------------------------------------------------------------------

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
    }

    // =========================================================================
    // C - Associa bemid alla riga, wmsst=1
    // =========================================================================

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

    // =========================================================================
    // D - Annulla scarico: azzera campi in_*, wmsst=0
    // =========================================================================

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

    // =========================================================================
    // T - Aggiorna dati testata (targa, data arrivo)
    // =========================================================================

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
                if (dataArrivo.isEmpty()) {
                    ps.setNull(idx++, java.sql.Types.DATE);
                } else {
                    ps.setObject(idx++,
                        java.time.LocalDate.parse(toIsoDate(dataArrivo)));
                }
            }
            ps.setString(idx, uuid);
            int rows = ps.executeUpdate();
            log.debug("updateTestata uuid={} rows={}", uuid, rows);
            return rows;
        } catch (SQLException e) {
            throw new RepositoryException("Errore updateTestata: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // U - Aggiorna dati di riga
    // =========================================================================

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
            ps.setBoolean(i++, ddt == null);        ps.setString(i++, ddt);
            ps.setBoolean(i++, dataDdt == null);
            if (dataDdt == null || dataDdt.isBlank()) {
                ps.setNull(i++, java.sql.Types.DATE);
            } else {
                ps.setObject(i++, java.time.LocalDate.parse(toIsoDate(dataDdt)));
            }
            ps.setBoolean(i++, inMenge == null);    ps.setString(i++, inMenge);
            ps.setBoolean(i++, inWerks == null);    ps.setString(i++, inWerks);
            ps.setBoolean(i++, inLgort == null);    ps.setString(i++, inLgort);
            ps.setBoolean(i++, colliTot == null);   ps.setString(i++, colliTot);
            ps.setBoolean(i++, colliRow == null);   ps.setString(i++, colliRow);
            ps.setBoolean(i++, pesoLordoTot == null); ps.setString(i++, pesoLordoTot);
            ps.setBoolean(i++, pesoLordoRow == null); ps.setString(i++, pesoLordoRow);
            ps.setBoolean(i++, pesoNettoTot == null); ps.setString(i++, pesoNettoTot);
            ps.setBoolean(i++, pesoNettoRow == null); ps.setString(i++, pesoNettoRow);
            ps.setBoolean(i++, qtaxtag == null);    ps.setString(i++, qtaxtag);
            ps.setBoolean(i++, inCharg == null);    ps.setString(i++, inCharg);
            ps.setString(i, idEket);

            int rows = ps.executeUpdate();
            log.debug("updateRiga idEket={} rows={}", idEket, rows);
            return rows;
        } catch (SQLException e) {
            throw new RepositoryException("Errore updateRiga: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // I / F - Aggiorna wmsst su tutte le righe del bemid
    // =========================================================================

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

    // =========================================================================
    // F - Verifica esistenza bemid con stato dato
    // =========================================================================

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

    // =========================================================================
    // F - Carica le righe EKET per bemid (input per GR e per MSEG)
    // =========================================================================

    /**
     * Legge tutte le righe di tabfcseket per il bemid dato.
     * Restituisce una lista di record con tutti i campi necessari
     * sia per popolare tabfcsmseg sia per costruire il payload GR verso S/4HC.
     */
    public List<EketRiga> loadRigheByBemid(String uuid) {
        String sql = """
                SELECT tenant, ebeln, ebelp, etenr, id_eket,
                       kappl, matnr, maktx, mtart, lifnr,
                       meins, werks, lgort, charg, xchpf,
                       in_xblnr, in_charg, in_menge, in_werks, in_lgort,
                       in_bldat, in_traid, in_data_arrivo,
                       in_colli_tot, in_colli_row,
                       in_brgew_tot, in_brgew_row,
                       in_ntgew_tot, in_ntgew_row,
                       in_qtaxtag, bemid, ernam
                FROM public.tabfcseket
                WHERE bemid = ?
                ORDER BY ebeln, ebelp, etenr
                """;
        List<EketRiga> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                EketRiga r = new EketRiga();
                r.tenant       = rs.getString("tenant");
                r.ebeln        = rs.getString("ebeln");
                r.ebelp        = rs.getString("ebelp");
                r.etenr        = rs.getString("etenr");
                r.idEket       = rs.getString("id_eket");
                r.kappl        = rs.getString("kappl");
                r.matnr        = rs.getString("matnr");
                r.maktx        = rs.getString("maktx");
                r.mtart        = rs.getString("mtart");
                r.lifnr        = rs.getString("lifnr");
                r.meins        = rs.getString("meins");
                r.werks        = rs.getString("werks");
                r.lgort        = rs.getString("lgort");
                r.charg        = rs.getString("charg");
                r.xchpf        = toBoolean(rs, "xchpf");
                r.inXblnr      = rs.getString("in_xblnr");
                r.inCharg      = rs.getString("in_charg");
                r.inMenge      = toFloat(rs, "in_menge");
                r.inWerks      = rs.getString("in_werks");
                r.inLgort      = rs.getString("in_lgort");
                r.inBldat      = toLocalDate(rs, "in_bldat");
                r.inTraid      = rs.getString("in_traid");
                r.inDataArrivo = toLocalDate(rs, "in_data_arrivo");
                r.inColliTot   = toInteger(rs, "in_colli_tot");
                r.inColliRow   = toInteger(rs, "in_colli_row");
                r.inBrgewTot   = toFloat(rs, "in_brgew_tot");
                r.inBrgewRow   = toFloat(rs, "in_brgew_row");
                r.inNtgewTot   = toFloat(rs, "in_ntgew_tot");
                r.inNtgewRow   = toFloat(rs, "in_ntgew_row");
                r.inQtaxtag    = toFloat(rs, "in_qtaxtag");
                r.bemid        = rs.getString("bemid");
                r.ernam        = rs.getString("ernam");
                result.add(r);
            }
            log.debug("loadRigheByBemid uuid={} righe={}", uuid, result.size());
            return result;
        } catch (SQLException e) {
            throw new RepositoryException("Errore loadRigheByBemid: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // F - Popola tabfcsmseg dalle righe EKET
    // =========================================================================

    /**
     * Inserisce (o aggiorna) le righe in tabfcsmseg a partire dalle righe EKET.
     * La chiave è (tenant, ebeln, ebelp, etenr, in_xblnr, in_charg).
     * In caso di conflitto aggiorna i dati di scarico (quantità, plant, magazzino).
     *
     * Nota: in_xblnr e in_charg possono essere NULL — PostgreSQL tratta NULL
     * come non uguale a NULL nelle chiavi, quindi usiamo COALESCE('') nella
     * constraint. Verificare che la PK sulla tabella sia definita di conseguenza
     * (es. using COALESCE o con colonne NOT NULL con default '').
     */
    public int upsertMseg(List<EketRiga> righe) {
        String sql = """
                INSERT INTO public.tabfcsmseg
                    (tenant, ebeln, ebelp, etenr, in_xblnr, in_charg,
                     id_eket, kappl, bemid, xchpf, mtart, charg,
                     maktx, meins, in_menge, in_werks, in_lgort,
                     datum, uzeit, ernam)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, CURRENT_TIME, ?)
                ON CONFLICT (tenant, ebeln, ebelp, etenr, in_xblnr, in_charg) DO UPDATE SET
                    id_eket  = EXCLUDED.id_eket,
                    bemid    = EXCLUDED.bemid,
                    xchpf    = EXCLUDED.xchpf,
                    mtart    = EXCLUDED.mtart,
                    charg    = EXCLUDED.charg,
                    maktx    = EXCLUDED.maktx,
                    meins    = EXCLUDED.meins,
                    in_menge = EXCLUDED.in_menge,
                    in_werks = EXCLUDED.in_werks,
                    in_lgort = EXCLUDED.in_lgort,
                    datum    = CURRENT_DATE,
                    uzeit    = CURRENT_TIME,
                    ernam    = EXCLUDED.ernam
                """;
        int count = 0;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (EketRiga r : righe) {
                ps.setString(1,  r.tenant);
                ps.setString(2,  r.ebeln);
                ps.setString(3,  r.ebelp);
                ps.setString(4,  r.etenr);
                ps.setString(5,  r.inXblnr);   // può essere null
                ps.setString(6,  r.inCharg);   // può essere null
                ps.setString(7,  r.idEket);
                ps.setString(8,  r.kappl);
                ps.setString(9,  r.bemid);
                ps.setObject(10, r.xchpf);
                ps.setString(11, r.mtart);
                ps.setString(12, r.charg);
                ps.setString(13, r.maktx);
                ps.setString(14, r.meins);
                ps.setObject(15, r.inMenge);
                ps.setString(16, r.inWerks != null ? r.inWerks : r.werks); // fallback su werks OdA
                ps.setString(17, r.inLgort != null ? r.inLgort : r.lgort); // fallback su lgort OdA
                ps.setString(18, r.ernam);
                ps.addBatch();
                count++;
            }
            ps.executeBatch();
            log.info("upsertMseg: {} righe inserite/aggiornate in tabfcsmseg", count);
            return count;
        } catch (SQLException e) {
            throw new RepositoryException("Errore upsertMseg: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // F - Archiviazione atomica post-GR (chiamata solo se GR ha avuto successo)
    // =========================================================================

    /**
     * Esegue in un'unica transazione PostgreSQL le operazioni di archiviazione
     * post-registrazione EM:
     *   1. Copia tabfcseket  → tabfcsekethst  (aggiunge mblnr, mjahr)
     *   2. Copia tabfcsmseg  → tabfcsmseghst  (aggiunge mblnr, mjahr)
     *   3. Cancella da tabfcsmseg  le righe del bemid
     *   4. Cancella da tabfcseket  le righe del bemid
     *
     * Se un qualsiasi passo fallisce, viene fatto rollback dell'intera
     * transazione — nessun dato resta a metà tra live e storico.
     *
     * @param uuid  bemid dello scarico
     * @param mblnr numero documento materiale restituito da S/4HC
     */
    public void archiviaDopoGr(String uuid, String mblnr) {
        String mjahr = String.valueOf(LocalDate.now().getYear());

        String sqlInsertEketHst = """
                INSERT INTO public.tabfcsekethst
                    (tenant, ebeln, ebelp, etenr, mblnr, mjahr,
                     id_eket, kappl, xchpf, eindt, reswk, lifnr,
                     name1, mtart, charg, maktx, werks, lgort,
                     menge, ameng, wemng, wamng, menge_open,
                     meins, bstme, mengexbstme, qtaxtag, bstmexpallet,
                     qtaxbag, nrtag, nrbag, tag_filler, brgew_row, ntgew_row,
                     datum, uzeit, ernam, bemid, gewei, wmsst,
                     in_xblnr, in_traid, in_colli_tot, in_brgew_tot,
                     in_ntgew_tot, in_qtaxtag, in_data_arrivo, in_bldat)
                SELECT
                    tenant, ebeln, ebelp, etenr, ?, ?,
                    id_eket, kappl, xchpf, eindt, reswk, lifnr,
                    name1, mtart, charg, maktx, werks, lgort,
                    menge, ameng, wemng, wamng, menge_open,
                    meins, bstme, mengexbstme, in_qtaxtag, bstmexpallet,
                    qtaxbag, nrtag, nrbag, tag_filler, in_brgew_row, in_ntgew_row,
                    CURRENT_DATE, CURRENT_TIME, ernam, bemid, gewei, wmsst,
                    in_xblnr, in_traid, in_colli_tot, in_brgew_tot,
                    in_ntgew_tot, in_qtaxtag, in_data_arrivo, in_bldat
                FROM public.tabfcseket
                WHERE bemid = ?
                ON CONFLICT DO NOTHING
                """;

        String sqlInsertMsegHst = """
                INSERT INTO public.tabfcsmseghst
                    (tenant, ebeln, ebelp, etenr, in_xblnr, in_charg, mblnr, mjahr,
                     id_eket, kappl, bemid, mtart, charg, maktx, meins,
                     in_menge, in_werks, in_lgort, datum, uzeit, ernam)
                SELECT
                    tenant, ebeln, ebelp, etenr, in_xblnr, in_charg, ?, ?,
                    id_eket, kappl, bemid, mtart, charg, maktx, meins,
                    in_menge, in_werks, in_lgort, CURRENT_DATE, CURRENT_TIME, ernam
                FROM public.tabfcsmseg
                WHERE bemid = ?
                ON CONFLICT DO NOTHING
                """;

        String sqlDeleteMseg  = "DELETE FROM public.tabfcsmseg  WHERE bemid = ?";
        String sqlDeleteEket  = "DELETE FROM public.tabfcseket  WHERE bemid = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. EKET → EKETHST
                try (PreparedStatement ps = conn.prepareStatement(sqlInsertEketHst)) {
                    ps.setString(1, mblnr);
                    ps.setString(2, mjahr);
                    ps.setString(3, uuid);
                    int rows = ps.executeUpdate();
                    log.info("archiviaDopoGr: {} righe copiate in tabfcsekethst", rows);
                }

                // 2. MSEG → MSEGHST
                try (PreparedStatement ps = conn.prepareStatement(sqlInsertMsegHst)) {
                    ps.setString(1, mblnr);
                    ps.setString(2, mjahr);
                    ps.setString(3, uuid);
                    int rows = ps.executeUpdate();
                    log.info("archiviaDopoGr: {} righe copiate in tabfcsmseghst", rows);
                }

                // 3. Cancella da MSEG
                try (PreparedStatement ps = conn.prepareStatement(sqlDeleteMseg)) {
                    ps.setString(1, uuid);
                    int rows = ps.executeUpdate();
                    log.info("archiviaDopoGr: {} righe cancellate da tabfcsmseg", rows);
                }

                // 4. Cancella da EKET
                try (PreparedStatement ps = conn.prepareStatement(sqlDeleteEket)) {
                    ps.setString(1, uuid);
                    int rows = ps.executeUpdate();
                    log.info("archiviaDopoGr: {} righe cancellate da tabfcseket", rows);
                }

                conn.commit();
                log.info("archiviaDopoGr: transazione completata per uuid={} mblnr={}", uuid, mblnr);

            } catch (SQLException e) {
                conn.rollback();
                log.error("archiviaDopoGr: rollback per uuid={} — {}", uuid, e.getMessage());
                throw new RepositoryException("Errore archiviazione post-GR: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Errore connessione in archiviaDopoGr: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // F - Marca errore se GR fallisce
    // =========================================================================

    /**
     * Imposta wmsst='E' su tutte le righe del bemid in caso di fallimento GR.
     * Le righe rimangono in tabfcseket e tabfcsmseg per analisi e correzione.
     */
    public int setWmsstErrore(String uuid) {
        String sql = """
                UPDATE public.tabfcseket
                SET wmsst = 'E',
                    datum = CURRENT_DATE,
                    uzeit = CURRENT_TIME
                WHERE bemid = ?
                AND wmsst NOT IN ('3', 'E')
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            int rows = ps.executeUpdate();
            log.warn("setWmsstErrore: {} righe marcate in errore per uuid={}", rows, uuid);
            return rows;
        } catch (SQLException e) {
            throw new RepositoryException("Errore setWmsstErrore: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // DTO interno — riga EKET letta per evento F
    // =========================================================================

    /**
     * Struttura dati che rappresenta una riga di tabfcseket letta per l'evento F.
     * Usata internamente tra repository e resource/client GR.
     * Classe pubblica statica per permettere l'accesso da EketResource e
     * da GoodsReceiptClient senza dipendenze circolari.
     */
    public static class EketRiga {
        public String tenant;
        public String ebeln;
        public String ebelp;
        public String etenr;
        public String idEket;
        public String kappl;
        public String matnr;
        public String maktx;
        public String mtart;
        public String lifnr;
        public String meins;
        public String werks;
        public String lgort;
        public String charg;
        public Boolean xchpf;
        public String inXblnr;
        public String inCharg;
        public Float  inMenge;
        public String inWerks;
        public String inLgort;
        public java.time.LocalDate inBldat;
        public String inTraid;
        public java.time.LocalDate inDataArrivo;
        public Integer inColliTot;
        public Integer inColliRow;
        public Float   inBrgewTot;
        public Float   inBrgewRow;
        public Float   inNtgewTot;
        public Float   inNtgewRow;
        public Float   inQtaxtag;
        public String  bemid;
        public String  ernam;
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /**
     * Legge un campo NUMERIC/FLOAT dal ResultSet come Float, gestendo il NULL.
     * Necessario perché getObject(col, Float.class) non è supportato da tutti
     * i driver PostgreSQL per il tipo NUMERIC/DECIMAL.
     */
    private Float toFloat(ResultSet rs, String col) throws SQLException {
        float val = rs.getFloat(col);
        return rs.wasNull() ? null : val;
    }

    /**
     * Legge un campo INTEGER dal ResultSet come Integer, gestendo il NULL.
     */
    private Integer toInteger(ResultSet rs, String col) throws SQLException {
        int val = rs.getInt(col);
        return rs.wasNull() ? null : val;
    }

    /**
     * Legge un campo BOOLEAN dal ResultSet come Boolean, gestendo il NULL.
     */
    private Boolean toBoolean(ResultSet rs, String col) throws SQLException {
        boolean val = rs.getBoolean(col);
        return rs.wasNull() ? null : val;
    }

    /**
     * Legge un campo DATE dal ResultSet come LocalDate, gestendo il NULL.
     */
    private java.time.LocalDate toLocalDate(ResultSet rs, String col) throws SQLException {
        java.sql.Date val = rs.getDate(col);
        return val == null ? null : val.toLocalDate();
    }

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
