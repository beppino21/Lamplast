package lamplast.utility.service;

import java.util.Map;

public class SapResponse {

	private int httpStatus;
	private boolean success;
	private boolean warning;

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
			if (json.containsKey("d")) {
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
			this.warning = true;
			this.sapMessage = headers.get("sap-message").toString();
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
