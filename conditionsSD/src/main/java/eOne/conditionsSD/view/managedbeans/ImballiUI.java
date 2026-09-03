package eOne.conditionsSD.view.managedbeans;

import com.fasterxml.jackson.databind.JsonNode;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.defaultscreens.ModalPopup;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.pagebean.PageBean;
import org.eclnt.jsfserver.util.AutoCompleteMgr;
import org.eclnt.jsfserver.util.DefaultAutoCompleteProvider;

import eOne.conditionsSD.s4client.Imbal3ReadClient;
import eOne.conditionsSD.s4client.S4Config;
import eOne.conditionsSD.s4client.S4HttpClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pagina di manutenzione rapida di ZIMBAL_3 (associazione imballo
 * cliente/materiale), indipendente dal listino: si cerca per Cliente e/o
 * Materiale, si vedono tutte le righe che matchano (specifiche + generica,
 * senza cascata), e "Nuovo"/"Modifica selezionato" aprono lo STESSO popup
 * di attribuzione già usato dal listino ({@link ImballoAttribuzionePopupBean})
 * — stessa interfaccia, stesso comportamento collaudato, nessun form duplicato.
 * Include anche l'accesso rapido alle due gestioni "Parametri Imballo 1/2".
 */
@CCGenClass(expressionBase = "#{d.ImballiUI}")
public class ImballiUI extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;

    public class GridItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        final Imbal3ReadClient.SearchRow row;
        GridItem(Imbal3ReadClient.SearchRow row) { this.row = row; }

        public String getCustomer()   { return row.customer.isBlank() ? "(generico)" : row.customer; }
        public String getMaterial()   { return row.material; }
        public String getSpecificity() {
            return row.specificity == Imbal3ReadClient.Specificity.GENERIC ? "Generico" : "Specifico";
        }
        public String getImballoText() { return row.getImballoText("IT"); }
        public String getMeins()       { return row.meins; }
        public String getQuantitaFormatted() { return formatQty(row.quantita); }
        public String getNumerosita()  { return row.numerosita > 0 ? String.valueOf(row.numerosita) : ""; }
    }

    private final FIXGRIDListBinding<GridItem> m_grid = new FIXGRIDListBinding<>();

    // Criteri di ricerca — riusati anche come Cliente/Materiale per "Nuovo"
    private String m_searchCustomerInput = "";
    private String m_searchMaterialInput = "";

    private String  m_statusMessage = "";
    private boolean m_statusIsError;

    private DefaultAutoCompleteProvider m_customerProvider;
    private DefaultAutoCompleteProvider m_materialProvider;

    // ═══════════════════════════════════════════════════════════════════
    // Autocomplete Cliente/Materiale — stessa logica già usata in ListinoBean
    // (duplicata qui perché non ancora estratta in una classe condivisa)
    // ═══════════════════════════════════════════════════════════════════
    private class CustomerACProvider extends DefaultAutoCompleteProvider {
        private static final long serialVersionUID = 1L;
        @Override
        public List<String> getProposals(String searchString) {
            List<String> proposals = new ArrayList<>();
            if (searchString == null || searchString.trim().length() < 2) return proposals;
            try {
                S4Config cfg = S4Config.fromCCConfig();
                S4HttpClient http = new S4HttpClient(cfg);
                String term = searchString.trim().replace("*", "");
                String filter = "startswith(Customer,'" + term + "')"
                              + " or substringof('" + term + "',CustomerName)";
                String path = "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_Customer"
                            + "?$filter=" + S4HttpClient.encode(filter)
                            + "&$select=Customer,CustomerName&$top=20&$format=json";
                JsonNode root = http.getOData(path);
                JsonNode results = root.path("d").path("results");
                if (results.isArray())
                    for (JsonNode n : results)
                        proposals.add(n.path("Customer").asText("").strip()
                            + " — " + n.path("CustomerName").asText("").strip());
            } catch (Exception e) {
                System.err.println("Autocomplete clienti errore: " + e.getMessage());
            }
            return proposals;
        }
    }

    private class MaterialACProvider extends DefaultAutoCompleteProvider {
        private static final long serialVersionUID = 1L;
        @Override
        public List<String> getProposals(String searchString) {
            List<String> proposals = new ArrayList<>();
            if (searchString == null || searchString.trim().length() < 2) return proposals;
            try {
                S4Config cfg = S4Config.fromCCConfig();
                S4HttpClient http = new S4HttpClient(cfg);
                String term = searchString.trim().replace("*", "");
                String filter = "Language eq '" + cfg.getLanguage() + "'"
                    + " and (substringof('" + term + "',Product)"
                    + " or substringof('" + term + "',ProductDescription))";
                String path = "/sap/opu/odata/SAP/API_PRODUCT_SRV/A_ProductDescription"
                            + "?$filter=" + S4HttpClient.encode(filter)
                            + "&$select=Product,ProductDescription&$top=20&$format=json";
                JsonNode root = http.getOData(path);
                JsonNode results = root.path("d").path("results");
                if (results.isArray())
                    for (JsonNode n : results)
                        proposals.add(n.path("Product").asText("").strip()
                            + " — " + n.path("ProductDescription").asText("").strip());
            } catch (Exception e) {
                System.err.println("Autocomplete materiali errore: " + e.getMessage());
            }
            return proposals;
        }
    }

    public ImballiUI() {
        m_customerProvider = new CustomerACProvider();
        m_materialProvider = new MaterialACProvider();
        AutoCompleteMgr.add(m_customerProvider);
        AutoCompleteMgr.add(m_materialProvider);
    }

    public String getPageName() { return "/conditionssd/listino/imballi.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.ImballiUI}"; }

    // ═══════════════════════════════════════════════════════════════════
    // Ricerca
    // ═══════════════════════════════════════════════════════════════════
    public void onCerca(ActionEvent event) {
        String cust = extractCode(m_searchCustomerInput);
        String mat  = extractCode(m_searchMaterialInput);
        if (cust.isBlank() && mat.isBlank()) {
            m_statusMessage = "Inserire almeno Cliente o Materiale per la ricerca.";
            m_statusIsError = true;
            return;
        }
        m_grid.getItems().clear();
        try {
            S4Config cfg = S4Config.fromCCConfig();
            Imbal3ReadClient readClient = new Imbal3ReadClient(new S4HttpClient(cfg));
            List<Imbal3ReadClient.SearchRow> rows = readClient.search(cust, mat);
            for (Imbal3ReadClient.SearchRow row : rows)
                m_grid.getItems().add(new GridItem(row));
            m_statusMessage = rows.isEmpty() ? "Nessuna associazione trovata." : rows.size() + " associazioni trovate.";
            m_statusIsError = false;
        } catch (Exception e) {
            m_statusMessage = "Errore durante la ricerca: " + e.getMessage();
            m_statusIsError = true;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Nuovo / Modifica — aprono lo stesso popup di attribuzione del listino
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Apre il popup per creare una nuova associazione, usando Cliente (se
     * valorizzato — altrimenti generico) e Materiale attualmente inseriti
     * nei campi di ricerca in alto. Richiede almeno il Materiale.
     */
    public void onNuovo(ActionEvent event) {
        String material = extractCode(m_searchMaterialInput);
        if (material.isBlank()) {
            m_statusMessage = "Inserire almeno il Materiale (in alto) prima di \"Nuovo\".";
            m_statusIsError = true;
            return;
        }
        String customer = extractCode(m_searchCustomerInput); // può essere vuoto: generico
        openAttribuzionePopup(customer, material);
    }

    /** Apre il popup per modificare la riga attualmente selezionata nella griglia risultati. */
    public void onModificaSelezionato(ActionEvent event) {
        GridItem selected = m_grid.getSelectedItem();
        if (selected == null) {
            m_statusMessage = "Seleziona prima una riga nella tabella dei risultati.";
            m_statusIsError = true;
            return;
        }
        openAttribuzionePopup(selected.row.customer, selected.row.material);
    }

    /**
     * NOTA: la lingua per le descrizioni IT/EN nelle tendine del popup è
     * fissata a "IT" (qui, a differenza del listino, non c'è già una riga
     * cliente estratta da cui leggere la lingua di corrispondenza reale) —
     * incide solo sul testo mostrato nelle tendine, non sul dato salvato.
     */
    private void openAttribuzionePopup(String customer, String material) {
        final ImballoAttribuzionePopupBean popupBean = new ImballoAttribuzionePopupBean();
        popupBean.prepare(customer, material, "IT", new ImballoAttribuzionePopupBean.IListener() {
            @Override
            public void reactOnSaved() {
                m_statusMessage = "Attribuzione imballo salvata.";
                m_statusIsError = false;
                onCerca(null); // ricarica la griglia con il dato aggiornato
            }
            @Override
            public void reactOnClosed() {
                closePopup(popupBean);
            }
        });
        openModalPopup(popupBean, "Attribuzione imballo", 624, 416, new ModalPopup.IModalPopupListener() {
            @Override
            public void reactOnPopupClosedByUser() {
                closePopup(popupBean);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // Popup Parametri Imballo 1/2 — stessa funzione dei link in main.xml
    // ═══════════════════════════════════════════════════════════════════
    public void onGestisciImballo1(ActionEvent event) {
        final Imbal1ParametriPopupBean popupBean = new Imbal1ParametriPopupBean();
        popupBean.prepare(new Imbal1ParametriPopupBean.IListener() {
            @Override public void reactOnChanged() { }
            @Override public void reactOnClosed()  { closePopup(popupBean); }
        });
        openModalPopup(popupBean, "Parametri Imballo 1", 600, 620, new ModalPopup.IModalPopupListener() {
            @Override public void reactOnPopupClosedByUser() { closePopup(popupBean); }
        });
    }

    public void onGestisciImballo2(ActionEvent event) {
        final Imbal2ParametriPopupBean popupBean = new Imbal2ParametriPopupBean();
        popupBean.prepare(new Imbal2ParametriPopupBean.IListener() {
            @Override public void reactOnChanged() { }
            @Override public void reactOnClosed()  { closePopup(popupBean); }
        });
        openModalPopup(popupBean, "Parametri Imballo 2", 620, 620, new ModalPopup.IModalPopupListener() {
            @Override public void reactOnPopupClosedByUser() { closePopup(popupBean); }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════
    private static String extractCode(String v) {
        if (v == null) return "";
        int sep = v.indexOf(" — ");
        return sep > 0 ? v.substring(0, sep).trim() : v.trim();
    }

    private static String formatQty(double q) {
        if (q <= 0d) return "";
        return (q == Math.floor(q)) ? String.valueOf((long) q) : String.valueOf(q);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Getters / Setters
    // ═══════════════════════════════════════════════════════════════════
    public FIXGRIDListBinding<GridItem> getGrid() { return m_grid; }

    public String getSearchCustomerInput()        { return m_searchCustomerInput; }
    public void   setSearchCustomerInput(String v) { m_searchCustomerInput = v; }
    public String getSearchMaterialInput()        { return m_searchMaterialInput; }
    public void   setSearchMaterialInput(String v) { m_searchMaterialInput = v; }
    public String getCustomerACURL() { return m_customerProvider.getURL(); }
    public String getMaterialACURL() { return m_materialProvider.getURL(); }

    public String  getStatusMessage() { return m_statusMessage; }
    public boolean getStatusIsError() { return m_statusIsError; }
}