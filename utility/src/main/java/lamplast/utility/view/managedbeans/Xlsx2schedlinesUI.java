package lamplast.utility.view.managedbeans;

import java.io.Serializable;
import java.util.List;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.defaultscreens.OKPopup;
import org.eclnt.jsfserver.defaultscreens.Statusbar;
import org.eclnt.jsfserver.elements.events.BaseActionEventUpload;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.pagebean.PageBean;

import lamplast.utility.config.SapConfiguration;
import lamplast.utility.model.ScheduleLineData;
import lamplast.utility.service.ExcelParser;
import lamplast.utility.service.SapResponse;
import lamplast.utility.service.SapScheduleLineService;

@CCGenClass(expressionBase = "#{d.Xlsx2schedlinesUI}")
public class Xlsx2schedlinesUI extends PageBean implements Serializable {

    // =========================
    // SERVIZI
    // =========================

    private SapConfiguration        sapConfig;
    private SapScheduleLineService  sapService;
    private ExcelParser             excelParser;

    // =========================
    // DATI UI
    // =========================

    private FIXGRIDListBinding<GridJSONdataItem> m_gridJSONdata = new FIXGRIDListBinding<>();

    // Link VA03 - App Fiori Manage Sales Order
    private Boolean m_enableVA03           = false;
    private String  m_salesOrderNumberVA03;

    // Link FioriVA03 - FactSheet Fiori (read-only)
    private Boolean m_enableFioriVA03      = false;
    private String  m_salesOrderNumberFiori;

    private String  m_fileName;
    private String  m_logText = "Nuova sessione";

    // Label colonne — lette da config.properties tramite SapConfiguration
    String m_lblOrdine;
    String m_lblPosizione;
    String m_lblSchedulazione;
    String m_lblMateriale;
    String m_lblMaterialeText;
    String m_lblQuantita;
    String m_lblDataProd;

    // Dati caricati dal file
    private List<ScheduleLineData> scheduleLines;

    // =========================
    // INNER CLASS GRID
    // =========================

    public class GridJSONdataItem extends FIXGRIDItem implements Serializable {

        private ScheduleLineData data;

        public GridJSONdataItem(ScheduleLineData data) {
            this.data = data;
        }

        public String getOrderNumber()      { return data.getOrderNumber(); }
        public String getItemNumber()       { return data.getItemNumber()   != null ? data.getItemNumber().toString()   : ""; }
        public String getSchedLine()        { return data.getScheduleLine() != null ? data.getScheduleLine().toString() : ""; }
        public String getMaterial()         { return data.getMaterial(); }
        public String getMaterialText()     { return data.getMaterialText(); }
        public String getQuantity()         { return data.getQuantity(); }

        public String getSchedDate() {
            return data.getProductionDate() != null
                ? data.getProductionDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "";
        }

        public String getProcessingResult() {
            return data.getProcessingResult() != null ? data.getProcessingResult() : "";
        }

        public String getErrorMessage() {
            return data.getErrorMessage() != null ? data.getErrorMessage() : "";
        }

        public void onRowSelect()  { showOrderDetails(data.getOrderNumber()); }
        public void onRowExecute() { showOrderDetails(data.getOrderNumber()); }

        private void showOrderDetails(String orderNumber) {
            Statusbar.outputSuccess("Ordine selezionato: " + orderNumber);

            // Link VA03 - App Fiori Manage Sales Order (transazionale)
            m_salesOrderNumberVA03   = sapConfig.getFullUrlVa03(orderNumber);
            m_enableVA03             = true;

            // Link FioriVA03 - FactSheet Fiori (read-only)
            m_salesOrderNumberFiori  = sapConfig.getFullUrlFiori(orderNumber);
            m_enableFioriVA03        = true;
        }
    }

    // =========================
    // COSTRUTTORE
    // =========================

