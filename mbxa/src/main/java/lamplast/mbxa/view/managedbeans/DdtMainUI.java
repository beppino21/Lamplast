package lamplast.mbxa.view.managedbeans;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.defaultscreens.ModalPopup;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.elements.util.ValidValuesBinding;
import org.eclnt.jsfserver.pagebean.PageBean;

import lamplast.mbxa.logic.config.AppConfig;
import lamplast.mbxa.logic.model.DdtStorico;
import lamplast.mbxa.logic.sap.MaterialDocumentClient;
import lamplast.mbxa.logic.service.DdtService;

/**
 * Pagina principale: acquisizione di un documento materiale (per numero o dall'elenco
 * dei 301 recenti), ricerca per numero DDT, elenco dei DDT in archivio.
 */
@CCGenClass(expressionBase = "#{d.DdtMainUI}")
public class DdtMainUI extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------------
    // righe delle griglie
    // ------------------------------------------------------------------------

    public class DdtItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final DdtStorico.ElencoRiga r;

        DdtItem(DdtStorico.ElencoRiga r) { this.r = r; }

        public String getNumero() { return UiUtil.nvl(r.ddtNumero); }
        public String getData() { return UiUtil.fmt(r.ddtData); }
        public String getMatDoc() { return r.matDoc + "/" + r.matDocAnno; }
        public String getDataReg() { return UiUtil.fmt(r.dataRegistrazione); }
        public String getMittente() { return UiUtil.nvl(r.mittCodice); }
        public String getDestinatario() {
            return UiUtil.nvl(r.destCodice) + (r.destRagioneSociale == null ? "" : " - " + r.destRagioneSociale);
        }
        public String getStato() { return r.stato; }
        public String getStatoFont() {
            switch (r.stato) {
                case "BOZZA": return "color:#E65100";
                case "ANNULLATO": return "color:#B71C1C";
                default: return "color:#1B5E20";
            }
        }
        public String getVersione() { return r.versioneCorrente == 0 ? "" : String.valueOf(r.versioneCorrente); }
        /** Chiave di ordinamento per data (aaaammgg). */
        public String getDataSort() { return r.ddtData == null ? "" : r.ddtData.toString(); }

        @Override
        public void onRowExecute() {
            apriDettaglio(r.id);
        }
    }

    public class DaAcquisireItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final MaterialDocumentClient.Summary s;

        DaAcquisireItem(MaterialDocumentClient.Summary s) { this.s = s; }

        public String getMatDoc() { return s.number; }
        public String getAnno() { return s.year; }
        public String getDataReg() { return UiUtil.fmt(s.postingDate); }
        public String getDataSort() { return s.postingDate == null ? "" : s.postingDate.toString(); }
        public String getMittente() { return UiUtil.nvl(s.plant); }
        public String getDestinatario() { return UiUtil.nvl(s.receivingPlant); }
        public String getRighe() { return String.valueOf(s.itemCount); }
        public String getNota() { return s.cancelled ? "Stornato" : ""; }

        @Override
        public void onRowExecute() {
            acquisisci(s.year, s.number);
        }
    }

    // ------------------------------------------------------------------------
    // stato
    // ------------------------------------------------------------------------

    private final DdtService m_service = new DdtService();

    private String m_matDoc;
    private String m_anno = String.valueOf(LocalDate.now().getYear());
    private String m_numeroDdt;

    private Date m_filtroDa = UiUtil.toDate(LocalDate.now().minusDays(30));
    private Date m_filtroA;
    private String m_filtroStato = "";
    private String m_filtroTesto;
    private final ValidValuesBinding m_statiVvb = new ValidValuesBinding();

    private final FIXGRIDListBinding<DdtItem> m_gridDdt = new FIXGRIDListBinding<>();
    private final FIXGRIDListBinding<DaAcquisireItem> m_gridDaAcquisire = new FIXGRIDListBinding<>();

    private String m_messaggio = "";
    private String m_messaggioFont = UiUtil.FONT_OK;
    private String m_titoloDaAcquisire = "Movimenti 301 da acquisire";

    public DdtMainUI() {
        m_statiVvb.addValidValue("", "Tutti");
        m_statiVvb.addValidValue("BOZZA", "Bozza");
        m_statiVvb.addValidValue("EMESSO", "Emesso");
        m_statiVvb.addValidValue("ANNULLATO", "Annullato");
        caricaElenco();
        caricaDaAcquisire();
    }

    @Override
    public String getPageName() { return "/mbxa/ddt_main.xml"; }

    @Override
    public String getRootExpressionUsedInPage() { return "#{d.DdtMainUI}"; }

    // ------------------------------------------------------------------------
    // azioni
    // ------------------------------------------------------------------------

    /** Acquisisce il documento materiale indicato (o apre il DDT se già acquisito). */
    public void onAcquisisci(ActionEvent event) {
        acquisisci(m_anno, m_matDoc);
    }

    public void onAcquisisciSelezionato(ActionEvent event) {
        DaAcquisireItem sel = m_gridDaAcquisire.getSelectedItem();
        if (sel == null) {
            errore("Selezionare un movimento nell'elenco dei 301 da acquisire.");
            return;
        }
        acquisisci(sel.s.year, sel.s.number);
    }

    /** Ricerca per numero DDT: vale solo per i DDT già acquisiti (in SAP il numero non è leggibile). */
    public void onCercaNumero(ActionEvent event) {
        try {
            List<Long> ids = m_service.cercaPerNumero(m_numeroDdt);
            if (ids.isEmpty()) {
                errore("Nessun DDT con numero " + m_numeroDdt.trim() + " in archivio. La ricerca per numero vale solo"
                    + " per i DDT già acquisiti: per un documento nuovo usare il numero del documento materiale.");
            } else if (ids.size() == 1) {
                ok("");
                apriDettaglio(ids.get(0));
            } else {
                m_filtroDa = null;
                m_filtroA = null;
                m_filtroStato = "";
                m_filtroTesto = m_numeroDdt.trim();
                caricaElenco();
                ok("Più DDT con numero " + m_numeroDdt.trim() + " (anni diversi): selezionarlo nell'elenco.");
            }
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    public void onCerca(ActionEvent event) {
        caricaElenco();
    }

    public void onApriSelezionato(ActionEvent event) {
        DdtItem sel = m_gridDdt.getSelectedItem();
        if (sel == null) {
            errore("Selezionare un DDT nell'elenco.");
            return;
        }
        apriDettaglio(sel.r.id);
    }

    public void onAggiornaDaAcquisire(ActionEvent event) {
        caricaDaAcquisire();
    }

    public void onAnagrafiche(ActionEvent event) {
        final AnagraficheUI bean = new AnagraficheUI();
        openModalPopup(bean, "Anagrafiche", 1150, 860, new ModalPopup.IModalPopupListener() {
            @Override
            public void reactOnPopupClosedByUser() {
                closePopup(bean);
            }
        });
    }

    // ------------------------------------------------------------------------
    // logica
    // ------------------------------------------------------------------------

    private void acquisisci(String anno, String matDoc) {
        try {
            DdtService.Acquisizione a = m_service.acquisisci(anno, matDoc, UiUtil.postazione());
            if (a.giaPresente) ok("Documento già acquisito: apro il DDT.");
            else if (a.sostituisceNumero != null) avviso("Nuovo DDT in sostituzione del DDT " + a.sostituisceNumero
                + " annullato." + (a.avvisi.isEmpty() ? "" : " " + String.join(" ", a.avvisi)));
            else if (!a.avvisi.isEmpty()) avviso("DDT acquisito con avvisi: " + String.join(" ", a.avvisi));
            else ok("Documento " + matDoc.trim() + "/" + anno.trim() + " acquisito.");
            m_matDoc = null;
            caricaElenco();
            caricaDaAcquisire();
            apriDettaglio(a.id);
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    private void apriDettaglio(long id) {
        final DdtDettaglioUI bean = new DdtDettaglioUI();
        try {
            bean.prepare(id, new DdtDettaglioUI.IListener() {
                @Override
                public void reactOnChiudi() {
                    closePopup(bean);
                    caricaElenco();
                }

                @Override
                public void reactOnEliminato() {
                    closePopup(bean);
                    ok("Bozza eliminata: il documento è tornato tra quelli da acquisire.");
                    caricaElenco();
                    caricaDaAcquisire();
                }
            });
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
            return;
        }
        openModalPopup(bean, "DDT - documento materiale " + bean.getMatDocChiave(), 1280, 860,
            new ModalPopup.IModalPopupListener() {
                @Override
                public void reactOnPopupClosedByUser() {
                    closePopup(bean);
                    caricaElenco();
                }
            });
    }

    private void caricaElenco() {
        try {
            DdtStorico.Filtro f = new DdtStorico.Filtro();
            f.dataDa = UiUtil.toLocalDate(m_filtroDa);
            f.dataA = UiUtil.toLocalDate(m_filtroA);
            f.stato = m_filtroStato == null || m_filtroStato.isBlank() ? null : m_filtroStato;
            f.testo = m_filtroTesto;
            m_gridDdt.getItems().clear();
            for (DdtStorico.ElencoRiga r : m_service.elenco(f)) m_gridDdt.getItems().add(new DdtItem(r));
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    private void caricaDaAcquisire() {
        try {
            m_titoloDaAcquisire = "Movimenti 301 da acquisire (ultimi " + AppConfig.get().getListaGiorni() + " giorni)";
            m_gridDaAcquisire.getItems().clear();
            for (MaterialDocumentClient.Summary s : m_service.daAcquisire()) {
                m_gridDaAcquisire.getItems().add(new DaAcquisireItem(s));
            }
        } catch (Exception e) {
            errore("Elenco 301 da SAP non disponibile - " + UiUtil.messaggio(e));
        }
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
    // proprietà
    // ------------------------------------------------------------------------

    public String getMatDoc() { return m_matDoc; }
    public void setMatDoc(String v) { m_matDoc = v; }
    public String getAnno() { return m_anno; }
    public void setAnno(String v) { m_anno = v; }
    public String getNumeroDdt() { return m_numeroDdt; }
    public void setNumeroDdt(String v) { m_numeroDdt = v; }
    public Date getFiltroDa() { return m_filtroDa; }
    public void setFiltroDa(Date v) { m_filtroDa = v; }
    public Date getFiltroA() { return m_filtroA; }
    public void setFiltroA(Date v) { m_filtroA = v; }
    public String getFiltroStato() { return m_filtroStato; }
    public void setFiltroStato(String v) { m_filtroStato = v; }
    public String getFiltroTesto() { return m_filtroTesto; }
    public void setFiltroTesto(String v) { m_filtroTesto = v; }
    public ValidValuesBinding getStatiVvb() { return m_statiVvb; }
    public FIXGRIDListBinding<DdtItem> getGridDdt() { return m_gridDdt; }
    public FIXGRIDListBinding<DaAcquisireItem> getGridDaAcquisire() { return m_gridDaAcquisire; }
    public String getMessaggio() { return m_messaggio; }
    public String getMessaggioFont() { return m_messaggioFont; }
    public String getTitoloDaAcquisire() { return m_titoloDaAcquisire; }
    public String getModoNumerazione() {
        try {
            return "Numerazione: " + AppConfig.get().getModoNumerazione();
        } catch (Exception e) {
            return "Configurazione non disponibile: " + e.getMessage();
        }
    }
}