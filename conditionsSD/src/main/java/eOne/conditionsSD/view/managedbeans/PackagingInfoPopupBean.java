package eOne.conditionsSD.view.managedbeans;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.pagebean.PageBean;

import eOne.conditionsSD.s4client.PackagingInfoClient;
import eOne.conditionsSD.s4client.S4Config;
import eOne.conditionsSD.s4client.S4HttpClient;

/**
 * Popup di manutenzione (crea/modifica/elimina) per un record della tabella
 * custom ZZPACKAGING_INFO (imballo di default cliente/materiale).
 */
@CCGenClass(expressionBase = "#{d.PackagingInfoPopupBean}")
public class PackagingInfoPopupBean extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;

    public interface IListener {
        /** Chiamato dopo un salvataggio o un'eliminazione andati a buon fine. */
        void reactOnSaved();
        void reactOnClosed();
    }

    private String  m_customer;
    private String  m_material;
    private String  m_packagingText;
    private boolean m_keysProvided; // true se Cliente/Materiale arrivano da una riga selezionata in griglia
    private boolean m_isNew;        // true se non esiste ancora un record per questa combinazione
    private String  m_statusMessage = "";
    private boolean m_statusIsError;

    private IListener m_listener;

    public PackagingInfoPopupBean() { }

    public String getPageName() { return "/conditionssd/listino/packaginginfo_popup.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.PackagingInfoPopupBean}"; }

    /**
     * @param customer  se null/vuoto: nuovo inserimento a chiave libera
     * @param material  idem
     */
    public void prepare(String customer, String material, IListener listener) {
        m_listener      = listener;
        m_customer      = customer != null ? customer : "";
        m_material      = material != null ? material : "";
        m_packagingText = "";
        m_statusMessage = "";
        m_statusIsError = false;
        m_keysProvided  = !m_customer.isBlank() && !m_material.isBlank();
        m_isNew         = true; // fino a prova contraria (record trovato sotto)

        if (m_keysProvided) {
            try {
                S4Config cfg = S4Config.fromCCConfig();
                PackagingInfoClient client = new PackagingInfoClient(new S4HttpClient(cfg));
                String existing = client.fetchOne(m_customer, m_material);
                if (existing != null) {
                    m_packagingText = existing;
                    m_isNew = false; // record trovato davvero: sarà un update
                }
            } catch (Exception e) {
                m_statusMessage = "Impossibile leggere l'imballo esistente: " + e.getMessage();
                m_statusIsError = true;
            }
        }
    }

    public String  getCustomer()                { return m_customer; }
    public void    setCustomer(String v)         { m_customer = v; }
    public String  getMaterial()                 { return m_material; }
    public void    setMaterial(String v)          { m_material = v; }
    public String  getPackagingText()            { return m_packagingText; }
    public void    setPackagingText(String v)     { m_packagingText = v; }
    public boolean getIsNew()                    { return m_isNew; }
    public boolean getKeyEditable()               { return !m_keysProvided; } // bloccate se arrivano da riga selezionata
    public String  getStatusMessage()            { return m_statusMessage; }
    public boolean getStatusIsError()            { return m_statusIsError; }
    public String  getTitleText() {
        if (m_isNew)
            return "Nuovo imballo" + (m_keysProvided ? " (" + m_customer + " / " + m_material + ")" : "");
        return "Modifica imballo: " + m_customer + " / " + m_material;
    }

    public void onSalva(ActionEvent event) {
        if (m_customer == null || m_customer.isBlank() || m_material == null || m_material.isBlank()) {
            m_statusMessage = "Cliente e Materiale sono obbligatori.";
            m_statusIsError = true;
            return;
        }
        try {
            S4Config cfg = S4Config.fromCCConfig();
            PackagingInfoClient client = new PackagingInfoClient(new S4HttpClient(cfg));
            if (m_isNew) {
                client.create(m_customer.trim(), m_material.trim(), m_packagingText);
                m_isNew = false; // dopo la creazione, ulteriori "Salva" fanno update
            } else {
                client.update(m_customer.trim(), m_material.trim(), m_packagingText);
            }
            m_statusMessage = "Salvato con successo.";
            m_statusIsError = false;
            if (m_listener != null) m_listener.reactOnSaved();
        } catch (Exception e) {
            m_statusMessage = "Errore durante il salvataggio: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    public void onElimina(ActionEvent event) {
        if (m_isNew) return; // niente da eliminare, non ancora salvato
        try {
            S4Config cfg = S4Config.fromCCConfig();
            PackagingInfoClient client = new PackagingInfoClient(new S4HttpClient(cfg));
            client.delete(m_customer.trim(), m_material.trim());
            m_statusMessage = "Eliminato con successo.";
            m_statusIsError = false;
            if (m_listener != null) {
                m_listener.reactOnSaved();
                m_listener.reactOnClosed();
            }
        } catch (Exception e) {
            m_statusMessage = "Errore durante l'eliminazione: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    public void onChiudi(ActionEvent event) {
        if (m_listener != null) m_listener.reactOnClosed();
    }
}
