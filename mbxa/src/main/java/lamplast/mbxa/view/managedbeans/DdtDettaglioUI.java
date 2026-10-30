package lamplast.mbxa.view.managedbeans;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.bufferedcontent.BufferedContentMgr;
import org.eclnt.jsfserver.bufferedcontent.DefaultBufferedContent;
import org.eclnt.jsfserver.defaultscreens.YESNOPopup;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.elements.util.Trigger;
import org.eclnt.jsfserver.elements.util.ValidValuesBinding;
import org.eclnt.jsfserver.pagebean.PageBean;
import org.eclnt.jsfserver.util.tempfile.TempFileManager;
import org.eclnt.util.valuemgmt.ValueManager;

import lamplast.mbxa.logic.config.AppConfig;
import lamplast.mbxa.logic.config.AppConfig.ModoNumerazione;
import lamplast.mbxa.logic.model.Anagrafiche.TipoDecodifica;
import lamplast.mbxa.logic.model.Anagrafiche.Vettore;
import lamplast.mbxa.logic.model.Ddt;
import lamplast.mbxa.logic.model.DdtPosizione;
import lamplast.mbxa.logic.model.DdtStorico;
import lamplast.mbxa.logic.pdf.DdtPdfBuilder;
import lamplast.mbxa.logic.service.DdtComposer;
import lamplast.mbxa.logic.service.DdtException;
import lamplast.mbxa.logic.service.DdtService;

/**
 * Dettaglio DDT (popup): completamento dei dati, stampa/ristampa con anteprima,
 * storico delle versioni e degli eventi, annullamento.
 */
