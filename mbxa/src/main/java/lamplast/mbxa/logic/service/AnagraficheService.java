package lamplast.mbxa.logic.service;

import java.sql.Connection;
import java.util.List;

import lamplast.mbxa.logic.db.AnagraficheDao;
import lamplast.mbxa.logic.db.Db;
import lamplast.mbxa.logic.model.Anagrafiche.Decodifica;
import lamplast.mbxa.logic.model.Anagrafiche.Stabilimento;
import lamplast.mbxa.logic.model.Anagrafiche.TipoDecodifica;
import lamplast.mbxa.logic.model.Anagrafiche.Vettore;

/** Manutenzione delle anagrafiche accessorie. */
public class AnagraficheService implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private final AnagraficheDao m_dao = new AnagraficheDao();

    public List<Stabilimento> stabilimenti() throws Exception {
        try (Connection c = Db.open()) {
            return m_dao.listStabilimenti(c, false);
        }
    }

    public void salvaStabilimento(Stabilimento s) throws Exception {
        if (vuoto(s.getCodice()) || s.getCodice().trim().length() > 4) {
            throw new DdtException("Codice stabilimento obbligatorio (codice Plant SAP, max 4 caratteri).");
        }
        if (vuoto(s.getRagioneSociale()) || vuoto(s.getIndirizzo()) || vuoto(s.getCap()) || vuoto(s.getCitta())) {
            throw new DdtException("Stabilimento " + s.getCodice() + ": ragione sociale, indirizzo, CAP e città sono obbligatori.");
        }
        String chi = "Stabilimento " + s.getCodice().trim() + ": ";
        lunghezza(chi, "Ragione sociale", s.getRagioneSociale(), 120);
        lunghezza(chi, "Indirizzo", s.getIndirizzo(), 120);
        lunghezza(chi, "CAP", s.getCap(), 10);
        lunghezza(chi, "Città", s.getCitta(), 60);
        lunghezza(chi, "Provincia", s.getProvincia(), 2);
        lunghezza(chi, "Nazione", s.getNazione(), 2);
        lunghezza(chi, "Partita IVA", s.getPartitaIva(), 20);
        lunghezza(chi, "Codice fiscale", s.getCodiceFiscale(), 20);
        Db.inTransaction(c -> {
            m_dao.saveStabilimento(c, s);
            return null;
        });
    }

    public void eliminaStabilimento(String codice) throws Exception {
        Db.inTransaction(c -> {
            m_dao.deleteStabilimento(c, codice);
            return null;
        });
    }

    public List<Vettore> vettori() throws Exception {
        try (Connection c = Db.open()) {
            return m_dao.listVettori(c, false);
        }
    }

    public void salvaVettore(Vettore v) throws Exception {
        if (vuoto(v.getRagioneSociale())) throw new DdtException("Ragione sociale del vettore obbligatoria.");
        String chi = "Vettore " + v.getRagioneSociale().trim() + ": ";
        lunghezza(chi, "Ragione sociale", v.getRagioneSociale(), 120);
        lunghezza(chi, "Indirizzo", v.getIndirizzo(), 120);
        lunghezza(chi, "CAP", v.getCap(), 10);
        lunghezza(chi, "Città", v.getCitta(), 60);
        lunghezza(chi, "Provincia", v.getProvincia(), 2);
        lunghezza(chi, "Partita IVA", v.getPartitaIva(), 20);
        lunghezza(chi, "Iscrizione albo", v.getIscrizioneAlbo(), 40);
        Db.inTransaction(c -> {
            m_dao.saveVettore(c, v);
            return null;
        });
    }

    /** @return true se cancellato, false se disattivato perché già usato in qualche DDT */
    public boolean eliminaVettore(int id) throws Exception {
        return Db.inTransaction(c -> m_dao.deleteVettore(c, id));
    }

    public List<Decodifica> decodifiche(TipoDecodifica tipo) throws Exception {
        try (Connection c = Db.open()) {
            return m_dao.listDecodifiche(c, tipo, false);
        }
    }

    public void salvaDecodifica(TipoDecodifica tipo, Decodifica d) throws Exception {
        if (vuoto(d.getCodice()) || d.getCodice().trim().length() > 10) {
            throw new DdtException("Codice obbligatorio (max 10 caratteri).");
        }
        if (vuoto(d.getDescrizione())) throw new DdtException("Descrizione obbligatoria.");
        lunghezza("Voce " + d.getCodice().trim() + ": ", "Descrizione", d.getDescrizione(), 80);
        Db.inTransaction(c -> {
            m_dao.saveDecodifica(c, tipo, d);
            return null;
        });
    }

    public void eliminaDecodifica(TipoDecodifica tipo, String codice) throws Exception {
        Db.inTransaction(c -> {
            m_dao.deleteDecodifica(c, tipo, codice);
            return null;
        });
    }

    /** Controllo esplicito delle lunghezze del database, con un messaggio comprensibile. */
    private static void lunghezza(String chi, String campo, String valore, int max) {
        if (valore == null) return;
        int n = valore.trim().length();
        if (n > max) {
            throw new DdtException(chi + "campo \"" + campo + "\" troppo lungo: " + n + " caratteri, massimo " + max + ".");
        }
    }

    private static boolean vuoto(String s) {
        return s == null || s.isBlank();
    }
}
