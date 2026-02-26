package com.eone.coaextractor.client;

import com.eone.coaextractor.config.AppConfig;
import com.eone.coaextractor.model.GlAccount;
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
 * Client OData v4 per S/4HANA Cloud - Piano dei Conti.
 *
 * Servizio: ZPDC_SBV4
 * Entity set: ZPDC_C
 * Filtro: CompanyCode + Language
 *
 * Paging: gestisce sia $top/$skip che @odata.nextLink (server-driven paging).
 * Retry automatico (max 3 tentativi) su errori temporanei (5xx, timeout).
 */
public class S4HanaClient {

    private static final Logger log = LoggerFactory.getLogger(S4HanaClient.class);

    // Path del servizio OData v4 custom
    private static final String SERVICE_PATH = "/sap/opu/odata4/sap/zc_pdctext_sb/srvd_a2x/sap/zc_pdctext_srv/0001";
    private static final String ENTITY_SET   = "ZC_PDCTEXT";

    private static final int MAX_RETRIES     = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final AppConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String basicAuthHeader;

    public S4HanaClient(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();

        String credentials = config.s4Username + ":" + config.s4Password;
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.httpTimeoutConnect))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<GlAccount> fetchAllAccounts() {
        log.info("Avvio estrazione piano dei conti per società '{}' in lingua '{}'",
                config.companyCode, config.language);

        List<GlAccount> allAccounts = new ArrayList<>();
        String nextUrl = buildFirstUrl();
        int page = 1;

        while (nextUrl != null) {
            log.debug("Richiesta pagina {} - URL: {}", page, nextUrl);

            String responseBody = executeWithRetry(nextUrl);
            PageResult result = parseResponse(responseBody);

            log.info("Pagina {}: ricevuti {} conti", page, result.accounts().size());
            allAccounts.addAll(result.accounts());

            if (result.nextLink() != null) {
                // Server-driven paging: usa il nextLink fornito dal server
                nextUrl = result.nextLink();
            } else if (result.accounts().size() == config.pageSize) {
                // Client-driven paging: ci potrebbero essere altre pagine
                nextUrl = buildSkipUrl(allAccounts.size());
            } else {
                // Ultima pagina
                nextUrl = null;
            }
            page++;
        }

        log.info("Estrazione completata: {} conti totali", allAccounts.size());
        return allAccounts;
    }

    // -------------------------------------------------------------------------
    // URL Building
    // -------------------------------------------------------------------------

    private String buildFirstUrl() {
        String filter = "CompanyCode eq '" + config.companyCode + "'"
                      + " and Language eq '" + config.language + "'";

        String select = "CompanyCode,ChartOfAccounts,GLAccount,Language,GLAccountName,GLAccountLongName";

        return config.s4BaseUrl + SERVICE_PATH + "/" + ENTITY_SET
                + "?$filter=" + encodeParam(filter)
                + "&$select=" + encodeParam(select)
                + "&$top=" + config.pageSize;
    }

    private String buildSkipUrl(int skip) {
        String filter = "CompanyCode eq '" + config.companyCode + "'"
                      + " and Language eq '" + config.language + "'";

        String select = "CompanyCode,ChartOfAccounts,GLAccount,Language,GLAccountName,GLAccountLongName";

        return config.s4BaseUrl + SERVICE_PATH + "/" + ENTITY_SET
                + "?$filter=" + encodeParam(filter)
                + "&$select=" + encodeParam(select)
                + "&$top=" + config.pageSize
                + "&$skip=" + skip;
    }

    private String encodeParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // HTTP + Retry
    // -------------------------------------------------------------------------

    private String executeWithRetry(String url) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", basicAuthHeader)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(config.httpTimeoutRead))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                int status = response.statusCode();
                log.debug("HTTP {}", status);

                if (status == 200) {
                    return response.body();
                } else if (status == 401) {
                    throw new S4HanaClientException(
                            "Autenticazione fallita (HTTP 401). Verificare username/password in config.properties.");
                } else if (status == 403) {
                    throw new S4HanaClientException(
                            "Accesso negato (HTTP 403). Verificare che l'utente sia associato al " +
                            "Communication Arrangement con scenario ZC_PDCTEXT_COM.");
                } else if (status == 404) {
                    throw new S4HanaClientException(
                            "Servizio non trovato (HTTP 404). Verificare s4.base.url e il path del servizio.");
                } else if (status >= 500 && attempt < MAX_RETRIES) {
                    log.warn("Errore server HTTP {} (tentativo {}/{}). Retry tra {}ms...",
                            status, attempt, MAX_RETRIES, RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS * attempt);
                } else {
                    throw new S4HanaClientException(
                            "Risposta HTTP inattesa: " + status + " - " + response.body());
                }

            } catch (S4HanaClientException e) {
                throw e;
            } catch (java.net.http.HttpTimeoutException e) {
                if (attempt < MAX_RETRIES) {
                    log.warn("Timeout (tentativo {}/{}). Retry...", attempt, MAX_RETRIES);
                    sleep(RETRY_DELAY_MS * attempt);
                } else {
                    throw new S4HanaClientException("Timeout HTTP dopo " + MAX_RETRIES + " tentativi.", e);
                }
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    log.warn("Errore di rete (tentativo {}/{}): {}. Retry...", attempt, MAX_RETRIES, e.getMessage());
                    sleep(RETRY_DELAY_MS * attempt);
                } else {
                    throw new S4HanaClientException("Errore HTTP dopo " + MAX_RETRIES + " tentativi: " + e.getMessage(), e);
                }
            }
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    // -------------------------------------------------------------------------
    // JSON Parsing
    // -------------------------------------------------------------------------

    private PageResult parseResponse(String json) {
        List<GlAccount> accounts = new ArrayList<>();
        String nextLink = null;

        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                String message = error.path("message").asText(error.toString());
                throw new S4HanaClientException("Errore OData dal server: " + message);
            }

            JsonNode valueArray = root.path("value");
            if (!valueArray.isArray()) {
                throw new S4HanaClientException("Struttura JSON inattesa (manca array 'value'): " + json);
            }

            for (JsonNode node : valueArray) {
                String chartOfAccounts = node.path("ChartOfAccounts").asText("");
                String glAccount       = node.path("GLAccount").asText("").trim();
                String shortText       = node.path("GLAccountName").asText(null);
                String longText        = node.path("GLAccountLongName").asText(null);

                if (shortText != null && shortText.isBlank()) shortText = null;
                if (longText  != null && longText.isBlank())  longText  = null;

                accounts.add(new GlAccount(chartOfAccounts, glAccount, shortText, longText));
            }

            // OData v4 server-driven paging
            JsonNode nextLinkNode = root.path("@odata.nextLink");
            if (!nextLinkNode.isMissingNode() && !nextLinkNode.asText().isBlank()) {
                nextLink = nextLinkNode.asText();
                log.debug("Trovato @odata.nextLink, ci sono altre pagine");
            }

        } catch (S4HanaClientException e) {
            throw e;
        } catch (Exception e) {
            throw new S4HanaClientException("Errore nel parsing della risposta JSON: " + e.getMessage(), e);
        }

        return new PageResult(accounts, nextLink);
    }

    private record PageResult(List<GlAccount> accounts, String nextLink) {}
}
