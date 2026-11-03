package lamplast.mbxa.logic.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lamplast.mbxa.logic.model.Anagrafiche.Decodifica;
import lamplast.mbxa.logic.model.Anagrafiche.Stabilimento;
import lamplast.mbxa.logic.model.Anagrafiche.TipoDecodifica;
import lamplast.mbxa.logic.model.Anagrafiche.Vettore;

/** Accesso alle anagrafiche accessorie. */
public class AnagraficheDao implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------------
    // stabilimenti
    // ------------------------------------------------------------------------

    public List<Stabilimento> listStabilimenti(Connection c, boolean soloAttivi) throws SQLException {
        List<Stabilimento> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM ana_stabilimento"
                + (soloAttivi ? " WHERE attivo" : "") + " ORDER BY codice")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapStabilimento(rs));
            }
        }
        return list;
    }

    public Stabilimento getStabilimento(Connection c, String codice) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM ana_stabilimento WHERE codice = ?")) {
            ps.setString(1, codice);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapStabilimento(rs) : null;
            }
        }
    }

    public void saveStabilimento(Connection c, Stabilimento s) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ana_stabilimento (codice, ragione_sociale, indirizzo, cap, citta, provincia, nazione,"
                + " partita_iva, codice_fiscale, attivo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (codice) DO UPDATE SET ragione_sociale = EXCLUDED.ragione_sociale,"
                + " indirizzo = EXCLUDED.indirizzo, cap = EXCLUDED.cap, citta = EXCLUDED.citta,"
                + " provincia = EXCLUDED.provincia, nazione = EXCLUDED.nazione,"
                + " partita_iva = EXCLUDED.partita_iva, codice_fiscale = EXCLUDED.codice_fiscale,"
                + " attivo = EXCLUDED.attivo")) {
            ps.setString(1, s.getCodice().trim().toUpperCase());
            ps.setString(2, s.getRagioneSociale().trim());
            ps.setString(3, s.getIndirizzo().trim());
            ps.setString(4, s.getCap().trim());
            ps.setString(5, s.getCitta().trim());
            Db.setString(ps, 6, upper(s.getProvincia()));
            ps.setString(7, s.getNazione() == null || s.getNazione().isBlank() ? "IT" : s.getNazione().trim().toUpperCase());
            Db.setString(ps, 8, upper(s.getPartitaIva()));
            Db.setString(ps, 9, upper(s.getCodiceFiscale()));
            ps.setBoolean(10, s.isAttivo());
            ps.executeUpdate();
        }
    }

    public void deleteStabilimento(Connection c, String codice) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM ana_stabilimento WHERE codice = ?")) {
            ps.setString(1, codice);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------------
    // vettori
    // ------------------------------------------------------------------------

    public List<Vettore> listVettori(Connection c, boolean soloAttivi) throws SQLException {
        List<Vettore> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM ana_vettore"
                + (soloAttivi ? " WHERE attivo" : "") + " ORDER BY ragione_sociale")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapVettore(rs));
            }
        }
        return list;
    }

    public Vettore getVettore(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM ana_vettore WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapVettore(rs) : null;
            }
        }
    }

    public Vettore getVettorePredefinito(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM ana_vettore WHERE predefinito AND attivo")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapVettore(rs) : null;
            }
        }
    }

    public void saveVettore(Connection c, Vettore v) throws SQLException {
        if (v.isPredefinito()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE ana_vettore SET predefinito = false WHERE predefinito AND id IS DISTINCT FROM ?")) {
                Db.setInt(ps, 1, v.getId());
                ps.executeUpdate();
            }
        }
        String sql = v.getId() == null
            ? "INSERT INTO ana_vettore (ragione_sociale, indirizzo, cap, citta, provincia, nazione, partita_iva,"
                + " iscrizione_albo, predefinito, attivo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id"
            : "UPDATE ana_vettore SET ragione_sociale = ?, indirizzo = ?, cap = ?, citta = ?, provincia = ?,"
                + " nazione = ?, partita_iva = ?, iscrizione_albo = ?, predefinito = ?, attivo = ? WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, v.getRagioneSociale().trim());
            Db.setString(ps, 2, v.getIndirizzo());
            Db.setString(ps, 3, v.getCap());
            Db.setString(ps, 4, v.getCitta());
            Db.setString(ps, 5, upper(v.getProvincia()));
            Db.setString(ps, 6, upper(v.getNazione()));
            Db.setString(ps, 7, v.getPartitaIva());
            Db.setString(ps, 8, v.getIscrizioneAlbo());
            ps.setBoolean(9, v.isPredefinito());
            ps.setBoolean(10, v.isAttivo());
            if (v.getId() == null) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    v.setId(rs.getInt(1));
                }
            } else {
                ps.setInt(11, v.getId());
                ps.executeUpdate();
            }
        }
    }

    /**
     * Cancella il vettore; se è già usato in qualche DDT lo disattiva soltanto
     * (il DDT conserva comunque la copia dei dati).
     * @return true se cancellato, false se solo disattivato
     */
    public boolean deleteVettore(Connection c, int id) throws SQLException {
        boolean usato;
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM ddt_testata WHERE vettore_id = ? LIMIT 1")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                usato = rs.next();
            }
        }
        String sql = usato
            ? "UPDATE ana_vettore SET attivo = false, predefinito = false WHERE id = ?"
            : "DELETE FROM ana_vettore WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        return !usato;
    }

    // ------------------------------------------------------------------------
    // decodifiche (causale, porto, aspetto)
    // ------------------------------------------------------------------------

    public List<Decodifica> listDecodifiche(Connection c, TipoDecodifica tipo, boolean soloAttivi) throws SQLException {
        List<Decodifica> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + tipo.getTable()
                + (soloAttivi ? " WHERE attivo" : "") + " ORDER BY descrizione")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Decodifica d = new Decodifica();
                    d.setCodice(rs.getString("codice"));
                    d.setDescrizione(rs.getString("descrizione"));
                    d.setPredefinito(rs.getBoolean("predefinito"));
                    d.setAttivo(rs.getBoolean("attivo"));
                    list.add(d);
                }
            }
        }
        return list;
    }

    /** Descrizione della voce predefinita, oppure null. */
    public String getDecodificaPredefinita(Connection c, TipoDecodifica tipo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT descrizione FROM " + tipo.getTable() + " WHERE predefinito AND attivo")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public void saveDecodifica(Connection c, TipoDecodifica tipo, Decodifica d) throws SQLException {
        String codice = d.getCodice().trim().toUpperCase();
        if (d.isPredefinito()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + tipo.getTable() + " SET predefinito = false WHERE predefinito AND codice <> ?")) {
                ps.setString(1, codice);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + tipo.getTable() + " (codice, descrizione, predefinito, attivo) VALUES (?, ?, ?, ?)"
                + " ON CONFLICT (codice) DO UPDATE SET descrizione = EXCLUDED.descrizione,"
                + " predefinito = EXCLUDED.predefinito, attivo = EXCLUDED.attivo")) {
            ps.setString(1, codice);
            ps.setString(2, d.getDescrizione().trim());
            ps.setBoolean(3, d.isPredefinito());
            ps.setBoolean(4, d.isAttivo());
            ps.executeUpdate();
        }
    }

    public void deleteDecodifica(Connection c, TipoDecodifica tipo, String codice) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + tipo.getTable() + " WHERE codice = ?")) {
            ps.setString(1, codice);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------------
    // mapping
    // ------------------------------------------------------------------------

    private Stabilimento mapStabilimento(ResultSet rs) throws SQLException {
        Stabilimento s = new Stabilimento();
        s.setCodice(rs.getString("codice"));
        s.setRagioneSociale(rs.getString("ragione_sociale"));
        s.setIndirizzo(rs.getString("indirizzo"));
        s.setCap(rs.getString("cap"));
        s.setCitta(rs.getString("citta"));
        s.setProvincia(rs.getString("provincia"));
        s.setNazione(rs.getString("nazione"));
        s.setPartitaIva(rs.getString("partita_iva"));
        s.setCodiceFiscale(rs.getString("codice_fiscale"));
        s.setAttivo(rs.getBoolean("attivo"));
        return s;
    }

    private Vettore mapVettore(ResultSet rs) throws SQLException {
        Vettore v = new Vettore();
        v.setId(rs.getInt("id"));
        v.setRagioneSociale(rs.getString("ragione_sociale"));
        v.setIndirizzo(rs.getString("indirizzo"));
        v.setCap(rs.getString("cap"));
        v.setCitta(rs.getString("citta"));
        v.setProvincia(rs.getString("provincia"));
        v.setNazione(rs.getString("nazione"));
        v.setPartitaIva(rs.getString("partita_iva"));
        v.setIscrizioneAlbo(rs.getString("iscrizione_albo"));
        v.setPredefinito(rs.getBoolean("predefinito"));
        v.setAttivo(rs.getBoolean("attivo"));
        return v;
    }

    private static String upper(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }
}