    public Xlsx2schedlinesUI() {
        try {
            this.sapConfig  = new SapConfiguration();
            this.sapService = new SapScheduleLineService(sapConfig);

            m_lblOrdine        = sapConfig.getColOrdine();
            m_lblPosizione     = sapConfig.getColPosizione();
            m_lblSchedulazione = sapConfig.getColSchedulazione();
            m_lblMateriale     = sapConfig.getColMateriale();
            m_lblMaterialeText = sapConfig.getColMaterialeText();
            m_lblQuantita      = sapConfig.getColQuantita();
            m_lblDataProd      = sapConfig.getColDataProd();

        } catch (Exception e) {
            m_logText = "ERRORE CONFIGURAZIONE: " + e.getMessage()
                      + " — verificare che config.properties sia in src/main/resources/";
            Statusbar.outputAlert(m_logText);
        }

        m_logText = "Nuova sessione";
    }

    // =========================
    // GESTORI EVENTI
    // =========================

    public void onLoadXLSX(ActionEvent ae) {

        if (sapConfig == null) {
            Statusbar.outputAlert("Configurazione non disponibile — verificare config.properties");
            return;
        }

        ExcelParser.ColumnMapping mapping = new ExcelParser.ColumnMapping();
        mapping.orderColumn        = m_lblOrdine;
        mapping.itemColumn         = m_lblPosizione;
        mapping.scheduleColumn     = m_lblSchedulazione;
        mapping.materialColumn     = m_lblMateriale;
        mapping.materialTextColumn = m_lblMaterialeText;
        mapping.quantityColumn     = m_lblQuantita;
        mapping.dateColumn         = m_lblDataProd;

        this.excelParser = new ExcelParser(mapping);

        // Reset stato
        m_gridJSONdata.getItems().clear();
        m_salesOrderNumberVA03  = "";
        m_salesOrderNumberFiori = "";
        m_enableVA03            = false;
        m_enableFioriVA03       = false;
        m_logText               = "Nuova sessione";
        scheduleLines           = null;

        if (!(ae instanceof BaseActionEventUpload)) return;

        BaseActionEventUpload bae = (BaseActionEventUpload) ae;
        m_fileName = bae.getClientFileName();

        try {
            byte[] excelBytes = hexStringToByteArray(bae.getHexByteString());
            scheduleLines = excelParser.parseExcel(excelBytes);

            for (ScheduleLineData data : scheduleLines) {
                m_gridJSONdata.getItems().add(new GridJSONdataItem(data));
            }

            Statusbar.outputMessage("File " + m_fileName + " caricato: "
                + scheduleLines.size() + " righe");

        } catch (Exception e) {
            Statusbar.outputAlert("Errore caricamento file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void onUpdSchedLine(ActionEvent event) {

        if (!m_logText.equalsIgnoreCase("Nuova sessione")) {
            OKPopup.createInstance("", "Sessione modificata, elaborazione non più possibile");
            return;
        }

        if (scheduleLines == null || scheduleLines.isEmpty()) {
            OKPopup.createInstance("", "Nessun file di input è stato caricato");
            return;
        }

        StringBuilder log = new StringBuilder();
        log.append("Inizio elaborazione\n");
        log.append("=====================\n\n");

        int successi = 0;
        int errori   = 0;

        for (ScheduleLineData data : scheduleLines) {
            try {
                log.append("Elaborazione: ").append(data.toString()).append("\n");

                String validationError = data.validate();
                if (validationError != null) {
                    log.append("  ⚠ Errore validazione: ").append(validationError).append("\n\n");
                    data.setProcessingResult("⚠ Errore");
                    data.setErrorMessage("Validazione: " + validationError);
                    errori++;
                    continue;
                }

                SapResponse response = sapService.updateScheduleLine(data);

                if (response.isSuccess()) {
                    log.append("  ✓ Successo (HTTP ").append(response.getHttpStatus()).append(")\n");
                    data.setProcessingResult("✓ Successo");
                    data.setErrorMessage(null);

                    if (response.isWarning()) {
                        log.append("  ⚠ Warning: ").append(response.getSapMessage()).append("\n");
                        data.setProcessingResult("✓ Successo  ⚠ Warning");
                        data.setErrorMessage("HTTP " + response.getHttpStatus());
                    }
                    successi++;
                } else {
                    log.append("  ✗ Errore (HTTP ").append(response.getHttpStatus()).append(")\n");
                    data.setProcessingResult("✗ Errore");
                    data.setErrorMessage("HTTP " + response.getHttpStatus());
                    if (response.getSapMessage() != null)
                        log.append("    SAP: ").append(response.getSapMessage()).append("\n");
                    if (response.getSapCode() != null)
                        log.append("    Codice: ").append(response.getSapCode()).append("\n");
                    errori++;
                }

                log.append("\n");

            } catch (Exception e) {
                log.append("  ✗ Eccezione: ").append(e.getMessage()).append("\n\n");
                data.setProcessingResult("✗ Eccezione");
                data.setErrorMessage("Eccezione: " + e.getMessage());
                errori++;
            }
        }

        log.append("=====================\n");
        log.append("Elaborazione completata\n");
        log.append("Successi: ").append(successi).append("\n");
        log.append("Errori: ").append(errori).append("\n");

        m_logText = log.toString();
        Statusbar.outputMessage("Elaborazione completata: " + successi + " OK, " + errori + " KO");
    }

    // =========================
    // UTILITY
    // =========================

    private byte[] hexStringToByteArray(String hex) {
        int len     = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i),   16) << 4)
                                +  Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    // =========================
    // GETTERS / SETTERS
    // =========================

