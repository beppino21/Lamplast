package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Client HTTP per le API OData di S/4HC.
 * Autenticazione Basic (User ID and Password) — stesso pattern di MovementClient in fcs.
 *
 * Non serve gestione token: ogni chiamata porta le credenziali Basic nell'header.
 */
public class S4HttpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final S4Config   config;
    private final HttpClient http;
    private final String     basicAuthHeader;

    public S4HttpClient(S4Config config) {
        this.config = config;
        this.http   = HttpClient.newHttpClient();
        // Pre-calcola header Basic Auth: "Basic base64(user:password)"
        String credentials = config.getUsername() + ":" + config.getPassword();
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Esegue una GET OData e restituisce il JsonNode radice della risposta.
     * Il path è relativo al baseUrl, es.:
     *   /sap/opu/odata/sap/API_SLSPRICINGCONDITIONRECORD_SRV/A_SlsPrcgCndnRecdValidity?...
     */
    public JsonNode getOData(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(config.getBaseUrl() + path))
            .header("Authorization", basicAuthHeader)
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new IOException("OData GET failed: HTTP " + resp.statusCode()
                + " — path: " + path
                + " — body: " + resp.body());
        }

        return MAPPER.readTree(resp.body());
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
