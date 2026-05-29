package lamplast.utility.view.managedbeans;

import java.io.Serializable;
import java.util.ArrayList;
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

    private SapConfiguration       sapConfig;
    private SapScheduleLineService  sapService;
    private ExcelParser             excelParser;

    // =========================
    // DATI UI
    // =========================

    /** Lista completa degli item (sempre tutti, usata per elaborazione). */
    private final List<GridJSONdataItem> allItems = new ArrayList<>();

    private FIXGRIDListBinding<GridJSONdataItem> m_gridJSONdata = new FIXGRIDListBinding<>();

    // Link VA03 - App Fiori Manage Sales Order
    private Boolean m_enableVA03          = false;
    private String  m_salesOrderNumberVA03;

    // Link FioriVA03 - FactSheet Fiori (read-only)
    private Boolean m_enableFioriVA03     = false;
    private String  m_salesOrderNumberFiori;

    private String  m_fileName;
    private String  m_logText          = "Nuova sessione";
    private boolean m_dryRunDone       = false;
    private boolean m_elaborazioneFatta = false;

    /**
     * Modalità visualizzazione griglia dopo "Aggiorna Ordini".
     * true  = sintetico (errori + aggiunte + cancellazioni).
     * false = completo (tutto).
     * Il default viene letto da config.properties.
     */
    private Boolean m_viewModeSintetico = true;

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

        public GridJSONdataItem(ScheduleLineData data) { this.data = data; }

        public String getOrderNumber()  { return data.getOrderNumber(); }
        public String getItemNumber()   { return data.getItemNumber()   != null ? data.getItemNumber().toString()   : ""; }
        public String getSchedLine()    { return data.getScheduleLine() != null ? data.getScheduleLine().toString() : ""; }
        public String getMaterial()     { return data.getMaterial(); }
        public String getMaterialText() { return data.getMaterialText(); }
        public String getQuantity()     { return data.getQuantity(); }

        public String getSchedDate() {
            return data.getProductionDate() != null
                ? data.getProductionDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "";
        }

        /**
         * Numero schedulazione creata (solo INSERT riusciti).
         * Per cancellazioni/errori: "0". Per PATCH: "".
         */
        public String getCreatedScheduleLine() {
            String v = data.getCreatedScheduleLine();
            return v != null ? v : "";
        }

        /**
         * Tipo di operazione prevista — calcolata al caricamento del file.
         */
        public String getAzione() {
            Integer sl = data.getScheduleLine();
            if (sl == null) return "";
            if (sl < 0) return "Inserimento";
            String qty = data.getQuantity();
            boolean qtyZero = (qty == null || qty.isBlank()
                    || qty.equals("0") || qty.equals("0.0")
                    || qty.equals("0,0") || qty.matches("0+[.,]?0*"));
            return qtyZero ? "Eliminazione" : "Modifica";
        }

        /**
         * Esito con icona Unicode.
         */
        public String getEsito() {
            String r = data.getProcessingResult();
            if (r == null || r.isBlank()) return "";
            if (r.startsWith("✅") || r.startsWith("✓")) return r;
            if (r.startsWith("❌") || r.startsWith("✗")) return r;
            if (r.startsWith("⚠"))                       return r;
            if (r.startsWith("⏭") || r.startsWith("📦") || r.startsWith("⛔")) return r;
            String rl = r.toLowerCase();
            if (rl.contains("successo") || rl.equals("ok")) return "✅ " + r;
            if (rl.contains("errore")   || rl.equals("ko")) return "❌ " + r;
            if (rl.contains("warning"))                     return "⚠️ " + r;
            return r;
        }

        /** Esito del dry-run — colonna separata. */
        public String getDryRunEsito() {
            String r = data.getDryRunResult();
            return r != null ? r : "";
        }

        /** Messaggio SAP leggibile (non il JSON grezzo). */
        public String getErrorMessage() {
            String m = data.getErrorMessage();
            return m != null ? m : "";
        }

        public void onRowSelect()  { showOrderDetails(data.getOrderNumber()); }
        public void onRowExecute() { showOrderDetails(data.getOrderNumber()); }

        private void showOrderDetails(String orderNumber) {
            String sapOrderNumber = sapConfig.normalizeOrderNumber(orderNumber);
            Statusbar.outputSuccess("Ordine selezionato: " + orderNumber);
            m_salesOrderNumberVA03  = sapConfig.getFullUrlVa03(sapOrderNumber);
            m_enableVA03            = true;
            m_salesOrderNumberFiori = sapConfig.getFullUrlFiori(sapOrderNumber);
            m_enableFioriVA03       = true;
        }

        /**
         * Indica se la riga deve essere inclusa nel filtro sintetico.
         * Include: errori, inserimenti riusciti, cancellazioni riuscite.
         * Esclude: PATCH riusciti, righe saltate.
         */
        public boolean isSignificativa() {
            String r = data.getProcessingResult();
            if (r == null) return false;
            String azione = getAzione();
            // Errori e blocchi: richiedono attenzione del richiedente
            if (r.contains("Errore") || r.contains("Non applicata") || r.contains("Eccezione")) return true;
            // Non modificabile (evase, bloccate, disallineamento materiale):
            // visibili nel sintetico perché il richiedente deve rimuoverle dal file MRP
            if (r.contains("Non modificabile")) return true;
            // INSERT riuscito (con o senza warning)
            if ("Inserimento".equals(azione) && (r.contains("Inserimento") || r.contains("warning"))) return true;
            // Cancellazione riuscita (con o senza warning)
            if ("Eliminazione".equals(azione) && (r.contains("Eliminazione") || r.contains("warning"))) return true;
            return false;
        }
    }

    // =========================
    // COSTRUTTORE
    // =========================

    public Xlsx2schedlinesUI() {
        try {
            this.sapConfig  = new SapConfiguration();
            this.sapService = new SapScheduleLineService(sapConfig);

            m_sheetName           = sapConfig.getSheetName();
            m_lblOrdine           = sapConfig.getColOrdine();
            m_lblPosizione        = sapConfig.getColPosizione();
            m_lblSchedulazione    = sapConfig.getColSchedulazione();
            m_lblMateriale        = sapConfig.getColMateriale();
            m_lblMaterialeText    = sapConfig.getColMaterialeText();
            m_lblQuantita         = sapConfig.getColQuantita();
            m_lblDataProd         = sapConfig.getColDataProd();
            m_viewModeSintetico   = sapConfig.isViewModeSinteticoDefault();

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
        allItems.clear();
        m_gridJSONdata.getItems().clear();
        m_salesOrderNumberVA03  = "";
        m_salesOrderNumberFiori = "";
        m_enableVA03            = false;
        m_enableFioriVA03       = false;
        m_logText               = "Nuova sessione";
        m_dryRunDone            = false;
        m_elaborazioneFatta     = false;
        scheduleLines           = null;

        if (!(ae instanceof BaseActionEventUpload)) return;

        BaseActionEventUpload bae = (BaseActionEventUpload) ae;
        m_fileName = bae.getClientFileName();

        try {
            byte[] excelBytes = hexStringToByteArray(bae.getHexByteString());
            scheduleLines = excelParser.parseExcel(excelBytes, m_fileName);

            for (ScheduleLineData data : scheduleLines) {
                GridJSONdataItem item = new GridJSONdataItem(data);
                allItems.add(item);
                m_gridJSONdata.getItems().add(item);
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
                    // Blank: la schedulazione è corretta, semplicemente non verrà toccata
                    data.setDryRunResult("⏭️ Nessuna modifica — qtà e data invariate, verrà saltata");
                    data.setProcessingResult("⏭️ Nessuna modifica");
                    // createdScheduleLine: blank (situazione ok, nessuna azione)
                } else if ("EVASA".equals(dettaglio)) {
                    data.setDryRunResult("📦 Schedulazione già evasa (qtà open = 0) — verrà saltata");
                    data.setProcessingResult("📦 Già evasa");
                    data.setCreatedScheduleLine("0"); // saltata = 0
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
                    data.setCreatedScheduleLine("0"); // bloccata = 0
                } else if (result.isError()) {
                    data.setDryRunResult("❌ " + (dettaglio != null ? dettaglio : result.getEsitoIcona()));
                    data.setProcessingResult("❌ Errore dry-run");
                    data.setCreatedScheduleLine("0"); // errore = 0
                } else {
                    data.setDryRunResult(result.getEsitoIcona());
                    // createdScheduleLine: blank (verrà valorizzato dopo aggiornamento reale)
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

        for (int idx = 0; idx < scheduleLines.size(); idx++) {
            ScheduleLineData data = scheduleLines.get(idx);
            try {
                log.append("Elaborazione: ").append(data.toString()).append("\n");

                // Salta righe marcate dal dry-run
                String procResult = data.getProcessingResult();
                if (procResult != null && (
                        procResult.contains("Nessuna modifica")
                     || procResult.contains("Già evasa")
                     || procResult.contains("Non modificabile")
                     || procResult.contains("Errore dry-run"))) {
                    log.append("  ⏭️ Saltata (dry-run): ")
                       .append(data.getOrderNumber()).append("/").append(data.getItemNumber())
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

                GridJSONdataItem item = allItems.get(idx);
                String azione = item.getAzione();

                SapResponse response = sapService.updateScheduleLine(data);

                if (response.isSuccess()) {
                    if (response.isFrozen()) {
                        // SAP ha risposto 2xx ma la modifica non è stata applicata
                        String msg = response.getDisplayMessage(200);
                        log.append("  ⛔ [").append(azione)
                           .append("] Non applicata — schedule line bloccata\n");
                        log.append("    ").append(msg).append("\n");
                        data.setProcessingResult("⛔ Non applicata");
                        data.setErrorMessage(msg.isBlank() ? "Schedule line bloccata" : msg);
                        // Colonna schedulazione: 0 per operazione non applicata
                        if (data.isInsert() || data.isDelete()) {
                            data.setCreatedScheduleLine("0");
                        }
                        errori++;
                    } else {
                        log.append("  ✓ [").append(azione).append("] Successo (HTTP ")
                           .append(response.getHttpStatus()).append(")\n");
                        data.setProcessingResult("✅ " + azione);
                        data.setErrorMessage(null);

                        // Colonna schedulazione creata
                        if (data.isInsert()) {
                            String sl = response.getCreatedScheduleLine();
                            data.setCreatedScheduleLine(sl != null ? sl : "?");
                        } else if (data.isDelete()) {
                            data.setCreatedScheduleLine("0");
                        }
                        // PATCH riuscito: createdScheduleLine rimane null → cella vuota

                        if (response.isWarning()) {
                            String msg = response.getDisplayMessage(200);
                            log.append("  ⚠ Warning SAP: ").append(msg).append("\n");
                            data.setProcessingResult("⚠️ " + azione + " (warning)");
                            data.setErrorMessage(msg.isBlank() ? "HTTP " + response.getHttpStatus() : msg);
                        }
                        successi++;
                    }
                } else {
                    // Errore HTTP — costruiamo un messaggio esplicativo
                    String msg = buildErrorMessage(response);
                    log.append("  ✗ [").append(azione).append("] Errore (HTTP ")
                       .append(response.getHttpStatus()).append(")\n");
                    log.append("    ").append(msg).append("\n");
                    if (response.getSapCode() != null)
                        log.append("    Codice: ").append(response.getSapCode()).append("\n");

                    data.setProcessingResult("❌ Errore");
                    data.setErrorMessage(msg);

                    // Colonna schedulazione: 0 per errore su INSERT o DELETE
                    if (data.isInsert() || data.isDelete()) {
                        data.setCreatedScheduleLine("0");
                    }
                    errori++;
                }

                log.append("\n");

            } catch (Exception e) {
                log.append("  ✗ Eccezione: ").append(e.getMessage()).append("\n\n");
                data.setProcessingResult("✗ Eccezione");
                data.setErrorMessage("Eccezione: " + e.getMessage());
                if (data.isInsert() || data.isDelete()) {
                    data.setCreatedScheduleLine("0");
                }
                errori++;
            }
        }

        log.append("=====================\n");
        log.append("Elaborazione completata\n");
        log.append("Successi: ").append(successi).append("\n");
        log.append("Errori: ").append(errori).append("\n");

        m_logText = log.toString();
        m_elaborazioneFatta = true;

        // Aggiorna la griglia in base alla modalità di visualizzazione
        applyViewFilter();

        Statusbar.outputMessage("Elaborazione completata: " + successi + " OK, " + errori + " KO");
    }

    /**
     * Commutazione modalità visualizzazione dalla checkbox in tab Parametri.
     * Riapplica il filtro sulla griglia (solo dopo l'elaborazione).
     */
    public void onToggleViewMode(ActionEvent event) {
        m_viewModeSintetico = !Boolean.TRUE.equals(m_viewModeSintetico);
        Statusbar.outputMessage("Modalità: " + (Boolean.TRUE.equals(m_viewModeSintetico) ? "Sintetica" : "Completa"));
        applyViewFilter();
    }

    /**
     * Applica il filtro sintetico/completo sulla griglia.
     * Se l'elaborazione non è ancora avvenuta, mostra tutto.
     */
    private void applyViewFilter() {
        m_gridJSONdata.getItems().clear();
        if (m_elaborazioneFatta && Boolean.TRUE.equals(m_viewModeSintetico)) {
            // Sintetico: solo righe significative
            for (GridJSONdataItem item : allItems) {
                if (item.isSignificativa()) {
                    m_gridJSONdata.getItems().add(item);
                }
            }
        } else {
            // Completo: tutto (anche prima dell'elaborazione)
            m_gridJSONdata.getItems().addAll(allItems);
        }
    }

    // =========================
    // UTILITY
    // =========================

    /**
     * Costruisce un messaggio d'errore leggibile dalla SapResponse.
     * Intercetta casi specifici noti (lock, tipo documento, ecc.).
     */
    private String buildErrorMessage(SapResponse response) {
        int    status   = response.getHttpStatus();
        String sapMsg   = response.getSapMessage();
        String sapCode  = response.getSapCode();

        // Caso: documento bloccato da altro utente
        // SAP restituisce tipicamente HTTP 423 (Locked) o 400/409 con codici specifici
        if (status == 423) {
            return "Documento bloccato da un altro utente — riprovare più tardi"
                + (sapMsg != null && !sapMsg.isBlank() ? " (" + sapMsg + ")" : "");
        }

        // Codici OData noti per il lock
        if (sapCode != null && (
                sapCode.contains("CM_MGW_RT/021")   // entità bloccata
             || sapCode.contains("LOCK")
             || sapCode.contains("locked"))) {
            return "Documento bloccato — " + (sapMsg != null ? sapMsg : "HTTP " + status);
        }

        // Messaggio SAP disponibile: usalo direttamente
        if (sapMsg != null && !sapMsg.isBlank()) {
            return sapMsg;
        }

        // Fallback generico con status code esplicito
        switch (status) {
            case 400: return "Richiesta non valida (HTTP 400) — verificare i dati della schedulazione";
            case 401: return "Credenziali non autorizzate (HTTP 401) — verificare utente tecnico";
            case 403: return "Accesso negato (HTTP 403) — utente privo delle autorizzazioni necessarie";
            case 404: return "Schedulazione non trovata su SAP (HTTP 404)";
            case 409: return "Conflitto — il documento potrebbe essere bloccato (HTTP 409)";
            case 500: return "Errore interno SAP (HTTP 500) — contattare l'amministratore";
            default:  return "Errore HTTP " + status;
        }
    }

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
    public Boolean getEnableVA03()               { return m_enableVA03; }
    public void    setEnableVA03(Boolean v)      { this.m_enableVA03 = v; }
    public String  getSalesOrderNumber()         { return m_salesOrderNumberVA03; }
    public void    setSalesOrderNumber(String v) { this.m_salesOrderNumberVA03 = v; }

    // Link FioriVA03
    public Boolean getEnableFioriVA03()               { return m_enableFioriVA03; }
    public void    setEnableFioriVA03(Boolean v)      { this.m_enableFioriVA03 = v; }
    public String  getSalesOrderNumberFiori()          { return m_salesOrderNumberFiori; }
    public void    setSalesOrderNumberFiori(String v)  { this.m_salesOrderNumberFiori = v; }

    // Label colonne
    public String  getSheetName()              { return m_sheetName; }
    public void    setSheetName(String v)      { this.m_sheetName = v; }
    public String  getLblOrdine()              { return m_lblOrdine; }
    public void    setLblOrdine(String v)      { this.m_lblOrdine = v; }
    public String  getLblPosizione()           { return m_lblPosizione; }
    public void    setLblPosizione(String v)   { this.m_lblPosizione = v; }
    public String  getLblSchedulazione()       { return m_lblSchedulazione; }
    public void    setLblSchedulazione(String v){ this.m_lblSchedulazione = v; }
    public String  getLblMateriale()           { return m_lblMateriale; }
    public void    setLblMateriale(String v)   { this.m_lblMateriale = v; }
    public String  getLblMaterialeText()       { return m_lblMaterialeText; }
    public void    setLblMaterialeText(String v){ this.m_lblMaterialeText = v; }
    public String  getLblQuantita()            { return m_lblQuantita; }
    public void    setLblQuantita(String v)    { this.m_lblQuantita = v; }
    public String  getLblDataProd()            { return m_lblDataProd; }
    public void    setLblDataProd(String v)    { this.m_lblDataProd = v; }

    // Modalità visualizzazione
    public Boolean getViewModeSintetico()          { return m_viewModeSintetico; }
    public void    setViewModeSintetico(Boolean v) { this.m_viewModeSintetico = v; }

    public String  getViewModeButtonLabel() {
        return Boolean.TRUE.equals(m_viewModeSintetico)
            ? "Vista: Sintetica (click per Completa)"
            : "Vista: Completa (click per Sintetica)";
    }

    // Altri
    public String  getLogText()          { return m_logText; }
    public void    setLogText(String v)  { this.m_logText = v; }
    public boolean isDryRunDone()        { return m_dryRunDone; }
    public String  getFileName()         { return m_fileName; }
    public void    setFileName(String v) { this.m_fileName = v; }

    // =========================
    // PAGEBEAN OVERRIDES
    // =========================

    public String getPageName()                 { return "/xlsx2schedlines.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.Xlsx2schedlinesUI}"; }
}
