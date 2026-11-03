package lamplast.mbxa.logic.service;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ObjectNode;

import lamplast.mbxa.logic.config.AppConfig;
import lamplast.mbxa.logic.config.AppConfig.ControlloSequenza;
import lamplast.mbxa.logic.config.AppConfig.ModoNumerazione;
import lamplast.mbxa.logic.db.AnagraficheDao;
import lamplast.mbxa.logic.db.Db;
import lamplast.mbxa.logic.db.DdtDao;
import lamplast.mbxa.logic.model.Anagrafiche.Stabilimento;
import lamplast.mbxa.logic.model.Anagrafiche.TipoDecodifica;
import lamplast.mbxa.logic.model.Anagrafiche.Vettore;
import lamplast.mbxa.logic.model.Ddt;
import lamplast.mbxa.logic.model.DdtPosizione;
import lamplast.mbxa.logic.model.DdtStorico;
import lamplast.mbxa.logic.pdf.DdtPdfBuilder;
import lamplast.mbxa.logic.pdf.Filigrana;
import lamplast.mbxa.logic.sap.MaterialDocumentClient;
import lamplast.mbxa.logic.sap.ProductClient;
import lamplast.mbxa.logic.sap.ProductClient.ProductInfo;
import lamplast.mbxa.logic.sap.SapMaterialDocument;

/**
 * Casi d'uso del DDT. Ogni operazione che cambia lo stato di un DDT avviene in UNA
 * transazione: se qualcosa fallisce non resta nulla a metà (numero assegnato senza PDF,
 * PDF senza evento, ecc.).
 */
