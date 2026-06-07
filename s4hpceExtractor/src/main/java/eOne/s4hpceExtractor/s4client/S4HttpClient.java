package eOne.s4hpceExtractor.s4client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class S4HttpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PAGE_SIZE = 500;

    private final S4Config config;
    private final HttpClient http;
    private final String authHeader;

    public S4HttpClient(S4Config config) {
        this.config = config;
        this.http   = HttpClient.newHttpClient();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
            (config.getUsername() + ":" + config.getPassword())
                .getBytes(StandardCharsets.UTF_8));
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public JsonNode getOData(String path) throws IOException, InterruptedException {
        String url = config.getBaseUrl() + path;
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300)
            throw new IOException("OData GET failed: HTTP " + resp.statusCode()
                + " ? path: " + path + " ? body: " + resp.body());
        return MAPPER.readTree(resp.body());
    }

    /** Legge tutte le pagine OData con $skiptoken o $skip come fallback */
    public List<JsonNode> fetchAllPages(String firstUrl) throws IOException, InterruptedException {
        List<JsonNode> all = new ArrayList<>();
        String url = firstUrl;
        int skip = 0;
        while (url != null) {
            String relUrl = url.startsWith("http") ? url.substring(config.getBaseUrl().length()) : url;
            JsonNode root = getOData(relUrl);
            JsonNode results = root.path("d").path("results");
            int count = 0;
            if (results.isArray()) {
                results.forEach(all::add);
                count = results.size();
            }
            JsonNode next = root.path("d").path("__next");
            if (!next.isMissingNode() && !next.asText().isBlank()) {
                // SAP fornisce __next — lo usiamo direttamente
                url = next.asText();
                skip = 0;
            } else if (count == PAGE_SIZE) {
                // Nessun __next ma pagina piena: prova con $skip
                skip += PAGE_SIZE;
                url = addOrReplaceSkip(firstUrl, skip);
            } else {
                url = null;
            }
        }
        return all;
    }

    private String addOrReplaceSkip(String baseUrl, int skip) {
        // Rimuove eventuale $skip esistente e aggiunge il nuovo
        String url = baseUrl.replaceAll("[&?]\\$skip=\\d+", "");
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "$skip=" + skip;
    }

    public int getPageSize() { return PAGE_SIZE; }
    public S4Config getConfig() { return config; }

    protected String str(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText().strip();
    }

    protected double dbl(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return 0.0;
        try { return Double.parseDouble(v.asText().strip()); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
