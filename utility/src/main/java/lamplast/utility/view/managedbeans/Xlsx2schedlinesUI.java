package lamplast.utility.view.managedbeans;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.eclnt.jsfserver.defaultscreens.BlockerInfo;
import org.eclnt.jsfserver.defaultscreens.OKPopup;
import org.eclnt.jsfserver.defaultscreens.Statusbar;
import org.eclnt.jsfserver.elements.events.BaseActionEventUpload;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.pagebean.PageBean;
import org.eclnt.jsfserver.polling.LongOperationWithObserverPopup;
import org.eclnt.util.log.IObserver;

import lamplast.utility.config.SapConfiguration;
import lamplast.utility.model.ScheduleLineData;
import lamplast.utility.service.ExcelParser;
import lamplast.utility.service.SapResponse;
import lamplast.utility.service.SapScheduleLineService;
import lamplast.utility.service.SapScheduleLineService.SapDryRunResult;

@CCGenClass(expressionBase = "#{d.Xlsx2schedlinesUI}")
public class Xlsx2schedlinesUI extends PageBean implements Serializable {

    // =========================
    // STATO ELABORAZIONE
    // =========================

    /**
     * Tiene traccia dello stato dell'elaborazione in corso o dell'ultima
     * completata. Vive nel PageBean (sessione CC) — sopravvive a
     * disconnessioni del browser finché il container CF non viene riavviato.
     */
    public enum StatoElab { IDLE, IN_CORSO, COMPLETATA, ERRORE }

    private volatile StatoElab     m_statoElab        = StatoElab.IDLE;
    private volatile int           m_elabTotale       = 0;
    private volatile int           m_elabRigaCorrente = 0;  // 1-based, indice elaborazione (non rowIndex)
    private volatile int           m_elabSuccessi     = 0;
    private volatile int           m_elabErrori       = 0;
    private volatile int           m_elabSaltate      = 0;
    private volatile LocalDateTime m_elabInizio       = null;
    private volatile LocalDateTime m_elabFine         = null;
    private volatile String        m_pendingLog       = "";

    private static final DateTimeFormatter FMT_TS =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

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
    private Boolean m_enableVA03        = false;
    private String  m_salesOrderNumberVA03;

    // Link FioriVA03 - FactSheet Fiori (read-only)
    private Boolean m_enableFioriVA03   = false;
    private String  m_salesOrderNumberFiori;

    private String  m_fileName;
    private String  m_logText           = "Nuova sessione";
    private boolean m_dryRunDone        = false;
    private boolean m_elaborazioneFatta = false;

    /**
     * Modalità visualizzazione griglia dopo "Aggiorna Ordini".
     * true  = sintetico (errori + aggiunte + cancellazioni).
     * false = completo (tutto).
     */
    private Boolean m_viewModeSintetico = true;

    // Label colonne
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

        /**
         * Numero di riga nel file Excel originale (1-based).
         * Assegnato al momento del parsing e non cambia mai.
         * Usato per il check pre-elaborazione e per il log CF.
         */
        private final int rowIndex;

        public GridJSONdataItem(ScheduleLineData data, int rowIndex) {
            this.data     = data;
            this.rowIndex = rowIndex;
        }

        /** Riga nel file Excel originale — esposta alla colonna "Riga Excel" nella griglia. */
        public int    getRowIndex()     { return rowIndex; }

        public String getOrderNumber()  { return data.getOrderNumber(); }
        public String getItemNumber()   { return data.getItemNumber()   != null ? data.getItemNumber().toString()   : ""; }
        public String getSchedLine()    { return data.getScheduleLine() != null ? data.getScheduleLine().toString() : ""; }
        public String getMaterial()     { return data.getMaterial(); }
        public String getMaterialText() { return data.getMaterialText(); }
        public String getQuantity()     { return data.getQuantity(); }

        public String getSchedDate() {
            return data.getProductionDate() != null
                ? data.getProductionDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "";
        }

        public String getCreatedScheduleLine() {
            String v = data.getCreatedScheduleLine();
            return v != null ? v : "";
        }

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

        public String getDryRunEsito() {
            String r = data.getDryRunResult();
            return r != null ? r : "";
        }

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