    public FIXGRIDListBinding<GridJSONdataItem> getGridJSONdata() { return m_gridJSONdata; }

    // Link VA03
    public Boolean getEnableVA03()              { return m_enableVA03; }
    public void    setEnableVA03(Boolean v)     { this.m_enableVA03 = v; }
    public String  getSalesOrderNumber()        { return m_salesOrderNumberVA03; }
    public void    setSalesOrderNumber(String v){ this.m_salesOrderNumberVA03 = v; }

    // Link FioriVA03
    public Boolean getEnableFioriVA03()              { return m_enableFioriVA03; }
    public void    setEnableFioriVA03(Boolean v)     { this.m_enableFioriVA03 = v; }
    public String  getSalesOrderNumberFiori()        { return m_salesOrderNumberFiori; }
    public void    setSalesOrderNumberFiori(String v){ this.m_salesOrderNumberFiori = v; }

    // Label colonne
    public String  getLblOrdine()           { return m_lblOrdine; }
    public void    setLblOrdine(String v)   { this.m_lblOrdine = v; }
    public String  getLblPosizione()           { return m_lblPosizione; }
    public void    setLblPosizione(String v)   { this.m_lblPosizione = v; }
    public String  getLblSchedulazione()           { return m_lblSchedulazione; }
    public void    setLblSchedulazione(String v)   { this.m_lblSchedulazione = v; }
    public String  getLblMateriale()           { return m_lblMateriale; }
    public void    setLblMateriale(String v)   { this.m_lblMateriale = v; }
    public String  getLblMaterialeText()           { return m_lblMaterialeText; }
    public void    setLblMaterialeText(String v)   { this.m_lblMaterialeText = v; }
    public String  getLblQuantita()           { return m_lblQuantita; }
    public void    setLblQuantita(String v)   { this.m_lblQuantita = v; }
    public String  getLblDataProd()           { return m_lblDataProd; }
    public void    setLblDataProd(String v)   { this.m_lblDataProd = v; }

    // Altri
    public String  getLogText()          { return m_logText; }
    public void    setLogText(String v)  { this.m_logText = v; }
    public String  getFileName()         { return m_fileName; }
    public void    setFileName(String v) { this.m_fileName = v; }

    // =========================
    // PAGEBEAN OVERRIDES
    // =========================

    public String getPageName()                 { return "/xlsx2schedlines.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.Xlsx2schedlinesUI}"; }
}
