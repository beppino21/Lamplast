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
import org.eclnt.jsfserver.defaultscreens.YESNOPopup;
import org.eclnt.jsfserver.elements.events.BaseActionEventUpload;
import org.eclnt.jsfserver.elements.impl.FIXGRIDItem;
import org.eclnt.jsfserver.elements.impl.FIXGRIDListBinding;
import org.eclnt.jsfserver.elements.util.Trigger;
import org.eclnt.jsfserver.elements.util.ValidValuesBinding;
import org.eclnt.jsfserver.pagebean.PageBean;
import org.eclnt.jsfserver.polling.LongOperationWithObserverPopup;
import org.eclnt.util.log.IObserver;

import lamplast.utility.config.SapConfiguration;
import lamplast.utility.config.SapSystemRegistry;
import lamplast.utility.config.SapSystemRegistry.SapSystemInfo;
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

    private SapSystemRegistry       sapRegistry;
    private SapConfiguration        sapConfig;
    private SapScheduleLineService  sapService;
    private ExcelParser             excelParser;

    // =========================
    // SELEZIONE SISTEMA SAP
    // =========================

    private final ValidValuesBinding m_systemVVB = new ValidValuesBinding();
    private String                   m_selectedSystemId;

    // =========================
    // PREFERENZA UTENTE (local storage del browser)
    // =========================

    /**
     * Sistema "fissato" dall'utente, salvato nel local storage del browser
     * tramite il componente CLIENTLOCALSTORAGE. Su Cloud Foundry il disco
     * del container non è persistente: il browser sì. La preferenza vale
     * quindi per quel browser/PC, sopravvive a restart e redeploy e ha
     * precedenza su sap.system.default di config.properties.
     */
    private String  m_preferredSystemId;
    /** La preferenza letta dal browser viene applicata una sola volta, all'apertura. */
    private boolean m_preferenceStartupDone = false;
    /** Forza un roundtrip all'avvio, così il valore del local storage arriva subito al server. */
    private final Trigger m_startupTrigger  = new Trigger();

    // Colori barra di sistema
    private static final String COLOR_TEST_BG   = "#D50000";   // rosso acceso
    private static final String COLOR_TEST_FG   = "#FFFFFF";
    private static final String COLOR_TEST_BODY = "top:6;bottom:6;left:6;right:6;color:#D50000";
    private static final String COLOR_PROD_BG   = "#1B5E20";   // verde scuro
    private static final String COLOR_PROD_FG   = "#FFFFFF";
    private static final String COLOR_NONE_BG   = "#616161";   // grigio: nessun sistema valido

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
            this.sapRegistry = new SapSystemRegistry();
            for (SapSystemInfo si : sapRegistry.getSystems()) {
                m_systemVVB.addValidValue(si.getId(), si.getComboText());
            }
            System.out.println("[Xlsx2schedlines] Configurazione letta da " + sapRegistry.getSource()
                + " — sistemi: " + sapRegistry.getSystems().size());

            String err = activateSystem(sapRegistry.getDefaultSystemId());
            if (err != null) {
                Statusbar.outputAlert("Sistema di default non utilizzabile: " + err
                    + " — selezionare un altro sistema");
            }

        } catch (Exception e) {
            String msg = "ERRORE CONFIGURAZIONE: " + e.getMessage()
                       + " — verificare config.properties";
            System.out.println("[Xlsx2schedlines] " + msg);
            Statusbar.outputAlert(msg);
        }

        m_logText = "Nuova sessione";
        m_startupTrigger.trigger();
    }

    /**
     * Attiva il sistema indicato: crea configurazione e servizio dedicati
     * (il servizio ha una cache ordine→variante API che NON deve essere
     * condivisa tra sistemi diversi) e ricarica le label Excel, che possono
     * essere ridefinite per singolo sistema.
     *
     * @return null se ok, altrimenti il messaggio d'errore (stato invariato)
     */
    private String activateSystem(String systemId) {
        try {
            SapConfiguration       cfg = sapRegistry.createConfiguration(systemId);
            SapScheduleLineService svc = new SapScheduleLineService(cfg);

            this.sapConfig          = cfg;
            this.sapService         = svc;
            this.m_selectedSystemId = systemId;

            m_sheetName         = cfg.getSheetName();
            m_lblOrdine         = cfg.getColOrdine();
            m_lblPosizione      = cfg.getColPosizione();
            m_lblSchedulazione  = cfg.getColSchedulazione();
            m_lblMateriale      = cfg.getColMateriale();
            m_lblMaterialeText  = cfg.getColMaterialeText();
            m_lblQuantita       = cfg.getColQuantita();
            m_lblDataProd       = cfg.getColDataProd();
            m_viewModeSintetico = cfg.isViewModeSinteticoDefault();

            System.out.println("[Xlsx2schedlines] Sistema attivo: " + cfg.getSystemLabel());
            return null;
        } catch (Exception e) {
            System.out.println("[Xlsx2schedlines] Attivazione sistema " + systemId + " fallita: " + e.getMessage());
            return e.getMessage();
        }
    }

    /**
     * Cambio sistema da combo. Bloccato durante un'elaborazione. Se un file
     * è già caricato:
     *  - se l'aggiornamento è già stato eseguito → reset completo (va ricaricato il file);
     *  - altrimenti si mantengono le righe ma si azzerano gli esiti del dry-run,
     *    che erano riferiti al sistema precedente.
     */
    private void changeSystem(String newId) {
        if (newId == null || newId.equals(m_selectedSystemId)) return;

        if (m_statoElab == StatoElab.IN_CORSO) {
            OKPopup.createInstance("Cambio sistema non consentito",
                "Elaborazione in corso sul sistema " + sapConfig.getSystemName()
                + " — attendere il completamento prima di cambiare sistema.");
            return;
        }

        String oldLabel = sapConfig != null ? sapConfig.getSystemLabel() : "—";
        String err      = activateSystem(newId);
        if (err != null) {
            OKPopup.createInstance("Sistema non utilizzabile",
                "Impossibile attivare il sistema " + newId + ":\n\n" + err
                + "\n\nResta attivo: " + oldLabel);
            return;
        }

        // Link agli ordini puntavano al sistema precedente
        m_salesOrderNumberVA03  = "";
        m_salesOrderNumberFiori = "";
        m_enableVA03            = false;
        m_enableFioriVA03       = false;

        String note;
        if (m_elaborazioneFatta) {
            allItems.clear();
            m_gridJSONdata.getItems().clear();
            scheduleLines   = null;
            m_fileName      = null;
            m_logText       = "Nuova sessione";
            note = " — griglia azzerata: ricaricare il file Excel";
        } else if (scheduleLines != null && !scheduleLines.isEmpty()) {
            for (ScheduleLineData d : scheduleLines) {
                d.setDryRunResult(null);
                d.setProcessingResult(null);
                d.setErrorMessage(null);
                d.setCreatedScheduleLine(null);
            }
            applyViewFilter();
            note = " — esiti del dry-run azzerati: rieseguire la verifica";
        } else {
            note = "";
        }
        m_dryRunDone        = false;
        m_elaborazioneFatta = false;
        m_statoElab         = StatoElab.IDLE;

        System.out.println("[Xlsx2schedlines] CAMBIO SISTEMA: " + oldLabel + " → " + sapConfig.getSystemLabel());
        if (sapConfig.isProductive()) {
            Statusbar.outputWarning("Connesso a PRODUZIONE: " + sapConfig.getSystemName() + note);
        } else {
            Statusbar.outputAlert("Connesso a sistema di TEST: " + sapConfig.getSystemName() + note);
        }
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

        if (sapService == null) {
            OKPopup.createInstance("", "Nessun sistema SAP attivo — selezionare un sistema valido.");
            return;
        }

        final List<ScheduleLineData>    lines  = scheduleLines;
        final List<GridJSONdataItem>    items  = new ArrayList<>(allItems);
        final int                       totale = lines.size();
        final SapScheduleLineService    svc    = sapService;   // sistema fissato per tutta l'operazione
        final String                    sysLbl = sapConfig.getSystemLabel();

        System.out.println("[Xlsx2schedlines] DRY-RUN avviato — sistema: " + sysLbl
            + " — file: " + m_fileName
            + " — righe: " + totale
            + " — " + LocalDateTime.now().format(FMT_TS));

        final IObserver observer = LongOperationWithObserverPopup.prepare(
            "Verifica preventiva (Dry-run) — " + sysLbl);

        Runnable longOperation = new Runnable() {
            public void run() {

                // Risolve una volta per tutte, per ogni ordine distinto nel
                // file, se è categoria standard o "senza addebito" — prima
                // di elaborare le singole righe, cosi' il messaggio corretto
                // compare fin dalla prima riga di ciascun ordine.
                try {
                    java.util.List<String> orderNumbers = new ArrayList<>();
                    for (ScheduleLineData d : lines) orderNumbers.add(d.getOrderNumber());
                    svc.resolveOrderVariants(orderNumbers, msg -> {
                        observer.addMessage(msg);
                        System.out.println("[Xlsx2schedlines] " + msg);
                    });
                } catch (Exception e) {
                    observer.addMessage("⚠️ Risoluzione tipo ordine fallita: " + e.getMessage()
                        + " — si procede comunque riga per riga");
                    System.out.println("[Xlsx2schedlines] Risoluzione tipo ordine fallita: " + e.getMessage());
                }

                for (int i = 0; i < totale; i++) {
                    ScheduleLineData data     = lines.get(i);
                    int              rigaExcel = items.get(i).getRowIndex();

                    try {
                        SapDryRunResult result    = svc.dryRun(data);
                        String          dettaglio = result.getDettaglio();

                        if ("NESSUNA_MODIFICA".equals(dettaglio)) {
                            data.setDryRunResult("⏭️ Nessuna modifica — qtà e data invariate, verrà saltata");
                            data.setProcessingResult("⏭️ Nessuna modifica");
                        } else if ("EVASA".equals(dettaglio)) {
                            data.setDryRunResult("📦 Schedulazione già evasa (qtà open = 0) — verrà saltata");
                            data.setProcessingResult("📦 Già evasa");
                            data.setCreatedScheduleLine("0");
                        } else if (dettaglio != null && dettaglio.startsWith("NON_GESTIBILE:")) {
                            String motivo = dettaglio.substring("NON_GESTIBILE:".length());
                            data.setDryRunResult("🚫 Non gestibile — " + motivo);
                            data.setProcessingResult("🚫 Non gestibile");
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

        if (sapService == null) {
            OKPopup.createInstance("", "Nessun sistema SAP attivo — selezionare un sistema valido.");
            return;
        }

        final List<ScheduleLineData> lines  = scheduleLines;
        final List<GridJSONdataItem> items  = new ArrayList<>(itemsInGriglia);
        final int                    totale = lines.size();
        final SapScheduleLineService svc    = sapService;   // sistema fissato per tutta l'operazione
        final String                 sysLbl = sapConfig.getSystemLabel();

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

        System.out.println("[Xlsx2schedlines] ELABORAZIONE avviata — sistema: " + sysLbl
            + " — file: " + m_fileName
            + " — righe: " + totale
            + " — " + m_elabInizio.format(FMT_TS));

        final IObserver observer = LongOperationWithObserverPopup.prepare(
            "Aggiornamento Schedule Lines SAP — " + sysLbl);

        Runnable longOperation = new Runnable() {
            public void run() {

                StringBuilder log = new StringBuilder();
                log.append("Inizio elaborazione: ").append(m_elabInizio.format(FMT_TS)).append("\n");
                log.append("Sistema SAP: ").append(sysLbl).append("\n");
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
                             || procResult.contains("Non gestibile")
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
                        SapResponse response = svc.updateScheduleLine(data);

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
    // PREFERENZA UTENTE — sistema predefinito
    // =========================

    /** Roundtrip di avvio: serve solo a far arrivare il valore del local storage. */
    public void onStartupRoundtrip(ActionEvent event) {
        m_preferenceStartupDone = true;   // se il browser non aveva nulla, non arriverà più nulla
    }

    public String getPreferredSystemId() { return m_preferredSystemId; }

    /**
     * Chiamato da CLIENTLOCALSTORAGE quando il browser comunica il valore
     * salvato. All'avvio la preferenza viene applicata, ma solo se l'utente
     * non ha già iniziato a lavorare.
     */
    public void setPreferredSystemId(String v) {
        String id = (v == null || v.isBlank()) ? null : v.trim();
        m_preferredSystemId = id;

        if (m_preferenceStartupDone) return;
        m_preferenceStartupDone = true;

        if (id == null || sapRegistry == null || id.equals(m_selectedSystemId)) return;
        if (sapRegistry.getSystem(id) == null) {
            Statusbar.outputWarning("Il sistema predefinito salvato in questo browser (" + id
                + ") non esiste più in config.properties — preferenza ignorata");
            return;
        }
        if (m_statoElab == StatoElab.IN_CORSO || scheduleLines != null) return;

        String err = activateSystem(id);
        if (err != null) {
            Statusbar.outputWarning("Sistema predefinito " + id + " non utilizzabile: " + err);
            return;
        }
        if (sapConfig.isProductive()) {
            Statusbar.outputWarning("Aperto su PRODUZIONE (" + sapConfig.getSystemName()
                + ") — sistema predefinito di questo browser");
        } else {
            Statusbar.outputMessage("Aperto su " + sapConfig.getSystemName()
                + " — sistema predefinito di questo browser");
        }
    }

    /** Fissa il sistema attualmente selezionato come predefinito per questo browser. */
    public void onSavePreferredSystem(ActionEvent event) {
        if (sapConfig == null) {
            OKPopup.createInstance("", "Nessun sistema attivo da salvare.");
            return;
        }
        final String id   = sapConfig.getSystemId();
        final String name = sapConfig.getSystemName();

        if (sapConfig.isProductive()) {
            YESNOPopup.createInstance("Sistema predefinito: PRODUZIONE",
                "Stai per fissare come predefinito il sistema di PRODUZIONE \"" + name + "\".\n\n"
                + "Da questo browser l'applicazione si aprirà sempre collegata alla produzione.\n\n"
                + "Confermi?",
                new YESNOPopup.IYesNoListener() {
                    public void reactOnYes() { storePreferredSystem(id, name); }
                    public void reactOnNo()  { Statusbar.outputMessage("Operazione annullata"); }
                });
            return;
        }
        storePreferredSystem(id, name);
    }

    private void storePreferredSystem(String id, String name) {
        m_preferredSystemId     = id;     // CLIENTLOCALSTORAGE lo scrive nel browser
        m_preferenceStartupDone = true;
        Statusbar.outputSuccess("Sistema predefinito per questo browser: " + name);
    }

    /** Rimuove la preferenza: si torna a sap.system.default di config.properties. */
    public void onClearPreferredSystem(ActionEvent event) {
        m_preferredSystemId     = "";     // stringa vuota = nessuna preferenza
        m_preferenceStartupDone = true;
        Statusbar.outputMessage("Preferenza rimossa — all'apertura verrà usato il default di config.properties");
    }

    public boolean isPreferredSystemSet() {
        return m_preferredSystemId != null && !m_preferredSystemId.isBlank();
    }

    public String getPreferredSystemText() {
        String def = sapRegistry != null ? sapRegistry.getDefaultSystemId() : "?";
        SapSystemInfo defInfo = sapRegistry != null ? sapRegistry.getSystem(def) : null;
        String defTxt = defInfo != null ? defInfo.getComboText() : def;
        if (!isPreferredSystemSet()) {
            return "Nessuna preferenza salvata — all'apertura si usa il default di config.properties: " + defTxt;
        }
        SapSystemInfo p = sapRegistry != null ? sapRegistry.getSystem(m_preferredSystemId) : null;
        return "All'apertura si usa: " + (p != null ? p.getComboText() : m_preferredSystemId + " (non più configurato)");
    }

    public Trigger getStartupTrigger() { return m_startupTrigger; }

    // =========================
    // BARRA SISTEMA SAP (header)
    // =========================

    public boolean isSystemProductive() { return sapConfig != null && sapConfig.isProductive(); }

    public String getSystemBarBackground() {
        if (sapConfig == null) return COLOR_NONE_BG;
        return sapConfig.isProductive() ? COLOR_PROD_BG : COLOR_TEST_BG;
    }

    public String getSystemBarForeground() {
        return (sapConfig != null && sapConfig.isProductive()) ? COLOR_PROD_FG : COLOR_TEST_FG;
    }

    /** Font: molto grande per i sistemi di test, normale per la produzione. */
    public String getSystemBarFont() {
        return (sapConfig != null && sapConfig.isProductive()) ? "size:14;weight:bold" : "size:22;weight:bold";
    }

    public String getSystemBarText() {
        if (sapConfig == null) return "⛔ NESSUN SISTEMA SAP ATTIVO — verificare config.properties";
        if (sapConfig.isProductive()) {
            return "● PRODUZIONE — " + sapConfig.getSystemName() + "  [" + sapConfig.getSystemId() + "]";
        }
        return "⚠ SISTEMA DI TEST — NON PRODUTTIVO ⚠   " + sapConfig.getSystemName()
             + "  [" + sapConfig.getSystemId() + "]";
    }

    public String getSystemBarSubText() {
        if (sapConfig == null) return "";
        return sapConfig.getHost() + "  ·  client " + sapConfig.getClient()
             + "  ·  utente " + sapConfig.getUsername();
    }

    /** Bordo rosso spesso attorno all'area dati quando non si è in produzione. */
    public String getBodyBorder() {
        return (sapConfig != null && sapConfig.isProductive()) ? "" : COLOR_TEST_BODY;
    }

    public String getPageTitle() {
        String base = "Aggiorna schedulazioni OdV da file XLSX/XLS";
        if (sapConfig == null) return base;
        return base + (sapConfig.isProductive()
            ? "  —  PRODUZIONE: " + sapConfig.getSystemName()
            : "  —  ⚠ TEST: " + sapConfig.getSystemName());
    }

    public ValidValuesBinding getSystemVVB()      { return m_systemVVB; }
    public String  getSelectedSystemId()          { return m_selectedSystemId; }
    public void    setSelectedSystemId(String v)  { changeSystem(v); }
    /** Combo disabilitata durante l'elaborazione. */
    public boolean isSystemSelectionEnabled()     { return m_statoElab != StatoElab.IN_CORSO; }

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
