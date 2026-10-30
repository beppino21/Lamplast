package lamplast.mbxa.logic.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lamplast.mbxa.logic.model.Ddt;
import lamplast.mbxa.logic.model.DdtPosizione;
import lamplast.mbxa.logic.model.DdtStorico;

/**
 * Accesso alle tabelle ddt_testata, ddt_posizione, ddt_versione, ddt_evento, ddt_numeratore.
 * Tutti i metodi ricevono la connessione: la transazione la governa il servizio.
 */
public class DdtDao implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    // Colonne aggiornabili della testata (ordine usato da insert e update)
    private static final String[] COLS = {
        "mat_doc_anno", "mat_doc", "data_registrazione", "data_documento", "utente_sap",
        "ddt_tipo_num", "ddt_anno", "ddt_progressivo", "ddt_numero", "ddt_data",
        "mitt_codice", "mitt_ragione_sociale", "mitt_indirizzo", "mitt_cap", "mitt_citta", "mitt_provincia", "mitt_nazione",
        "dest_codice", "dest_ragione_sociale", "dest_indirizzo", "dest_cap", "dest_citta", "dest_provincia", "dest_nazione",
        "dest_partita_iva",
        "causale", "porto", "aspetto_beni", "vettore_id", "vett_ragione_sociale", "vett_indirizzo", "vett_partita_iva",
        "vett_iscrizione_albo", "colli", "peso_lordo", "peso_netto", "unita_peso", "inizio_trasporto", "annotazioni",
        "stato", "versione_corrente", "storno_mat_doc_anno", "storno_mat_doc", "annullato_at", "motivo_annullamento",
        "sostituisce_id"
    };

    // ------------------------------------------------------------------------
    // testata
    // ------------------------------------------------------------------------

    /** DDT non annullato del documento materiale (al massimo uno), oppure null. */
    public Long findIdByMatDoc(Connection c, String anno, String matDoc) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM ddt_testata WHERE mat_doc_anno = ? AND mat_doc = ? AND stato <> 'ANNULLATO'")) {
            ps.setString(1, anno);
            ps.setString(2, matDoc);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Ultimo DDT annullato del documento materiale, oppure null. */
    public Long findUltimoAnnullato(Connection c, String anno, String matDoc) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM ddt_testata WHERE mat_doc_anno = ? AND mat_doc = ? AND stato = 'ANNULLATO'"
                + " ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, anno);
            ps.setString(2, matDoc);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** DDT con un certo numero (qualsiasi tipo numerazione), più recenti prima. */
    public List<Long> findIdsByNumero(Connection c, String numero) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM ddt_testata WHERE upper(ddt_numero) = upper(?) ORDER BY ddt_anno DESC, id DESC")) {
            ps.setString(1, numero.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    /** Chiavi "anno/documento" con un DDT non annullato, per gli anni indicati. */
    public Set<String> acquiredKeys(Connection c, Set<String> anni) throws SQLException {
        Set<String> keys = new HashSet<>();
        if (anni.isEmpty()) return keys;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT mat_doc_anno, mat_doc FROM ddt_testata WHERE mat_doc_anno::text = ANY (?::text[]) AND stato <> 'ANNULLATO'")) {
            ps.setArray(1, c.createArrayOf("text", anni.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) keys.add(rs.getString(1).trim() + "/" + rs.getString(2));
            }
        }
        return keys;
    }

    public Ddt load(Connection c, long id) throws SQLException {
        return load(c, id, false);
    }

    /** Carica e blocca la riga fino a fine transazione (emissione, salvataggio). */
    public Ddt loadForUpdate(Connection c, long id) throws SQLException {
        return load(c, id, true);
    }

    private Ddt load(Connection c, long id, boolean forUpdate) throws SQLException {
        Ddt d;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM ddt_testata WHERE id = ?" + (forUpdate ? " FOR UPDATE" : ""))) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                d = mapTestata(rs);
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM ddt_posizione WHERE testata_id = ? ORDER BY ordinamento, riga_sap")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) d.getPosizioni().add(mapPosizione(rs));
            }
        }
        return d;
    }

    public long insert(Connection c, Ddt d) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO ddt_testata (");
        StringBuilder val = new StringBuilder(") VALUES (");
        for (int i = 0; i < COLS.length; i++) {
            if (i > 0) { sql.append(", "); val.append(", "); }
            sql.append(COLS[i]);
            val.append("?");
        }
        sql.append(val).append(") RETURNING id");
        long id;
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bindTestata(ps, d);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                id = rs.getLong(1);
            }
        }
        d.setId(id);
        insertPosizioni(c, d);
        return id;
    }

    public void updateTestata(Connection c, Ddt d) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE ddt_testata SET ");
        for (int i = 0; i < COLS.length; i++) {
            if (i > 0) sql.append(", ");
            sql.append(COLS[i]).append(" = ?");
        }
        sql.append(" WHERE id = ?");
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int n = bindTestata(ps, d);
            ps.setLong(n, d.getId());
            ps.executeUpdate();
        }
    }

    /** Solo bozze: il trigger del database impedisce di cancellare DDT emessi o annullati. */
    public void deleteBozza(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM ddt_evento WHERE testata_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM ddt_testata WHERE id = ? AND stato = 'BOZZA'")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------------
    // numerazione
    // ------------------------------------------------------------------------

    /** Ultimo numero SAP (modo MANUALE) emesso nell'anno: solo DDT emessi (no bozze, no annullati). */
    public Long maxNumeroSap(Connection c, int anno, long escludiId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT max(ddt_numero::bigint) FROM ddt_testata"
                + " WHERE ddt_tipo_num = 'SAP' AND ddt_anno = ? AND stato = 'EMESSO' AND id <> ?")) {
            ps.setInt(1, anno);
            ps.setLong(2, escludiId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long v = rs.getLong(1);
                return rs.wasNull() ? null : v;
            }
        }
    }

    /**
     * Documento materiale che usa già quel numero, oppure null.
     * Numerazione SAP: i DDT annullati non occupano il numero (stessa regola dell'indice unico).
     */
    public String matDocConNumero(Connection c, String tipo, int anno, String numero, long escludiId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT mat_doc, mat_doc_anno FROM ddt_testata"
                + " WHERE ddt_tipo_num = ? AND ddt_anno = ? AND ddt_numero = ? AND id <> ?"
                + " AND (stato <> 'ANNULLATO' OR ddt_tipo_num <> 'SAP')")) {
            ps.setString(1, tipo);
            ps.setInt(2, anno);
            ps.setString(3, numero);
            ps.setLong(4, escludiId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) + "/" + rs.getString(2).trim() : null;
            }
        }
    }

    /**
     * Prossimo progressivo della serie interna. Il lock di riga dell'UPDATE serializza
     * le emissioni concorrenti; un rollback non lascia buchi (a differenza di una SEQUENCE).
     */
    public int nextProgressivo(Connection c, String tipo, int anno) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ddt_numeratore (tipo, anno) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, tipo);
            ps.setInt(2, anno);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE ddt_numeratore SET ultimo_progressivo = ultimo_progressivo + 1, updated_at = now()"
                + " WHERE tipo = ? AND anno = ? RETURNING ultimo_progressivo")) {
            ps.setString(1, tipo);
            ps.setInt(2, anno);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ------------------------------------------------------------------------
    // versioni
    // ------------------------------------------------------------------------

    public void insertVersione(Connection c, long testataId, int versione, String datiJson, byte[] pdf, String sha256)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ddt_versione (testata_id, versione, dati, pdf, pdf_sha256) VALUES (?, ?, ?::jsonb, ?, ?)")) {
            ps.setLong(1, testataId);
            ps.setInt(2, versione);
            ps.setString(3, datiJson);
            ps.setBytes(4, pdf);
            ps.setString(5, sha256);
            ps.executeUpdate();
        }
    }

    /** Fotografia JSON della versione indicata, oppure null. */
    public String datiVersione(Connection c, long testataId, int versione) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT dati::text FROM ddt_versione WHERE testata_id = ? AND versione = ?")) {
            ps.setLong(1, testataId);
            ps.setInt(2, versione);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public byte[] pdfVersione(Connection c, long testataId, int versione) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT pdf FROM ddt_versione WHERE testata_id = ? AND versione = ?")) {
            ps.setLong(1, testataId);
            ps.setInt(2, versione);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }

    public List<DdtStorico.Versione> listVersioni(Connection c, long testataId) throws SQLException {
        List<DdtStorico.Versione> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT versione, creata_at, pdf_sha256 FROM ddt_versione WHERE testata_id = ? ORDER BY versione DESC")) {
            ps.setLong(1, testataId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DdtStorico.Versione v = new DdtStorico.Versione();
                    v.versione = rs.getInt("versione");
                    v.creataAt = Db.getDateTimeTz(rs, "creata_at");
                    v.sha256 = rs.getString("pdf_sha256");
                    list.add(v);
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------------------------
    // eventi
    // ------------------------------------------------------------------------

    public void insertEvento(Connection c, long testataId, Integer versione, String tipo, String postazione, String note)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ddt_evento (testata_id, versione, tipo, postazione, note) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, testataId);
            Db.setInt(ps, 2, versione);
            ps.setString(3, tipo);
            Db.setString(ps, 4, truncate(postazione, 100));
            Db.setString(ps, 5, truncate(note, 500));
            ps.executeUpdate();
        }
    }

    public List<DdtStorico.Evento> listEventi(Connection c, long testataId) throws SQLException {
        List<DdtStorico.Evento> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM ddt_evento WHERE testata_id = ? ORDER BY evento_at DESC, id DESC")) {
            ps.setLong(1, testataId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DdtStorico.Evento e = new DdtStorico.Evento();
                    e.eventoAt = Db.getDateTimeTz(rs, "evento_at");
                    e.tipo = rs.getString("tipo");
                    e.versione = Db.getInt(rs, "versione");
                    e.postazione = rs.getString("postazione");
                    e.note = rs.getString("note");
                    list.add(e);
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------------------------
    // elenco
    // ------------------------------------------------------------------------

    public List<DdtStorico.ElencoRiga> list(Connection c, DdtStorico.Filtro f, int max) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT id, ddt_numero, ddt_data, mat_doc, mat_doc_anno, data_registrazione, mitt_codice, dest_codice,"
            + " dest_ragione_sociale, stato, versione_corrente FROM ddt_testata WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (f.dataDa != null) {
            sql.append(" AND coalesce(ddt_data, data_registrazione) >= ?");
            params.add(f.dataDa);
        }
        if (f.dataA != null) {
            sql.append(" AND coalesce(ddt_data, data_registrazione) <= ?");
            params.add(f.dataA);
        }
        if (f.stato != null && !f.stato.isBlank()) {
            sql.append(" AND stato = ?");
            params.add(f.stato);
        }
        if (f.testo != null && !f.testo.isBlank()) {
            sql.append(" AND (upper(coalesce(ddt_numero,'')) LIKE ? OR mat_doc LIKE ? OR upper(dest_codice) LIKE ?"
                + " OR upper(coalesce(dest_ragione_sociale,'')) LIKE ?)");
            String like = "%" + f.testo.trim().toUpperCase() + "%";
            for (int i = 0; i < 4; i++) params.add(like);
        }
        sql.append(" ORDER BY coalesce(ddt_data, data_registrazione) DESC, id DESC LIMIT ").append(max);
        List<DdtStorico.ElencoRiga> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DdtStorico.ElencoRiga r = new DdtStorico.ElencoRiga();
                    r.id = rs.getLong("id");
                    r.ddtNumero = rs.getString("ddt_numero");
                    r.ddtData = Db.getDate(rs, "ddt_data");
                    r.matDoc = rs.getString("mat_doc");
                    r.matDocAnno = rs.getString("mat_doc_anno").trim();
                    r.dataRegistrazione = Db.getDate(rs, "data_registrazione");
                    r.mittCodice = rs.getString("mitt_codice");
                    r.destCodice = rs.getString("dest_codice");
                    r.destRagioneSociale = rs.getString("dest_ragione_sociale");
                    r.stato = rs.getString("stato");
                    r.versioneCorrente = rs.getInt("versione_corrente");
                    list.add(r);
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------------------------
    // mapping
    // ------------------------------------------------------------------------

    private void insertPosizioni(Connection c, Ddt d) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ddt_posizione (testata_id, riga_sap, ordinamento, materiale, descrizione, lotto, um,"
                + " quantita, um_base, quantita_base, peso_netto) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (DdtPosizione p : d.getPosizioni()) {
                ps.setLong(1, d.getId());
                ps.setString(2, p.getRigaSap());
                ps.setInt(3, p.getOrdinamento());
                ps.setString(4, p.getMateriale());
                Db.setString(ps, 5, truncate(p.getDescrizione(), 120));
                Db.setString(ps, 6, p.getLotto());
                ps.setString(7, p.getUm());
                ps.setBigDecimal(8, p.getQuantita());
                Db.setString(ps, 9, p.getUmBase());
                Db.setDecimal(ps, 10, p.getQuantitaBase());
                Db.setDecimal(ps, 11, p.getPesoNetto());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private int bindTestata(PreparedStatement ps, Ddt d) throws SQLException {
        int i = 1;
        ps.setString(i++, d.getMatDocAnno());
        ps.setString(i++, d.getMatDoc());
        Db.setDate(ps, i++, d.getDataRegistrazione());
        Db.setDate(ps, i++, d.getDataDocumento());
        Db.setString(ps, i++, d.getUtenteSap());
        Db.setString(ps, i++, d.getDdtTipoNum());
        if (d.getDdtAnno() == null) ps.setNull(i++, java.sql.Types.SMALLINT);
        else ps.setShort(i++, d.getDdtAnno().shortValue());
        Db.setInt(ps, i++, d.getDdtProgressivo());
        Db.setString(ps, i++, d.getDdtNumero());
        Db.setDate(ps, i++, d.getDdtData());
        ps.setString(i++, d.getMittCodice());
        Db.setString(ps, i++, d.getMittRagioneSociale());
        Db.setString(ps, i++, d.getMittIndirizzo());
        Db.setString(ps, i++, d.getMittCap());
        Db.setString(ps, i++, d.getMittCitta());
        Db.setString(ps, i++, d.getMittProvincia());
        Db.setString(ps, i++, d.getMittNazione());
        ps.setString(i++, d.getDestCodice());
        Db.setString(ps, i++, d.getDestRagioneSociale());
        Db.setString(ps, i++, d.getDestIndirizzo());
        Db.setString(ps, i++, d.getDestCap());
        Db.setString(ps, i++, d.getDestCitta());
        Db.setString(ps, i++, d.getDestProvincia());
        Db.setString(ps, i++, d.getDestNazione());
        Db.setString(ps, i++, d.getDestPartitaIva());
        Db.setString(ps, i++, d.getCausale());
        Db.setString(ps, i++, d.getPorto());
        Db.setString(ps, i++, d.getAspettoBeni());
        Db.setInt(ps, i++, d.getVettoreId());
        Db.setString(ps, i++, d.getVettRagioneSociale());
        Db.setString(ps, i++, d.getVettIndirizzo());
        Db.setString(ps, i++, d.getVettPartitaIva());
        Db.setString(ps, i++, d.getVettIscrizioneAlbo());
        Db.setInt(ps, i++, d.getColli());
        Db.setDecimal(ps, i++, d.getPesoLordo());
        Db.setDecimal(ps, i++, d.getPesoNetto());
        ps.setString(i++, d.getUnitaPeso() == null ? "KG" : d.getUnitaPeso());
        Db.setDateTime(ps, i++, d.getInizioTrasporto());
        Db.setString(ps, i++, d.getAnnotazioni());
        ps.setString(i++, d.getStato());
        ps.setInt(i++, d.getVersioneCorrente());
        Db.setString(ps, i++, d.getStornoMatDocAnno());
        Db.setString(ps, i++, d.getStornoMatDoc());
        if (d.getAnnullatoAt() == null) ps.setNull(i++, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        else ps.setObject(i++, d.getAnnullatoAt().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime());
        Db.setString(ps, i++, d.getMotivoAnnullamento());
        if (d.getSostituisceId() == null) ps.setNull(i++, java.sql.Types.BIGINT);
        else ps.setLong(i++, d.getSostituisceId());
        return i;
    }

    private Ddt mapTestata(ResultSet rs) throws SQLException {
        Ddt d = new Ddt();
        d.setId(rs.getLong("id"));
        d.setMatDocAnno(rs.getString("mat_doc_anno").trim());
        d.setMatDoc(rs.getString("mat_doc"));
        d.setDataRegistrazione(Db.getDate(rs, "data_registrazione"));
        d.setDataDocumento(Db.getDate(rs, "data_documento"));
        d.setUtenteSap(rs.getString("utente_sap"));
        d.setDdtTipoNum(rs.getString("ddt_tipo_num"));
        d.setDdtAnno(Db.getInt(rs, "ddt_anno"));
        d.setDdtProgressivo(Db.getInt(rs, "ddt_progressivo"));
        d.setDdtNumero(rs.getString("ddt_numero"));
        d.setDdtData(Db.getDate(rs, "ddt_data"));
        d.setMittCodice(rs.getString("mitt_codice"));
        d.setMittRagioneSociale(rs.getString("mitt_ragione_sociale"));
        d.setMittIndirizzo(rs.getString("mitt_indirizzo"));
        d.setMittCap(rs.getString("mitt_cap"));
        d.setMittCitta(rs.getString("mitt_citta"));
        d.setMittProvincia(rs.getString("mitt_provincia"));
        d.setMittNazione(rs.getString("mitt_nazione"));
        d.setDestCodice(rs.getString("dest_codice"));
        d.setDestRagioneSociale(rs.getString("dest_ragione_sociale"));
        d.setDestIndirizzo(rs.getString("dest_indirizzo"));
        d.setDestCap(rs.getString("dest_cap"));
        d.setDestCitta(rs.getString("dest_citta"));
        d.setDestProvincia(rs.getString("dest_provincia"));
        d.setDestNazione(rs.getString("dest_nazione"));
        d.setDestPartitaIva(rs.getString("dest_partita_iva"));
        d.setCausale(rs.getString("causale"));
        d.setPorto(rs.getString("porto"));
        d.setAspettoBeni(rs.getString("aspetto_beni"));
        d.setVettoreId(Db.getInt(rs, "vettore_id"));
        d.setVettRagioneSociale(rs.getString("vett_ragione_sociale"));
        d.setVettIndirizzo(rs.getString("vett_indirizzo"));
        d.setVettPartitaIva(rs.getString("vett_partita_iva"));
        d.setVettIscrizioneAlbo(rs.getString("vett_iscrizione_albo"));
        d.setColli(Db.getInt(rs, "colli"));
        d.setPesoLordo(rs.getBigDecimal("peso_lordo"));
        d.setPesoNetto(rs.getBigDecimal("peso_netto"));
        d.setUnitaPeso(rs.getString("unita_peso"));
        d.setInizioTrasporto(Db.getDateTime(rs, "inizio_trasporto"));
        d.setAnnotazioni(rs.getString("annotazioni"));
        d.setStato(rs.getString("stato"));
        d.setVersioneCorrente(rs.getInt("versione_corrente"));
        String sa = rs.getString("storno_mat_doc_anno");
        d.setStornoMatDocAnno(sa == null ? null : sa.trim());
        d.setStornoMatDoc(rs.getString("storno_mat_doc"));
        d.setAnnullatoAt(Db.getDateTimeTz(rs, "annullato_at"));
        d.setMotivoAnnullamento(rs.getString("motivo_annullamento"));
        long sost = rs.getLong("sostituisce_id");
        d.setSostituisceId(rs.wasNull() ? null : sost);
        return d;
    }

    private DdtPosizione mapPosizione(ResultSet rs) throws SQLException {
        DdtPosizione p = new DdtPosizione();
        p.setId(rs.getLong("id"));
        p.setRigaSap(rs.getString("riga_sap"));
        p.setOrdinamento(rs.getInt("ordinamento"));
        p.setMateriale(rs.getString("materiale"));
        p.setDescrizione(rs.getString("descrizione"));
        p.setLotto(rs.getString("lotto"));
        p.setUm(rs.getString("um"));
        p.setQuantita(rs.getBigDecimal("quantita"));
        p.setUmBase(rs.getString("um_base"));
        p.setQuantitaBase(rs.getBigDecimal("quantita_base"));
        p.setPesoNetto(rs.getBigDecimal("peso_netto"));
        return p;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
