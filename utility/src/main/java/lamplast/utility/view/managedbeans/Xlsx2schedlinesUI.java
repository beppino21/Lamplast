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

	private SapConfiguration sapConfig = new SapConfiguration();
	private SapScheduleLineService sapService = null;
	private ExcelParser excelParser = null;

	// =========================
	// DATI UI
	// =========================

	private FIXGRIDListBinding<GridJSONdataItem> m_gridJSONdata = new FIXGRIDListBinding<>();

	private Boolean m_enableVA03 = false;
	private String m_salesOrderNumber;
	private String m_fileName;
	private String m_logText = "Nuova sessione";

	String m_lblDataProd;
	String m_lblQuantita;
	String m_lblMaterialeText;
	String m_lblMateriale;
	String m_lblSchedulazione;
	String m_lblPosizione;
	String m_lblOrdine;

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

		public String getOrderNumber() {
			return data.getOrderNumber();
		}

		public String getItemNumber() {
			return data.getItemNumber() != null ? data.getItemNumber().toString() : "";
		}

		public String getSchedLine() {
			return data.getScheduleLine() != null ? data.getScheduleLine().toString() : "";
		}

		public String getMaterial() {
			return data.getMaterial();
		}

		public String getMaterialText() {
			return data.getMaterialText();
		}

		public String getQuantity() {
			return data.getQuantity();
		}

		public String getSchedDate() {
			return data.getProductionDate() != null
					? data.getProductionDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
					: "";
		}

		public String getProcessingResult() {
			if (data.getProcessingResult() == null) {
				return ""; // Non ancora elaborato
			}
			return data.getProcessingResult();
		}

		public String getErrorMessage() {
			return data.getErrorMessage() != null ? data.getErrorMessage() : "";
		}

		public void onRowSelect() {
			showOrderDetails(data.getOrderNumber());
		}

		public void onRowExecute() {
			showOrderDetails(data.getOrderNumber());
		}

		private void showOrderDetails(String orderNumber) {
			Statusbar.outputSuccess("Ordine selezionato: " + orderNumber);
			//I link seguenti sono validi, ma solo il terzo è responsive, cioè funziona anche da cellulare!
			//m_salesOrderNumber = "https://my428121.s4hana.cloud.sap/ui#SalesOrder-display?SalesOrder=" + orderNumber;
			//m_salesOrderNumber = "https://my428121.s4hana.cloud.sap/sap/bc/ui2/flp#SalesOrder-display?SalesOrder=" + orderNumber;
			//m_salesOrderNumber = "https://my428121.s4hana.cloud.sap/sap/bc/ui2/flp#SalesOrder-displayFactSheet?SalesOrder=" + orderNumber;
			m_salesOrderNumber = sapConfig.getBaseUrl() + "/sap/bc/ui2/flp#SalesOrder-displayFactSheet?SalesOrder=" + orderNumber;
			m_enableVA03 = true;
		}
	}
	
	// =========================
	// COSTRUTTORE
	// =========================

	public Xlsx2schedlinesUI() {
		// Inizializzazione servizi
		this.sapConfig = new SapConfiguration();
		this.sapService = new SapScheduleLineService(sapConfig);

		// Configurazione parser Excel
		m_lblOrdine = "Ordine";
		m_lblPosizione = "Pos.";
		m_lblSchedulazione = "Sch.";
		m_lblMateriale = "Materiale";
		m_lblMaterialeText = "Text";
		m_lblQuantita = "Qtà";
		m_lblDataProd = "Data prod.";

		m_logText = "Nuova sessione";

		// Configurazione parser Excel
//        ExcelParser.ColumnMapping mapping = new ExcelParser.ColumnMapping();
//        mapping.orderColumn = m_lblOrdine;
//        mapping.itemColumn = m_lblPosizione;
//        mapping.scheduleColumn = m_lblSchedulazione;
//        mapping.materialColumn = m_lblMateriale;
//        mapping.materialTextColumn = m_lblMaterialeText;
//        mapping.quantityColumn = m_lblQuantita;
//        mapping.dateColumn = m_lblDataProd;
//        
//        this.excelParser = new ExcelParser(mapping);
	}

	// =========================
	// GESTORI EVENTI
	// =========================

	/**
	 * Caricamento file Excel
	 */
	public void onLoadXLSX(ActionEvent ae) {

		// Configurazione parser Excel
		ExcelParser.ColumnMapping mapping = new ExcelParser.ColumnMapping();
		mapping.orderColumn = m_lblOrdine;
		mapping.itemColumn = m_lblPosizione;
		mapping.scheduleColumn = m_lblSchedulazione;
		mapping.materialColumn = m_lblMateriale;
		mapping.materialTextColumn = m_lblMaterialeText;
		mapping.quantityColumn = m_lblQuantita;
		mapping.dateColumn = m_lblDataProd;

		this.excelParser = new ExcelParser(mapping);

		// Reset stato
		m_gridJSONdata.getItems().clear();
		m_salesOrderNumber = "";
		m_enableVA03 = false;
		m_logText = "Nuova sessione";
		scheduleLines = null;

		if (!(ae instanceof BaseActionEventUpload)) {
			return;
		}

		BaseActionEventUpload bae = (BaseActionEventUpload) ae;
		m_fileName = bae.getClientFileName();

		try {
			// Conversione hex -> byte[]
			byte[] excelBytes = hexStringToByteArray(bae.getHexByteString());

			// Parsing Excel
			scheduleLines = excelParser.parseExcel(excelBytes);

			// Popolamento griglia
			for (ScheduleLineData data : scheduleLines) {
				m_gridJSONdata.getItems().add(new GridJSONdataItem(data));
			}

			Statusbar.outputMessage("File " + m_fileName + " caricato: " + scheduleLines.size() + " righe");

		} catch (Exception e) {
			Statusbar.outputMessage("Errore caricamento file: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Aggiornamento schedulazioni su SAP
	 */
	public void onUpdSchedLine(ActionEvent event) {

		// Validazioni iniziali
		if (!m_logText.equalsIgnoreCase("Nuova sessione")) {
			OKPopup.createInstance("", "Sessione modificata, elaborazione non più possibile");
			return;
		}

		if (scheduleLines == null || scheduleLines.isEmpty()) {
			OKPopup.createInstance("", "Nessun file di input è stato caricato");
			return;
		}

		// Elaborazione schedulazioni
		StringBuilder log = new StringBuilder();
		log.append("Inizio elaborazione\n");
		log.append("=====================\n\n");

		int successi = 0;
		int errori = 0;

		for (ScheduleLineData data : scheduleLines) {

			try {
				log.append("Elaborazione: ").append(data.toString()).append("\n");

				// Validazione dati
				String validationError = data.validate();
				if (validationError != null) {
					log.append("  ⚠ Errore validazione: ").append(validationError).append("\n\n");
					data.setProcessingResult("⚠ Errore");
					data.setErrorMessage("Validazione: " + validationError);
					errori++;
					continue;
				}

				// Chiamata SAP
				SapResponse response = sapService.updateScheduleLine(data);

				if (response.isSuccess()) {
					log.append("  ✓ Successo (HTTP ").append(response.getHttpStatus()).append(")\n");
					data.setProcessingResult("✓ Successo");
					data.setErrorMessage(null);

					if (response.isWarning()) {
						log.append("  ⚠ Warning: ").append(response.getSapMessage()).append("\n");						
						String errorMsg = "HTTP " + response.getHttpStatus();
						data.setProcessingResult("✓ Successo" + "  ⚠ Warning");
						data.setErrorMessage(errorMsg);
					}
					successi++;
				} else {
					log.append("  ✗ Errore (HTTP ").append(response.getHttpStatus()).append(")\n");					
					String errorMsg = "HTTP " + response.getHttpStatus();
					data.setProcessingResult("✗ Errore");
					data.setErrorMessage(errorMsg);
					if (response.getSapMessage() != null) {
						log.append("    SAP: ").append(response.getSapMessage()).append("\n");
					}
					if (response.getSapCode() != null) {
						log.append("    Codice: ").append(response.getSapCode()).append("\n");
					}

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

		// Riepilogo
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
		int len = hex.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
		}
		return data;
	}

	// =========================
	// GETTERS/SETTERS
	// =========================

	public FIXGRIDListBinding<GridJSONdataItem> getGridJSONdata() {
		return m_gridJSONdata;
	}

	public String getLblDataProd() {
		return m_lblDataProd;
	}

	public void setLblDataProd(String value) {
		this.m_lblDataProd = value;
	}

	public String getLblQuantita() {
		return m_lblQuantita;
	}

	public void setLblQuantita(String value) {
		this.m_lblQuantita = value;
	}

	public String getLblMaterialeText() {
		return m_lblMaterialeText;
	}

	public void setLblMaterialeText(String value) {
		this.m_lblMaterialeText = value;
	}

	public String getLblMateriale() {
		return m_lblMateriale;
	}

	public void setLblMateriale(String value) {
		this.m_lblMateriale = value;
	}

	public String getLblSchedulazione() {
		return m_lblSchedulazione;
	}

	public void setLblSchedulazione(String value) {
		this.m_lblSchedulazione = value;
	}

	public String getLblPosizione() {
		return m_lblPosizione;
	}

	public void setLblPosizione(String value) {
		this.m_lblPosizione = value;
	}

	public String getLblOrdine() {
		return m_lblOrdine;
	}

	public void setLblOrdine(String value) {
		this.m_lblOrdine = value;
	}

	public Boolean getEnableVA03() {
		return m_enableVA03;
	}

	public void setEnableVA03(Boolean value) {
		this.m_enableVA03 = value;
	}

	public String getSalesOrderNumber() {
		return m_salesOrderNumber;
	}

	public void setSalesOrderNumber(String value) {
		this.m_salesOrderNumber = value;
	}

	public String getLogText() {
		return m_logText;
	}

	public void setLogText(String value) {
		this.m_logText = value;
	}

	public String getFileName() {
		return m_fileName;
	}

	public void setFileName(String value) {
		this.m_fileName = value;
	}

	// =========================
	// PAGEBEAN OVERRIDES
	// =========================

	public String getPageName() {
		return "/xlsx2schedlines.xml";
	}

	public String getRootExpressionUsedInPage() {
		return "#{d.Xlsx2schedlinesUI}";
	}
}