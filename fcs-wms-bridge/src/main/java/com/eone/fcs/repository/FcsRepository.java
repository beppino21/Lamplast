package com.eone.fcs.repository;

import com.eone.fcs.config.AppConfig;
import com.eone.fcs.model.Customer;
import com.eone.fcs.model.EketLine;
import com.eone.fcs.model.Product;
import com.eone.fcs.model.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
                    (tenant, matnr, maktx, mtart, matkl, meins, bstme, datum, uzeit, uname, updfl)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                ON CONFLICT (tenant, matnr) DO UPDATE SET
                    maktx  = EXCLUDED.maktx,
                    mtart  = EXCLUDED.mtart,
                    matkl  = EXCLUDED.matkl,
                    meins  = EXCLUDED.meins,
                    bstme  = EXCLUDED.bstme,
                    datum  = EXCLUDED.datum,
                    uzeit  = EXCLUDED.uzeit,
                    uname  = EXCLUDED.uname,
                    updfl  = true
                """;

        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Product p : products) {
                ps.setString(1, tenant);
                ps.setString(2, p.matnr());
                ps.setString(3, p.maktx());
                ps.setString(4, p.mtart());
                ps.setString(5, p.matkl());
                ps.setString(6, p.meins());
                ps.setString(7, p.bstme());
                ps.setDate  (8, p.datum() != null ? Date.valueOf(p.datum()) : null);
                ps.setTime  (9, p.uzeit() != null ? Time.valueOf(p.uzeit()) : null);
                ps.setString(10, p.uname());
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
                     datum, uzeit, ernam, wmsst)
                VALUES
                    (?, ?, ?, ?,
                     ?, ?, ?, ?,
                     ?, ?, ?, ?, ?, ?, ?,
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
                ps.setDate  (21, e.datum() != null ? Date.valueOf(e.datum()) : null);
                ps.setTime  (22, e.uzeit() != null ? Time.valueOf(e.uzeit()) : null);
                ps.setString(23, e.ernam());
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
