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

    /** Legge tutte le pagine OData con $skiptoken */
    public List<JsonNode> fetchAllPages(String firstUrl) throws IOException, InterruptedException {
        List<JsonNode> all = new ArrayList<>();
        String url = firstUrl;
        while (url != null) {
            JsonNode root = getOData(url.startsWith("http") ? url.substring(config.getBaseUrl().length()) : url);
            JsonNode results = root.path("d").path("results");
            if (results.isArray()) results.forEach(all::add);
            JsonNode next = root.path("d").path("__next");
            url = (!next.isMissingNode() && !next.asText().isBlank()) ? next.asText() : null;
        }
        return all;
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