@CCGenClass(expressionBase = "#{d.DdtDettaglioUI}")
public class DdtDettaglioUI extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Notifiche verso la pagina che ha aperto il popup. */
    public interface IListener extends Serializable {
        void reactOnChiudi();
        void reactOnEliminato();
    }

    // ------------------------------------------------------------------------
    // righe delle griglie
    // ------------------------------------------------------------------------

    public class PosizioneItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final DdtPosizione p;

        PosizioneItem(DdtPosizione p) { this.p = p; }

        public String getMateriale() { return p.getMateriale(); }
        public String getDescrizione() { return UiUtil.nvl(p.getDescrizione()); }
        public String getLotto() { return UiUtil.nvl(p.getLotto()); }
        public String getUm() { return p.getUm(); }
        public String getQuantita() { return DdtPdfBuilder.numero(p.getQuantita()); }
        public String getPeso() { return DdtPdfBuilder.numero(p.getPesoNetto()); }
    }

    public class VersioneItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final DdtStorico.Versione v;

        VersioneItem(DdtStorico.Versione v) { this.v = v; }

        public String getVersione() { return String.valueOf(v.versione); }
        public String getCreata() { return v.creataAt == null ? "" : v.creataAt.format(UiUtil.DT); }
        public String getHash() { return v.sha256 == null ? "" : v.sha256.substring(0, 12) + "..."; }

        /** Doppio clic: mostra il PDF di quella versione (consultazione, non stampa). */
        @Override
        public void onRowExecute() {
            try {
                mostraPdf(m_service.pdfVersione(m_ddt.getId(), v.versione), false);
                ok("Visualizzata la versione " + v.versione + ".");
            } catch (Exception e) {
                errore(UiUtil.messaggio(e));
            }
        }
    }

    public class EventoItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final DdtStorico.Evento e;

        EventoItem(DdtStorico.Evento e) { this.e = e; }

        public String getQuando() { return e.eventoAt == null ? "" : e.eventoAt.format(UiUtil.DT); }
        public String getTipo() { return e.tipo; }
        public String getVersione() { return e.versione == null || e.versione == 0 ? "" : String.valueOf(e.versione); }
        public String getPostazione() { return UiUtil.nvl(e.postazione); }
        public String getNote() { return UiUtil.nvl(e.note); }
    }

    /** PDF servito al browser per la nuova scheda di stampa. */
    public static class PdfContent extends DefaultBufferedContent {
        private static final long serialVersionUID = 1L;
        private final byte[] m_bytes;

        PdfContent(byte[] bytes) {
            m_bytes = bytes;
            setExtension("pdf");
        }

        @Override
        public byte[] getContent() { return m_bytes; }

        @Override
        public String getContentType() { return "application/pdf"; }
    }

    // ------------------------------------------------------------------------
    // stato
    // ------------------------------------------------------------------------

    private final DdtService m_service = new DdtService();
    private IListener m_listener;
    /** DDT vuoto finché non arriva prepare(): la pagina aperta da sola non va in errore. */
    private Ddt m_ddt = new Ddt();
    private boolean m_modoManuale;

    private final ValidValuesBinding m_vettoriVvb = new ValidValuesBinding();
    private final ValidValuesBinding m_causaliVvb = new ValidValuesBinding();
    private final ValidValuesBinding m_portiVvb = new ValidValuesBinding();
    private final ValidValuesBinding m_aspettiVvb = new ValidValuesBinding();

    private final FIXGRIDListBinding<PosizioneItem> m_gridPosizioni = new FIXGRIDListBinding<>();
    private final FIXGRIDListBinding<VersioneItem> m_gridVersioni = new FIXGRIDListBinding<>();
    private final FIXGRIDListBinding<EventoItem> m_gridEventi = new FIXGRIDListBinding<>();

    /** PDF mostrato in anteprima. */
    private String m_pdf;
    /** PDF aperto in una scheda del browser per la stampa (t:jshowurl). */
    private PdfContent m_printContent;
    private String m_printUrl;
    private final Trigger m_printTrigger = new Trigger();
    private String m_downloadUrl;

    private String m_inizioOra;
    private String m_sostituisceNumero;
    private String m_motivoAnnullamento;
    private String m_stornoRilevato;

    private String m_messaggio = "";
    private String m_messaggioFont = UiUtil.FONT_OK;

    public DdtDettaglioUI() {
        errore("Pagina aperta direttamente: aprire il DDT dalla pagina principale (ddt_main).");
    }

    @Override
    public String getPageName() { return "/mbxa/ddt_dettaglio.xml"; }

    @Override
    public String getRootExpressionUsedInPage() { return "#{d.DdtDettaglioUI}"; }

    /** Carica il DDT; mostra l'ultima versione stampata e controlla eventuali storni in SAP. */
    public void prepare(long id, IListener listener) throws Exception {
        m_listener = listener;
        m_messaggio = "";
        m_modoManuale = AppConfig.get().getModoNumerazione() == ModoNumerazione.MANUALE;
        ricarica(id);
        caricaTendine();
        if (m_ddt.isStampato()) {
            mostraPdf(m_service.pdfVersione(id, m_ddt.getVersioneCorrente()), false);
        }
        if (m_ddt.isEmesso()) controllaStorno();
        if (m_messaggio.isEmpty() && m_ddt.isBozza()) {
            ok(m_modoManuale
                ? "Completare i dati e inserire il numero DDT letto in MIGO, poi Stampa per emettere."
                : "Completare i dati, poi Stampa: il numero DDT viene assegnato all'emissione.");
        }
    }

    // ------------------------------------------------------------------------
    // azioni
    // ------------------------------------------------------------------------

    public void onSalva(ActionEvent event) {
        if (!aperto()) return;
        try {
            salva();
            ok(m_ddt.isEmesso() && m_service.modificatoDopoStampa(m_ddt.getId())
                ? "Salvato. I dati differiscono dall'ultima stampa: alla prossima stampa nascerà la versione "
                    + (m_ddt.getVersioneCorrente() + 1) + "."
                : "Salvato.");
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    /** Salva e stampa: la prima volta emette il DDT; poi nuova versione solo se i dati sono cambiati. */
    public void onStampa(ActionEvent event) {
        if (!aperto()) return;
        try {
            if (!m_ddt.isAnnullato()) salva();
            stampa(false);
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    public void onRistampa(ActionEvent event) {
        if (!aperto()) return;
        try {
            DdtService.Stampa s = m_service.ristampa(m_ddt.getId(), UiUtil.postazione());
            ricarica(m_ddt.getId());
            mostraPdf(s.pdf, true);
            ok("Ristampa della versione " + s.versione + " (documento identico all'originale).");
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    public void onVerificaStorno(ActionEvent event) {
        if (!aperto()) return;
        try {
            controllaStorno();
            if (m_stornoRilevato == null) ok("In SAP il documento materiale non risulta stornato.");
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    /** Annullamento per storno rilevato in SAP: motivo precompilato, conferma esplicita. */
    public void onAnnullaPerStorno(ActionEvent event) {
        if (m_stornoRilevato == null) return;
        m_motivoAnnullamento = "Storno del documento materiale con " + m_stornoRilevato;
        onAnnulla(event);
    }

    public void onAnnulla(ActionEvent event) {
        if (!aperto()) return;
        if (m_motivoAnnullamento == null || m_motivoAnnullamento.isBlank()) {
            errore("Indicare il motivo dell'annullamento.");
            return;
        }
        YESNOPopup.createInstance("Annullamento DDT",
            "Annullare il DDT " + UiUtil.nvl(m_ddt.getDdtNumero()) + "? I PDF emessi restano consultabili."
                + " Se il documento materiale non è stornato in SAP, tornerà tra quelli da acquisire per emettere"
                + " un nuovo DDT. L'operazione non si può annullare.",
            new YESNOPopup.IYesNoListener() {
                @Override
                public void reactOnYes() {
                    try {
                        String storno = m_stornoRilevato != null && m_stornoRilevato.matches("\\d+/\\d{4}")
                            ? m_stornoRilevato : null;
                        m_service.annulla(m_ddt.getId(), m_motivoAnnullamento, storno, UiUtil.postazione());
                        ricarica(m_ddt.getId());
                        m_stornoRilevato = null;
                        ok("DDT annullato. Se serve un nuovo DDT, riacquisire il documento materiale dalla pagina principale.");
                    } catch (Exception e) {
                        errore(UiUtil.messaggio(e));
                    }
                }

                @Override
                public void reactOnNo() {}
            });
    }

    public void onEliminaBozza(ActionEvent event) {
        if (!aperto()) return;
        YESNOPopup.createInstance("Eliminazione bozza",
            "Eliminare la bozza? Il documento materiale tornerà tra quelli da acquisire.",
            new YESNOPopup.IYesNoListener() {
                @Override
                public void reactOnYes() {
                    try {
                        m_service.eliminaBozza(m_ddt.getId());
                        if (m_listener != null) m_listener.reactOnEliminato();
                    } catch (Exception e) {
                        errore(UiUtil.messaggio(e));
                    }
                }

                @Override
                public void reactOnNo() {}
            });
    }

    public void onRileggiAnagrafiche(ActionEvent event) {
        if (!aperto()) return;
        try {
            salva();
            m_service.rileggiAnagrafiche(m_ddt.getId());
            ricarica(m_ddt.getId());
            ok("Mittente e destinatario ripresi dall'anagrafica stabilimenti.");
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    /** Ripropone il peso netto come somma dei pesi di riga calcolati all'acquisizione. */
    public void onRicalcolaPeso(ActionEvent event) {
        if (!aperto()) return;
        BigDecimal tot = DdtService.sommaPesiRighe(m_ddt);
        if (tot == null) {
            errore("Nessun peso di riga disponibile dall'anagrafica materiali.");
            return;
        }
        m_ddt.setPesoNetto(tot);
        ok("Peso netto riproposto dalla somma delle righe: ricordarsi di salvare.");
    }

    public void onChiudi(ActionEvent event) {
        if (m_listener != null) m_listener.reactOnChiudi();
    }

    // ------------------------------------------------------------------------
    // logica
    // ------------------------------------------------------------------------

    /** False (con messaggio) se la pagina non è stata aperta su un DDT. */
    private boolean aperto() {
        if (m_ddt.getId() != null) return true;
        errore("Nessun DDT aperto: aprire il DDT dalla pagina principale (ddt_main).");
        return false;
    }

    private void stampa(boolean sequenzaConfermata) throws Exception {
        try {
            DdtService.Stampa s = m_service.stampa(m_ddt.getId(), UiUtil.postazione(), sequenzaConfermata);
            ricarica(m_ddt.getId());
            mostraPdf(s.pdf, true);
            if (s.ristampa) ok("Dati invariati dall'ultima stampa: ristampa della versione " + s.versione + ".");
            else if (s.versione == 1) ok("DDT " + s.numeroDdt + " emesso.");
            else ok("Stampata la versione " + s.versione + " del DDT " + s.numeroDdt + ".");
        } catch (DdtException.SequenzaDaConfermare q) {
            YESNOPopup.createInstance("Numero DDT fuori sequenza", q.getMessage() + "\n\nEmettere comunque?",
                new YESNOPopup.IYesNoListener() {
                    @Override
                    public void reactOnYes() {
                        try {
                            stampa(true);
                        } catch (Exception e) {
                            errore(UiUtil.messaggio(e));
                        }
                    }

                    @Override
                    public void reactOnNo() {
                        avviso("Emissione sospesa: controllare il numero DDT.");
                    }
                });
        }
    }

    private void salva() throws Exception {
        if (m_ddt.isAnnullato()) return;
        m_ddt.setInizioTrasporto(componiInizioTrasporto());
        m_service.salva(m_ddt, UiUtil.postazione());
        ricarica(m_ddt.getId());
    }

    private LocalDateTime componiInizioTrasporto() {
        LocalDate giorno = m_ddt.getInizioTrasporto() == null ? null : m_ddt.getInizioTrasporto().toLocalDate();
        if (giorno == null) {
            if (m_inizioOra != null && !m_inizioOra.isBlank()) {
                throw new DdtException("Indicare anche la data di inizio trasporto.");
            }
            return null;
        }
        LocalTime ora = LocalTime.MIDNIGHT;
        if (m_inizioOra != null && !m_inizioOra.isBlank()) {
            try {
                String s = m_inizioOra.trim().replace('.', ':');
                if (s.matches("\\d{1,2}")) s = s + ":00";
                if (s.matches("\\d:\\d{2}")) s = "0" + s;
                ora = LocalTime.parse(s);
            } catch (DateTimeParseException e) {
                throw new DdtException("Ora di inizio trasporto non valida: \"" + m_inizioOra + "\" (formato HH:MM).");
            }
        }
        return LocalDateTime.of(giorno, ora);
    }

    private void ricarica(long id) throws Exception {
        m_ddt = m_service.carica(id);
        m_sostituisceNumero = m_ddt.getSostituisceId() == null ? null
            : UiUtil.nvl(m_service.carica(m_ddt.getSostituisceId()).getDdtNumero());
        m_inizioOra = m_ddt.getInizioTrasporto() == null ? null : m_ddt.getInizioTrasporto().toLocalTime().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        m_gridPosizioni.getItems().clear();
        for (DdtPosizione p : m_ddt.getPosizioni()) m_gridPosizioni.getItems().add(new PosizioneItem(p));
        m_gridVersioni.getItems().clear();
        for (DdtStorico.Versione v : m_service.versioni(id)) m_gridVersioni.getItems().add(new VersioneItem(v));
        m_gridEventi.getItems().clear();
        for (DdtStorico.Evento e : m_service.eventi(id)) m_gridEventi.getItems().add(new EventoItem(e));
    }

    private void caricaTendine() throws Exception {
        m_vettoriVvb.addValidValue("", "Mittente (mezzo proprio)");
        for (Vettore v : m_service.vettoriAttivi()) {
            m_vettoriVvb.addValidValue(String.valueOf(v.getId()), v.getRagioneSociale());
        }
        riempi(m_causaliVvb, TipoDecodifica.CAUSALE, m_ddt.getCausale());
        riempi(m_portiVvb, TipoDecodifica.PORTO, m_ddt.getPorto());
        riempi(m_aspettiVvb, TipoDecodifica.ASPETTO, m_ddt.getAspettoBeni());
    }

    /**
     * Voci attive della tabella, più il valore già presente sul DDT se nel frattempo
     * è stato disattivato o modificato in anagrafica (altrimenti la tendina lo nasconderebbe).
     */
    private void riempi(ValidValuesBinding vvb, TipoDecodifica tipo, String attuale) throws Exception {
        java.util.Set<String> presenti = new java.util.LinkedHashSet<>();
        presenti.add("");
        presenti.addAll(m_service.descrizioniDecodifica(tipo));
        if (attuale != null && !attuale.isBlank()) presenti.add(attuale);
        for (String s : presenti) vvb.addValidValue(s, s);
    }

    private void controllaStorno() throws Exception {
        Optional<String> storno = m_service.verificaStorno(m_ddt);
        m_stornoRilevato = storno.orElse(null);
        if (m_stornoRilevato != null) {
            avviso("Attenzione: in SAP il documento materiale risulta stornato (" + m_stornoRilevato
                + "). Valutare l'annullamento del DDT.");
        }
    }

    private void mostraPdf(byte[] pdf, boolean stampa) {
        m_pdf = ValueManager.encodeHexString(pdf);
        if (stampa) {
            // nuovo contenuto (e quindi nuovo URL) a ogni stampa: il browser non mostra una copia in cache
            if (m_printContent != null) BufferedContentMgr.remove(m_printContent);
            m_printContent = new PdfContent(pdf);
            BufferedContentMgr.add(m_printContent);
            m_printUrl = m_printContent.getURL();
        }
        try {
            String nome = "DDT_" + (m_ddt.getDdtNumero() == null ? m_ddt.getMatDoc() : m_ddt.getDdtNumero()
                .replaceAll("[^A-Za-z0-9]", "_")) + ".pdf";
            m_downloadUrl = TempFileManager.saveTempFile(nome, "application/pdf", pdf);
        } catch (Throwable t) {
            m_downloadUrl = null;
        }
        if (stampa) m_printTrigger.trigger();
    }

    private void ok(String m) {
        m_messaggio = m;
        m_messaggioFont = UiUtil.FONT_OK;
    }

    private void avviso(String m) {
        m_messaggio = m;
        m_messaggioFont = UiUtil.FONT_WARN;
    }

    private void errore(String m) {
        m_messaggio = m;
        m_messaggioFont = UiUtil.FONT_ERR;
    }

    // ------------------------------------------------------------------------
    // proprietà per la pagina
    // ------------------------------------------------------------------------

    public Ddt getDdt() { return m_ddt; }
    public String getMatDocChiave() { return m_ddt == null ? "" : m_ddt.getMatDocChiave(); }

    public String getStatoTesto() {
        String s = m_ddt.getStato();
        if (m_ddt.isStampato()) s += " - versione " + m_ddt.getVersioneCorrente();
        if (m_ddt.isAnnullato() && m_ddt.getMotivoAnnullamento() != null) s += " (" + m_ddt.getMotivoAnnullamento() + ")";
        if (m_sostituisceNumero != null) s += " - sostituisce il DDT " + m_sostituisceNumero + " annullato";
        return s;
    }

    public String getStatoFont() {
        if (m_ddt.isBozza()) return "weight:bold;color:#E65100";
        if (m_ddt.isAnnullato()) return "weight:bold;color:#B71C1C";
        return "weight:bold;color:#1B5E20";
    }

    public String getDataRegistrazione() { return UiUtil.fmt(m_ddt.getDataRegistrazione()); }
    public String getDataDocumento() { return UiUtil.fmt(m_ddt.getDataDocumento()); }

    /** Bozza: si modificano anche numero, data DDT e indirizzi. */
    public boolean isBozza() { return m_ddt.isBozza(); }
    /** Bozza o emesso: si modificano i dati accessori. */
    public boolean isModificabile() { return !m_ddt.isAnnullato(); }
    public boolean isNumeroEditabile() { return m_ddt.isBozza() && m_modoManuale; }
    public boolean isStampato() { return m_ddt.isStampato(); }
    public boolean isAnnullabile() { return m_ddt.isEmesso(); }
    public boolean isStornoRilevato() { return m_stornoRilevato != null && m_ddt.isEmesso(); }

    public String getNumeroEtichetta() {
        if (!m_ddt.isBozza()) return "Numero DDT";
        return m_modoManuale ? "Numero DDT (da MIGO)" : "Numero DDT (assegnato all'emissione)";
    }

    public String getDdtNumero() { return m_ddt.getDdtNumero(); }
    public void setDdtNumero(String v) { if (isNumeroEditabile()) m_ddt.setDdtNumero(v); }

    public Date getDdtData() { return UiUtil.toDate(m_ddt.getDdtData()); }
    public void setDdtData(Date v) { if (m_ddt.isBozza()) m_ddt.setDdtData(UiUtil.toLocalDate(v)); }

    public Date getInizioData() {
        return m_ddt.getInizioTrasporto() == null ? null : UiUtil.toDate(m_ddt.getInizioTrasporto().toLocalDate());
    }

    public void setInizioData(Date v) {
        LocalDate d = UiUtil.toLocalDate(v);
        m_ddt.setInizioTrasporto(d == null ? null : d.atStartOfDay());
    }

    public String getInizioOra() { return m_inizioOra; }
    public void setInizioOra(String v) { m_inizioOra = v; }

    public Double getPesoLordo() { return m_ddt.getPesoLordo() == null ? null : m_ddt.getPesoLordo().doubleValue(); }
    public void setPesoLordo(Double v) { m_ddt.setPesoLordo(v == null ? null : BigDecimal.valueOf(v)); }
    public Double getPesoNetto() { return m_ddt.getPesoNetto() == null ? null : m_ddt.getPesoNetto().doubleValue(); }
    public void setPesoNetto(Double v) { m_ddt.setPesoNetto(v == null ? null : BigDecimal.valueOf(v)); }
    public Integer getColli() { return m_ddt.getColli(); }
    public void setColli(Integer v) { m_ddt.setColli(v); }

    /** Vettore scelto dalla tendina: ne copia subito i dati nel DDT (modificabili). */
    public String getVettoreSel() { return m_ddt.getVettoreId() == null ? "" : String.valueOf(m_ddt.getVettoreId()); }

    public void setVettoreSel(String v) {
        String attuale = getVettoreSel();
        String nuovo = v == null ? "" : v.trim();
        if (nuovo.equals(attuale)) return;
        try {
            DdtComposer.copiaVettore(m_ddt, nuovo.isEmpty() ? null : m_service.vettore(Integer.parseInt(nuovo)));
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    public ValidValuesBinding getVettoriVvb() { return m_vettoriVvb; }
    public ValidValuesBinding getCausaliVvb() { return m_causaliVvb; }
    public ValidValuesBinding getPortiVvb() { return m_portiVvb; }
    public ValidValuesBinding getAspettiVvb() { return m_aspettiVvb; }

    public FIXGRIDListBinding<PosizioneItem> getGridPosizioni() { return m_gridPosizioni; }
    public FIXGRIDListBinding<VersioneItem> getGridVersioni() { return m_gridVersioni; }
    public FIXGRIDListBinding<EventoItem> getGridEventi() { return m_gridEventi; }

    public String getPdf() { return m_pdf; }
    public String getPrintUrl() { return m_printUrl; }
    public Trigger getPrintTrigger() { return m_printTrigger; }
    public String getDownloadUrl() { return m_downloadUrl; }
    public boolean isDownloadDisponibile() { return m_downloadUrl != null; }

    public String getMotivoAnnullamento() { return m_motivoAnnullamento; }
    public void setMotivoAnnullamento(String v) { m_motivoAnnullamento = v; }

    public String getMessaggio() { return m_messaggio; }
    public String getMessaggioFont() { return m_messaggioFont; }
}
