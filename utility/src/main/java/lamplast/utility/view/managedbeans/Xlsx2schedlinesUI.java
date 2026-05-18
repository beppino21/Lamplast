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
import lamplast.utility.service.SapScheduleLineService.SapDryRunResult;
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
    private String  m_logText    = "Nuova sessione";
    private boolean m_dryRunDone = false;

    // Label colonne — lette da config.properties tramite SapConfiguration
    String m_sheetName;
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

        /**
         * Tipo di operazione prevista — calcolata al caricamento del file:
         *   scheduleLine < 0                    → Inserimento nuova schedulazione
         *   scheduleLine >= 0 && quantity == 0  → Eliminazione schedulazione
         *   scheduleLine >= 0 && quantity > 0   → Modifica quantità/data
         */
        public String getAzione() {
            Integer sl = data.getScheduleLine();
            if (sl == null) return "";
            if (sl < 0) return "Inserimento";
            // Quantità zero o blank = eliminazione
            String qty = data.getQuantity();
            boolean qtyZero = (qty == null || qty.isBlank()
                    || qty.equals("0") || qty.equals("0.0")
                    || qty.equals("0,0") || qty.matches("0+[.,]?0*"));
            return qtyZero ? "Eliminazione" : "Modifica";
        }

        /**
         * Esito con icona Unicode:
         *  - Prima dell'elaborazione: blank (l'azione è già nella colonna Azione)
         *  - Dopo elaborazione OK:    ✅ Successo
         *  - Dopo elaborazione KO:    ❌ Errore
         *  - Dopo elaborazione warn:  ⚠️ Warning
         */
        public String getEsito() {
            String r = data.getProcessingResult();
            if (r == null || r.isBlank()) return "";
            if (r.startsWith("✅") || r.startsWith("✓")) return r;
            if (r.startsWith("❌") || r.startsWith("✗")) return r;
            if (r.startsWith("⚠"))                       return r;
            String rl = r.toLowerCase();
            if (rl.contains("successo") || rl.equals("ok")) return "✅ " + r;
            if (rl.contains("errore")   || rl.equals("ko")) return "❌ " + r;
            if (rl.contains("warning"))                     return "⚠️ " + r;
            return r;
        }

        /** Esito del dry-run — colonna separata, non sovrascritta dall'elaborazione reale. */
        public String getDryRunEsito() {
            String r = data.getDryRunResult();
            return r != null ? r : "";
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

            m_sheetName        = sapConfig.getSheetName();
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
        mapping.sheetName          = m_sheetName;
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
        m_dryRunDone            = false;
        scheduleLines           = null;

        if (!(ae instanceof BaseActionEventUpload)) return;

        BaseActionEventUpload bae = (BaseActionEventUpload) ae;
        m_fileName = bae.getClientFileName();

        try {
            byte[] excelBytes = hexStringToByteArray(bae.getHexByteString());
            scheduleLines = excelParser.parseExcel(excelBytes, m_fileName);

            for (ScheduleLineData data : scheduleLines) {
                m_gridJSONdata.getItems().add(new GridJSONdataItem(data));
            }

            String sheetNote = excelParser.getLastSheetNote();
            Statusbar.outputMessage("File " + m_fileName + " caricato: "
                + scheduleLines.size() + " righe — " + sheetNote);

        } catch (Exception e) {
            Statusbar.outputAlert("Errore caricamento file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void onDryRun(ActionEvent event) {

        if (scheduleLines == null || scheduleLines.isEmpty()) {
            OKPopup.createInstance("", "Caricare prima un file Excel.");
            return;
        }

        int ok      = 0;
        int errori  = 0;
        int warning = 0;

        for (int i = 0; i < scheduleLines.size(); i++) {
            ScheduleLineData data = scheduleLines.get(i);
            try {
                SapDryRunResult result = sapService.dryRun(data);
                String dettaglio = result.getDettaglio();
                if ("NESSUNA_MODIFICA".equals(dettaglio)) {
                    data.setDryRunResult("⏭️ Nessuna modifica — qtà e data invariate, verrà saltata");
                    data.setProcessingResult("⏭️ Nessuna modifica");
                } else if ("EVASA".equals(dettaglio)) {
                    data.setDryRunResult("📦 Schedulazione già evasa (qtà open = 0) — verrà saltata");
                    data.setProcessingResult("📦 Già evasa");
                } else if (dettaglio != null && dettaglio.startsWith("BLOCCATA:")) {
                    String motivo;
                    if (dettaglio.equals("BLOCCATA:EVASA")) {
                        motivo = "schedulazione completamente evasa (qtà open = 0)";
                    } else if (dettaglio.startsWith("BLOCCATA:QTA_SOTTO_CONSEGNATO:")) {
                        String consegnato = dettaglio.substring("BLOCCATA:QTA_SOTTO_CONSEGNATO:".length());
                        motivo = "qtà richiesta inferiore al già consegnato (" + consegnato + ")";
                    } else if (dettaglio.startsWith("BLOCCATA:API_INACCESSIBILE")) {
                        motivo = "schedule line categoria CP/MRP non accessibile via API (bloccata)";
                    } else {
                        motivo = dettaglio.substring("BLOCCATA:".length());
                    }
                    data.setDryRunResult("⛔ Non modificabile — " + motivo);
                    data.setProcessingResult("⛔ Non modificabile");
                } else if (result.isError()) {
                    data.setDryRunResult("❌ " + (dettaglio != null ? dettaglio : result.getEsitoIcona()));
                    data.setProcessingResult("❌ Errore dry-run");
                } else {
                    data.setDryRunResult(result.getEsitoIcona());
                }
                if      (result.isOk())    ok++;
                else if (result.isError()) errori++;
                else                       warning++;
            } catch (Exception e) {
                data.setDryRunResult("❌ Eccezione: " + e.getMessage());
                errori++;
            }
        }

        m_dryRunDone = true;
        Statusbar.outputMessage("Dry-run completato: "
            + ok + " OK, " + warning + " warning, " + errori + " errori."
            + " — Verificare la colonna Esito prima di procedere.");
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

                // Salta righe che il dry-run ha marcato come non elaborabili.
                // Usiamo processingResult che ha valori controllati, non il testo
                // localizzato di dryRunResult che può cambiare.
                String procResult = data.getProcessingResult();
                if (procResult != null && (
                        procResult.contains("Nessuna modifica")
                     || procResult.contains("Già evasa")
                     || procResult.contains("Non modificabile")
                     || procResult.contains("Errore dry-run"))) {
                    log.append("  ⏭️ Saltata (dry-run): ").append(data.getOrderNumber())
                       .append("/").append(data.getItemNumber())
                       .append(" — ").append(procResult).append("\n");
                    continue;
                }

                String validationError = data.validate();
                if (validationError != null) {
                    log.append("  ⚠ Errore validazione: ").append(validationError).append("\n\n");
                    data.setProcessingResult("⚠ Errore");
                    data.setErrorMessage("Validazione: " + validationError);
                    errori++;
                    continue;
                }

                SapResponse response = sapService.updateScheduleLine(data);

                // Recupera l'azione prevista dalla grid item
                GridJSONdataItem item = (GridJSONdataItem)
                    m_gridJSONdata.getItems().get(scheduleLines.indexOf(data));
                String azione = item.getAzione();

                if (response.isSuccess()) {
                    if (response.isFrozen()) {
                        log.append("  ⛔ [").append(azione)
                           .append("] Non applicata — schedule line bloccata\n");
                        log.append("    ").append(response.getSapMessage()).append("\n");
                        data.setProcessingResult("⛔ Non applicata");
                        data.setErrorMessage(response.getSapMessage() != null
                            ? response.getSapMessage().substring(0,
                                Math.min(150, response.getSapMessage().length()))
                            : "Schedule line bloccata");
                        errori++;
                    } else {
                        log.append("  ✓ [").append(azione).append("] Successo (HTTP ")
                           .append(response.getHttpStatus()).append(")\n");
                        data.setProcessingResult("✅ " + azione);
                        data.setErrorMessage(null);

                        if (response.isWarning()) {
                            log.append("  ⚠ Warning: ").append(response.getSapMessage()).append("\n");
                            data.setProcessingResult("⚠️ " + azione + " (warning)");
                            data.setErrorMessage(response.getSapMessage() != null
                                ? response.getSapMessage().substring(0,
                                    Math.min(120, response.getSapMessage().length()))
                                : "HTTP " + response.getHttpStatus());
                        }
                        successi++;
                    }
                } else {
                    log.append("  ✗ [").append(azione).append("] Errore (HTTP ")
                       .append(response.getHttpStatus()).append(")\n");
                    data.setProcessingResult("❌ Errore");
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
    public String  getSheetName()           { return m_sheetName; }
    public void    setSheetName(String v)   { this.m_sheetName = v; }
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
    public boolean isDryRunDone()        { return m_dryRunDone; }
    public void    setLogText(String v)  { this.m_logText = v; }
    public String  getFileName()         { return m_fileName; }
    public void    setFileName(String v) { this.m_fileName = v; }

    // =========================
    // PAGEBEAN OVERRIDES
    // =========================

    public String getPageName()                 { return "/xlsx2schedlines.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.Xlsx2schedlinesUI}"; }
}
