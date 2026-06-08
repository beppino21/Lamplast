package it.eone.coge;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Base64;

/**
 * Client HTTP per chiamare la SOAP API di S/4HC.
 * Usa java.net.http.HttpClient (Java 11+) — nessuna dipendenza esterna.
 */
public class S4HcClient implements AutoCloseable {

	private static final String SOAP_PATH =
		    "/sap/bc/srt/scs_ext/sap/journalentrycreaterequestconfi";

		private static final String SOAP_ACTION =
		    "http://sap.com/xi/SAPSCORE/SFIN/JournalEntryCreateRequestConfirmation_In/JournalEntryCreateRequestConfirmation_InRequest";

    private final AppConfig   config;
    private final HttpClient  httpClient;
    private final String      endpointUrl;
    private final String      basicAuth;

    public S4HcClient(AppConfig config) {
        this.config      = config;
        this.endpointUrl = config.baseUrl + SOAP_PATH;
        this.httpClient  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.basicAuth   = "Basic " + Base64.getEncoder().encodeToString(
                (config.username + ":" + config.password)
                        .getBytes(StandardCharsets.UTF_8));

        System.out.println("[S4HC] Endpoint SOAP: " + endpointUrl);
    }

    public SoapResponse post(String soapEnvelope) throws IOException {

        // Dump payload completo su file (il terminale Windows tronca le righe lunghe)
        System.out.println("[DEBUG] URL: " + endpointUrl);
        System.out.println("[DEBUG] Payload length: " + soapEnvelope.length() + " chars");
        try {
            Path dumpPath = Paths.get("soap_request_debug.xml");
            Files.writeString(dumpPath, soapEnvelope, StandardCharsets.UTF_8);
            System.out.println("[DEBUG] Payload scritto in: " + dumpPath.toAbsolutePath());
        } catch (Exception ex) {
            System.out.println("[DEBUG] (dump non riuscito: " + ex.getMessage() + ")");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"" + SOAP_ACTION + "\"")
                .header("Authorization", basicAuth)
                .header("Accept-Language", config.language)
                .POST(HttpRequest.BodyPublishers.ofString(soapEnvelope, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            System.out.println("[DEBUG] HTTP Status: " + response.statusCode());
            System.out.println("[DEBUG] Headers: " + response.headers().map());

            // Dump risposta su file
            try {
                Path dumpResp = Paths.get("soap_response_debug.xml");
                Files.writeString(dumpResp, response.body(), StandardCharsets.UTF_8);
                System.out.println("[DEBUG] Response scritto in: " + dumpResp.toAbsolutePath());
            } catch (Exception ex) {
                System.out.println("[DEBUG] Body:\n" + response.body());
            }

            return new SoapResponse(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Richiesta interrotta: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        // HttpClient Java 11 non richiede chiusura esplicita
    }

    // -------------------------------------------------------------------------

    public static class SoapResponse {
        public final int    httpStatus;
        public final String body;

        public SoapResponse(int httpStatus, String body) {
            this.httpStatus = httpStatus;
            this.body       = body;
        }

        /**
         * Successo reale = HTTP 200, nessun Fault SOAP,
         * AccountingDocument presente e != "0000000000"
         */
        public boolean isSuccess() {
            if (httpStatus != 200) return false;
            if (body.contains("faultcode") || body.contains("Fault")) return false;
            String doc = extractDocumentNumber();
            return !doc.isEmpty() && !doc.equals("0000000000");
        }

        public String extractDocumentNumber() {
            return extractTag(body, "AccountingDocument");
        }

        /**
         * Estrae tutti i messaggi <Note> dal log della response
         * (sia Fault SOAP sia errori applicativi nel JournalEntryBulkCreateConfirmation).
         */
        public String extractFaultMessage() {
            // Prima prova SOAP Fault standard
            String msg = extractTag(body, "faultstring");
            if (!msg.isEmpty()) return msg;

            // Poi raccoglie tutti i <Note> dal Log applicativo SAP
            StringBuilder sb = new StringBuilder();
            String remaining = body;
            while (true) {
                int start = remaining.indexOf("<Note>");
                if (start == -1) break;
                int end = remaining.indexOf("</Note>", start);
                if (end == -1) break;
                String note = remaining.substring(start + 6, end).trim();
                if (!note.isEmpty()) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(note);
                }
                remaining = remaining.substring(end + 7);
            }
            return sb.length() > 0 ? sb.toString() : "(nessun dettaglio)";
        }

        private static String extractTag(String xml, String tag) {
            String open  = "<"  + tag + ">";
            String close = "</" + tag + ">";
            int start = xml.indexOf(open);
            if (start == -1) {
                String open2 = "<" + tag + " ";
                start = xml.indexOf(open2);
                if (start == -1) return "";
                start = xml.indexOf('>', start) + 1;
            } else {
                start += open.length();
            }
            int end = xml.indexOf(close, start);
            return end == -1 ? "" : xml.substring(start, end).trim();
        }
    }
}