        public boolean isSignificativa() {
            String r = data.getProcessingResult();
            if (r == null) return false;
            String azione = getAzione();
            if (r.contains("Errore") || r.contains("Non applicata") || r.contains("Eccezione")) return true;
            if (r.contains("Non modificabile")) return true;
            if ("Inserimento".equals(azione) && (r.contains("Inserimento") || r.contains("warning"))) return true;
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

            m_sheetName         = sapConfig.getSheetName();
            m_lblOrdine         = sapConfig.getColOrdine();
            m_lblPosizione      = sapConfig.getColPosizione();
            m_lblSchedulazione  = sapConfig.getColSchedulazione();
            m_lblMateriale      = sapConfig.getColMateriale();
            m_lblMaterialeText  = sapConfig.getColMaterialeText();
            m_lblQuantita       = sapConfig.getColQuantita();
            m_lblDataProd       = sapConfig.getColDataProd();
            m_viewModeSintetico = sapConfig.isViewModeSinteticoDefault();

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

        if (m_statoElab == StatoElab.IN_CORSO) {
            OKPopup.createInstance("", "Elaborazione in corso — attendere il completamento prima di caricare un nuovo file.");
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
        m_statoElab             = StatoElab.IDLE;

        if (!(ae instanceof BaseActionEventUpload)) return;

        BaseActionEventUpload bae = (BaseActionEventUpload) ae;
        m_fileName = bae.getClientFileName();

        try {
            byte[] excelBytes = hexStringToByteArray(bae.getHexByteString());
            scheduleLines = excelParser.parseExcel(excelBytes, m_fileName);

            // rowIndex 1-based: rappresenta la riga nel file Excel originale
            for (int i = 0; i < scheduleLines.size(); i++) {
                GridJSONdataItem item = new GridJSONdataItem(scheduleLines.get(i), i + 1);
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

        if (m_statoElab == StatoElab.IN_CORSO) {
            OKPopup.createInstance("", "Elaborazione in corso — attendere il completamento.");
            return;
        }

        final List<ScheduleLineData>    lines  = scheduleLines;
        final List<GridJSONdataItem>    items  = new ArrayList<>(allItems);
        final int                       totale = lines.size();

        System.out.println("[Xlsx2schedlines] DRY-RUN avviato — file: " + m_fileName
            + " — righe: " + totale
            + " — " + LocalDateTime.now().format(FMT_TS));

        final IObserver observer = LongOperationWithObserverPopup.prepare("Verifica preventiva (Dry-run)");

        Runnable longOperation = new Runnable() {
            public void run() {
                for (int i = 0; i < totale; i++) {
                    ScheduleLineData data     = lines.get(i);
                    int              rigaExcel = items.get(i).getRowIndex();

                    try {
                        SapDryRunResult result    = sapService.dryRun(data);
                        String          dettaglio = result.getDettaglio();

                        if ("NESSUNA_MODIFICA".equals(dettaglio)) {
                            data.setDryRunResult("⏭️ Nessuna modifica — qtà e data invariate, verrà saltata");
                            data.setProcessingResult("⏭️ Nessuna modifica");
                        } else if ("EVASA".equals(dettaglio)) {
                            data.setDryRunResult("📦 Schedulazione già evasa (qtà open = 0) — verrà saltata");
                            data.setProcessingResult("📦 Già evasa");
                            data.setCreatedScheduleLine("0");
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
                            data.setCreatedScheduleLine("0");
                        } else if (result.isError()) {
                            data.setDryRunResult("❌ " + (dettaglio != null ? dettaglio : result.getEsitoIcona()));
                            data.setProcessingResult("❌ Errore dry-run");
                            data.setCreatedScheduleLine("0");
                        } else {
                            data.setDryRunResult(result.getEsitoIcona());
                        }

                    } catch (Exception e) {
                        data.setDryRunResult("❌ Eccezione: " + e.getMessage());
                    }

                    String msgRiga = String.format("[riga Excel %d | %d/%d] %s / pos.%s — %s",
                        rigaExcel, i + 1, totale,
                        data.getOrderNumber(),
                        data.getItemNumber(),
                        data.getDryRunResult());

                    observer.addMessage(msgRiga);
                    System.out.println("[Xlsx2schedlines] DRY-RUN " + msgRiga);
                    BlockerInfo.sendProgressToClient(
                        "Verifica riga " + (i + 1) + " di " + totale,
                        (i + 1) * 100 / totale);
                }

                System.out.println("[Xlsx2schedlines] DRY-RUN completato — "
                    + LocalDateTime.now().format(FMT_TS));
            }
        };

        Runnable finishOperation = new Runnable() {
            public void run() {
                int ok = 0, warn = 0, err = 0;
                for (ScheduleLineData d : lines) {
                    String dr = d.getDryRunResult();
                    if (dr == null) continue;
                    if      (dr.startsWith("✅")) ok++;
                    else if (dr.startsWith("❌")) err++;
                    else                          warn++;
                }
                m_dryRunDone = true;
                String msg = "Dry-run completato: " + ok + " OK, " + warn + " warning/skip, " + err + " errori."
                           + " — Verificare la colonna Esito prima di procedere.";
                Statusbar.outputMessage(msg);
                System.out.println("[Xlsx2schedlines] " + msg);
            }
        };

        LongOperationWithObserverPopup.run(longOperation, finishOperation);
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

        if (m_statoElab == StatoElab.IN_CORSO) {
            OKPopup.createInstance("", "Elaborazione già in corso — attendere il completamento.");
            return;
        }

        // -------------------------------------------------------
        // CHECK ORDINE ORIGINALE (Approccio B)
        // Verifica che la sequenza dei rowIndex nella griglia
        // corrisponda a 1, 2, 3 ... N.
        // Se l'utente ha riordinato la griglia e non ha ripristinato
        // l'ordine originale, l'elaborazione viene bloccata.
        // -------------------------------------------------------
        List<GridJSONdataItem> itemsInGriglia = m_gridJSONdata.getItems();
        for (int i = 0; i < itemsInGriglia.size(); i++) {
            int atteso   = i + 1;
            int trovato  = itemsInGriglia.get(i).getRowIndex();
            if (trovato != atteso) {
                OKPopup.createInstance("Ordine non originale",
                    "La griglia non è nell'ordine originale del file Excel.\n\n"
                    + "Alla posizione " + atteso + " è presente la riga Excel " + trovato + ".\n\n"
                    + "Riordinare la griglia per colonna \"Riga Excel\" in modo crescente "
                    + "prima di procedere con l'elaborazione.");
                return;
            }
        }

        final List<ScheduleLineData> lines  = scheduleLines;
        final List<GridJSONdataItem> items  = new ArrayList<>(itemsInGriglia);
        final int                    totale = lines.size();

        // Inizializza stato elaborazione
        m_statoElab        = StatoElab.IN_CORSO;
        m_elabTotale       = totale;
        m_elabRigaCorrente = 0;
        m_elabSuccessi     = 0;
        m_elabErrori       = 0;
        m_elabSaltate      = 0;
        m_elabInizio       = LocalDateTime.now();
        m_elabFine         = null;
        m_pendingLog       = "";

        System.out.println("[Xlsx2schedlines] ELABORAZIONE avviata — file: " + m_fileName
            + " — righe: " + totale
            + " — " + m_elabInizio.format(FMT_TS));

        final IObserver observer = LongOperationWithObserverPopup.prepare("Aggiornamento Schedule Lines SAP");

        Runnable longOperation = new Runnable() {
            public void run() {

                StringBuilder log = new StringBuilder();
                log.append("Inizio elaborazione: ").append(m_elabInizio.format(FMT_TS)).append("\n");
                log.append("File: ").append(m_fileName).append(" — ").append(totale).append(" righe\n");
                log.append("=====================\n\n");

                for (int idx = 0; idx < totale; idx++) {

                    ScheduleLineData data      = lines.get(idx);
                    GridJSONdataItem item      = items.get(idx);
                    int              rigaExcel = item.getRowIndex();

                    // Aggiorna riga corrente (visibile nel banner se browser si riconnette)
                    m_elabRigaCorrente = idx + 1;

                    // Prefisso standard per TUTTI i System.out — la riga Excel è sempre in evidenza
                    String prefisso = String.format("[RIGA EXCEL %d | %d/%d]", rigaExcel, idx + 1, totale);

                    try {
                        log.append("Elaborazione: ").append(prefisso).append(" ").append(data.toString()).append("\n");

                        // --- Righe saltate dal dry-run ---
                        String procResult = data.getProcessingResult();
                        if (procResult != null && (
                                procResult.contains("Nessuna modifica")
                             || procResult.contains("Già evasa")
                             || procResult.contains("Non modificabile")
                             || procResult.contains("Errore dry-run"))) {

                            log.append("  ⏭️ Saltata (dry-run): ")
                               .append(data.getOrderNumber()).append("/").append(data.getItemNumber())
                               .append(" — ").append(procResult).append("\n");
                            m_elabSaltate++;

                            String msgRiga = prefisso + " ⏭️ Saltata: "
                                + data.getOrderNumber() + " / pos." + data.getItemNumber();
                            observer.addMessage(msgRiga);
                            System.out.println("[Xlsx2schedlines] " + msgRiga);
                            BlockerInfo.sendProgressToClient(
                                "Riga " + (idx + 1) + " di " + totale + " (saltata)",
                                (idx + 1) * 100 / totale);
                            continue;
                        }

                        // --- Validazione ---
                        String validationError = data.validate();
                        if (validationError != null) {
                            log.append("  ⚠ Errore validazione: ").append(validationError).append("\n\n");
                            data.setProcessingResult("⚠ Errore");
                            data.setErrorMessage("Validazione: " + validationError);
                            m_elabErrori++;

                            String msgRiga = prefisso + " ⚠️ Validazione: "
                                + data.getOrderNumber() + " / pos." + data.getItemNumber()
                                + " — " + validationError;
                            observer.addMessage(msgRiga);
                            System.out.println("[Xlsx2schedlines] " + msgRiga);
                            BlockerInfo.sendProgressToClient(
                                "Riga " + (idx + 1) + " di " + totale,
                                (idx + 1) * 100 / totale);
                            continue;
                        }

                        // --- Chiamata SAP ---
                        String      azione   = item.getAzione();
                        SapResponse response = sapService.updateScheduleLine(data);

                        if (response.isSuccess()) {
                            if (response.isFrozen()) {
                                String msg = response.getDisplayMessage(200);
                                log.append("  ⛔ [").append(azione).append("] Non applicata — schedule line bloccata\n");
                                log.append("    ").append(msg).append("\n");
                                data.setProcessingResult("⛔ Non applicata");
                                data.setErrorMessage(msg.isBlank() ? "Schedule line bloccata" : msg);
                                if (data.isInsert() || data.isDelete()) data.setCreatedScheduleLine("0");
                                m_elabErrori++;

                                String msgRiga = prefisso + " ⛔ Non applicata: "
                                    + data.getOrderNumber() + " / pos." + data.getItemNumber()
                                    + " — " + msg;
                                observer.addMessage(msgRiga);
                                System.out.println("[Xlsx2schedlines] " + msgRiga);

                            } else {
                                log.append("  ✓ [").append(azione).append("] Successo (HTTP ")
                                   .append(response.getHttpStatus()).append(")\n");
                                data.setProcessingResult("✅ " + azione);
                                data.setErrorMessage(null);

                                if (data.isInsert()) {
                                    String sl = response.getCreatedScheduleLine();
                                    data.setCreatedScheduleLine(sl != null ? sl : "?");
                                } else if (data.isDelete()) {
                                    data.setCreatedScheduleLine("0");
                                }

                                if (response.isWarning()) {
                                    String msg = response.getDisplayMessage(200);
                                    log.append("  ⚠ Warning SAP: ").append(msg).append("\n");
                                    data.setProcessingResult("⚠️ " + azione + " (warning)");
                                    data.setErrorMessage(msg.isBlank() ? "HTTP " + response.getHttpStatus() : msg);
                                }
                                m_elabSuccessi++;

                                String msgRiga = prefisso + " ✅ " + azione + ": "
                                    + data.getOrderNumber() + " / pos." + data.getItemNumber();
                                observer.addMessage(msgRiga);
                                System.out.println("[Xlsx2schedlines] " + msgRiga);
                            }

                        } else {
                            String msg = buildErrorMessage(response);
                            log.append("  ✗ [").append(azione).append("] Errore (HTTP ")
                               .append(response.getHttpStatus()).append(")\n");
                            log.append("    ").append(msg).append("\n");
                            if (response.getSapCode() != null)
                                log.append("    Codice: ").append(response.getSapCode()).append("\n");
                            data.setProcessingResult("❌ Errore");
                            data.setErrorMessage(msg);
                            if (data.isInsert() || data.isDelete()) data.setCreatedScheduleLine("0");
                            m_elabErrori++;

                            String msgRiga = prefisso + " ❌ Errore HTTP " + response.getHttpStatus() + ": "
                                + data.getOrderNumber() + " / pos." + data.getItemNumber()
                                + " — " + msg;
                            observer.addMessage(msgRiga);
                            System.out.println("[Xlsx2schedlines] " + msgRiga);
                        }

                        log.append("\n");

                    } catch (Exception e) {
                        log.append("  ✗ Eccezione: ").append(e.getMessage()).append("\n\n");
                        data.setProcessingResult("✗ Eccezione");
                        data.setErrorMessage("Eccezione: " + e.getMessage());
                        if (data.isInsert() || data.isDelete()) data.setCreatedScheduleLine("0");
                        m_elabErrori++;

                        String msgRiga = prefisso + " ❌ Eccezione: "
                            + data.getOrderNumber() + " / pos." + data.getItemNumber()
                            + " — " + e.getMessage();
                        observer.addMessage(msgRiga);
                        System.out.println("[Xlsx2schedlines] " + msgRiga);
                    }

                    BlockerInfo.sendProgressToClient(
                        "Riga " + (idx + 1) + " di " + totale,
                        (idx + 1) * 100 / totale);
                }

                // Riepilogo finale
                m_elabFine = LocalDateTime.now();
                log.append("=====================\n");
                log.append("Elaborazione completata: ").append(m_elabFine.format(FMT_TS)).append("\n");
                log.append("Successi: ").append(m_elabSuccessi).append("\n");
                log.append("Errori:   ").append(m_elabErrori).append("\n");
                log.append("Saltate:  ").append(m_elabSaltate).append("\n");
                m_pendingLog = log.toString();

                System.out.println("[Xlsx2schedlines] ELABORAZIONE completata"
                    + " — successi: " + m_elabSuccessi
                    + ", errori: "    + m_elabErrori
                    + ", saltate: "   + m_elabSaltate
                    + " — "          + m_elabFine.format(FMT_TS));
            }
        };

        Runnable finishOperation = new Runnable() {
            public void run() {
                m_logText           = m_pendingLog;
                m_elaborazioneFatta = true;
                m_statoElab         = StatoElab.COMPLETATA;

                applyViewFilter();

                String riepilogo = "Elaborazione completata: "
                    + m_elabSuccessi + " OK, "
                    + m_elabErrori   + " KO, "
                    + m_elabSaltate  + " saltate";
                Statusbar.outputMessage(riepilogo);
            }
        };

        LongOperationWithObserverPopup.run(longOperation, finishOperation);
    }

    // =========================
    // BANNER UI — stato elaborazione
    // =========================

    public boolean isBannerVisible() {
        return m_statoElab == StatoElab.IN_CORSO
            || m_statoElab == StatoElab.COMPLETATA;
    }

    public String getBannerText() {
        switch (m_statoElab) {
            case IN_CORSO:
                return String.format(
                    "⏳ Elaborazione in corso: riga %d di %d"
                    + " — avviata alle %s"
                    + " — NON chiudere questa finestra",
                    m_elabRigaCorrente,
                    m_elabTotale,
                    m_elabInizio != null ? m_elabInizio.format(FMT_TS) : "—");
            case COMPLETATA:
                return String.format(
                    "✅ Elaborazione completata alle %s"
                    + " — %d OK  |  %d KO  |  %d saltate",
                    m_elabFine != null ? m_elabFine.format(FMT_TS) : "—",
                    m_elabSuccessi,
                    m_elabErrori,
                    m_elabSaltate);
            default:
                return "";
        }
    }

    public String getBannerStylevariant() {
        return m_statoElab == StatoElab.IN_CORSO ? "cc_warning" : "cc_success";
    }

    // =========================
    // ALTRI GESTORI EVENTI
    // =========================

    public void onToggleViewMode(ActionEvent event) {
        m_viewModeSintetico = !Boolean.TRUE.equals(m_viewModeSintetico);
        Statusbar.outputMessage("Modalità: " + (Boolean.TRUE.equals(m_viewModeSintetico) ? "Sintetica" : "Completa"));
        applyViewFilter();
    }

    private void applyViewFilter() {
        m_gridJSONdata.getItems().clear();
        if (m_elaborazioneFatta && Boolean.TRUE.equals(m_viewModeSintetico)) {
            for (GridJSONdataItem item : allItems) {
                if (item.isSignificativa()) m_gridJSONdata.getItems().add(item);
            }
        } else {
            m_gridJSONdata.getItems().addAll(allItems);
        }
    }

    // =========================
    // UTILITY
    // =========================

    private String buildErrorMessage(SapResponse response) {
        int    status  = response.getHttpStatus();
        String sapMsg  = response.getSapMessage();
        String sapCode = response.getSapCode();

        if (status == 423)
            return "Documento bloccato da un altro utente — riprovare più tardi"
                + (sapMsg != null && !sapMsg.isBlank() ? " (" + sapMsg + ")" : "");

        if (sapCode != null && (
                sapCode.contains("CM_MGW_RT/021")
             || sapCode.contains("LOCK")
             || sapCode.contains("locked")))
            return "Documento bloccato — " + (sapMsg != null ? sapMsg : "HTTP " + status);

        if (sapMsg != null && !sapMsg.isBlank()) return sapMsg;

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
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                +  Character.digit(hex.charAt(i + 1), 16));
        return data;
    }

    // =========================
    // GETTERS / SETTERS
    // =========================

    public FIXGRIDListBinding<GridJSONdataItem> getGridJSONdata() { return m_gridJSONdata; }

    public Boolean getEnableVA03()               { return m_enableVA03; }
    public void    setEnableVA03(Boolean v)      { this.m_enableVA03 = v; }
    public String  getSalesOrderNumber()         { return m_salesOrderNumberVA03; }
    public void    setSalesOrderNumber(String v) { this.m_salesOrderNumberVA03 = v; }

    public Boolean getEnableFioriVA03()              { return m_enableFioriVA03; }
    public void    setEnableFioriVA03(Boolean v)     { this.m_enableFioriVA03 = v; }
    public String  getSalesOrderNumberFiori()         { return m_salesOrderNumberFiori; }
    public void    setSalesOrderNumberFiori(String v) { this.m_salesOrderNumberFiori = v; }

    public String  getSheetName()                { return m_sheetName; }
    public void    setSheetName(String v)        { this.m_sheetName = v; }
    public String  getLblOrdine()                { return m_lblOrdine; }
    public void    setLblOrdine(String v)        { this.m_lblOrdine = v; }
    public String  getLblPosizione()             { return m_lblPosizione; }
    public void    setLblPosizione(String v)     { this.m_lblPosizione = v; }
    public String  getLblSchedulazione()         { return m_lblSchedulazione; }
    public void    setLblSchedulazione(String v) { this.m_lblSchedulazione = v; }
    public String  getLblMateriale()             { return m_lblMateriale; }
    public void    setLblMateriale(String v)     { this.m_lblMateriale = v; }
    public String  getLblMaterialeText()         { return m_lblMaterialeText; }
    public void    setLblMaterialeText(String v) { this.m_lblMaterialeText = v; }
    public String  getLblQuantita()              { return m_lblQuantita; }
    public void    setLblQuantita(String v)      { this.m_lblQuantita = v; }
    public String  getLblDataProd()              { return m_lblDataProd; }
    public void    setLblDataProd(String v)      { this.m_lblDataProd = v; }

    public Boolean getViewModeSintetico()            { return m_viewModeSintetico; }
    public void    setViewModeSintetico(Boolean v)   { this.m_viewModeSintetico = v; }
    public String  getViewModeButtonLabel() {
        return Boolean.TRUE.equals(m_viewModeSintetico)
            ? "Vista: Sintetica (click per Completa)"
            : "Vista: Completa (click per Sintetica)";
    }

    public String  getLogText()          { return m_logText; }
    public void    setLogText(String v)  { this.m_logText = v; }
    public boolean isDryRunDone()        { return m_dryRunDone; }
    public String  getFileName()         { return m_fileName; }
    public void    setFileName(String v) { this.m_fileName = v; }

    public String getPageName()                 { return "/xlsx2schedlines.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.Xlsx2schedlinesUI}"; }
}
