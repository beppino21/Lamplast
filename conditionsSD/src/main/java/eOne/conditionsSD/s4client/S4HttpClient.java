package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieManager;
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
 * Non serve gestione token per le sole letture (GET): ogni chiamata porta le
 * credenziali Basic nell'header. Per le scritture (POST/PATCH/DELETE) serve
 * invece un token CSRF, da richiedere con una GET dedicata e da riusare nella
 * stessa "sessione" (cookie) — per questo l'HttpClient qui mantiene i cookie
 * tra le chiamate (necessario solo per i metodi di scrittura).
 */
public class S4HttpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final S4Config   config;
    private final HttpClient http;
    private final String     basicAuthHeader;

    public S4HttpClient(S4Config config) {
        this.config = config;
        this.http   = HttpClient.newBuilder()
            .cookieHandler(new CookieManager()) // necessario per riusare la sessione del token CSRF
            .build();
        // Pre-calcola header Basic Auth: "Basic base64(user:password)"
        String credentials = config.getUsername() + ":" + config.getPassword();
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public S4Config getConfig() { return config; }

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

    /**
     * Richiede un token CSRF (necessario per POST/PATCH/DELETE) facendo una
     * GET con l'header "X-CSRF-Token: Fetch" sul path indicato. I cookie di
     * sessione restituiti vengono mantenuti automaticamente (CookieManager)
     * e riusati dalla successiva chiamata di scrittura.
     *
     * @param path un path GET qualunque dello stesso servizio (va bene anche
     *             l'entity set stesso senza filtri, es. quello che si sta
     *             per scrivere)
     */
    private String fetchCsrfToken(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(config.getBaseUrl() + path))
            .header("Authorization", basicAuthHeader)
            .header("X-CSRF-Token", "Fetch")
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        String token = resp.headers().firstValue("x-csrf-token").orElse(null);
        if (token == null || token.isBlank()) {
            throw new IOException("CSRF token non ricevuto (header 'x-csrf-token' assente) — path: " + path
                + " — status: " + resp.statusCode());
        }
        return token;
    }

    /**
     * Esegue una POST OData (creazione) con corpo JSON, gestendo in automatico
     * il recupero del token CSRF. Restituisce il JsonNode della risposta
     * (l'entità creata) in caso di successo (2xx).
     *
     * @param path     path dell'entity set (stesso path usato per la GET)
     * @param jsonBody corpo JSON della nuova entità, es. {"Customer":"...", ...}
     */
    public JsonNode postOData(String path, String jsonBody) throws IOException, InterruptedException {
        String token = fetchCsrfToken(path);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(config.getBaseUrl() + path))
            .header("Authorization", basicAuthHeader)
            .header("X-CSRF-Token", token)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() / 100 != 2) {
            throw new IOException("OData POST failed: HTTP " + resp.statusCode()
                + " — path: " + path
                + " — body: " + resp.body());
        }

        return resp.body() == null || resp.body().isBlank() ? null : MAPPER.readTree(resp.body());
    }

    /**
     * Esegue una PATCH OData (modifica) su una singola entità, identificata
     * dal path completo con la chiave, es.:
     *   .../PackagingInfo(Customer='1000000',Material='A60383')
     * Corpo JSON con solo i campi da modificare.
     */
    public void patchOData(String pathWithKey, String jsonBody) throws IOException, InterruptedException {
        String token = fetchCsrfToken(pathWithKey);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(config.getBaseUrl() + pathWithKey))
            .header("Authorization", basicAuthHeader)
            .header("X-CSRF-Token", token)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() / 100 != 2) {
            throw new IOException("OData PATCH failed: HTTP " + resp.statusCode()
                + " — path: " + pathWithKey
                + " — body: " + resp.body());
        }
    }

    /**
     * Esegue una DELETE OData su una singola entità, identificata dal path
     * completo con la chiave (stesso formato di patchOData).
     */
    public void deleteOData(String pathWithKey) throws IOException, InterruptedException {
        String token = fetchCsrfToken(pathWithKey);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(config.getBaseUrl() + pathWithKey))
            .header("Authorization", basicAuthHeader)
            .header("X-CSRF-Token", token)
            .header("Accept", "application/json")
            .DELETE()
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() / 100 != 2) {
            throw new IOException("OData DELETE failed: HTTP " + resp.statusCode()
                + " — path: " + pathWithKey
                + " — body: " + resp.body());
        }
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
