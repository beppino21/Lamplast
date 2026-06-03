package eOne.conditionsSD.view.managedbeans;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;
import java.util.Calendar;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.pagebean.PageBean;

import eOne.conditionsSD.logic.ListinoExtractor;
import eOne.conditionsSD.model.ExtractParams;
import eOne.conditionsSD.model.ListinoRow;
import eOne.conditionsSD.model.ListinoRow.RowType;
import eOne.conditionsSD.s4client.S4Config;

@CCGenClass(expressionBase = "#{d.ListinoBean}")
public class ListinoBean extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Font per tipo riga
    private static final String FONT_CUSTOMER    = "weight:bold;size:13;color:#1565C0";
    private static final String FONT_SCALE_HDR   = "weight:bold;color:#1565C0";
    private static final String FONT_ZONE_HDR    = "weight:bold;color:#555555";
    private static final String FONT_MATERIAL    = "";
    private static final String FONT_ZONE_REF    = "color:#007700";
    private static final String FONT_ZONE        = "color:#888888";
    private static final String FONT_UM_WARNING  = "color:#cc0000;weight:bold";
    private static final String FONT_ALERT_HDR   = "weight:bold;color:#cc6600;size:13";
    private static final String FONT_ALERT       = "color:#cc6600";

    private FIXGRIDListBinding<GridListinoItem> m_gridListino = new FIXGRIDListBinding<>();

    private String  m_customerInput      = "";
    private String  m_materialInput      = "";
    private Date m_referenceDateInput = new Date();
    private String  m_statusMessage      = "";
    private boolean m_hasWarnings        = false;

    // ═══════════════════════════════════════════════════════════════════════
    // Inner class griglia
    // ═══════════════════════════════════════════════════════════════════════
    public class GridListinoItem extends FIXGRIDItem implements Serializable {

        private static final long serialVersionUID = 1L;
        private final ListinoRow row;

        public GridListinoItem(ListinoRow row) { this.row = row; }

        // ── Font ────────────────────────────────────────────────────────
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

        // ── Descrizione ──────────────────────────────────────────────────
        public String getDescription() {
            if (row.getRowType() == RowType.CUSTOMER)
                return row.getCustomerCode() + "  —  " + row.getCustomerName();
            return nvl(row.getDescription());
        }

        // ── Colonne scaglione ─────────────────────────────────────────────
        // HEADER_SCALE → soglia "≥ X TO" o "Base"
        // MATERIAL     → prezzo PPR0+ZTRA merged
        // ZONE         → delta con segno
        // Altre        → vuoto

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
                // Slot senza soglia: flat in colonna 5
                if (n == 5) return unit.trim().isEmpty() ? "Qualsiasi" : "Qualsiasi (" + unit + ")";
                return "";
            }
            // Soglia "fino a X TO" (Descending)
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
            if (row.isPreferredZone()) return v == 0.0 ? "—" : String.format("%+,.2f", v);
            return v != 0.0 ? String.format("%+,.2f", v) : "—";
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

        private String nvl(String s) { return s != null ? s : ""; }
        private String nvl(String a, String b) {
            return (a != null && !a.isBlank()) ? a : (b != null ? b : "");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PageBean
    // ═══════════════════════════════════════════════════════════════════════
    public ListinoBean() {}

    @Override
    public String getPageName()                 { return "/conditionssd/listino/main.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.ListinoBean}"; }

    // ═══════════════════════════════════════════════════════════════════════
    // Azioni
    // ═══════════════════════════════════════════════════════════════════════
    public void onExtract(ActionEvent event) {
        if (m_customerInput == null || m_customerInput.isBlank()) {
            m_statusMessage = "Inserire almeno un codice cliente (es: 54  oppure  11 39 54).";
            m_hasWarnings   = true;
            return;
        }
        doExtract();
    }

    public void onReset(ActionEvent event) {
        m_customerInput      = "";
        m_materialInput      = "";
        m_referenceDateInput = new Date();
        m_statusMessage      = "";
        m_hasWarnings        = false;
        m_gridListino.getItems().clear();
    }

    private void doExtract() {
        try {
            m_statusMessage = "";
            m_hasWarnings   = false;
            m_gridListino.getItems().clear();

            ExtractParams params = ExtractParams.builder()
                .customers(parseTokens(m_customerInput))
                .materials(parseTokens(m_materialInput))
                .referenceDate(toLocalDate(m_referenceDateInput))
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

        } catch (Exception e) {
            m_statusMessage = "Errore: " + e.getMessage();
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
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

    // ═══════════════════════════════════════════════════════════════════════
    // Getters / Setters
    // ═══════════════════════════════════════════════════════════════════════
    public FIXGRIDListBinding<GridListinoItem> getGridListino() { return m_gridListino; }
    public String  getCustomerInput()              { return m_customerInput; }
    public void    setCustomerInput(String v)       { m_customerInput = v; }
    public String  getMaterialInput()              { return m_materialInput; }
    public void    setMaterialInput(String v)       { m_materialInput = v; }
    public Date   getReferenceDateInput()          { return m_referenceDateInput; }
    public void   setReferenceDateInput(Date v)    { m_referenceDateInput = v; }
    public String  getStatusMessage()              { return m_statusMessage; }
    public boolean isHasWarnings()                 { return m_hasWarnings; }
}
