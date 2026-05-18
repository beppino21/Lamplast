package lamplast.utility.service;

import java.util.Map;

public class SapResponse {

	private int httpStatus;
	private boolean success;
	private boolean warning;
	private boolean frozen; // true = SAP ha risposto OK ma la modifica non è stata applicata

	private String sapCode;
	private String sapMessage;
	private String transactionId;

	private String rawBody;

	// =========================
	// COSTRUTTORI
	// =========================

	public SapResponse(int httpStatus) {
		this.httpStatus = httpStatus;
		this.success = httpStatus >= 200 && httpStatus < 300;
	}

	// =========================
	// GETTER
	// =========================

	public int getHttpStatus() {
		return httpStatus;
	}

	public boolean isSuccess() {
		return success;
	}

	public boolean isWarning() {
		return warning;
	}

	public boolean isFrozen() {
		return frozen;
	}

	public void setFrozen(boolean frozen) {
		this.frozen = frozen;
	}

	public void setSapMessage(String sapMessage) {
		this.sapMessage = sapMessage;
	}

	public String getSapCode() {
		return sapCode;
	}

	public String getSapMessage() {
		return sapMessage;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public String getRawBody() {
		return rawBody;
	}

	// =========================
	// PARSING
	// =========================

	@SuppressWarnings("unchecked")
	public void parseBody(String body) {

		this.rawBody = body;

		if (body == null || body.isBlank() || body.startsWith("<")) {
			return;
		}

		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

			Map<String, Object> json = mapper.readValue(body, Map.class);

			// --- CASO ERRORE ODATA ---
			if (json.containsKey("error")) {

				Map<String, Object> error = (Map<String, Object>) json.get("error");

				this.success = false;
				this.sapCode = (String) error.get("code");

				Map<String, Object> message = (Map<String, Object>) error.get("message");

				if (message != null) {
					this.sapMessage = (String) message.get("value");
				}

				Map<String, Object> inner = (Map<String, Object>) error.get("innererror");

				if (inner != null) {
					this.transactionId = (String) inner.get("transactionid");
				}
			}

			// --- CASO SUCCESSO POST (201) ---
			if (json.containsKey("d") && this.httpStatus >= 200 && this.httpStatus < 300) {
				this.success = true;
			}

		} catch (Exception e) {
			this.sapMessage = "Errore parsing risposta SAP: " + e.getMessage();
		}
	}

	public void parseHeaders(Map<String, java.util.List<String>> headers) {

		if (headers == null)
			return;

		// sap-message (warning / info)
		if (headers.containsKey("sap-message")) {
			String rawMsg = headers.get("sap-message").toString();

			// SLS_LORD/025 "Field SLINE_DATE is not an input field" è un warning
			// strutturale dell'API S/4HANA Public Cloud: SAP lo emette sempre quando
			// si aggiorna RequestedDeliveryDate, ma non indica alcun problema reale.
			boolean isKnownNoise = rawMsg.contains("SLS_LORD/025")
					&& rawMsg.contains("SLINE_DATE");

			if (!isKnownNoise) {
				if (this.httpStatus >= 200 && this.httpStatus < 300) {
					// Warning rilevante solo su risposta HTTP positiva
					this.warning    = true;
					this.sapMessage = rawMsg;
				} else {
					// Su errore HTTP il messaggio va nel log ma non alza il flag warning
					this.sapMessage = rawMsg;
				}
			}
		}

		// transactionid (a volte è header)
		if (headers.containsKey("transactionid")) {
			this.transactionId = headers.get("transactionid").get(0);
		}
	}

	// =========================
	// UTIL
	// =========================

	@Override
	public String toString() {
		return "SapResponse{" + "httpStatus=" + httpStatus + ", success=" + success + ", warning=" + warning
				+ ", sapCode='" + sapCode + '\'' + ", sapMessage='" + sapMessage + '\'' + ", transactionId='"
				+ transactionId + '\'' + '}';
	}
}
