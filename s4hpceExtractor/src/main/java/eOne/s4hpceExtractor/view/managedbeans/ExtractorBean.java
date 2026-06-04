package eOne.s4hpceExtractor.view.managedbeans;

import eOne.s4hpceExtractor.model.ConditionRecord;
import eOne.s4hpceExtractor.model.TaxRecord;
import eOne.s4hpceExtractor.s4client.PricingExtractClient;
import eOne.s4hpceExtractor.s4client.S4Config;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.pagebean.PageBean;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@CCGenClass(expressionBase = "#{d.ExtractorBean}")
public class ExtractorBean extends PageBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ═══════════════════════════════════════════════════════════════════════
    // Campi m_
    // ═══════════════════════════════════════════════════════════════════════
    private FIXGRIDListBinding<GridConditionItem> m_grid;
    private FIXGRIDListBinding<GridTaxItem>       m_gridTax;
    private Date    m_referenceDateInput;
    private String  m_statusMessage;
    private boolean m_hasWarnings;

    // ═══════════════════════════════════════════════════════════════════════
    // Inner class griglia PPR0/ZTRA
    // ═══════════════════════════════════════════════════════════════════════
    public class GridConditionItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final ConditionRecord rec;

        public GridConditionItem(ConditionRecord rec) { this.rec = rec; }

        public String getConditionType()  { return nvl(rec.getConditionType()); }
        public String getCustomer()       { return nvl(rec.getCustomer()); }
        public String getMaterial()       { return nvl(rec.getMaterial()); }
        public String getSalesDistrict()  { return nvl(rec.getSalesDistrict()); }
        public String getScaleQtyFrom()   { return formatQty(rec.getScaleQtyFrom()); }
        public String getScaleQtyTo()     { return formatQty(rec.getScaleQtyTo()); }
        public String getScaleUnit()      { return nvl(rec.getScaleUnit()); }
        public String getPrice()          { return formatPrice(rec.getPrice()); }
        public String getCurrency()       { return nvl(rec.getCurrency()); }
        public String getConditionQty()   { return formatQty(rec.getConditionQty()); }
        public String getConditionUnit()  { return nvl(rec.getConditionUnit()); }
        public String getScaleType()      { return nvl(rec.getScaleType()); }
        public String getValidFrom()      { return nvl(rec.getValidFrom()); }
        public String getValidTo()        { return nvl(rec.getValidTo()); }

        private String nvl(String s)             { return s != null ? s : ""; }
        private String formatPrice(double v)     { return v != 0.0 ? String.format("%,.2f", v) : ""; }
        private String formatQty(double v) {
            if (v == 0.0) return "";
            return v == Math.floor(v)
                ? String.format("%.0f", v)
                : String.format("%.3f", v).replaceAll("0+$", "");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inner class griglia TTX1
    // ═══════════════════════════════════════════════════════════════════════
    public class GridTaxItem extends FIXGRIDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final TaxRecord rec;

        public GridTaxItem(TaxRecord rec) { this.rec = rec; }

        public String getConditionType()      { return nvl(rec.getConditionType()); }
        public String getDepartureCountry()   { return nvl(rec.getDepartureCountry()); }
        public String getDestinationCountry() { return nvl(rec.getDestinationCountry()); }
        public String getCustomerTaxClass()   { return nvl(rec.getCustomerTaxClass()); }
        public String getProductTaxClass()    { return nvl(rec.getProductTaxClass()); }
        public String getTaxCode()            { return nvl(rec.getTaxCode()); }
        public String getValidFrom()          { return nvl(rec.getValidFrom()); }
        public String getValidTo()            { return nvl(rec.getValidTo()); }

        private String nvl(String s) { return s != null ? s : ""; }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Costruttore
    // ═══════════════════════════════════════════════════════════════════════
    public ExtractorBean() {
        m_grid               = new FIXGRIDListBinding<>();
        m_gridTax            = new FIXGRIDListBinding<>();
        m_referenceDateInput = new Date();
        m_statusMessage      = "";
        m_hasWarnings        = false;
    }

    @Override
    public String getPageName()                 { return "/s4hpceextractor/main/main.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.ExtractorBean}"; }

    // ═══════════════════════════════════════════════════════════════════════
    // Azioni
    // ═══════════════════════════════════════════════════════════════════════
    public void onExtractPPR0(ActionEvent event) { doExtract("PPR0"); }
    public void onExtractZTRA(ActionEvent event) { doExtract("ZTRA"); }
    public void onExtractTTX1(ActionEvent event) { doExtractTax(); }

    public void onReset(ActionEvent event) {
        m_grid.getItems().clear();
        m_gridTax.getItems().clear();
        m_referenceDateInput = new Date();
        m_statusMessage      = "";
        m_hasWarnings        = false;
    }

    private void doExtract(String conditionType) {
        try {
            m_statusMessage = "";
            m_hasWarnings   = false;
            m_grid.getItems().clear();
            m_gridTax.getItems().clear();

            LocalDate refDate = toLocalDate(m_referenceDateInput);
            S4Config cfg = S4Config.fromCCConfig();
            PricingExtractClient client = new PricingExtractClient(cfg);

            List<ConditionRecord> records = client.extract(conditionType, refDate);
            for (ConditionRecord rec : records)
                m_grid.getItems().add(new GridConditionItem(rec));

            m_statusMessage = conditionType + ": estratte " + records.size()
                + " righe (data rif. " + refDate.format(FMT) + ")";
            m_hasWarnings = records.isEmpty();

        } catch (Exception e) {
            m_statusMessage = "Errore estrazione: " + e.getMessage();
            m_hasWarnings   = true;
            e.printStackTrace();
        }
    }

    private void doExtractTax() {
        try {
            m_statusMessage = "";
            m_hasWarnings   = false;
            m_grid.getItems().clear();
            m_gridTax.getItems().clear();

            LocalDate refDate = toLocalDate(m_referenceDateInput);
            S4Config cfg = S4Config.fromCCConfig();
            PricingExtractClient client = new PricingExtractClient(cfg);

            List<TaxRecord> records = client.extractTTX1(refDate);
            for (TaxRecord rec : records)
                m_gridTax.getItems().add(new GridTaxItem(rec));

            m_statusMessage = "TTX1: estratte " + records.size()
                + " righe (data rif. " + refDate.format(FMT) + ")";
            m_hasWarnings = records.isEmpty();

        } catch (Exception e) {
            m_statusMessage = "Errore estrazione TTX1: " + e.getMessage();
            m_hasWarnings   = true;
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════
    private LocalDate toLocalDate(Date date) {
        if (date == null) return LocalDate.now();
        return date.toInstant()
                   .atZone(java.time.ZoneId.systemDefault())
                   .toLocalDate();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Getters / Setters
    // ═══════════════════════════════════════════════════════════════════════
    public FIXGRIDListBinding<GridConditionItem> getGrid()    { return m_grid; }
    public FIXGRIDListBinding<GridTaxItem>       getGridTax() { return m_gridTax; }

    public Date    getReferenceDateInput()       { return m_referenceDateInput; }
    public void    setReferenceDateInput(Date v) { m_referenceDateInput = v; }

    public String  getStatusMessage()            { return m_statusMessage; }
    public boolean isHasWarnings()               { return m_hasWarnings; }
}