public class DdtService implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** Esito dell'acquisizione. */
    public static class Acquisizione {
        public long id;
        public boolean giaPresente;
        /** Numero del DDT annullato sostituito dal nuovo DDT, oppure null. */
        public String sostituisceNumero;
        public final List<String> avvisi = new ArrayList<>();
    }

    /** Esito di una stampa. */
    public static class Stampa {
        public byte[] pdf;
        public int versione;
        /** True se i dati non erano cambiati: restituito il PDF già emesso. */
        public boolean ristampa;
        public String numeroDdt;
    }

    private final DdtDao m_dao = new DdtDao();
    private final AnagraficheDao m_ana = new AnagraficheDao();

    private AppConfig cfg() {
        return AppConfig.get();
    }

    // ------------------------------------------------------------------------
    // ricerca
    // ------------------------------------------------------------------------

    public List<DdtStorico.ElencoRiga> elenco(DdtStorico.Filtro filtro) throws Exception {
        try (Connection c = Db.open()) {
            return m_dao.list(c, filtro, 500);
        }
    }

    public Ddt carica(long id) throws Exception {
        try (Connection c = Db.open()) {
            Ddt d = m_dao.load(c, id);
            if (d == null) throw new DdtException("DDT non trovato (id " + id + ").");
            return d;
        }
    }

    /** DDT già acquisiti con quel numero (la ricerca per numero vale solo per i DDT già in archivio). */
    public List<Long> cercaPerNumero(String numero) throws Exception {
        if (numero == null || numero.isBlank()) throw new DdtException("Inserire il numero DDT da cercare.");
        String n = numero.trim();
        try (Connection c = Db.open()) {
            List<Long> ids = m_dao.findIdsByNumero(c, n);
            if (ids.isEmpty() && n.matches("0+[0-9]+")) ids = m_dao.findIdsByNumero(c, n.replaceFirst("^0+", ""));
            return ids;
        }
    }

    /** 301 recenti non ancora acquisiti. */
    public List<MaterialDocumentClient.Summary> daAcquisire() throws Exception {
        AppConfig cfg = cfg();
        LocalDate from = LocalDate.now().minusDays(cfg.getListaGiorni());
        List<MaterialDocumentClient.Summary> recenti = new MaterialDocumentClient(cfg).listRecent301(from, cfg.getListaMax());
        Set<String> anni = new HashSet<>();
        for (MaterialDocumentClient.Summary s : recenti) anni.add(s.year);
        Set<String> acquisiti;
        try (Connection c = Db.open()) {
            acquisiti = m_dao.acquiredKeys(c, anni);
        }
        List<MaterialDocumentClient.Summary> result = new ArrayList<>();
        for (MaterialDocumentClient.Summary s : recenti) {
            // stornati in SAP: non vanno stampati; acquisiti: hanno già un DDT non annullato
            if (s.cancelled) continue;
            if (!acquisiti.contains(s.year + "/" + s.number)) result.add(s);
        }
        return result;
    }

    // ------------------------------------------------------------------------
    // acquisizione
    // ------------------------------------------------------------------------

    /**
     * Legge il documento da SAP e crea la bozza. Se il documento è già stato acquisito
     * restituisce quello esistente (un DDT per documento materiale).
     */
    public Acquisizione acquisisci(String anno, String matDoc, String postazione) throws Exception {
        String a = anno == null ? "" : anno.trim();
        String m = matDoc == null ? "" : matDoc.trim();
        if (!a.matches("\\d{4}")) throw new DdtException("Anno del documento materiale non valido: \"" + a + "\"");
        if (!m.matches("\\d{1,10}")) throw new DdtException("Numero documento materiale non valido: \"" + m + "\"");

        Acquisizione res = new Acquisizione();
        try (Connection c = Db.open()) {
            Long existing = m_dao.findIdByMatDoc(c, a, m);
            if (existing != null) {
                res.id = existing;
                res.giaPresente = true;
                return res;
            }
        }

        AppConfig cfg = cfg();
        Optional<SapMaterialDocument> opt = new MaterialDocumentClient(cfg).read(a, m);
        if (!opt.isPresent()) throw new DdtException("Documento materiale " + m + "/" + a + " non trovato in SAP.");
        SapMaterialDocument doc = opt.get();
        String[] plants = DdtComposer.stabilimenti(doc);

        Set<String> materiali = new HashSet<>();
        for (SapMaterialDocument.Item it : doc.getOutgoingTransferItems()) materiali.add(it.material);
        ProductClient pc = new ProductClient(cfg);
        Map<String, ProductInfo> prodotti = pc.fetch(materiali, cfg.getLinguaDescrizioni());

        return Db.inTransaction(c -> {
            // ricontrollo dentro la transazione: due postazioni potrebbero acquisire insieme
            Long existing = m_dao.findIdByMatDoc(c, a, m);
            if (existing != null) {
                res.id = existing;
                res.giaPresente = true;
                return res;
            }
            Stabilimento mitt = m_ana.getStabilimento(c, plants[0]);
            Stabilimento dest = m_ana.getStabilimento(c, plants[1]);
            DdtComposer.Default def = new DdtComposer.Default();
            def.causale = m_ana.getDecodificaPredefinita(c, TipoDecodifica.CAUSALE);
            def.porto = m_ana.getDecodificaPredefinita(c, TipoDecodifica.PORTO);
            def.aspetto = m_ana.getDecodificaPredefinita(c, TipoDecodifica.ASPETTO);
            def.vettore = m_ana.getVettorePredefinito(c);
            DdtComposer.Risultato r = DdtComposer.daDocumentoSap(doc, prodotti, mitt, dest, def);
            // documento con un DDT annullato (per errore, non per storno): nuovo DDT in sostituzione
            Long annullato = m_dao.findUltimoAnnullato(c, a, m);
            String nota = "Documento materiale " + m + "/" + a;
            if (annullato != null) {
                Ddt vecchio = m_dao.load(c, annullato);
                r.ddt.setSostituisceId(annullato);
                res.sostituisceNumero = vecchio.getDdtNumero();
                nota += " - sostituisce il DDT " + vecchio.getDdtNumero() + " annullato";
            }
            res.id = m_dao.insert(c, r.ddt);
            res.avvisi.addAll(r.avvisi);
            res.avvisi.addAll(pc.getWarnings());
            m_dao.insertEvento(c, res.id, null, "ACQUISIZIONE", postazione, nota);
            return res;
        });
    }

    // ------------------------------------------------------------------------
    // modifica
    // ------------------------------------------------------------------------

    /**
     * Salva i dati modificabili. In bozza si possono cambiare anche numero e data DDT;
     * dopo l'emissione solo i dati accessori (le modifiche producono una nuova versione
     * alla stampa successiva). Le posizioni vengono da SAP e non si modificano.
     */
    public void salva(Ddt input, String postazione) throws Exception {
        Db.inTransaction(c -> {
            Ddt d = m_dao.loadForUpdate(c, input.getId());
            if (d == null) throw new DdtException("DDT non trovato.");
            if (d.isAnnullato()) throw new DdtException("Il DDT è annullato: non è modificabile.");
            if (d.isBozza()) {
                d.setDdtData(input.getDdtData());
                if (cfg().getModoNumerazione() == ModoNumerazione.MANUALE) {
                    String n = input.getDdtNumero();
                    d.setDdtNumero(n == null || n.isBlank() ? null : Numerazione.normalizzaNumeroSap(n));
                }
                d.setMittRagioneSociale(input.getMittRagioneSociale());
                d.setMittIndirizzo(input.getMittIndirizzo());
                d.setMittCap(input.getMittCap());
                d.setMittCitta(input.getMittCitta());
                d.setMittProvincia(input.getMittProvincia());
                d.setMittNazione(input.getMittNazione());
                d.setDestRagioneSociale(input.getDestRagioneSociale());
                d.setDestIndirizzo(input.getDestIndirizzo());
                d.setDestCap(input.getDestCap());
                d.setDestCitta(input.getDestCitta());
                d.setDestProvincia(input.getDestProvincia());
                d.setDestNazione(input.getDestNazione());
                d.setDestPartitaIva(input.getDestPartitaIva());
                d.setDestCodiceFiscale(input.getDestCodiceFiscale());
            }
            d.setCausale(input.getCausale());
            d.setPorto(input.getPorto());
            d.setAspettoBeni(input.getAspettoBeni());
            d.setVettoreId(input.getVettoreId());
            d.setVettRagioneSociale(input.getVettRagioneSociale());
            d.setVettIndirizzo(input.getVettIndirizzo());
            d.setVettPartitaIva(input.getVettPartitaIva());
            d.setVettIscrizioneAlbo(input.getVettIscrizioneAlbo());
            d.setColli(input.getColli());
            d.setPesoLordo(input.getPesoLordo());
            d.setPesoNetto(input.getPesoNetto());
            d.setInizioTrasporto(input.getInizioTrasporto());
            d.setAnnotazioni(input.getAnnotazioni());
            m_dao.updateTestata(c, d);
            if (d.isEmesso()) {
                m_dao.insertEvento(c, d.getId(), d.getVersioneCorrente(), "MODIFICA", postazione,
                    "Modifica dati accessori dopo l'emissione");
            }
            return null;
        });
    }

    /** Solo bozze: ricopia mittente e destinatario dall'anagrafica (dopo averla completata). */
    public void rileggiAnagrafiche(long id) throws Exception {
        Db.inTransaction(c -> {
            Ddt d = m_dao.loadForUpdate(c, id);
            if (d == null) throw new DdtException("DDT non trovato.");
            if (!d.isBozza()) throw new DdtException("Solo le bozze riprendono i dati dall'anagrafica.");
            DdtComposer.copiaMittente(d, m_ana.getStabilimento(c, d.getMittCodice()));
            DdtComposer.copiaDestinatario(d, m_ana.getStabilimento(c, d.getDestCodice()));
            m_dao.updateTestata(c, d);
            return null;
        });
    }

    // ------------------------------------------------------------------------
    // stampa
    // ------------------------------------------------------------------------

    /**
     * Prima stampa = emissione (assegnazione/controllo del numero, stato EMESSO).
     * Stampe successive: se i dati sono cambiati nuova versione, altrimenti ristampa
     * dello stesso PDF, byte per byte.
     *
     * @param sequenzaConfermata true se l'operatore ha già confermato un numero fuori sequenza
     */
    public Stampa stampa(long id, String postazione, boolean sequenzaConfermata) throws Exception {
        AppConfig cfg = cfg();
        return Db.inTransaction(c -> {
            Ddt d = m_dao.loadForUpdate(c, id);
            if (d == null) throw new DdtException("DDT non trovato.");
            Stampa s = new Stampa();

            if (d.isAnnullato()) {
                if (!d.isStampato()) throw new DdtException("DDT annullato e mai stampato.");
                return ristampaInterna(c, d, postazione, "Ristampa di DDT annullato");
            }

            boolean emissione = d.isBozza();
            if (emissione) {
                List<String> problemi = DdtComposer.problemiEmissione(d);
                if (!problemi.isEmpty()) throw new DdtException(String.join("\n", problemi));
                assegnaNumero(c, d, cfg, sequenzaConfermata);
                d.setStato(Ddt.STATO_EMESSO);
            }

            ObjectNode foto = Fotografia.crea(d);
            if (d.isStampato()) {
                String ultima = m_dao.datiVersione(c, d.getId(), d.getVersioneCorrente());
                if (Fotografia.uguali(foto, ultima)) {
                    return ristampaInterna(c, d, postazione, null);
                }
            }

            byte[] pdf = new DdtPdfBuilder(cfg).build(d);
            int versione = d.getVersioneCorrente() + 1;
            m_dao.insertVersione(c, d.getId(), versione, Fotografia.toJson(foto), pdf, sha256(pdf));
            d.setVersioneCorrente(versione);
            m_dao.updateTestata(c, d);
            m_dao.insertEvento(c, d.getId(), versione, "STAMPA", postazione,
                emissione ? "Emissione DDT " + d.getDdtNumero() : "Nuova versione per dati modificati");
            s.pdf = pdf;
            s.versione = versione;
            s.ristampa = false;
            s.numeroDdt = d.getDdtNumero();
            return s;
        });
    }

    /** Ristampa esplicita dell'ultima versione emessa (stesso PDF). */
    public Stampa ristampa(long id, String postazione) throws Exception {
        return Db.inTransaction(c -> {
            Ddt d = m_dao.load(c, id);
            if (d == null) throw new DdtException("DDT non trovato.");
            if (!d.isStampato()) throw new DdtException("Il DDT non è ancora stato stampato.");
            return ristampaInterna(c, d, postazione, null);
        });
    }

    /**
     * PDF di una versione specifica (storico e anteprima). Non registra eventi: è una consultazione.
     * Per un DDT annullato il PDF esce con la filigrana "ANNULLATO".
     */
    public byte[] pdfVersione(long id, int versione) throws Exception {
        try (Connection c = Db.open()) {
            byte[] pdf = m_dao.pdfVersione(c, id, versione);
            if (pdf == null) throw new DdtException("Versione " + versione + " non trovata.");
            Ddt d = m_dao.load(c, id);
            return perUscita(d, pdf);
        }
    }

    /**
     * Unico punto da cui un PDF salvato esce dal servizio. Un DDT annullato porta sempre
     * la filigrana, applicata su una copia: il PDF in database resta quello emesso.
     */
    private static byte[] perUscita(Ddt d, byte[] pdf) throws Exception {
        if (d != null && d.isAnnullato()) return Filigrana.annullato(pdf, d.getAnnullatoAt());
        return pdf;
    }

    /**
     * True se, dall'ultima stampa, i dati sono cambiati (alla prossima stampa nascerà
     * una nuova versione).
     */
    public boolean modificatoDopoStampa(long id) throws Exception {
        try (Connection c = Db.open()) {
            Ddt d = m_dao.load(c, id);
            if (d == null || !d.isStampato()) return false;
            return !Fotografia.uguali(Fotografia.crea(d), m_dao.datiVersione(c, id, d.getVersioneCorrente()));
        }
    }

    private Stampa ristampaInterna(Connection c, Ddt d, String postazione, String note) throws Exception {
        Stampa s = new Stampa();
        byte[] originale = m_dao.pdfVersione(c, d.getId(), d.getVersioneCorrente());
        if (originale == null) throw new DdtException("PDF della versione " + d.getVersioneCorrente() + " non trovato.");
        s.pdf = perUscita(d, originale);
        s.versione = d.getVersioneCorrente();
        s.ristampa = true;
        s.numeroDdt = d.getDdtNumero();
        if (d.isAnnullato()) {
            note = (note == null || note.isBlank() ? "Ristampa di DDT annullato" : note) + " (filigrana ANNULLATO)";
        }
        m_dao.insertEvento(c, d.getId(), s.versione, "RISTAMPA", postazione, note);
        return s;
    }

    private void assegnaNumero(Connection c, Ddt d, AppConfig cfg, boolean sequenzaConfermata) throws Exception {
        int anno = d.getDdtData().getYear();
        if (cfg.getModoNumerazione() == ModoNumerazione.MANUALE) {
            String numero = Numerazione.normalizzaNumeroSap(d.getDdtNumero());
            String usato = m_dao.matDocConNumero(c, Ddt.TIPO_NUM_SAP, anno, numero, d.getId());
            if (usato != null) {
                throw new DdtException("Il numero DDT " + numero + "/" + anno
                    + " è già stato usato per il documento materiale " + usato + ".");
            }
            ControlloSequenza ctrl = cfg.getControlloSequenza();
            if (ctrl != ControlloSequenza.NESSUNO) {
                Long max = m_dao.maxNumeroSap(c, anno, d.getId());
                if (max != null && new BigInteger(numero).compareTo(BigInteger.valueOf(max)) <= 0) {
                    String msg = "Il numero " + numero + " non è successivo all'ultimo DDT da trasferimento emesso nel "
                        + anno + " (" + max + "). Verificare in MIGO di aver letto il numero del documento "
                        + d.getMatDocChiave() + ".";
                    if (ctrl == ControlloSequenza.BLOCCO) throw new DdtException(msg);
                    if (!sequenzaConfermata) throw new DdtException.SequenzaDaConfermare(msg);
                }
            }
            d.setDdtTipoNum(Ddt.TIPO_NUM_SAP);
            d.setDdtAnno(anno);
            d.setDdtProgressivo(null);
            d.setDdtNumero(numero);
        } else {
            String tipo = cfg.getTipoNumerazioneInterna();
            int progressivo = m_dao.nextProgressivo(c, tipo, anno);
            d.setDdtTipoNum(tipo);
            d.setDdtAnno(anno);
            d.setDdtProgressivo(progressivo);
            d.setDdtNumero(Numerazione.formatta(cfg.getFormatoNumerazioneInterna(), tipo, anno, progressivo));
        }
    }

    // ------------------------------------------------------------------------
    // storni e annullamento
    // ------------------------------------------------------------------------

    /**
     * Controlla in SAP se il documento è stato stornato.
     * @return vuoto se non stornato; altrimenti "numero/anno" del 302 (o testo generico se non trovato)
     */
    public Optional<String> verificaStorno(Ddt d) throws Exception {
        MaterialDocumentClient mdc = new MaterialDocumentClient(cfg());
        Optional<SapMaterialDocument> doc = mdc.read(d.getMatDocAnno(), d.getMatDoc());
        if (!doc.isPresent() || !doc.get().isCancelled()) return Optional.empty();
        Optional<String[]> rev = mdc.findReversal(d.getMatDocAnno(), d.getMatDoc());
        return Optional.of(rev.map(r -> r[1] + "/" + r[0]).orElse("(documento di storno non individuato)"));
    }

    /**
     * Annulla un DDT emesso. I PDF restano consultabili. Se il documento materiale non è
     * stornato in SAP, torna tra quelli da acquisire e si può emettere un nuovo DDT.
     * Numerazione SAP: il numero torna libero; numerazione interna: il progressivo non si riusa.
     */
    public void annulla(long id, String motivo, String stornoChiave, String postazione) throws Exception {
        if (motivo == null || motivo.isBlank()) throw new DdtException("Indicare il motivo dell'annullamento.");
        Db.inTransaction(c -> {
            Ddt d = m_dao.loadForUpdate(c, id);
            if (d == null) throw new DdtException("DDT non trovato.");
            if (d.isBozza()) throw new DdtException("Una bozza non si annulla: si elimina.");
            if (d.isAnnullato()) throw new DdtException("Il DDT è già annullato.");
            d.setStato(Ddt.STATO_ANNULLATO);
            d.setAnnullatoAt(LocalDateTime.now());
            d.setMotivoAnnullamento(motivo.trim().length() > 200 ? motivo.trim().substring(0, 200) : motivo.trim());
            if (stornoChiave != null && stornoChiave.matches("\\d+/\\d{4}")) {
                String[] p = stornoChiave.split("/");
                d.setStornoMatDoc(p[0]);
                d.setStornoMatDocAnno(p[1]);
            }
            m_dao.updateTestata(c, d);
            m_dao.insertEvento(c, d.getId(), d.getVersioneCorrente(), "ANNULLAMENTO", postazione, d.getMotivoAnnullamento());
            return null;
        });
    }

    /** Elimina una bozza (mai stampata): il documento materiale torna tra quelli da acquisire. */
    public void eliminaBozza(long id) throws Exception {
        Db.inTransaction(c -> {
            Ddt d = m_dao.loadForUpdate(c, id);
            if (d == null) return null;
            if (!d.isBozza()) throw new DdtException("Solo le bozze si possono eliminare; un DDT emesso si annulla.");
            m_dao.deleteBozza(c, id);
            return null;
        });
    }

    // ------------------------------------------------------------------------
    // storico
    // ------------------------------------------------------------------------

    public List<DdtStorico.Versione> versioni(long id) throws Exception {
        try (Connection c = Db.open()) {
            return m_dao.listVersioni(c, id);
        }
    }

    public List<DdtStorico.Evento> eventi(long id) throws Exception {
        try (Connection c = Db.open()) {
            return m_dao.listEventi(c, id);
        }
    }

    // ------------------------------------------------------------------------
    // anagrafiche per le maschere
    // ------------------------------------------------------------------------

    public List<Vettore> vettoriAttivi() throws Exception {
        try (Connection c = Db.open()) {
            return m_ana.listVettori(c, true);
        }
    }

    public Vettore vettore(int id) throws Exception {
        try (Connection c = Db.open()) {
            return m_ana.getVettore(c, id);
        }
    }

    public List<String> descrizioniDecodifica(TipoDecodifica tipo) throws Exception {
        List<String> list = new ArrayList<>();
        try (Connection c = Db.open()) {
            for (var d : m_ana.listDecodifiche(c, tipo, true)) list.add(d.getDescrizione());
        }
        return list;
    }

    // ------------------------------------------------------------------------
    // util
    // ------------------------------------------------------------------------

    static String sha256(byte[] data) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Somma dei pesi netti di riga (per ricalcolare la proposta). */
    public static java.math.BigDecimal sommaPesiRighe(Ddt d) {
        java.math.BigDecimal tot = null;
        for (DdtPosizione p : d.getPosizioni()) {
            if (p.getPesoNetto() == null) continue;
            tot = tot == null ? p.getPesoNetto() : tot.add(p.getPesoNetto());
        }
        return tot;
    }
}
