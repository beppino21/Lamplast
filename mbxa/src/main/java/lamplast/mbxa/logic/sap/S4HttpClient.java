package lamplast.mbxa.logic.sap;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client HTTP minimale per le API OData V2 di S/4HANA Cloud, con Basic Auth
 * (utente di comunicazione). Solo lettura: nessuna gestione del token CSRF.
 */
public class S4HttpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ODATA_DATE = Pattern.compile("/Date\\((-?\\d+)([+-]\\d{4})?\\)/");
    private static final Pattern ODATA_TIME = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");

    /** Errore HTTP con codice di stato, per distinguere ad es. il 404 "documento non trovato". */
    public static class ODataException extends IOException {
        private static final long serialVersionUID = 1L;
        private final int m_status;

        public ODataException(int status, String message) {
            super(message);
            m_status = status;
        }

        public int getStatus() { return m_status; }
    }

    private final String m_baseUrl;
    private final String m_authHeader;
    private final HttpClient m_http;
    private final Duration m_timeout;

    public S4HttpClient(String baseUrl, String user, String password, int timeoutSeconds) {
        m_baseUrl = baseUrl;
        m_authHeader = "Basic " + Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        m_timeout = Duration.ofSeconds(timeoutSeconds);
        m_http = HttpClient.newBuilder().connectTimeout(m_timeout).build();
    }

    /**
     * GET di un path OData relativo al baseUrl (es. "/sap/opu/odata/sap/API_X/Entity?$filter=...").
     * Restituisce il nodo "d" della risposta V2.
     */
    public JsonNode get(String path) throws IOException, InterruptedException {
        String sep = path.contains("?") ? "&" : "?";
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(m_baseUrl + path + sep + "$format=json"))
            .timeout(m_timeout)
            .header("Authorization", m_authHeader)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> resp = m_http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            String body = resp.body() == null ? "" : resp.body();
            if (body.length() > 600) body = body.substring(0, 600) + "...";
            throw new ODataException(resp.statusCode(),
                "OData GET fallita: HTTP " + resp.statusCode() + " - " + path + " - " + body);
        }
        JsonNode root = MAPPER.readTree(resp.body());
        return root.path("d");
    }

    // ------------------------------------------------------------------------
    // helper OData V2
    // ------------------------------------------------------------------------

    /** Codifica un'espressione ($filter, $select...) per l'uso nella query string. */
    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Valore stringa per un letterale OData: raddoppia l'apice. */
    public static String lit(String s) {
        return "'" + (s == null ? "" : s.replace("'", "''")) + "'";
    }

    /** Elenco dei risultati di una collection V2 ("results"), anche se assente. */
    public static JsonNode results(JsonNode d) {
        JsonNode r = d.path("results");
        return r.isArray() ? r : MAPPER.createArrayNode();
    }

    public static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText();
        return s.isBlank() ? null : s.trim();
    }

    /** Edm.DateTime V2: "/Date(1770595200000)/" (mezzanotte UTC per i campi data). */
    public static LocalDate parseDate(String s) {
        if (s == null) return null;
        Matcher m = ODATA_DATE.matcher(s);
        if (!m.matches()) return null;
        long millis = Long.parseLong(m.group(1));
        return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate();
    }

    /** Edm.Time V2: "PT11H23M31S". */
    public static LocalTime parseTime(String s) {
        if (s == null) return null;
        Matcher m = ODATA_TIME.matcher(s);
        if (!m.matches()) return null;
        int h = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
        int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        int sec = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        return LocalTime.of(h % 24, min, sec);
    }

    /** Letterale datetime V2 per i filtri: datetime'2026-10-01T00:00:00'. */
    public static String dateLiteral(LocalDate d) {
        return "datetime'" + d + "T00:00:00'";
    }
}
