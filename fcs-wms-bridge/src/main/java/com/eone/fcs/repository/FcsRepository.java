package com.eone.fcs.repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eone.fcs.config.AppConfig;
import com.eone.fcs.model.Customer;
import com.eone.fcs.model.EketLine;
import com.eone.fcs.model.Product;
import com.eone.fcs.model.Supplier;

/**
 * Repository PostgreSQL per le tabelle FCS.
 *
 * Strategia:
 *   - Anagrafiche: UPSERT (INSERT ... ON CONFLICT DO UPDATE)
 *   - EKET: DELETE righe in attesa (wmsst=' ') + INSERT
 *           Le righe con wmsst != ' ' non vengono mai toccate
 */
public class FcsRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FcsRepository.class);

    private final Connection conn;
    private final String     tenant;

    public FcsRepository(AppConfig config) throws SQLException {
        this.conn   = DriverManager.getConnection(config.dbUrl, config.dbUsername, config.dbPassword);
        this.tenant = config.dbTenant;
        this.conn.setAutoCommit(false);
        log.info("Connessione PostgreSQL stabilita: {}", config.dbUrl);
    }

    // -------------------------------------------------------------------------
    // Prodotti → tabfcsmara
    // -------------------------------------------------------------------------

    public int upsertProducts(List<Product> products) throws SQLException {
        String sql = """
                INSERT INTO public.tabfcsmara
                    (tenant, matnr, maktx, mtart, matkl, meins, bstme,
                     brgew, ntgew, gewei,
                     datum, uzeit, uname, updfl)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                ON CONFLICT (tenant, matnr) DO UPDATE SET
                    maktx  = EXCLUDED.maktx,
                    mtart  = EXCLUDED.mtart,
                    matkl  = EXCLUDED.matkl,
                    meins  = EXCLUDED.meins,
                    bstme  = EXCLUDED.bstme,
                    brgew  = EXCLUDED.brgew,
                    ntgew  = EXCLUDED.ntgew,
                    gewei  = EXCLUDED.gewei,
                    datum  = EXCLUDED.datum,
                    uzeit  = EXCLUDED.uzeit,
                    uname  = EXCLUDED.uname,
                    updfl  = true
                """;

        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Product p : products) {
                ps.setString(1,  tenant);
                ps.setString(2,  p.matnr());
                ps.setString(3,  p.maktx());
                ps.setString(4,  p.mtart());
                ps.setString(5,  p.matkl());
                ps.setString(6,  p.meins());
                ps.setString(7,  p.bstme());
                ps.setObject(8,  p.brgew());
                ps.setObject(9,  p.ntgew());
                ps.setString(10, p.gewei());
                ps.setDate  (11, p.datum() != null ? Date.valueOf(p.datum()) : null);
                ps.setTime  (12, p.uzeit() != null ? Time.valueOf(p.uzeit()) : null);
                ps.setString(13, p.uname());
                ps.addBatch();
                count++;
                if (count % 100 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
        conn.commit();
        log.info("Prodotti upsertati: {}", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Fornitori → tabfcslfa1
    // -------------------------------------------------------------------------

    public int upsertSuppliers(List<Supplier> suppliers) throws SQLException {
        String sql = """
                INSERT INTO public.tabfcslfa1
                    (tenant, lifnr, name1, name2, stcd1, stcd2, stceg, datum, uzeit, uname, updfl)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                ON CONFLICT (tenant, lifnr) DO UPDATE SET
                    name1  = EXCLUDED.name1,
                    name2  = EXCLUDED.name2,
                    stcd1  = EXCLUDED.stcd1,
                    stcd2  = EXCLUDED.stcd2,
                    stceg  = EXCLUDED.stceg,
                    datum  = EXCLUDED.datum,
                    uzeit  = EXCLUDED.uzeit,
                    uname  = EXCLUDED.uname,
                    updfl  = true
                """;

        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Supplier s : suppliers) {
                ps.setString(1, tenant);
                ps.setString(2, s.lifnr());
                ps.setString(3, s.name1());
                ps.setString(4, s.name2());
                ps.setString(5, s.stcd1());
                ps.setString(6, s.stcd2());
                ps.setString(7, s.stceg());
                ps.setDate  (8, Date.valueOf(s.datum()));
                ps.setTime  (9, Time.valueOf(s.uzeit()));
                ps.setString(10, s.uname());
                ps.addBatch();
                count++;
                if (count % 100 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
        conn.commit();
        log.info("Fornitori upsertati: {}", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Clienti → tabfcskna1
    // -------------------------------------------------------------------------

    public int upsertCustomers(List<Customer> customers) throws SQLException {
        String sql = """
                INSERT INTO public.tabfcskna1
                    (tenant, kunnr, name1, name2, stcd1, stcd2, stceg, datum, uzeit, uname, updfl)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                ON CONFLICT (tenant, kunnr) DO UPDATE SET
                    name1  = EXCLUDED.name1,
                    name2  = EXCLUDED.name2,
                    stcd1  = EXCLUDED.stcd1,
                    stcd2  = EXCLUDED.stcd2,
                    stceg  = EXCLUDED.stceg,
                    datum  = EXCLUDED.datum,
                    uzeit  = EXCLUDED.uzeit,
                    uname  = EXCLUDED.uname,
                    updfl  = true
                """;

        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Customer c : customers) {
                ps.setString(1, tenant);
                ps.setString(2, c.kunnr());
                ps.setString(3, c.name1());
                ps.setString(4, c.name2());
                ps.setString(5, c.stcd1());
                ps.setString(6, c.stcd2());
                ps.setString(7, c.stceg());
                ps.setDate  (8, Date.valueOf(c.datum()));
                ps.setTime  (9, Time.valueOf(c.uzeit()));
                ps.setString(10, c.uname());
                ps.addBatch();
                count++;
                if (count % 100 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
        conn.commit();
        log.info("Clienti upsertati: {}", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // EKET → tabfcseket
    // -------------------------------------------------------------------------

    /**
     * Sincronizza le righe EKET per una lista di OdA.
     *
     * Per ogni ebeln presente nelle righe estratte:
     *   1. DELETE righe con wmsst = ' ' (in attesa) → possono essere sovrascritte
     *   2. INSERT le nuove righe estratte da S/4HC
     *
     * Le righe con wmsst != ' ' (in scarico, completate, errore)
     * NON vengono mai toccate — garantisce integrità durante gli scarichi.
     *
     * @param lines righe estratte da S/4HC
     * @return numero di righe inserite
     */
    public int syncEketLines(List<EketLine> lines) throws SQLException {
        if (lines.isEmpty()) {
            log.info("Nessuna riga EKET da sincronizzare.");
            return 0;
        }

        // Raccogli gli ebeln distinti presenti nelle righe estratte
        Set<String> ebelns = lines.stream()
                .map(EketLine::ebeln)
                .collect(Collectors.toSet());

        log.info("Sincronizzazione EKET: {} OdA distinti, {} righe totali",
                ebelns.size(), lines.size());

        // 1. DELETE righe in attesa per gli OdA estratti
        int deleted = deleteEketInAttesa(ebelns);
        log.info("Righe EKET in attesa cancellate: {}", deleted);

        // 2. INSERT righe nuove
        int inserted = insertEketLines(lines);
        log.info("Righe EKET inserite: {}", inserted);

        conn.commit();
        return inserted;
    }

    /**
     * Sincronizzazione puntuale per un singolo OdA.
     * Usato quando S/4HC notifica il salvataggio di un OdA specifico.
     */
    public int syncEketLinesForOrder(String ebeln, List<EketLine> lines) throws SQLException {
        log.info("Sincronizzazione EKET puntuale per OdA: {}", ebeln);

        // DELETE righe in attesa per questo OdA
        int deleted = deleteEketInAttesaByEbeln(ebeln);
        log.info("Righe in attesa cancellate per OdA {}: {}", ebeln, deleted);

        // INSERT righe nuove
        int inserted = lines.isEmpty() ? 0 : insertEketLines(lines);
        log.info("Righe inserite per OdA {}: {}", ebeln, inserted);

        conn.commit();
        return inserted;
    }

    // -------------------------------------------------------------------------
    // Metodi privati EKET
    // -------------------------------------------------------------------------

    private int deleteEketInAttesa(Set<String> ebelns) throws SQLException {
        // Costruisce WHERE ebeln IN (?,?,?) per gli OdA estratti
        String placeholders = ebelns.stream()
                .map(e -> "?")
                .collect(Collectors.joining(","));

        String sql = "DELETE FROM public.tabfcseket " +
                     "WHERE tenant = ? AND wmsst = ' ' AND ebeln IN (" + placeholders + ")";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenant);
            int i = 2;
            for (String ebeln : ebelns) {
                ps.setString(i++, ebeln);
            }
            return ps.executeUpdate();
        }
    }

    private int deleteEketInAttesaByEbeln(String ebeln) throws SQLException {
        String sql = "DELETE FROM public.tabfcseket " +
                     "WHERE tenant = ? AND ebeln = ? AND wmsst = ' '";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenant);
            ps.setString(2, ebeln);
            return ps.executeUpdate();
        }
    }

    private int insertEketLines(List<EketLine> lines) throws SQLException {
        String sql = """
                INSERT INTO public.tabfcseket
                    (tenant, ebeln, ebelp, etenr,
                     kappl, id_eket, xchpf, eindt,
                     lifnr, name1, mtart, matnr, maktx, werks, lgort,
                     menge, wemng, menge_open, meins, bstme,
                     mengexbstme, qtaxtag, bstmexpallet, qtaxbag, tag_filler,
                     nrtag, nrbag, brgew_row, ntgew_row, gewei,
                     datum, uzeit, ernam, wmsst)
                VALUES
                    (?, ?, ?, ?,
                     ?, ?, ?, ?,
                     ?, ?, ?, ?, ?, ?, ?,
                     ?, ?, ?, ?, ?,
                     ?, ?, ?, ?, ?,
                     ?, ?, ?, ?, ?,
                     ?, ?, ?, ' ')
                """;

        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (EketLine e : lines) {
                ps.setString(1,  tenant);
                ps.setString(2,  e.ebeln());
                ps.setString(3,  e.ebelp());
                ps.setString(4,  e.etenr());
                // Gruppo 1
                ps.setString(5,  e.kappl());
                ps.setString(6,  e.idEket());
                ps.setObject(7,  e.xchpf());
                ps.setDate  (8,  e.eindt() != null ? Date.valueOf(e.eindt()) : null);
                ps.setString(9,  e.lifnr());
                ps.setString(10, e.name1());
                ps.setString(11, e.mtart());
                ps.setString(12, e.matnr());
                ps.setString(13, e.maktx());
                ps.setString(14, e.werks());
                ps.setString(15, e.lgort());
                ps.setObject(16, e.menge());
                ps.setObject(17, e.wemng());
                ps.setObject(18, e.mengeOpen());
                ps.setString(19, e.meins());
                ps.setString(20, e.bstme());
                // Gruppo 2
                ps.setObject(21, e.mengexbstme());
                ps.setObject(22, e.qtaxtag());
                ps.setObject(23, e.bstmexpallet());
                ps.setObject(24, e.qtaxbag());
                ps.setObject(25, e.tagFiller());
                ps.setObject(26, e.nrtag());
                ps.setObject(27, e.nrbag());
                ps.setObject(28, e.brgewRow());
                ps.setObject(29, e.ntgewRow());
                ps.setObject(30, e.gewei());
                // Gestione
                ps.setDate  (31, e.datum() != null ? Date.valueOf(e.datum()) : null);
                ps.setTime  (32, e.uzeit() != null ? Time.valueOf(e.uzeit()) : null);
                ps.setString(33, e.ernam());
                ps.addBatch();
                count++;
                if (count % 100 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Aggiornamenti post-GR
    // -------------------------------------------------------------------------

    /**
     * Aggiorna wemng e menge_open dopo la registrazione dell'entrata merce su S/4HC.
     */
    public void updateWemng(String ebeln, String ebelp, String etenr,
                            double wemng, double mengeOpen) throws SQLException {
        String sql = """
                UPDATE public.tabfcseket
                SET wemng = ?, menge_open = ?, datum = CURRENT_DATE, uzeit = CURRENT_TIME
                WHERE tenant = ? AND ebeln = ? AND ebelp = ? AND etenr = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, wemng);
            ps.setDouble(2, mengeOpen);
            ps.setString(3, tenant);
            ps.setString(4, ebeln);
            ps.setString(5, ebelp);
            ps.setString(6, etenr);
            ps.executeUpdate();
        }
        conn.commit();
    }

    /**
     * Aggiorna lo stato WMS di una riga.
     * wmsst: ' '=attesa, 'I'=in scarico, 'C'=completata, 'E'=errore
     */
    public void updateWmsStatus(String ebeln, String ebelp, String etenr,
                                String wmsst) throws SQLException {
        String sql = """
                UPDATE public.tabfcseket
                SET wmsst = ?
                WHERE tenant = ? AND ebeln = ? AND ebelp = ? AND etenr = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, wmsst);
            ps.setString(2, tenant);
            ps.setString(3, ebeln);
            ps.setString(4, ebelp);
            ps.setString(5, etenr);
            ps.executeUpdate();
        }
        conn.commit();
    }
    
    // -------------------------------------------------------------------------
    // UMFOR → tabumfor  (parametrizzazioni Materiale/Fornitore)
    // -------------------------------------------------------------------------

    /**
     * Carica da tabumfor i fattori di conversione validi alla data odierna
     * per i soli matnr presenti nelle righe EKET da sincronizzare.
     *
     * Strategia:
     *   - Query con WHERE matnr IN (...) → carica solo ciò che serve
     *   - Per ogni (matnr, lifnr, bstme) tiene il record con datab più recente
     *     tra quelli con datab <= CURRENT_DATE  (record attivo)
     *   - Restituisce una Map<"matnr|lifnr|bstme", Umfor> pronta per il lookup O(1)
     *
     * @param matnrs insieme dei codici materiale di interesse
     * @return mappa fattori di conversione indicizzata per chiave composta
     */
    public Map<String, com.eone.fcs.model.Umfor> loadUmfor(
            java.util.Set<String> matnrs) throws SQLException {

        if (matnrs.isEmpty()) return java.util.Map.of();

        // Costruisce IN (?, ?, ...)
        String placeholders = matnrs.stream()
                .map(m -> "?")
                .collect(java.util.stream.Collectors.joining(","));

        // DISTINCT ON (matnr, lifnr) + ORDER BY datab DESC
        // → per ogni coppia materiale/fornitore prende il record attivo più recente.
        // bstme NON è parte della chiave di ricerca: per ogni matnr+lifnr esiste
        // un solo imballo attivo, parametrizzato in tabumfor.
        String sql = """
                SELECT DISTINCT ON (matnr, lifnr)
                       matnr, lifnr, bstme, datab, meins,
                       mengexbstme, bstmexpallet
                  FROM public.tabumfor
                 WHERE matnr IN (""" + placeholders + """
                )
                   AND datab <= CURRENT_DATE
                 ORDER BY matnr, lifnr, datab DESC
                """;

        Map<String, com.eone.fcs.model.Umfor> result = new java.util.HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String matnr : matnrs) {
                ps.setString(i++, matnr);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    com.eone.fcs.model.Umfor u = new com.eone.fcs.model.Umfor(
                            rs.getString("matnr"),
                            rs.getString("lifnr"),
                            rs.getString("bstme"),
                            rs.getDate("datab") != null
                                    ? rs.getDate("datab").toLocalDate() : null,
                            rs.getString("meins"),
                            rs.getObject("mengexbstme")  != null ? rs.getDouble("mengexbstme")  : null,
                            rs.getObject("bstmexpallet") != null ? rs.getInt("bstmexpallet")    : null
                    );
                    result.put(u.key(), u);
                }
            }
        }

        log.info("Fattori UMFOR caricati: {} record per {} matnr distinti",
                result.size(), matnrs.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Pesi materiale → tabfcsmara
    // -------------------------------------------------------------------------

    /**
     * Carica i pesi unitari (brgew, ntgew, gewei) da tabfcsmara
     * per i soli matnr presenti nelle righe EKET da sincronizzare.
     *
     * @param matnrs insieme dei codici materiale di interesse
     * @return mappa matnr → double[]{brgew, ntgew} + gewei, indicizzata per matnr
     */
    public Map<String, com.eone.fcs.model.PesoMateriale> loadPesi(
            java.util.Set<String> matnrs) throws SQLException {

        if (matnrs.isEmpty()) return java.util.Map.of();

        String placeholders = matnrs.stream()
                .map(m -> "?")
                .collect(java.util.stream.Collectors.joining(","));

        String sql = """
                SELECT matnr, brgew, ntgew, gewei
                  FROM public.tabfcsmara
                 WHERE matnr IN (""" + placeholders + """
                )
                """;

        Map<String, com.eone.fcs.model.PesoMateriale> result = new java.util.HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String matnr : matnrs) {
                ps.setString(i++, matnr);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String matnr = rs.getString("matnr");
                    Double brgew = rs.getObject("brgew") != null ? rs.getDouble("brgew") : null;
                    Double ntgew = rs.getObject("ntgew") != null ? rs.getDouble("ntgew") : null;
                    String gewei = rs.getString("gewei");
                    result.put(matnr, new com.eone.fcs.model.PesoMateriale(matnr, brgew, ntgew, gewei));
                }
            }
        }

        log.info("Pesi materiale caricati: {} record per {} matnr distinti",
                result.size(), matnrs.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Aggiornamento Gruppo 2 su righe già inserite (uso futuro / ricalcolo)
    // -------------------------------------------------------------------------

    /**
     * Aggiorna i soli campi del Gruppo 2 su una riga EKET già persistita.
     * Utile se si vuole ricalcolare i valori senza rifare il ciclo DELETE+INSERT.
     * Per ora non usato nel flusso principale (i valori vengono inseriti direttamente).
     */
    public void updateGruppo2(EketLine e) throws SQLException {
        String sql = """
                UPDATE public.tabfcseket
                   SET mengexbstme  = ?,
                       qtaxtag      = ?,
                       bstmexpallet = ?,
                       qtaxbag      = ?,
                       tag_filler   = ?,
                       nrtag        = ?,
                       nrbag        = ?,
                       brgew_row    = ?,
                       ntgew_row    = ?,
                       gewei        = ?,
                       datum        = CURRENT_DATE,
                       uzeit        = CURRENT_TIME
                 WHERE tenant = ? AND ebeln = ? AND ebelp = ? AND etenr = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1,  e.mengexbstme());
            ps.setObject(2,  e.qtaxtag());
            ps.setObject(3,  e.bstmexpallet());
            ps.setObject(4,  e.qtaxbag());
            ps.setObject(5,  e.tagFiller());
            ps.setObject(6,  e.nrtag());
            ps.setObject(7,  e.nrbag());
            ps.setObject(8,  e.brgewRow());
            ps.setObject(9,  e.ntgewRow());
            ps.setObject(10, e.gewei());
            ps.setString(11, tenant);
            ps.setString(12, e.ebeln());
            ps.setString(13, e.ebelp());
            ps.setString(14, e.etenr());
            ps.executeUpdate();
        }
        conn.commit();
    }
    
    // -------------------------------------------------------------------------
    // UMCLI → tabumcli  (parametrizzazioni Materiale/Cliente per resi)
    // -------------------------------------------------------------------------

    /**
     * Carica da tabumcli i fattori di conversione validi alla data odierna
     * per i soli matnr presenti nelle righe di reso da sincronizzare.
     *
     * Speculare a loadUmfor ma usa kunnr (cliente) invece di lifnr (fornitore).
     * Usato da EketEnricher per le righe con kappl='V'.
     *
     * @param matnrs insieme dei codici materiale di interesse
     * @return mappa fattori di conversione indicizzata per "matnr|kunnr"
     */
    public Map<String, com.eone.fcs.model.Umcli> loadUmcli(
            java.util.Set<String> matnrs) throws SQLException {

        if (matnrs.isEmpty()) return java.util.Map.of();

        String placeholders = matnrs.stream()
                .map(m -> "?")
                .collect(java.util.stream.Collectors.joining(","));

        String sql = """
                SELECT DISTINCT ON (matnr, kunnr)
                       matnr, kunnr, bstme, datab, meins,
                       mengexbstme, bstmexpallet
                  FROM public.tabumcli
                 WHERE matnr IN (""" + placeholders + """
                )
                   AND datab <= CURRENT_DATE
                 ORDER BY matnr, kunnr, datab DESC
                """;

        Map<String, com.eone.fcs.model.Umcli> result = new java.util.HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String matnr : matnrs) {
                ps.setString(i++, matnr);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    com.eone.fcs.model.Umcli u = new com.eone.fcs.model.Umcli(
                            rs.getString("matnr"),
                            rs.getString("kunnr"),
                            rs.getString("bstme"),
                            rs.getDate("datab") != null
                                    ? rs.getDate("datab").toLocalDate() : null,
                            rs.getString("meins"),
                            rs.getObject("mengexbstme")  != null ? rs.getDouble("mengexbstme")  : null,
                            rs.getObject("bstmexpallet") != null ? rs.getInt("bstmexpallet")    : null
                    );
                    result.put(u.key(), u);
                }
            }
        }

        log.info("Fattori UMCLI caricati: {} record per {} matnr distinti",
                result.size(), matnrs.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Nomi fornitori → tabfcslfa1
    // -------------------------------------------------------------------------

    /**
     * Carica il campo name1 da tabfcslfa1 per i lifnr forniti.
     * Usato da EketEnricher per popolare il campo name1 nelle righe OdA.
     *
     * @param lifnrs insieme dei codici fornitore
     * @return mappa lifnr → name1
     */
    public Map<String, String> loadNomiFornitori(
            java.util.Set<String> lifnrs) throws SQLException {

        if (lifnrs.isEmpty()) return java.util.Map.of();

        String placeholders = lifnrs.stream()
                .map(l -> "?")
                .collect(java.util.stream.Collectors.joining(","));

        String sql = """
                SELECT lifnr, name1
                  FROM public.tabfcslfa1
                 WHERE tenant = ?
                   AND lifnr IN (""" + placeholders + """
                )
                """;

        Map<String, String> result = new java.util.HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenant);
            int i = 2;
            for (String lifnr : lifnrs) {
                ps.setString(i++, lifnr);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("lifnr"), rs.getString("name1"));
                }
            }
        }

        log.info("Nomi fornitori caricati: {} record", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Nomi clienti → tabfcskna1
    // -------------------------------------------------------------------------

    /**
     * Carica il campo name1 da tabfcskna1 per i kunnr forniti.
     * Usato da EketEnricher per popolare il campo name1 nelle righe di reso.
     * I kunnr sono in lifnr delle EketLine di tipo 'V' (cliente → lifnr per coerenza modello).
     *
     * @param kunnrs insieme dei codici cliente
     * @return mappa kunnr → name1
     */
    public Map<String, String> loadNomiClienti(
            java.util.Set<String> kunnrs) throws SQLException {

        if (kunnrs.isEmpty()) return java.util.Map.of();

        String placeholders = kunnrs.stream()
                .map(k -> "?")
                .collect(java.util.stream.Collectors.joining(","));

        String sql = """
                SELECT kunnr, name1
                  FROM public.tabfcskna1
                 WHERE tenant = ?
                   AND kunnr IN (""" + placeholders + """
                )
                """;

        Map<String, String> result = new java.util.HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenant);
            int i = 2;
            for (String kunnr : kunnrs) {
                ps.setString(i++, kunnr);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("kunnr"), rs.getString("name1"));
                }
            }
        }

        log.info("Nomi clienti caricati: {} record", result.size());
        return result;
    }
    
    // -------------------------------------------------------------------------
    // AutoCloseable
    // -------------------------------------------------------------------------
    
    @Override
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                log.info("Connessione PostgreSQL chiusa.");
            }
        } catch (SQLException e) {
            log.warn("Errore chiusura connessione: {}", e.getMessage());
        }
    }
}
