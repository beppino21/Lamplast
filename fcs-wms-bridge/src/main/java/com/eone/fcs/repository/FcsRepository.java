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
 *   - EKET massiva:  DELETE tutte le righe con wmsst IN ('0','3') del tenant
 *                    FILTRATE per kappl (es. 'ME' o 'V') + INSERT
 *   - EKET puntuale: DELETE righe con wmsst IN ('0','3') per singolo ebeln
 *                    FILTRATE per kappl + INSERT
 *   Le righe con wmsst IN ('1','2','E') non vengono mai toccate.
 *
 * Valori wmsst:
 *   '0' = in attesa (stato iniziale)
 *   '1' = scarico iniziato
 *   '2' = scarico in corso
 *   '3' = scarico completato
 *   'E' = errore
 *
 * Valori kappl:
 *   'ME' = schedulazioni da OdA (EKET)
 *   'V'  = schedulazioni da OdV reso (VBEP)
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
    // EKET / VBEP → tabfcseket
    // -------------------------------------------------------------------------

    /**
     * Sincronizzazione MASSIVA: usata dall'estrazione giornaliera completa.
     *
     * Strategia:
     *   1. DELETE tutte le righe con wmsst IN ('0','3') del tenant
     *      LIMITATE al kappl indicato (es. 'ME' per EKET, 'V' per VBEP)
     *      → le righe dell'altro applicativo non vengono mai toccate.
     *   2. INSERT le nuove righe estratte da S/4H con wmsst = '0'
     *      ON CONFLICT DO NOTHING: le righe con wmsst IN ('1','2','E')
     *      (in lavorazione / errore) non vengono mai sovrascritte.
     *
     * @param lines righe estratte da S/4H
     * @param kappl applicativo di provenienza ('ME' = OdA EKET, 'V' = OdV VBEP)
     * @return numero di righe effettivamente inserite
     */
    public int syncEketLines(List<EketLine> lines, String kappl) throws SQLException {
        log.info("Sincronizzazione massiva (kappl={}): {} righe da inserire", kappl, lines.size());

        // 1. DELETE massiva filtrata per kappl — avviene SEMPRE,
        //    anche se la lista e' vuota (pulizia righe non piu' presenti in S/4H
        //    o escluse dal filtro tabfcst001)
        int deleted = deleteEketMassiva(kappl);
        log.info("Righe cancellate (wmsst in '0','3', kappl={}): {}", kappl, deleted);

        // 2. INSERT righe nuove (ON CONFLICT DO NOTHING per le righe in lavorazione/errore)
        int inserted = lines.isEmpty() ? 0 : insertEketLines(lines);
        if (lines.isEmpty()) {
            log.info("Nessuna riga da inserire (kappl={}) — tabella ripulita.", kappl);
        } else {
            log.info("Righe inserite: {} su {} (kappl={})", inserted, lines.size(), kappl);
        }

        conn.commit();
        return inserted;
    }

    /**
     * Sincronizzazione PUNTUALE per un singolo documento (OdA o OdV reso).
     * Usata dopo ogni scarico o quando S/4H notifica il salvataggio di un documento specifico.
     *
     * Strategia:
     *   1. DELETE righe con wmsst IN ('0','3') per il singolo ebeln
     *      LIMITATE al kappl indicato → le righe dell'altro applicativo non vengono toccate.
     *   2. INSERT le nuove righe estratte da S/4H con wmsst = '0'
     *      ON CONFLICT DO NOTHING: le righe con wmsst IN ('1','2','E') non vengono toccate.
     *
     * @param ebeln numero documento (OdA o OdV)
     * @param lines righe estratte da S/4H per questo documento
     * @param kappl applicativo di provenienza ('ME' = OdA EKET, 'V' = OdV VBEP)
     * @return numero di righe effettivamente inserite
     */
    public int syncEketLinesForOrder(String ebeln, List<EketLine> lines,
                                     String kappl) throws SQLException {
        log.info("Sincronizzazione puntuale (kappl={}) per documento: {}", kappl, ebeln);

        // 1. DELETE righe in attesa o completate per questo documento, limitata a kappl
        int deleted = deleteEketPuntuale(ebeln, kappl);
        log.info("Righe cancellate (wmsst in '0','3', kappl={}) per {}: {}", kappl, ebeln, deleted);

        // 2. INSERT righe nuove (ON CONFLICT DO NOTHING per le righe in lavorazione/errore)
        int inserted = lines.isEmpty() ? 0 : insertEketLines(lines);
        log.info("Righe inserite per {} (kappl={}): {} su {} estratte da S/4H",
                 ebeln, kappl, inserted, lines.size());

        conn.commit();
        return inserted;
    }

    // -------------------------------------------------------------------------
    // Metodi privati EKET
    // -------------------------------------------------------------------------

    /**
     * DELETE massiva: rimuove le righe con wmsst IN ('0','3') del tenant
     * limitate al kappl specificato.
     */
    private int deleteEketMassiva(String kappl) throws SQLException {
        String sql = "DELETE FROM public.tabfcseket " +
                     "WHERE tenant = ? AND kappl = ? AND wmsst IN ('0', '3')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenant);
            ps.setString(2, kappl);
            return ps.executeUpdate();
        }
    }

    /**
     * DELETE puntuale: rimuove le righe con wmsst IN ('0','3') per un singolo documento
     * limitata al kappl specificato.
     */
    private int deleteEketPuntuale(String ebeln, String kappl) throws SQLException {
        String sql = "DELETE FROM public.tabfcseket " +
                     "WHERE tenant = ? AND ebeln = ? AND kappl = ? AND wmsst IN ('0', '3')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenant);
            ps.setString(2, ebeln);
            ps.setString(3, kappl);
            return ps.executeUpdate();
        }
    }

    private int insertEketLines(List<EketLine> lines) throws SQLException {
        /*
         * ON CONFLICT (tenant, ebeln, ebelp, etenr, kappl) DO NOTHING
         *
         * Se la riga esiste già con wmsst IN ('1','2','E') (in lavorazione / errore)
         * il record non viene toccato e l'inserimento viene silenziosamente ignorato.
         * Le righe effettivamente inserite sono solo quelle non in lavorazione/errore.
         */
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
                     ?, ?, ?, '0')
                ON CONFLICT (tenant, ebeln, ebelp, etenr, kappl) DO NOTHING
                """;

        int attempted = 0;
        int[] batchResults;
        int inserted = 0;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (EketLine e : lines) {
                ps.setString(1,  tenant);
                ps.setString(2,  e.ebeln());
                ps.setString(3,  e.ebelp());
                ps.setString(4,  e.etenr());
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
                ps.setDate  (31, e.datum() != null ? Date.valueOf(e.datum()) : null);
                ps.setTime  (32, e.uzeit() != null ? Time.valueOf(e.uzeit()) : null);
                ps.setString(33, e.ernam());
                ps.addBatch();
                attempted++;
                if (attempted % 100 == 0) {
                    batchResults = ps.executeBatch();
                    for (int r : batchResults) inserted += (r > 0 ? r : 0);
                }
            }
            batchResults = ps.executeBatch();
            for (int r : batchResults) inserted += (r > 0 ? r : 0);
        }

        int skipped = attempted - inserted;
        if (skipped > 0) {
            log.info("Righe in lavorazione/errore (wmsst in '1','2','E') non sovrascritte: {}", skipped);
        }
        return inserted;
    }

    // -------------------------------------------------------------------------
    // Aggiornamenti post-GR
    // -------------------------------------------------------------------------

    /**
     * Aggiorna wemng e menge_open dopo la registrazione dell'entrata merce su S/4H.
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
     *
     * Valori wmsst:
     *   '0' = in attesa (stato iniziale)
     *   '1' = scarico iniziato
     *   '2' = scarico in corso
     *   '3' = scarico completato
     *   'E' = errore
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

    public Map<String, com.eone.fcs.model.Umfor> loadUmfor(
            java.util.Set<String> matnrs) throws SQLException {

        if (matnrs.isEmpty()) return java.util.Map.of();

        String placeholders = matnrs.stream()
                .map(m -> "?")
                .collect(java.util.stream.Collectors.joining(","));

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
            for (String matnr : matnrs) ps.setString(i++, matnr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    com.eone.fcs.model.Umfor u = new com.eone.fcs.model.Umfor(
                            rs.getString("matnr"),
                            rs.getString("lifnr"),
                            rs.getString("bstme"),
                            rs.getDate("datab") != null ? rs.getDate("datab").toLocalDate() : null,
                            rs.getString("meins"),
                            rs.getObject("mengexbstme")  != null ? rs.getDouble("mengexbstme")  : null,
                            rs.getObject("bstmexpallet") != null ? rs.getInt("bstmexpallet")    : null
                    );
                    result.put(u.key(), u);
                }
            }
        }

        log.info("Fattori UMFOR caricati: {} record per {} matnr distinti", result.size(), matnrs.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Pesi materiale → tabfcsmara
    // -------------------------------------------------------------------------

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
            for (String matnr : matnrs) ps.setString(i++, matnr);
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

        log.info("Pesi materiale caricati: {} record per {} matnr distinti", result.size(), matnrs.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Aggiornamento Gruppo 2 su righe già inserite (uso futuro / ricalcolo)
    // -------------------------------------------------------------------------

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
            for (String matnr : matnrs) ps.setString(i++, matnr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    com.eone.fcs.model.Umcli u = new com.eone.fcs.model.Umcli(
                            rs.getString("matnr"),
                            rs.getString("kunnr"),
                            rs.getString("bstme"),
                            rs.getDate("datab") != null ? rs.getDate("datab").toLocalDate() : null,
                            rs.getString("meins"),
                            rs.getObject("mengexbstme")  != null ? rs.getDouble("mengexbstme")  : null,
                            rs.getObject("bstmexpallet") != null ? rs.getInt("bstmexpallet")    : null
                    );
                    result.put(u.key(), u);
                }
            }
        }

        log.info("Fattori UMCLI caricati: {} record per {} matnr distinti", result.size(), matnrs.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Nomi fornitori → tabfcslfa1
    // -------------------------------------------------------------------------

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
            for (String lifnr : lifnrs) ps.setString(i++, lifnr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString("lifnr"), rs.getString("name1"));
            }
        }

        log.info("Nomi fornitori caricati: {} record", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Nomi clienti → tabfcskna1
    // -------------------------------------------------------------------------

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
            for (String kunnr : kunnrs) ps.setString(i++, kunnr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString("kunnr"), rs.getString("name1"));
            }
        }

        log.info("Nomi clienti caricati: {} record", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // FCST001 → tabfcst001  (configurazione export per tipo materiale / plant)
    // -------------------------------------------------------------------------

    /**
     * Carica l'intera tabella tabfcst001 e restituisce una Map indicizzata
     * per chiave "mtart|werks".
     *
     * Solo le righe con exp2fcs = true abilitano l'export verso tabfcseket.
     * Se la tabella è vuota (nessuna configurazione) il metodo restituisce
     * una Map vuota; il chiamante decide se applicare un filtro open o chiuso.
     *
     * @return Map<"mtart|werks", Fcst001>
     */
    public Map<String, com.eone.fcs.model.Fcst001> loadFcst001() throws SQLException {

        String sql = """
                SELECT mtart, werks, exp2fcs
                  FROM public.tabfcst001
                 WHERE tenant = ?
                """;

        Map<String, com.eone.fcs.model.Fcst001> result = new java.util.HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    com.eone.fcs.model.Fcst001 row = new com.eone.fcs.model.Fcst001(
                            rs.getString("mtart"),
                            rs.getString("werks"),
                            rs.getObject("exp2fcs") != null ? rs.getBoolean("exp2fcs") : null
                    );
                    result.put(row.key(), row);
                }
            }
        }

        log.info("Configurazione FCST001 caricata: {} righe (tenant={})", result.size(), tenant);
        return result;
    }

 // =============================================================================
 // PATCH da aggiungere a FcsRepository.java
 // =============================================================================
 // Aggiungere questo metodo alla classe FcsRepository, subito prima del
 // blocco "// AutoCloseable".
 //
 // Serve a SyncRepository per condividere la stessa Connection JDBC
 // senza aprirne una seconda (evita due transazioni parallele sullo stesso DB).
 // =============================================================================

     // -------------------------------------------------------------------------
     // Accesso alla connessione (per SyncRepository)
     // -------------------------------------------------------------------------

     /**
      * Restituisce la Connection JDBC attiva.
      *
      * Usato da {@link com.eone.fcs.repository.SyncRepository} per condividere
      * la stessa connessione e lo stesso tenant senza aprirne una seconda.
      *
      * @return la Connection corrente (mai null finché il repository è aperto)
      */
     public java.sql.Connection getConnection() {
         return conn;
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
