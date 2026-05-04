package com.eone.fcs.client;

import com.eone.fcs.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Classe base per tutti i client OData V2 verso S/4HANA Public Cloud.
 *
 * Gestisce: - Autenticazione Basic Auth - Paging OData V2 (d.results
 * + @odata.nextLink) - Retry automatico su errori 5xx e timeout (max 3
 * tentativi) - Parsing JSON con estrazione array "d.results"
 *
 * Le classi figlie implementano solo la logica specifica: - URL del servizio e
 * dell'entity set - Costruzione dei filtri ($filter, $select, $expand) -
 * Mapping JsonNode → model
 */
public abstract class AbstractS4Client {

	private static final Logger log = LoggerFactory.getLogger(AbstractS4Client.class);

	private static final int MAX_RETRIES = 3;
	private static final long RETRY_DELAY_MS = 2000;

	protected final AppConfig config;
	protected final HttpClient httpClient;
	protected final ObjectMapper mapper;
	protected final String basicAuthHeader;

	protected AbstractS4Client(AppConfig config) {
		this.config = config;
		this.mapper = new ObjectMapper();

		String credentials = config.s4Username + ":" + config.s4Password;
		this.basicAuthHeader = "Basic "
				+ Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.s4TimeoutConnect))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	// -------------------------------------------------------------------------
	// API protetta - usata dalle classi figlie
	// -------------------------------------------------------------------------

	/**
	 * Esegue una GET paginata e restituisce tutti i nodi dell'array "d.results".
	 * Gestisce automaticamente il paging tramite $skip o @odata.nextLink.
	 *
	 * @param firstUrl URL della prima pagina (già completo di $filter, $select,
	 *                 $top)
	 * @return lista di tutti i JsonNode ricevuti
	 */
	protected List<JsonNode> fetchAllPages(String firstUrl) {
		List<JsonNode> all = new ArrayList<>();
		String nextUrl = firstUrl;
		int page = 1;

		while (nextUrl != null) {
			log.debug("Pagina {} - URL: {}", page, nextUrl);
			String body = executeWithRetry(nextUrl);
			PageResult result = parseODataV2Response(body);

			log.debug("Pagina {}: {} record ricevuti", page, result.nodes().size());
			all.addAll(result.nodes());

			// Determina se ci sono altre pagine
			if (result.nextLink() != null) {
				nextUrl = result.nextLink();
			} else if (result.nodes().size() == config.s4PageSize) {
				// Client-driven paging: aggiungi $skip calcolato sulla firstUrl
				// (non su nextUrl) per evitare duplicazione del parametro $skip
				nextUrl = appendSkip(firstUrl, all.size());
			} else {
				nextUrl = null; // ultima pagina
			}
			page++;
		}

		log.debug("Fetch completato: {} record totali", all.size());
		return all;
	}

	/**
	 * Esegue una GET paginata su endpoint OData V4. Struttura risposta V4: {
	 * "value": [...], "@odata.nextLink": "..." }
	 */
	protected List<JsonNode> fetchAllPagesV4(String firstUrl) {
		List<JsonNode> all = new ArrayList<>();
		String nextUrl = firstUrl;
		int page = 1;

		while (nextUrl != null) {
			log.debug("V4 Pagina {} - URL: {}", page, nextUrl);
			String body = executeWithRetry(nextUrl);

			try {
				JsonNode root = mapper.readTree(body);

				// Controllo errore OData V4
				JsonNode error = root.path("error");
				if (!error.isMissingNode()) {
					String msg = error.path("message").asText(error.toString());
					throw new S4ClientException("Errore OData V4: " + msg);
				}

				// V4 usa "value" invece di "d.results"
				JsonNode value = root.path("value");
				if (!value.isArray()) {
					throw new S4ClientException("Struttura JSON V4 inattesa (manca 'value'): "
							+ body.substring(0, Math.min(300, body.length())));
				}

				List<JsonNode> nodes = new ArrayList<>();
				value.forEach(nodes::add);
				all.addAll(nodes);
				log.debug("V4 Pagina {}: {} record ricevuti", page, nodes.size());

				// V4 paging tramite @odata.nextLink
				JsonNode nextLink = root.path("@odata.nextLink");
				if (!nextLink.isMissingNode() && !nextLink.asText().isBlank()) {
					nextUrl = nextLink.asText();
					log.debug("V4 nextLink: {}", nextUrl);
				} else if (nodes.size() == config.s4PageSize) {
					// Client-driven paging
					String sep = firstUrl.contains("?") ? "&" : "?";
					nextUrl = firstUrl + sep + "$skip=" + all.size();
				} else {
					nextUrl = null;
				}
				page++;

			} catch (S4ClientException e) {
				throw e;
			} catch (Exception e) {
				throw new S4ClientException("Errore parsing JSON V4: " + e.getMessage(), e);
			}
		}

		log.debug("V4 Fetch completato: {} record totali", all.size());
		return all;
	}

	/**
	 * Esegue una GET singola (senza paging) e restituisce i nodi. Usato per
	 * chiamate con filtro puntuale (es. singolo OdA).
	 */
	protected List<JsonNode> fetchSinglePage(String url) {
		String body = executeWithRetry(url);
		return parseODataV2Response(body).nodes();
	}

	// -------------------------------------------------------------------------
	// URL helpers
	// -------------------------------------------------------------------------

	/**
	 * Costruisce l'URL base del servizio OData. Le classi figlie forniscono il path
	 * del servizio e l'entity set.
	 *
	 * Esempio: https://my434383-api.s4hana.cloud.sap +
	 * /sap/opu/odata/SAP/API_PURCHASEORDER_PROCESS_SRV + /A_PurchaseOrderItem
	 */
	protected String buildUrl(String servicePath, String entitySet) {
		return config.s4BaseUrl + servicePath + "/" + entitySet;
	}

	protected String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/**
	 * Aggiunge (o sostituisce) il parametro $skip nell'URL. Rimuove eventuali $skip
	 * già presenti prima di aggiungerne uno nuovo, evitando la duplicazione del
	 * parametro su chiamate paginate successive.
	 */
	private String appendSkip(String baseUrl, int skip) {
		// Rimuove $skip preesistente (es. "&$skip=100" o "?$skip=100")
		String cleaned = baseUrl.replaceAll("([&?])\\$skip=\\d+", "");
		String sep = cleaned.contains("?") ? "&" : "?";
		return cleaned + sep + "$skip=" + skip;
	}

	// -------------------------------------------------------------------------
	// HTTP + Retry
	// -------------------------------------------------------------------------

	private String executeWithRetry(String url) {
		int attempt = 0;
		while (true) {
			attempt++;
			try {
				HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
						.header("Authorization", basicAuthHeader).header("Accept", "application/json")
						.timeout(Duration.ofSeconds(config.s4TimeoutRead)).GET().build();

				HttpResponse<String> response = httpClient.send(request,
						HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

				int status = response.statusCode();
				log.debug("HTTP {} per {}", status, url);

				switch (status) {
				case 200 -> {
					return response.body();
				}
				case 401 ->
					throw new S4ClientException("Autenticazione fallita (HTTP 401). Verificare username/password.");
				case 403 ->
					throw new S4ClientException("Accesso negato (HTTP 403). Verificare Communication Arrangement.");
				case 404 -> throw new S4ClientException("Servizio non trovato (HTTP 404). Verificare URL: " + url);
				default -> {
					if (status >= 500 && attempt < MAX_RETRIES) {
						log.warn("Errore server HTTP {} (tentativo {}/{}). Retry tra {}ms...", status, attempt,
								MAX_RETRIES, RETRY_DELAY_MS * attempt);
						sleep(RETRY_DELAY_MS * attempt);
					} else {
						throw new S4ClientException("HTTP " + status + " - "
								+ response.body().substring(0, Math.min(200, response.body().length())));
					}
				}
				}

			} catch (S4ClientException e) {
				throw e;
			} catch (java.net.http.HttpTimeoutException e) {
				if (attempt < MAX_RETRIES) {
					log.warn("Timeout (tentativo {}/{}). Retry...", attempt, MAX_RETRIES);
					sleep(RETRY_DELAY_MS * attempt);
				} else {
					throw new S4ClientException("Timeout dopo " + MAX_RETRIES + " tentativi.", e);
				}
			} catch (Exception e) {
				if (attempt < MAX_RETRIES) {
					log.warn("Errore rete (tentativo {}/{}): {}. Retry...", attempt, MAX_RETRIES, e.getMessage());
					sleep(RETRY_DELAY_MS * attempt);
				} else {
					throw new S4ClientException("Errore HTTP dopo " + MAX_RETRIES + " tentativi: " + e.getMessage(), e);
				}
			}
		}
	}

	private void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	// -------------------------------------------------------------------------
	// Parsing OData V2
	// -------------------------------------------------------------------------

	/**
	 * Parsa una risposta OData V2. La struttura attesa è: { "d": { "results":
	 * [...], "__next": "..." } }
	 */
	private PageResult parseODataV2Response(String json) {
		try {
			JsonNode root = mapper.readTree(json);

			// Controllo errore OData
			JsonNode error = root.path("error");
			if (!error.isMissingNode()) {
				String msg = error.path("message").path("value").asText(error.toString());
				throw new S4ClientException("Errore OData: " + msg);
			}

			// OData V2: d.results
			JsonNode results = root.path("d").path("results");
			if (!results.isArray()) {
				// Alcuni endpoint restituiscono d senza results (singola entità)
				// In quel caso "d" stesso è l'oggetto
				JsonNode d = root.path("d");
				if (d.isObject() && !d.isMissingNode()) {
					return new PageResult(List.of(d), null);
				}
				throw new S4ClientException("Struttura JSON inattesa (manca d.results): "
						+ json.substring(0, Math.min(300, json.length())));
			}

			List<JsonNode> nodes = new ArrayList<>();
			results.forEach(nodes::add);

			// OData V2 next link: campo "__next" dentro "d"
			String nextLink = null;
			JsonNode next = root.path("d").path("__next");
			if (!next.isMissingNode() && !next.asText().isBlank()) {
				nextLink = next.asText();
				log.debug("Trovato __next link: {}", nextLink);
			}

			return new PageResult(nodes, nextLink);

		} catch (S4ClientException e) {
			throw e;
		} catch (Exception e) {
			throw new S4ClientException("Errore parsing JSON: " + e.getMessage(), e);
		}
	}

	// -------------------------------------------------------------------------
	// Record interni
	// -------------------------------------------------------------------------

	protected record PageResult(List<JsonNode> nodes, String nextLink) {
	}

	// -------------------------------------------------------------------------
	// Utility per le classi figlie
	// -------------------------------------------------------------------------

	/** Legge un campo stringa dal nodo, restituisce null se assente o vuoto */
	protected static String str(JsonNode node, String field) {
		JsonNode n = node.path(field);
		if (n.isMissingNode() || n.isNull())
			return null;
		String v = n.asText("").trim();
		return v.isEmpty() ? null : v;
	}

	/** Legge un campo stringa, restituisce defaultValue se assente */
	protected static String str(JsonNode node, String field, String defaultValue) {
		String v = str(node, field);
		return v != null ? v : defaultValue;
	}

	/** Legge un campo numerico come Double, restituisce null se assente */
	protected static Double dbl(JsonNode node, String field) {
		JsonNode n = node.path(field);
		if (n.isMissingNode() || n.isNull())
			return null;
		try {
			return Double.parseDouble(n.asText("0").trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Legge un campo intero, restituisce null se assente */
	protected static Integer integer(JsonNode node, String field) {
		JsonNode n = node.path(field);
		if (n.isMissingNode() || n.isNull())
			return null;
		try {
			return Integer.parseInt(n.asText("0").trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Parsa una data OData V2 nel formato /Date(1234567890000)/ e restituisce una
	 * stringa ISO (yyyy-MM-dd) o null.
	 */
	protected static java.time.LocalDate odataDate(JsonNode node, String field) {
		String raw = str(node, field);
		if (raw == null)
			return null;
		try {
			// formato: /Date(milliseconds)/ oppure /Date(milliseconds+offset)/
			String digits = raw.replaceAll("[^0-9]", "");
			if (digits.isEmpty())
				return null;
			// Prende solo i primi 13 cifre (millisecondi epoch)
			long ms = Long.parseLong(digits.substring(0, Math.min(13, digits.length())));
			return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate();
		} catch (Exception e) {
			return null;
		}
	}
}