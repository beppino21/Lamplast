package eone.fcs.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;

/**
 * Repository per i servizi REST di lettura delle tabelle anagrafiche e EKET.
 *
 * Tabelle gestite:
 *   tabfcsmara  → anagrafica materiali
 *   tabfcskna1  → clienti
 *   tabfcslfa1  → fornitori
 *   tabfcseket  → schedulazioni OdA (con filtri opzionali per lifnr, ebeln, wmsst)
 *
 * La connessione DB viene letta da ccee_config.properties, stesso file di EketRepository.
 */
public class DataRepository {

    private static final Logger log = LoggerFactory.getLogger(DataRepository.class);

    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;

    // -------------------------------------------------------------------------
    // Costruttore
    // -------------------------------------------------------------------------

    public DataRepository() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("eone/ccee_config.properties")) {
            if (is == null) {
                throw new RepositoryException("ccee_config.properties non trovato nel classpath");
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
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
    }

    // =========================================================================
    // MARA - Anagrafica materiali
    // =========================================================================

    /**
     * Restituisce tutti i record di tabfcsmara ordinati per matnr.
     * Campi: matnr, maktx, mtart, matkl, meins, bstme, datum, uzeit, uname, updfl
     */
    public List<Map<String, Object>> findAllMara() {
        String sql = """
                SELECT matnr, maktx, mtart, matkl, meins, bstme,
                       datum, uzeit, uname, updfl
                FROM public.tabfcsmara
                ORDER BY matnr
                """;
        log.debug("findAllMara");
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return toListOfMaps(rs);
        } catch (SQLException e) {
            throw new RepositoryException("Errore findAllMara: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // KNA1 - Clienti
    // =========================================================================

    /**
     * Restituisce tutti i record di tabfcskna1 ordinati per kunnr.
     * Campi: kunnr, name1, name2, stcd1, stcd2, stceg, datum, uzeit, uname, updfl
     */
    public List<Map<String, Object>> findAllKna1() {
        String sql = """
                SELECT kunnr, name1, name2, stcd1, stcd2, stceg,
                       datum, uzeit, uname, updfl
                FROM public.tabfcskna1
                ORDER BY kunnr
                """;
        log.debug("findAllKna1");
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return toListOfMaps(rs);
        } catch (SQLException e) {
            throw new RepositoryException("Errore findAllKna1: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // LFA1 - Fornitori
    // =========================================================================

    /**
     * Restituisce tutti i record di tabfcslfa1 ordinati per lifnr.
     * Campi: lifnr, name1, name2, stcd1, stcd2, stceg, datum, uzeit, uname, updfl
     */
    public List<Map<String, Object>> findAllLfa1() {
        String sql = """
                SELECT lifnr, name1, name2, stcd1, stcd2, stceg,
                       datum, uzeit, uname, updfl
                FROM public.tabfcslfa1
                ORDER BY lifnr
                """;
        log.debug("findAllLfa1");
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return toListOfMaps(rs);
        } catch (SQLException e) {
            throw new RepositoryException("Errore findAllLfa1: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // EKET - Schedulazioni OdA con filtri opzionali
    // =========================================================================

    /**
     * Restituisce i record di tabfcseket con filtri opzionali combinabili.
     *
     * Tutti i parametri sono nullable: se null, il filtro corrispondente
     * non viene applicato (restituisce tutto).
     *
     * @param lifnr  codice fornitore (es. "0000001234") — filtra per fornitore
     * @param ebeln  numero OdA (es. "4500000042") — filtra per singolo ordine
     * @param wmsst  stato WMS: '0'=libero, '1'=assegnato, '2'=in scarico,
     *               '3'=completato, 'E'=errore — filtra per stato
     *
     * Ordinamento: ebeln, ebelp, etenr
     */
    public List<Map<String, Object>> findEket(String lifnr, String ebeln, String wmsst) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    ebeln, ebelp, etenr, id_eket, kappl, xchpf,
                    eindt, reswk, lifnr, name1, matnr, mtart, charg, maktx,
                    werks, lgort, menge, ameng, wemng, wamng, menge_open,
                    meins, bstme, mengexbstme, qtaxtag, bstmexpallet,
                    qtaxbag, nrtag, nrbag, tag_filler,
                    brgew_row, ntgew_row, datum, uzeit, ernam,
                    bemid, gewei, wmsst,
                    in_xblnr, in_traid, in_bldat,
                    in_colli_tot, in_colli_row,
                    in_brgew_tot, in_ntgew_tot,
                    in_menge, in_werks, in_lgort, in_charg,
                    in_brgew_row, in_ntgew_row,
                    in_qtaxtag, in_data_arrivo
                FROM public.tabfcseket
                WHERE 1=1
                """);

        List<Object> params = new ArrayList<>();

        if (lifnr != null && !lifnr.isBlank()) {
            sql.append("AND lifnr = ? ");
            params.add(lifnr.trim());
        }
        if (ebeln != null && !ebeln.isBlank()) {
            sql.append("AND ebeln = ? ");
            params.add(ebeln.trim());
        }
        if (wmsst != null && !wmsst.isBlank()) {
            sql.append("AND wmsst = ? ");
            params.add(wmsst.trim());
        }

        sql.append("ORDER BY ebeln, ebelp, etenr");

        log.debug("findEket lifnr={} ebeln={} wmsst={}", lifnr, ebeln, wmsst);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return toListOfMaps(rs);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Errore findEket: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Utility - converte ResultSet in List<Map<String, Object>>
    // =========================================================================

    /**
     * Converte un ResultSet in una lista di mappe colonna→valore.
     * Tipi Java restituiti:
     *   VARCHAR/CHAR → String
     *   DATE         → java.time.LocalDate (via getObject)
     *   TIME         → java.time.LocalTime (via getObject)
     *   NUMERIC/FLOAT→ Float o Integer secondo il tipo JDBC
     *   BOOLEAN      → Boolean
     *   NULL         → null (il campo è presente nella mappa con valore null)
     *
     * I nomi delle colonne vengono restituiti in lowercase, coerente con
     * PostgreSQL che li normalizza in minuscolo.
     */
    private List<Map<String, Object>> toListOfMaps(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        List<Map<String, Object>> result = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>(); // mantiene l'ordine delle colonne
            for (int i = 1; i <= cols; i++) {
                String col = meta.getColumnName(i).toLowerCase();
                Object val = rs.getObject(i);
                row.put(col, val);
            }
            result.add(row);
        }
        return result;
    }
}
