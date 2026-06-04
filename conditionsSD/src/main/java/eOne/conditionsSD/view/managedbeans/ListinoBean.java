package eOne.conditionsSD.view.managedbeans;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.pagebean.PageBean;
import org.eclnt.jsfserver.util.AutoCompleteMgr;
import org.eclnt.jsfserver.util.DefaultAutoCompleteProvider;

import com.fasterxml.jackson.databind.JsonNode;

import eOne.conditionsSD.model.ExtractMode;
import eOne.conditionsSD.logic.ListinoExtractor;
import eOne.conditionsSD.model.ExtractParams;
import eOne.conditionsSD.model.ListinoRow;
import eOne.conditionsSD.model.ListinoRow.RowType;
import eOne.conditionsSD.s4client.CustomerClient;
import eOne.conditionsSD.s4client.S4Config;
import eOne.conditionsSD.s4client.S4HttpClient;

@CCGenClass(expressionBase = "#{d.ListinoBean}")
public class ListinoBean extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ═══════════════════════════════════════════════════════════════════════
    // Font costanti per tipo riga
    // ═══════════════════════════════════════════════════════════════════════
    private static final String FONT_CUSTOMER   = "weight:bold;size:13;color:#1565C0";
    private static final String FONT_SCALE_HDR  = "weight:bold;color:#1565C0";
    private static final String FONT_ZONE_HDR   = "weight:bold;color:#555555";
    private static final String FONT_MATERIAL   = "";
    private static final String FONT_ZONE_REF   = "weight:bold;color:#007700";
    private static final String FONT_ZONE       = "color:#888888";
    private static final String FONT_UM_WARNING = "color:#cc0000;weight:bold";
    private static final String FONT_ALERT_HDR  = "weight:bold;color:#cc6600;size:13";
    private static final String FONT_ALERT      = "color:#cc6600";

    // ═══════════════════════════════════════════════════════════════════════
    // Campi — variabili di istanza (prefisso m_)
    // ═══════════════════════════════════════════════════════════════════════
    private FIXGRIDListBinding<GridListinoItem> m_gridListino;

    private String  m_priceGroupInput;
    private String  m_customerInput;
    private String  m_materialInput;
    private Date    m_referenceDateInput;
    private String  m_statusMessage;
    private boolean m_hasWarnings;
    private boolean m_awaitingConfirm;
    private List<CustomerClient.CustomerInfo> m_pendingCustomers;
    private ExtractMode m_pendingExtractMode;

    private DefaultAutoCompleteProvider m_priceGroupProvider;
    private DefaultAutoCompleteProvider m_customerProvider;
    private DefaultAutoCompleteProvider m_materialProvider;

    // ═══════════════════════════════════════════════════════════════════════
    // Classi interne — provider autocomplete
    // ═══════════════════════════════════════════════════════════════════════

    // Valori fissi Price Group — aggiornare manualmente se cambiano
    private static final List<String> PRICE_GROUP_VALUES = List.of(
        "01 — MARELLI EUROPE SpA",
        "02 — LYNXEO FRANCE",
        "03 — PLASTIC COMP. and",
        "04 — ALSIANO A/S",
        "05 — GA INTERN. TRADE SLU",
        "06 — NEXANS FRANCE CSP",
        "07 — OMERIN SAS",
        "08 — XBK-KABEL XAVER BECH",
        "09 — PRYSMIAN GR.FINLAND",
        "10 — ALFRED KAERCHER SE&C",
        "11 — TELDOR"
    );

    private class PriceGroupACProvider extends DefaultAutoCompleteProvider {
        private static final long serialVersionUID = 1L;
        @Override
        public List<String> getProposals(String searchString) {
            if (searchString == null || searchString.trim().isEmpty())
                return new ArrayList<>(PRICE_GROUP_VALUES);
            String term = searchString.trim().toLowerCase();
            List<String> proposals = new ArrayList<>();
            for (String v : PRICE_GROUP_VALUES) {
                if (v.toLowerCase().contains(term))
                    proposals.add(v);
            }
            return proposals;
        }
    }

    private class CustomerACProvider extends DefaultAutoCompleteProvider {
        private static final long serialVersionUID = 1L;
        @Override
        public List<String> getProposals(String searchString) {
            List<String> proposals = new ArrayList<>();
            if (searchString == null || searchString.trim().length() < 2) return proposals;
            try {
                S4Config cfg = S4Config.fromCCConfig();
                S4HttpClient http = new S4HttpClient(cfg);
                String term = searchString.trim();
                String filter;
                if (term.contains("*")) {
                    String t = term.replace("*", "");
                    filter = "substringof('" + t + "',Customer)"
                           + " or substringof('" + t + "',CustomerName)";
                } else {
                    filter = "startswith(Customer,'" + term + "')"
                           + " or substringof('" + term + "',CustomerName)";
                }
                String path = "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_Customer"
                            + "?$filter=" + S4HttpClient.encode(filter)
                            + "&$select=Customer,CustomerName"
                            + "&$top=20&$format=json";
                JsonNode root = http.getOData(path);
                JsonNode results = root.path("d").path("results");
                if (results.isArray()) {
                    for (JsonNode n : results) {
                        String code = n.path("Customer").asText("").strip();
                        String name = n.path("CustomerName").asText("").strip();
                        proposals.add(code + " — " + name);
                    }
                }
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
                String term = searchString.trim();
                String lang = cfg.getLanguage();
                String t = term.replace("*", "");
                String filter = "Language eq '" + lang + "'"
                    + " and (substringof('" + t + "',Product)"
                    + " or substringof('" + t + "',ProductDescription))";
                String path = "/sap/opu/odata/SAP/API_PRODUCT_SRV/A_ProductDescription"
                            + "?$filter=" + S4HttpClient.encode(filter)
                            + "&$select=Product,ProductDescription"
                            + "&$top=20&$format=json";
                JsonNode root = http.getOData(path);
                JsonNode results = root.path("d").path("results");
                if (results.isArray()) {
                    for (JsonNode n : results) {
                        String code = n.path("Product").asText("").strip();
                        String desc = n.path("ProductDescription").asText("").strip();
                        proposals.add(code + " — " + desc);
                    }
                }
            } catch (Exception e) {
                System.err.println("Autocomplete materiali errore: " + e.getMessage());
            }
            return proposals;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inner class griglia
    // ═══════════════════════════════════════════════════════════════════════
    public class GridListinoItem extends FIXGRIDItem implements Serializable {

        private static final long serialVersionUID = 1L;
        private final ListinoRow row;

        public GridListinoItem(ListinoRow row) { this.row = row; }

        public String getRowFont() {
            switch (row.getRowType()) {
                case CUSTOMER:     return FONT_CUSTOMER;
                case HEADER_SCALE: return FONT_SCALE_HDR;
                case HEADER_ZONE:  return FONT_ZONE_HDR;
                case MATERIAL:     return FONT_MATERIAL;
                case ZONE:         return row.isPreferredZone() ? FONT_ZONE_REF : FONT_ZONE;
                case ALERT:        return row.getCustomerCode() == null
                                       ? FONT_ALERT_HDR : FONT_ALERT;
                default:           return "";
            }
        }

        public String getDescription() {
            if (row.getRowType() == RowType.CUSTOMER)
                return row.getCustomerName();  // Il codice è già nella colonna descrizione via ListinoBuilder
            return nvl(row.getDescription());
        }

        public String getCol1() { return buildCol(1); }
        public String getCol2() { return buildCol(2); }
        public String getCol3() { return buildCol(3); }
        public String getCol4() { return buildCol(4); }
        public String getCol5() { return buildCol(5); }

        private String buildCol(int n) {
            if (n > row.getActiveCols() && !row.isCustomerRow()
                    && !row.isHeaderZoneRow() && !row.isAlertRow())
                return "";
            switch (row.getRowType()) {
                case HEADER_SCALE: return formatScaleHeader(n);
                case MATERIAL:     return formatPrice(n);
                case ZONE:         return formatDelta(n);
                default:           return "";
            }
        }

        private String formatScaleHeader(int n) {
            double q    = row.getScaleQty()[n - 1];
            String unit = nvl(row.getScaleUnit(), "");
            if (q <= 0) {
                if (n == 5) return unit.trim().isEmpty() ? "Qualsiasi" : "Qualsiasi (" + unit + ")";
                return "";
            }
            String qty = q == Math.floor(q)
                ? String.format("%.0f", q)
                : String.format("%.3f", q).replaceAll("0+$", "");
            return "fino a " + qty + (unit.trim().isEmpty() ? "" : " " + unit);
        }

        private String formatPrice(int n) {
            double v = row.getPrice()[n - 1];
            return v != 0.0 ? String.format("%,.2f", v) : "";
        }

        private String formatDelta(int n) {
            double v = row.getPrice()[n - 1];
            if (row.isAbsolutePrice()) {
                // ZTRA puro: prezzo assoluto senza parentesi e senza segno
                return v != 0.0 ? String.format("%,.2f", v) : "—";
            }
            // FULL: zona k* tra parentesi, le altre con delta e segno
            if (row.isPreferredZone())
                return v == 0.0 ? "" : String.format("(%,.2f)", v);
            return v != 0.0 ? String.format("%+,.2f", v) : "—";
        }

        public String getCurrency() {
            if (row.isCustomerRow() || row.isHeaderRow()) return "";
            return nvl(row.getCurrency());
        }

        public String getConditionQtyFormatted() {
            if (row.isCustomerRow() || row.isHeaderRow()) return "";
            return row.getConditionQty() > 0
                ? String.format("%.0f", row.getConditionQty()) : "";
        }

        public String getConditionUnit() {
            if (row.isCustomerRow() || row.isHeaderRow()) return "";
            return nvl(row.getConditionUnit());
        }

        public String getUnitWarningFont() {
            if (!row.isMaterialRow()) return "";
            return row.isUnitMismatch() ? FONT_UM_WARNING : "";
        }

        public String getValidFromFormatted() {
            if (row.isCustomerRow() || row.isHeaderRow()) return "";
            return row.getValidFrom() != null ? row.getValidFrom().format(FMT_DATE) : "";
        }

        public String getValidToFormatted() {
            if (row.isCustomerRow() || row.isHeaderRow()) return "";
            return row.getValidTo() != null ? row.getValidTo().format(FMT_DATE) : "";
        }

        public String getCustomerMaterialCode() {
            if (!row.isMaterialRow()) return "";
            return row.getCustomerMaterialCode();
        }

        private String nvl(String s) { return s != null ? s : ""; }
        private String nvl(String a, String b) {
            return (a != null && !a.isBlank()) ? a : (b != null ? b : "");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Costruttore — inizializzazione di tutti i campi m_
    // ═══════════════════════════════════════════════════════════════════════
    public ListinoBean() {
        m_gridListino        = new FIXGRIDListBinding<>();
        m_priceGroupInput    = "";
        m_customerInput      = "";
        m_materialInput      = "";
        m_referenceDateInput = new Date();
        m_statusMessage      = "";
        m_hasWarnings        = false;
        m_awaitingConfirm    = false;
        m_pendingCustomers   = new ArrayList<>();
        m_pendingExtractMode = ExtractMode.FULL;

        m_priceGroupProvider = new PriceGroupACProvider();
        m_customerProvider   = new CustomerACProvider();
        m_materialProvider   = new MaterialACProvider();

        AutoCompleteMgr.add(m_priceGroupProvider);
        AutoCompleteMgr.add(m_customerProvider);
        AutoCompleteMgr.add(m_materialProvider);
    }

    @Override
    public String getPageName()                 { return "/conditionssd/listino/main.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.ListinoBean}"; }

    // ═══════════════════════════════════════════════════════════════════════
    // Azioni
    // ═══════════════════════════════════════════════════════════════════════
    public void onExtract(ActionEvent event)     { startExtract(ExtractMode.FULL); }
    public void onExtractPPR0(ActionEvent event) { startExtract(ExtractMode.PPR0); }
    public void onExtractZTRA(ActionEvent event) { startExtract(ExtractMode.ZTRA); }

    private void startExtract(ExtractMode mode) {
        boolean noCustomer   = m_customerInput   == null || m_customerInput.isBlank();
        boolean noMaterial   = m_materialInput   == null || m_materialInput.isBlank();
        boolean noPriceGroup = m_priceGroupInput == null || m_priceGroupInput.isBlank();
        if (noCustomer && noMaterial && noPriceGroup) {
            m_statusMessage = "Inserire almeno un criterio di ricerca.";
            m_hasWarnings   = true;
            return;
        }
        m_pendingExtractMode = mode;
        if (!noPriceGroup) {
            doPreviewPriceGroup();
        } else {
            doExtract();
        }
    }

    /**
     * Fase 1: risolve i clienti del Price Group e mostra l'anteprima nella griglia.
     */
    private void doPreviewPriceGroup() {
        try {
            m_statusMessage   = "";
            m_hasWarnings     = false;
            m_awaitingConfirm = false;
            m_pendingCustomers = new ArrayList<>();
            m_gridListino.getItems().clear();

            S4Config cfg = S4Config.fromCCConfig();
            CustomerClient client = new CustomerClient(new S4HttpClient(cfg));
            java.util.Map<String, CustomerClient.CustomerInfo> customers =
                client.loadCustomersByPriceGroup(m_priceGroupInput.strip());

            if (customers.isEmpty()) {
                m_statusMessage = "Nessun cliente trovato per il Price Group '"
                    + m_priceGroupInput.strip() + "'.";
                m_hasWarnings = true;
                return;
            }

            // Popola la griglia con la lista clienti del gruppo
            m_pendingCustomers = new ArrayList<>(customers.values());
            for (CustomerClient.CustomerInfo info : m_pendingCustomers) {
                ListinoRow row = new ListinoRow();
                row.setRowType(ListinoRow.RowType.CUSTOMER);
                row.setCustomerCode(info.getCode());
                row.setCustomerName(info.getCode() + " — " + info.getName());
                m_gridListino.getItems().add(new GridListinoItem(row));
            }

            m_awaitingConfirm = true;
            String modeLabel = m_pendingExtractMode == ExtractMode.PPR0 ? "Listino Materiali"
                             : m_pendingExtractMode == ExtractMode.ZTRA ? "Listino Trasporti"
                             : "Listino Completo";
            m_statusMessage = modeLabel + " — Price Group '" + m_priceGroupInput.strip()
                + "': trovati " + m_pendingCustomers.size()
                + " clienti. Confermare l'estrazione?";

        } catch (Exception e) {
            m_statusMessage = "Errore anteprima gruppo: " + e.getMessage();
            e.printStackTrace();
        }
    }

    /**
     * Fase 2: estrae il listino completo per i clienti del gruppo già risolti.
     */
    public void onConfirm(ActionEvent event) {
        if (!m_awaitingConfirm || m_pendingCustomers.isEmpty()) return;
        m_awaitingConfirm = false;
        doExtract();
    }

    public void onReset(ActionEvent event) {
        m_priceGroupInput    = "";
        m_customerInput      = "";
        m_materialInput      = "";
        m_referenceDateInput = new Date();
        m_statusMessage      = "";
        m_hasWarnings        = false;
        m_awaitingConfirm    = false;
        m_pendingCustomers   = new ArrayList<>();
        m_pendingExtractMode = ExtractMode.FULL;
        m_gridListino.getItems().clear();
    }

    private void doExtract() {
        try {
            m_statusMessage = "";
            m_hasWarnings   = false;
            m_gridListino.getItems().clear();

            // Se arrivati dalla conferma del gruppo, i clienti sono già risolti
            List<String> resolvedCustomers = new ArrayList<>();
            if (!m_pendingCustomers.isEmpty()) {
                for (CustomerClient.CustomerInfo info : m_pendingCustomers)
                    resolvedCustomers.add(info.getCode());
            }

            ExtractParams params = ExtractParams.builder()
                .customers(resolvedCustomers.isEmpty()
                    ? parseTokens(m_customerInput) : resolvedCustomers)
                .materials(parseTokens(m_materialInput))
                .referenceDate(toLocalDate(m_referenceDateInput))
                .priceGroup(m_priceGroupInput != null && !m_priceGroupInput.isBlank()
                    ? m_priceGroupInput.strip() : null)
                .extractMode(m_pendingExtractMode)
                .build();

            ListinoExtractor extr = new ListinoExtractor(S4Config.fromCCConfig());
            List<ListinoRow> rows = extr.extract(params);
            List<String> warnings = extr.getLastWarnings();

            for (ListinoRow row : rows)
                m_gridListino.getItems().add(new GridListinoItem(row));

            long matRows  = rows.stream().filter(ListinoRow::isMaterialRow).count();
            long mismatch = rows.stream()
                .filter(r -> r.isMaterialRow() && r.isUnitMismatch()).count();
            m_hasWarnings = !warnings.isEmpty() || mismatch > 0;

            if (rows.isEmpty()) {
                m_statusMessage = "Nessun dato trovato.";
            } else {
                m_statusMessage = "Listino estratto: " + matRows + " righe materiale.";
                if (mismatch > 0)
                    m_statusMessage += "  ⚠ " + mismatch
                        + " righe con UM divergente (evidenziate in rosso).";
                else if (!warnings.isEmpty())
                    m_statusMessage += "  " + warnings.size() + " avvisi.";
            }

            m_pendingCustomers = new ArrayList<>();

        } catch (Exception e) {
            m_statusMessage = "Errore: " + e.getMessage();
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers privati
    // ═══════════════════════════════════════════════════════════════════════
    private List<String> parseTokens(String input) {
        if (input == null || input.isBlank()) return List.of();
        return Arrays.stream(input.split("[,;\\s]+"))
                     .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) return LocalDate.now();
        return date.toInstant()
                   .atZone(java.time.ZoneId.systemDefault())
                   .toLocalDate();
    }

    private String extractCode(String v) {
        if (v == null) return "";
        int sep = v.indexOf(" — ");
        return sep > 0 ? v.substring(0, sep).trim() : v.trim();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Getters / Setters
    // ═══════════════════════════════════════════════════════════════════════
    public FIXGRIDListBinding<GridListinoItem> getGridListino() { return m_gridListino; }

    public String  getPriceGroupInput()         { return m_priceGroupInput; }
    public void    setPriceGroupInput(String v) { m_priceGroupInput = extractCode(v != null ? v.strip() : ""); }

    public String  getCustomerInput()           { return m_customerInput; }
    public void    setCustomerInput(String v)   { m_customerInput = extractCode(v); }

    public String  getMaterialInput()           { return m_materialInput; }
    public void    setMaterialInput(String v)   { m_materialInput = extractCode(v); }

    public Date    getReferenceDateInput()          { return m_referenceDateInput; }
    public void    setReferenceDateInput(Date v)    { m_referenceDateInput = v; }

    public String  getStatusMessage()           { return m_statusMessage; }
    public boolean isHasWarnings()              { return m_hasWarnings; }
    public boolean isAwaitingConfirm()          { return m_awaitingConfirm; }

    public String  getPriceGroupACURL()         { return m_priceGroupProvider.getURL(); }
    public String  getCustomerACURL()           { return m_customerProvider.getURL(); }
    public String  getMaterialACURL()           { return m_materialProvider.getURL(); }
}
