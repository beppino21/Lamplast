package eOne.conditionsSD.view.managedbeans;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.pagebean.PageBean;

import eOne.conditionsSD.s4client.Imbal1ReadClient;
import eOne.conditionsSD.s4client.Imbal1WriteClient;
import eOne.conditionsSD.s4client.S4Config;
import eOne.conditionsSD.s4client.S4HttpClient;

/**
 * Popup di gestione "Parametri Imballo 1" (ZIMBAL_1 — tipologia imballo):
 * elenco libero dei codici esistenti, con Nuovo/Modifica/Salva/Elimina.
 * Non legato a una riga del listino — a differenza del popup di attribuzione
 * (ZIMBAL_3), qui si gestisce la tabella dei valori ammessi.
 */
@CCGenClass(expressionBase = "#{d.Imbal1ParametriPopupBean}")
public class Imbal1ParametriPopupBean extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;

    public interface IListener {
        void reactOnChanged();
        void reactOnClosed();
    }

    public class GridItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Imbal1ReadClient.Item item;
        GridItem(Imbal1ReadClient.Item item) { this.item = item; }
        public String getCodImballo() { return item.code; }
        public String getDescrIt()    { return item.descrIt; }
        public String getDescrEn()    { return item.descrEn; }
    }

    private final FIXGRIDListBinding<GridItem> m_grid = new FIXGRIDListBinding<>();

    private boolean m_creatingNew;
    private String  m_codImballo;
    private String  m_descrIt;
    private String  m_descrEn;

    private String  m_statusMessage = "";
    private boolean m_statusIsError;

    private IListener m_listener;

    public Imbal1ParametriPopupBean() { }

    public String getPageName() { return "/conditionssd/listino/imbal1_parametri_popup.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.Imbal1ParametriPopupBean}"; }

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
            for (Imbal1ReadClient.Item item : new Imbal1ReadClient(http).fetchAll())
                m_grid.getItems().add(new GridItem(item));
        } catch (Exception e) {
            m_statusMessage = "Errore nel caricamento: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    private void resetForm() {
        m_creatingNew = true;
        m_codImballo  = "";
        m_descrIt     = "";
        m_descrEn     = "";
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
        m_codImballo  = selected.item.code;
        m_descrIt     = selected.item.descrIt;
        m_descrEn     = selected.item.descrEn;
        m_statusMessage = "";
        m_statusIsError = false;
    }

    public void onSalva(ActionEvent event) {
        if (m_codImballo == null || m_codImballo.isBlank()) {
            m_statusMessage = "Inserire il codice imballo.";
            m_statusIsError = true;
            return;
        }
        try {
            S4Config cfg = S4Config.fromCCConfig();
            Imbal1WriteClient writeClient = new Imbal1WriteClient(new S4HttpClient(cfg));
            if (m_creatingNew) {
                writeClient.create(m_codImballo.trim(), m_descrIt, m_descrEn);
            } else {
                writeClient.update(m_codImballo.trim(), m_descrIt, m_descrEn);
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
        if (m_creatingNew || m_codImballo == null || m_codImballo.isBlank()) return;
        try {
            S4Config cfg = S4Config.fromCCConfig();
            Imbal1WriteClient writeClient = new Imbal1WriteClient(new S4HttpClient(cfg));
            writeClient.delete(m_codImballo.trim());
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

    public String getCodImballo()      { return m_codImballo; }
    public void   setCodImballo(String v) { m_codImballo = v; }
    public String getDescrIt()         { return m_descrIt; }
    public void   setDescrIt(String v) { m_descrIt = v; }
    public String getDescrEn()         { return m_descrEn; }
    public void   setDescrEn(String v) { m_descrEn = v; }

    public String  getStatusMessage() { return m_statusMessage; }
    public boolean getStatusIsError() { return m_statusIsError; }
}