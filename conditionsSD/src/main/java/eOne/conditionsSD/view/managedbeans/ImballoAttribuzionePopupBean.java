package eOne.conditionsSD.view.managedbeans;

import java.io.Serializable;
import java.util.ArrayList;
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
    private List<String> m_customerMaterials = List.of();  // altri materiali dello stesso cliente, per "Salva per il cliente"
    private boolean m_overwriteConflicts;   // checkbox: se true, "Salva per il cliente" sovrascrive anche i materiali già configurati diversamente

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

    /**
     * @param language lingua documento del cliente (IT/EN), per i testi delle tendine
     * @param customerMaterials elenco dei materiali dello stesso cliente presenti
     *        nell'estrazione corrente, usato dal bottone "Salva per il cliente"
     *        (di norma condividono lo stesso imballo)
     */
    public void prepare(String customer, String material, String language,
                         List<String> customerMaterials, IListener listener) {
        m_listener = listener;
        m_customer = customer != null ? customer : "";
        m_material = material != null ? material : "";
        m_customerMaterials = customerMaterials != null ? customerMaterials : List.of();
        m_overwriteConflicts = false;
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

    /** true se il cliente corrente ha più di un materiale nell'estrazione — mostra "Salva per il cliente". */
    public boolean getHasMultipleMaterials() { return m_customerMaterials.size() > 1; }

    public boolean getOverwriteConflicts() { return m_overwriteConflicts; }
    public void    setOverwriteConflicts(boolean v) { m_overwriteConflicts = v; }

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
        String targetCustomer = m_generico ? "" : m_customer;
        try {
            Object[] parsed = validateAndParse();
            if (parsed == null) return;  // messaggio di errore già impostato

            saveAssociation(targetCustomer, m_material, (double) parsed[0], (int) parsed[1]);

            m_hadExisting   = true;
            m_statusMessage = "Salvato con successo.";
            m_statusIsError = false;
            if (m_listener != null) {
                m_listener.reactOnSaved();
                m_listener.reactOnClosed();   // Salva ora chiude automaticamente
            }
        } catch (Exception e) {
            m_statusMessage = "Errore durante il salvataggio: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    /**
     * Applica la stessa attribuzione (Imballo 1/2, UM, Quantità, Numerosità)
     * a tutti i materiali del cliente corrente presenti nell'estrazione — utile
     * perché di norma condividono lo stesso imballo. Sempre specifica per
     * m_customer (ignora il flag "Generico": qui l'intento è esplicitamente
     * "per questo cliente").
     *
     * Non distruttivo di default: i materiali che hanno già un'attribuzione
     * SPECIFICA diversa vengono saltati (non un'attribuzione generica o
     * assente, che viene comunque valorizzata come atteso), a meno che la
     * checkbox "Sovrascrivi anche i materiali già configurati diversamente"
     * (vedi {@link #getOverwriteConflicts()}) non sia spuntata.
     */
    public void onSalvaPerCliente(ActionEvent event) {
        try {
            Object[] parsed = validateAndParse();
            if (parsed == null) return;
            double quantita   = (double) parsed[0];
            int    numerosita = (int) parsed[1];

            List<String> conflicts = m_overwriteConflicts ? List.of() : findConflicts(quantita, numerosita);

            int ok = 0, skipped = 0, ko = 0;
            for (String material : m_customerMaterials) {
                if (conflicts.contains(material)) { skipped++; continue; }
                try {
                    saveAssociation(m_customer, material, quantita, numerosita);
                    ok++;
                } catch (Exception e) {
                    ko++;
                }
            }

            m_hadExisting = true;
            StringBuilder msg = new StringBuilder("Salvato per " + ok + " materiali del cliente.");
            if (skipped > 0)
                msg.append("  ").append(skipped).append(" saltati perché già configurati diversamente"
                    + " (spunta \"Sovrascrivi\" per applicarli comunque): ").append(String.join(", ", conflicts)).append(".");
            if (ko > 0)
                msg.append("  ").append(ko).append(" falliti.");
            m_statusMessage = msg.toString();
            m_statusIsError = ko > 0 && ok == 0;

            if (m_listener != null) {
                m_listener.reactOnSaved();
                m_listener.reactOnClosed();
            }
        } catch (Exception e) {
            m_statusMessage = "Errore durante il salvataggio multiplo: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    /**
     * Tra {@link #m_customerMaterials}, individua quelli che hanno già
     * un'attribuzione SPECIFICA (per m_customer) diversa da quella che si
     * sta per salvare — un'attribuzione generica o assente non conta come
     * conflitto, perché "Salva per il cliente" la sovrascriverebbe comunque
     * come comportamento atteso (crea lo specifico dove non c'era).
     */
    private List<String> findConflicts(double quantita, int numerosita) {
        List<String> conflicts = new ArrayList<>();
        try {
            S4Config cfg = S4Config.fromCCConfig();
            Imbal3ReadClient readClient = new Imbal3ReadClient(new S4HttpClient(cfg));
            for (String material : m_customerMaterials) {
                Imbal3ReadClient.AssociationRecord existing = readClient.fetchAssociation(m_customer, material);
                if (existing == null || existing.specificity != Imbal3ReadClient.Specificity.SPECIFIC)
                    continue;
                boolean same = m_codImballo.equals(existing.codImballo)
                    && m_codImballo2.equals(existing.codImballo2)
                    && m_meins.equalsIgnoreCase(existing.meins)
                    && Double.compare(existing.quantita, quantita) == 0
                    && existing.numerosita == numerosita;
                if (!same) conflicts.add(material);
            }
        } catch (Exception e) {
            // In caso di errore nella verifica, non blocchiamo il flusso: nessun conflitto
            // rilevato — il salvataggio procederà come se non ci fossero attribuzioni pregresse.
        }
        return conflicts;
    }

    /**
     * Valida i campi comuni (Imballo 1/2, UM, Quantità, Numerosità) e li
     * converte. Ritorna null (con messaggio di errore già impostato) se la
     * validazione fallisce, altrimenti {quantita (Double), numerosita (Integer)}.
     */
    private Object[] validateAndParse() {
        if (m_codImballo == null || m_codImballo.isBlank()
                || m_codImballo2 == null || m_codImballo2.isBlank()) {
            m_statusMessage = "Seleziona sia Imballo 1 sia Imballo 2.";
            m_statusIsError = true;
            return null;
        }
        double quantita;
        int numerosita;
        try {
            quantita   = Double.parseDouble(m_quantitaText.replace(',', '.').trim());
            numerosita = Integer.parseInt(m_numerositaText.trim());
        } catch (Exception e) {
            m_statusMessage = "Quantità e Numerosità devono essere numeri validi.";
            m_statusIsError = true;
            return null;
        }
        String meinsUpper = m_meins == null ? "" : m_meins.trim().toUpperCase();
        if (meinsUpper.isBlank()) {
            m_statusMessage = "Inserire l'unità di misura.";
            m_statusIsError = true;
            return null;
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
                return null;
            }
        } catch (Exception e) {
            m_statusMessage = "Errore durante la validazione: " + e.getMessage();
            m_statusIsError = true;
            return null;
        }
        m_meins = meinsUpper;
        return new Object[] { quantita, numerosita };
    }

    /** Crea o aggiorna (a seconda che esista già) l'associazione per (targetCustomer, material). */
    private void saveAssociation(String targetCustomer, String material, double quantita, int numerosita)
            throws Exception {
        S4Config cfg = S4Config.fromCCConfig();
        S4HttpClient http = new S4HttpClient(cfg);

        Imbal3ReadClient readClient = new Imbal3ReadClient(http);
        Imbal3WriteClient writeClient = new Imbal3WriteClient(http);

        // Verifica puntuale (non a cascata) se esiste già un record esattamente
        // sulla combinazione target, per decidere create vs update.
        boolean existsAtTarget = existsExact(readClient, targetCustomer, material);

        if (existsAtTarget) {
            writeClient.update(targetCustomer, material, m_codImballo, m_codImballo2,
                m_meins, quantita, numerosita);
        } else {
            writeClient.create(targetCustomer, material, m_codImballo, m_codImballo2,
                m_meins, quantita, numerosita);
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