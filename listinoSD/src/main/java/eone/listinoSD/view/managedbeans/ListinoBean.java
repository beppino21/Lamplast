package eone.listinoSD.view.managedbeans;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.listinoSD.logic.ListinoExtractor;
import eone.listinoSD.model.ExtractParams;
import eone.listinoSD.model.ListinoRow;
import eone.listinoSD.model.ListinoRow.RowType;
import eone.listinoSD.s4client.S4Config;

@CCGenClass(expressionBase = "#{d.ListinoBean}")
public class ListinoBean extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Font per tipo riga
    private static final String FONT_CUSTOMER  = "weight:bold;size:13;color:#1565C0";
    private static final String FONT_HEADER    = "weight:bold;color:#555555";
    private static final String FONT_MATERIAL  = "";
    private static final String FONT_ZONE_REF  = "color:#007700";
    private static final String FONT_ZONE      = "color:#888888";

    private FIXGRIDListBinding<GridListinoItem> m_gridListino = new FIXGRIDListBinding<>();
    private String  m_customerInput      = "";
    private String  m_materialInput      = "";
    private String  m_referenceDateInput = LocalDate.now().toString();
    private String  m_statusMessage      = "";
    private boolean m_hasWarnings        = false;

    // ═══════════════════════════════════════════════════════════════════════
    // Inner class griglia
    // ═══════════════════════════════════════════════════════════════════════
    public class GridListinoItem extends FIXGRIDItem implements Serializable {

        private static final long serialVersionUID = 1L;
        private final ListinoRow row;

        public GridListinoItem(ListinoRow row) { this.row = row; }

        public String getRowFont() {
            switch (row.getRowType()) {
                case CUSTOMER:    return FONT_CUSTOMER;
                case HEADER_MAT:
                case HEADER_ZONE: return FONT_HEADER;
                case MATERIAL:    return FONT_MATERIAL;
                case ZONE:        return row.isPreferredZone() ? FONT_ZONE_REF : FONT_ZONE;
                default:          return "";
            }
        }

        public String getDescription() {
            if (row.getRowType() == RowType.CUSTOMER)
                return row.getCustomerCode() + "  —  " + row.getCustomerName();
            return nvl(row.getDescription());
        }

        // ── Colonne scaglione: una sola stringa per cella ─────────────────
        // Riga HEADER_MAT  → mostra la soglia (es. "≥ 100" o "Base")
        // Riga MATERIAL    → mostra il prezzo sommato PPR0+ZTRA
        // Riga ZONE        → mostra il delta ZTRA
        // Altre            → vuoto

        public String getCol1() { return buildCol(1); }
        public String getCol2() { return buildCol(2); }
        public String getCol3() { return buildCol(3); }
        public String getCol4() { return buildCol(4); }
        public String getCol5() { return buildCol(5); }

        private String buildCol(int n) {
            switch (row.getRowType()) {
                case HEADER_MAT:
                    return formatScaleQty(n);
                case MATERIAL:
                    return formatPrice(n);
                case ZONE:
                    return formatDelta(n);
                default:
                    return "";
            }
        }

        private String formatScaleQty(int n) {
            double q = row.getScaleQty()[n - 1];
            if (q > 0)   return String.format("≥ %.0f", q);
            // slot vuoti: solo ultimo slot attivo è "Base"
            // cerco l'ultimo slot con qty > 0
            boolean anyBefore = false;
            for (int i = 0; i < n - 1; i++)
                if (row.getScaleQty()[i] > 0) anyBefore = true;
            if (!anyBefore && n == 1) return "Base";
            return "";
        }

        private String formatPrice(int n) {
            double v = row.getPrice()[n - 1];
            return v != 0.0 ? String.format("%,.2f", v) : "";
        }

        private String formatDelta(int n) {
            double v = row.getPrice()[n - 1];
            if (row.isPreferredZone()) return v == 0.0 ? "—" : String.format("%+,.2f", v);
            return v != 0.0 ? String.format("%+,.2f", v) : "";
        }

        // ── Metadati ─────────────────────────────────────────────────────

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

        public String getValidFromFormatted() {
            if (row.isCustomerRow() || row.isHeaderRow()) return "";
            return row.getValidFrom() != null ? row.getValidFrom().format(FMT_DATE) : "";
        }

        public String getValidToFormatted() {
            if (row.isCustomerRow() || row.isHeaderRow()) return "";
            return row.getValidTo() != null ? row.getValidTo().format(FMT_DATE) : "";
        }

        private String nvl(String s) { return s != null ? s : ""; }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PageBean
    // ═══════════════════════════════════════════════════════════════════════
    public ListinoBean() {}

    @Override
    public String getPageName()                 { return "/listinosd/listino/main.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.ListinoBean}"; }

    // ═══════════════════════════════════════════════════════════════════════
    // Azioni
    // ═══════════════════════════════════════════════════════════════════════
    public void onExtract(ActionEvent event) {
        try {
            m_statusMessage = "";
            m_hasWarnings   = false;
            m_gridListino.getItems().clear();

            ExtractParams params  = buildParams();
            ListinoExtractor extr = new ListinoExtractor(S4Config.fromCCConfig());
            List<ListinoRow> rows = extr.extract(params);
            List<String> warnings = extr.getLastWarnings();

            for (ListinoRow row : rows)
                m_gridListino.getItems().add(new GridListinoItem(row));

            long matRows = rows.stream().filter(ListinoRow::isMaterialRow).count();
            m_hasWarnings = !warnings.isEmpty();

            if (rows.isEmpty())
                m_statusMessage = "Nessun dato trovato.";
            else if (m_hasWarnings)
                m_statusMessage = "Completato con " + warnings.size() + " avvisi — "
                    + matRows + " righe materiale.";
            else
                m_statusMessage = "Listino estratto: " + matRows + " righe materiale.";

        } catch (Exception e) {
            m_statusMessage = "Errore: " + e.getMessage();
            e.printStackTrace();
        }
    }

    public void onReset(ActionEvent event) {
        m_customerInput      = "";
        m_materialInput      = "";
        m_referenceDateInput = LocalDate.now().toString();
        m_statusMessage      = "";
        m_hasWarnings        = false;
        m_gridListino.getItems().clear();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════
    private ExtractParams buildParams() {
        return ExtractParams.builder()
            .customers(parseTokens(m_customerInput))
            .materials(parseTokens(m_materialInput))
            .referenceDate(parseDate(m_referenceDateInput))
            .build();
    }

    private List<String> parseTokens(String input) {
        if (input == null || input.isBlank()) return List.of();
        return Arrays.stream(input.split("[,;\\s]+"))
                     .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private LocalDate parseDate(String input) {
        try { return LocalDate.parse(input.trim()); }
        catch (Exception e) { return LocalDate.now(); }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Getters / Setters
    // ═══════════════════════════════════════════════════════════════════════
    public FIXGRIDListBinding<GridListinoItem> getGridListino() { return m_gridListino; }
    public String getCustomerInput()              { return m_customerInput; }
    public void   setCustomerInput(String v)       { m_customerInput = v; }
    public String getMaterialInput()              { return m_materialInput; }
    public void   setMaterialInput(String v)       { m_materialInput = v; }
    public String getReferenceDateInput()          { return m_referenceDateInput; }
    public void   setReferenceDateInput(String v)  { m_referenceDateInput = v; }
    public String getStatusMessage()              { return m_statusMessage; }
    public boolean isHasWarnings()                { return m_hasWarnings; }
}
