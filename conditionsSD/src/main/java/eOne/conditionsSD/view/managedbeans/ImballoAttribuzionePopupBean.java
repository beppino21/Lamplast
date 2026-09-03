package eOne.conditionsSD.view.managedbeans;

import java.io.Serializable;
import java.util.List;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.elements.util.ValidValuesBinding;
import org.eclnt.jsfserver.pagebean.PageBean;

import eOne.conditionsSD.s4client.Imbal1ReadClient;
import eOne.conditionsSD.s4client.Imbal2ReadClient;
import eOne.conditionsSD.s4client.Imbal3ReadClient;
import eOne.conditionsSD.s4client.Imbal3WriteClient;
import eOne.conditionsSD.s4client.S4Config;
import eOne.conditionsSD.s4client.S4HttpClient;
import eOne.conditionsSD.s4client.UnitOfMeasureClient;

/**
 * Popup di attribuzione imballo (ZIMBAL_3): associa a Cliente+Materiale (o
 * solo Materiale, per l'imballo generico) una coppia di codici ZIMBAL_1 +
 * ZIMBAL_2 + UM + Quantità + Numerosità per pallet.
 *
 * Si apre sempre da una riga materiale selezionata nel listino — Cliente e
 * Materiale arrivano già valorizzati e non sono editabili qui; la casella
 * "generico" decide se l'associazione riguarda quel cliente specifico o
 * tutti i clienti per quel materiale (cliente vuoto).
 */
