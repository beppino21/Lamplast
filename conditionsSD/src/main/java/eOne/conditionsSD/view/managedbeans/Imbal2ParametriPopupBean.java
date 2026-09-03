package eOne.conditionsSD.view.managedbeans;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.pagebean.PageBean;

import eOne.conditionsSD.s4client.Imbal2ReadClient;
import eOne.conditionsSD.s4client.Imbal2WriteClient;
import eOne.conditionsSD.s4client.S4Config;
import eOne.conditionsSD.s4client.S4HttpClient;

/**
 * Popup di gestione "Parametri Imballo 2" (ZIMBAL_2 — caratteristiche
 * imballo): elenco libero dei codici esistenti, con Nuovo/Modifica/Salva/
 * Elimina. Stesso pattern di Imbal1ParametriPopupBean, chiave cod_imballo2.
 */
@CCGenClass(expressionBase = "#{d.Imbal2ParametriPopupBean}")
public class Imbal2ParametriPopupBean extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;

    public interface IListener {
        void reactOnChanged();
        void reactOnClosed();
    }

    public class GridItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Imbal2ReadClient.Item item;
        GridItem(Imbal2ReadClient.Item item) { this.item = item; }
        public String getCodImballo2() { return item.code; }
        public String getDescrIt()     { return item.descrIt; }
        public String getDescrEn()     { return item.descrEn; }
    }

    private final FIXGRIDListBinding<GridItem> m_grid = new FIXGRIDListBinding<>();

    private boolean m_creatingNew;
    private String  m_codImballo2;
    private String  m_descrIt;
    private String  m_descrEn;

    private String  m_statusMessage = "";
    private boolean m_statusIsError;

    private IListener m_listener;

    public Imbal2ParametriPopupBean() { }

    public String getPageName() { return "/conditionssd/listino/imbal2_parametri_popup.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.Imbal2ParametriPopupBean}"; }

    public void prepare(IListener listener) {
        m_listener = listener;
        reload();
        resetForm();
    }

    private void reload() {
        m_grid.getItems().clear();
        try {
            S4Config cfg = S4Config.fromCCConfig();
            S4HttpClient http = new S4HttpClient(cfg);
            for (Imbal2ReadClient.Item item : new Imbal2ReadClient(http).fetchAll())
                m_grid.getItems().add(new GridItem(item));
        } catch (Exception e) {
            m_statusMessage = "Errore nel caricamento: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    private void resetForm() {
        m_creatingNew  = true;
        m_codImballo2  = "";
        m_descrIt      = "";
        m_descrEn      = "";
    }

    public void onNuovo(ActionEvent event) {
        resetForm();
        m_statusMessage = "";
        m_statusIsError = false;
    }

    /** Carica nella maschera la riga attualmente selezionata in griglia, per la modifica. */
    public void onModificaSelezionato(ActionEvent event) {
        GridItem selected = m_grid.getSelectedItem();
        if (selected == null) {
            m_statusMessage = "Seleziona prima una riga nella tabella.";
            m_statusIsError = true;
            return;
        }
        m_creatingNew = false;
        m_codImballo2 = selected.item.code;
        m_descrIt     = selected.item.descrIt;
        m_descrEn     = selected.item.descrEn;
        m_statusMessage = "";
        m_statusIsError = false;
    }

    public void onSalva(ActionEvent event) {
        if (m_codImballo2 == null || m_codImballo2.isBlank()) {
            m_statusMessage = "Inserire il codice imballo.";
            m_statusIsError = true;
            return;
        }
        try {
            S4Config cfg = S4Config.fromCCConfig();
            Imbal2WriteClient writeClient = new Imbal2WriteClient(new S4HttpClient(cfg));
            if (m_creatingNew) {
                writeClient.create(m_codImballo2.trim(), m_descrIt, m_descrEn);
            } else {
                writeClient.update(m_codImballo2.trim(), m_descrIt, m_descrEn);
            }
            m_statusMessage = "Salvato con successo.";
            m_statusIsError = false;
            m_creatingNew   = false;
            reload();
            if (m_listener != null) m_listener.reactOnChanged();
        } catch (Exception e) {
            m_statusMessage = "Errore durante il salvataggio: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    public void onElimina(ActionEvent event) {
        if (m_creatingNew || m_codImballo2 == null || m_codImballo2.isBlank()) return;
        try {
            S4Config cfg = S4Config.fromCCConfig();
            Imbal2WriteClient writeClient = new Imbal2WriteClient(new S4HttpClient(cfg));
            writeClient.delete(m_codImballo2.trim());
            m_statusMessage = "Eliminato con successo.";
            m_statusIsError = false;
            reload();
            resetForm();
            if (m_listener != null) m_listener.reactOnChanged();
        } catch (Exception e) {
            m_statusMessage = "Errore durante l'eliminazione: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    public void onChiudi(ActionEvent event) {
        if (m_listener != null) m_listener.reactOnClosed();
    }

    public FIXGRIDListBinding<GridItem> getGrid() { return m_grid; }

    public boolean getCreatingNew() { return m_creatingNew; }

    public String getCodImballo2()      { return m_codImballo2; }
    public void   setCodImballo2(String v) { m_codImballo2 = v; }
    public String getDescrIt()          { return m_descrIt; }
    public void   setDescrIt(String v)  { m_descrIt = v; }
    public String getDescrEn()          { return m_descrEn; }
    public void   setDescrEn(String v)  { m_descrEn = v; }

    public String  getStatusMessage() { return m_statusMessage; }
    public boolean getStatusIsError() { return m_statusIsError; }
}