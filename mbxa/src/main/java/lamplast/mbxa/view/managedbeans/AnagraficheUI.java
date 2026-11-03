package lamplast.mbxa.view.managedbeans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.defaultscreens.OKPopup;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.elements.util.ValidValuesBinding;
import org.eclnt.jsfserver.pagebean.PageBean;

import lamplast.mbxa.logic.model.Anagrafiche.Decodifica;
import lamplast.mbxa.logic.model.Anagrafiche.Stabilimento;
import lamplast.mbxa.logic.model.Anagrafiche.TipoDecodifica;
import lamplast.mbxa.logic.model.Anagrafiche.Vettore;
import lamplast.mbxa.logic.service.AnagraficheService;

/**
 * Manutenzione delle anagrafiche accessorie (popup): stabilimenti, vettori,
 * causali / porto / aspetto dei beni. Griglie modificabili: "Aggiungi" crea una riga,
 * "Salva" registra tutte le righe, "Elimina" la riga selezionata.
 */
@CCGenClass(expressionBase = "#{d.AnagraficheUI}")
public class AnagraficheUI extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------------
    // righe
    // ------------------------------------------------------------------------

    public class StabilimentoItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        final Stabilimento s;
        final boolean nuovo;

        StabilimentoItem(Stabilimento s, boolean nuovo) {
            this.s = s;
            this.nuovo = nuovo;
        }

        public boolean isNuovo() { return nuovo; }
        public String getCodice() { return s.getCodice(); }
        public void setCodice(String v) { if (nuovo) s.setCodice(v); }
        public String getRagioneSociale() { return s.getRagioneSociale(); }
        public void setRagioneSociale(String v) { s.setRagioneSociale(v); }
        public String getIndirizzo() { return s.getIndirizzo(); }
        public void setIndirizzo(String v) { s.setIndirizzo(v); }
        public String getCap() { return s.getCap(); }
        public void setCap(String v) { s.setCap(v); }
        public String getCitta() { return s.getCitta(); }
        public void setCitta(String v) { s.setCitta(v); }
        public String getProvincia() { return s.getProvincia(); }
        public void setProvincia(String v) { s.setProvincia(v); }
        public String getNazione() { return s.getNazione(); }
        public void setNazione(String v) { s.setNazione(v); }
        public String getPartitaIva() { return s.getPartitaIva(); }
        public void setPartitaIva(String v) { s.setPartitaIva(v); }
        public String getCodiceFiscale() { return s.getCodiceFiscale(); }
        public void setCodiceFiscale(String v) { s.setCodiceFiscale(v); }
        public boolean getAttivo() { return s.isAttivo(); }
        public void setAttivo(boolean v) { s.setAttivo(v); }
    }

    public class VettoreItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        final Vettore v;

        VettoreItem(Vettore v) { this.v = v; }

        public String getRagioneSociale() { return v.getRagioneSociale(); }
        public void setRagioneSociale(String x) { v.setRagioneSociale(x); }
        public String getIndirizzo() { return v.getIndirizzo(); }
        public void setIndirizzo(String x) { v.setIndirizzo(x); }
        public String getCap() { return v.getCap(); }
        public void setCap(String x) { v.setCap(x); }
        public String getCitta() { return v.getCitta(); }
        public void setCitta(String x) { v.setCitta(x); }
        public String getProvincia() { return v.getProvincia(); }
        public void setProvincia(String x) { v.setProvincia(x); }
        public String getPartitaIva() { return v.getPartitaIva(); }
        public void setPartitaIva(String x) { v.setPartitaIva(x); }
        public String getIscrizioneAlbo() { return v.getIscrizioneAlbo(); }
        public void setIscrizioneAlbo(String x) { v.setIscrizioneAlbo(x); }
        public boolean getPredefinito() { return v.isPredefinito(); }
        public void setPredefinito(boolean x) { v.setPredefinito(x); }
        public boolean getAttivo() { return v.isAttivo(); }
        public void setAttivo(boolean x) { v.setAttivo(x); }
    }

    public class DecodificaItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        final Decodifica d;
        final boolean nuovo;

        DecodificaItem(Decodifica d, boolean nuovo) {
            this.d = d;
            this.nuovo = nuovo;
        }

        public boolean isNuovo() { return nuovo; }
        public String getCodice() { return d.getCodice(); }
        public void setCodice(String v) { if (nuovo) d.setCodice(v); }
        public String getDescrizione() { return d.getDescrizione(); }
        public void setDescrizione(String v) { d.setDescrizione(v); }
        public boolean getPredefinito() { return d.isPredefinito(); }
        public void setPredefinito(boolean v) { d.setPredefinito(v); }
        public boolean getAttivo() { return d.isAttivo(); }
        public void setAttivo(boolean v) { d.setAttivo(v); }
    }

    // ------------------------------------------------------------------------
    // stato
    // ------------------------------------------------------------------------

    private final AnagraficheService m_service = new AnagraficheService();
    private final FIXGRIDListBinding<StabilimentoItem> m_gridStabilimenti = new FIXGRIDListBinding<>();
    private final FIXGRIDListBinding<VettoreItem> m_gridVettori = new FIXGRIDListBinding<>();
    private final FIXGRIDListBinding<DecodificaItem> m_gridDecodifiche = new FIXGRIDListBinding<>();
    private final ValidValuesBinding m_tipiVvb = new ValidValuesBinding();
    private String m_tipo = TipoDecodifica.CAUSALE.name();

    private String m_messaggio = "";
    private String m_messaggioFont = UiUtil.FONT_OK;

    public AnagraficheUI() {
        for (TipoDecodifica t : TipoDecodifica.values()) m_tipiVvb.addValidValue(t.name(), t.getTitolo());
        caricaStabilimenti();
        caricaVettori();
        caricaDecodifiche();
    }

    @Override
    public String getPageName() { return "/mbxa/anagrafiche.xml"; }

    @Override
    public String getRootExpressionUsedInPage() { return "#{d.AnagraficheUI}"; }

    // ------------------------------------------------------------------------
    // stabilimenti
    // ------------------------------------------------------------------------

    public void onAggiungiStabilimento(ActionEvent event) {
        StabilimentoItem it = new StabilimentoItem(new Stabilimento(), true);
        m_gridStabilimenti.getItems().add(it);
        m_gridStabilimenti.selectAndFocusItem(it);
    }

    public void onSalvaStabilimenti(ActionEvent event) {
        List<String> errori = new ArrayList<>();
        for (StabilimentoItem it : m_gridStabilimenti.getItems()) {
            try {
                m_service.salvaStabilimento(it.s);
            } catch (Exception e) {
                errori.add(UiUtil.messaggio(e));
            }
        }
        esito(errori, "Stabilimenti salvati.");
        if (errori.isEmpty()) caricaStabilimenti();
    }

    public void onEliminaStabilimento(ActionEvent event) {
        StabilimentoItem it = m_gridStabilimenti.getSelectedItem();
        if (it == null) {
            errore("Selezionare uno stabilimento.");
            return;
        }
        try {
            if (!it.nuovo) m_service.eliminaStabilimento(it.s.getCodice());
            caricaStabilimenti();
            ok("Stabilimento eliminato. I DDT già compilati conservano la loro copia dei dati.");
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    // ------------------------------------------------------------------------
    // vettori
    // ------------------------------------------------------------------------

    public void onAggiungiVettore(ActionEvent event) {
        VettoreItem it = new VettoreItem(new Vettore());
        m_gridVettori.getItems().add(it);
        m_gridVettori.selectAndFocusItem(it);
    }

    public void onSalvaVettori(ActionEvent event) {
        List<String> errori = new ArrayList<>();
        int predefiniti = 0;
        for (VettoreItem it : m_gridVettori.getItems()) if (it.v.isPredefinito()) predefiniti++;
        if (predefiniti > 1) {
            errore("Un solo vettore può essere predefinito.");
            return;
        }
        for (VettoreItem it : m_gridVettori.getItems()) {
            try {
                m_service.salvaVettore(it.v);
            } catch (Exception e) {
                errori.add(UiUtil.messaggio(e));
            }
        }
        esito(errori, "Vettori salvati.");
        if (errori.isEmpty()) caricaVettori();
    }

    public void onEliminaVettore(ActionEvent event) {
        VettoreItem it = m_gridVettori.getSelectedItem();
        if (it == null) {
            errore("Selezionare un vettore.");
            return;
        }
        try {
            boolean cancellato = it.v.getId() == null || m_service.eliminaVettore(it.v.getId());
            caricaVettori();
            ok(cancellato ? "Vettore eliminato." : "Vettore già usato in alcuni DDT: è stato disattivato.");
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    // ------------------------------------------------------------------------
    // decodifiche
    // ------------------------------------------------------------------------

    public void onAggiungiDecodifica(ActionEvent event) {
        DecodificaItem it = new DecodificaItem(new Decodifica(), true);
        m_gridDecodifiche.getItems().add(it);
        m_gridDecodifiche.selectAndFocusItem(it);
    }

    public void onSalvaDecodifiche(ActionEvent event) {
        List<String> errori = new ArrayList<>();
        int predefiniti = 0;
        for (DecodificaItem it : m_gridDecodifiche.getItems()) if (it.d.isPredefinito()) predefiniti++;
        if (predefiniti > 1) {
            errore("Una sola voce può essere predefinita.");
            return;
        }
        for (DecodificaItem it : m_gridDecodifiche.getItems()) {
            try {
                m_service.salvaDecodifica(tipo(), it.d);
            } catch (Exception e) {
                errori.add(UiUtil.messaggio(e));
            }
        }
        esito(errori, "Voci salvate.");
        if (errori.isEmpty()) caricaDecodifiche();
    }

    public void onEliminaDecodifica(ActionEvent event) {
        DecodificaItem it = m_gridDecodifiche.getSelectedItem();
        if (it == null) {
            errore("Selezionare una voce.");
            return;
        }
        try {
            if (!it.nuovo) m_service.eliminaDecodifica(tipo(), it.d.getCodice());
            caricaDecodifiche();
            ok("Voce eliminata.");
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    // ------------------------------------------------------------------------
    // caricamento
    // ------------------------------------------------------------------------

    private void caricaStabilimenti() {
        try {
            m_gridStabilimenti.getItems().clear();
            for (Stabilimento s : m_service.stabilimenti()) m_gridStabilimenti.getItems().add(new StabilimentoItem(s, false));
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    private void caricaVettori() {
        try {
            m_gridVettori.getItems().clear();
            for (Vettore v : m_service.vettori()) m_gridVettori.getItems().add(new VettoreItem(v));
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    private void caricaDecodifiche() {
        try {
            m_gridDecodifiche.getItems().clear();
            for (Decodifica d : m_service.decodifiche(tipo())) m_gridDecodifiche.getItems().add(new DecodificaItem(d, false));
        } catch (Exception e) {
            errore(UiUtil.messaggio(e));
        }
    }

    private TipoDecodifica tipo() {
        return TipoDecodifica.valueOf(m_tipo);
    }

    /**
     * Esito di un salvataggio. In caso di errore il messaggio va anche in un popup:
     * le righe non salvate non devono passare inosservate.
     */
    private void esito(List<String> errori, String okMsg) {
        if (errori.isEmpty()) {
            ok(okMsg);
            return;
        }
        errore("Salvataggio non completato: " + String.join(" ", errori));
        OKPopup.createInstance("Salvataggio non completato",
            "Le righe seguenti NON sono state salvate:\n\n- " + String.join("\n- ", errori)
                + "\n\nCorreggerle e premere di nuovo Salva.");
    }

    private void ok(String m) {
        m_messaggio = m;
        m_messaggioFont = UiUtil.FONT_OK;
    }

    private void errore(String m) {
        m_messaggio = m;
        m_messaggioFont = UiUtil.FONT_ERR;
    }

    // ------------------------------------------------------------------------
    // proprietà
    // ------------------------------------------------------------------------

    public FIXGRIDListBinding<StabilimentoItem> getGridStabilimenti() { return m_gridStabilimenti; }
    public FIXGRIDListBinding<VettoreItem> getGridVettori() { return m_gridVettori; }
    public FIXGRIDListBinding<DecodificaItem> getGridDecodifiche() { return m_gridDecodifiche; }
    public ValidValuesBinding getTipiVvb() { return m_tipiVvb; }
    public String getTipo() { return m_tipo; }

    /** Cambio tabella dalla tendina: ricarica la griglia. */
    public void setTipo(String v) {
        if (v == null || v.isBlank() || v.equals(m_tipo)) return;
        m_tipo = v;
        caricaDecodifiche();
    }

    public String getMessaggio() { return m_messaggio; }
    public String getMessaggioFont() { return m_messaggioFont; }
}