@CCGenClass(expressionBase = "#{d.ImballoAttribuzionePopupBean}")
public class ImballoAttribuzionePopupBean extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;

    public interface IListener {
        void reactOnSaved();
        void reactOnClosed();
    }

    private String  m_customer;
    private String  m_material;
    private boolean m_generico;      // true = l'associazione riguarda cliente=''(tutti), false = solo m_customer
    private boolean m_hadExisting;   // true se all'apertura esisteva già un'associazione (specifica o generica)

    private String  m_codImballo;
    private String  m_codImballo2;
    private String  m_meins;
    private String  m_quantitaText;
    private String  m_numerositaText;

    private final ValidValuesBinding m_imballo1Vvb = new ValidValuesBinding();
    private final ValidValuesBinding m_imballo2Vvb = new ValidValuesBinding();

    private String  m_statusMessage = "";
    private boolean m_statusIsError;

    private IListener m_listener;

    public ImballoAttribuzionePopupBean() { }

    public String getPageName() { return "/conditionssd/listino/imballo_attribuzione_popup.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.ImballoAttribuzionePopupBean}"; }

    /** @param language lingua documento del cliente (IT/EN), per i testi delle tendine */
    public void prepare(String customer, String material, String language, IListener listener) {
        m_listener = listener;
        m_customer = customer != null ? customer : "";
        m_material = material != null ? material : "";
        m_statusMessage = "";
        m_statusIsError = false;

        try {
            S4Config cfg = S4Config.fromCCConfig();
            S4HttpClient http = new S4HttpClient(cfg);

            // Popola le due tendine
            m_imballo1Vvb.clear();
            for (Imbal1ReadClient.Item item : new Imbal1ReadClient(http).fetchAll())
                m_imballo1Vvb.addValidValue(item.code, item.code + " — " + item.getText(language));
            m_imballo2Vvb.clear();
            for (Imbal2ReadClient.Item item : new Imbal2ReadClient(http).fetchAll())
                m_imballo2Vvb.addValidValue(item.code, item.code + " — " + item.getText(language));

            // Precompila con l'associazione esistente, se c'è
            Imbal3ReadClient.AssociationRecord existing =
                new Imbal3ReadClient(http).fetchAssociation(m_customer, m_material);

            if (existing != null) {
                m_hadExisting     = true;
                m_generico        = existing.specificity == Imbal3ReadClient.Specificity.GENERIC;
                m_codImballo      = existing.codImballo;
                m_codImballo2     = existing.codImballo2;
                m_meins           = existing.meins;
                m_quantitaText    = formatQty(existing.quantita);
                m_numerositaText  = String.valueOf(existing.numerosita);
            } else {
                m_hadExisting    = false;
                m_generico       = false;
                m_codImballo     = null;
                m_codImballo2    = null;
                m_meins          = "";
                m_quantitaText   = "";
                m_numerositaText = "";
            }
        } catch (Exception e) {
            m_statusMessage = "Errore nel caricamento: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    public String  getCustomer()  { return m_customer; }
    public String  getMaterial()  { return m_material; }
    public boolean getGenerico()  { return m_generico; }
    public void    setGenerico(boolean v) { m_generico = v; }
    public boolean getHadExisting() { return m_hadExisting; }

    public String  getCodImballo()  { return m_codImballo; }
    public void    setCodImballo(String v) { m_codImballo = v; }
    public String  getCodImballo2() { return m_codImballo2; }
    public void    setCodImballo2(String v) { m_codImballo2 = v; }
    public String  getMeins()  { return m_meins; }
    public void    setMeins(String v) { m_meins = v; }
    public String  getQuantitaText() { return m_quantitaText; }
    public void    setQuantitaText(String v) { m_quantitaText = v; }
    public String  getNumerositaText() { return m_numerositaText; }
    public void    setNumerositaText(String v) { m_numerositaText = v; }

    public ValidValuesBinding getImballo1Vvb() { return m_imballo1Vvb; }
    public ValidValuesBinding getImballo2Vvb() { return m_imballo2Vvb; }

    public String  getStatusMessage() { return m_statusMessage; }
    public boolean getStatusIsError() { return m_statusIsError; }

    public String getTitleText() {
        return m_hadExisting
            ? "Modifica attribuzione imballo: " + m_customer + " / " + m_material
            : "Nuova attribuzione imballo: " + m_customer + " / " + m_material;
    }

    public void onSalva(ActionEvent event) {
        if (m_codImballo == null || m_codImballo.isBlank()
                || m_codImballo2 == null || m_codImballo2.isBlank()) {
            m_statusMessage = "Seleziona sia Imballo 1 sia Imballo 2.";
            m_statusIsError = true;
            return;
        }
        double quantita;
        int numerosita;
        try {
            quantita   = Double.parseDouble(m_quantitaText.replace(',', '.').trim());
            numerosita = Integer.parseInt(m_numerositaText.trim());
        } catch (Exception e) {
            m_statusMessage = "Quantità e Numerosità devono essere numeri validi.";
            m_statusIsError = true;
            return;
        }

        String targetCustomer = m_generico ? "" : m_customer;

        String meinsUpper = m_meins == null ? "" : m_meins.trim().toUpperCase();
        if (meinsUpper.isBlank()) {
            m_statusMessage = "Inserire l'unità di misura.";
            m_statusIsError = true;
            return;
        }

        try {
            S4Config cfg = S4Config.fromCCConfig();
            S4HttpClient http = new S4HttpClient(cfg);

            // Validazione UM contro anagrafica SAP — se il servizio non è
            // ancora pronto (ZZ_UNITOFMEASURE_SRV da creare), isValid() non
            // blocca il salvataggio (vedi UnitOfMeasureClient).
            if (!new UnitOfMeasureClient(http).isValid(meinsUpper)) {
                m_statusMessage = "Unità di misura '" + meinsUpper + "' non valida — verifica il valore inserito.";
                m_statusIsError = true;
                return;
            }
            m_meins = meinsUpper;

            Imbal3ReadClient readClient = new Imbal3ReadClient(http);
            Imbal3WriteClient writeClient = new Imbal3WriteClient(http);

            // Verifica puntuale (non a cascata) se esiste già un record esattamente
            // sulla combinazione target, per decidere create vs update.
            boolean existsAtTarget = existsExact(readClient, targetCustomer, m_material);

            if (existsAtTarget) {
                writeClient.update(targetCustomer, m_material, m_codImballo, m_codImballo2,
                    m_meins, quantita, numerosita);
            } else {
                writeClient.create(targetCustomer, m_material, m_codImballo, m_codImballo2,
                    m_meins, quantita, numerosita);
            }

            m_hadExisting   = true;
            m_statusMessage = "Salvato con successo.";
            m_statusIsError = false;
            if (m_listener != null) m_listener.reactOnSaved();
        } catch (Exception e) {
            m_statusMessage = "Errore durante il salvataggio: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    public void onElimina(ActionEvent event) {
        if (!m_hadExisting) return;
        String targetCustomer = m_generico ? "" : m_customer;
        try {
            S4Config cfg = S4Config.fromCCConfig();
            Imbal3WriteClient writeClient = new Imbal3WriteClient(new S4HttpClient(cfg));
            writeClient.delete(targetCustomer, m_material);
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

    /** Verifica se esiste un'associazione esattamente su (customer, material) — nessuna cascata. */
    private boolean existsExact(Imbal3ReadClient readClient, String customer, String material)
            throws Exception {
        Imbal3ReadClient.AssociationRecord rec = readClient.fetchAssociation(customer, material);
        if (rec == null) return false;
        // fetchAssociation fa comunque la cascata specifico->generico: se stiamo
        // controllando lo scope "generico" (customer==""), un match è sempre valido;
        // se stiamo controllando lo scope "specifico", accettiamo solo se la cascata
        // ha davvero trovato lo specifico (altrimenti avrebbe trovato solo il generico
        // di qualcun altro, che non è la stessa riga).
        if (customer.isBlank()) return rec.specificity == Imbal3ReadClient.Specificity.GENERIC;
        return rec.specificity == Imbal3ReadClient.Specificity.SPECIFIC;
    }

    private static String formatQty(double q) {
        return (q == Math.floor(q)) ? String.valueOf((long) q) : String.valueOf(q);
    }
}