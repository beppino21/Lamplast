package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legge (e in futuro scriverà) l'imballo di default cliente/materiale dalla
 * tabella custom ZZPACKAGING_INFO, tramite il servizio RAP
 * ZZ_PACKAGINGINFO_SRV / ZZ_PACKAGINGINFO_SN — creato ad hoc perché nessuna
 * API standard SAP copriva questo dato (vedi discussione: I_AdditionalCustomerMaterial
 * bloccata per estensibilità, testo SAPscript non leggibile via CDS).
 *
 * IMPORTANTE — OData V4: la busta di risposta è {"value": [...]}, diversa da
 * quella V2 {"d": {"results": [...]}} usata dalle API standard SAP.
 */
public class PackagingInfoClient {

    // Percorso relativo del servizio custom OData V4 (host preso da S4Config,
    // come per tutti gli altri client). Nota: qui il segmento è "srvd" (non
    // "srvd_a2x" come per gli altri due servizi custom) perché questo binding
    // è di tipo "OData V4 - UI" invece di "Web API".
    private static final String ENTITY_PATH =
        "/sap/opu/odata4/sap/zz_packaginginfo_sn/srvd/sap/zz_packaginginfo_srv/0001/PackagingInfo";

    private static final int BATCH_SIZE = 30;

    private final S4HttpClient http;

    // Se il servizio custom non è (ancora) raggiungibile, si disattiva senza
    // bloccare l'estrazione: l'imballo semplicemente non compare.
    private volatile boolean available = true;

    public PackagingInfoClient(S4HttpClient http) { this.http = http; }

    /**
     * Dato un insieme di coppie (customer, material), restituisce una mappa
     * "customer|material" → testo imballo, solo per le coppie trovate.
     */
    public Map<String, String> fetchPackaging(List<String[]> pairs)
            throws IOException, InterruptedException {

        Map<String, String> result = new HashMap<>();
        if (!available || pairs == null || pairs.isEmpty()) return result;

        for (int i = 0; i < pairs.size(); i += BATCH_SIZE) {
            List<String[]> batch = pairs.subList(i, Math.min(i + BATCH_SIZE, pairs.size()));
            fetchBatch(batch, result);
        }
        return result;
    }

    private void fetchBatch(List<String[]> pairs, Map<String, String> result)
            throws IOException, InterruptedException {

        StringBuilder filter = new StringBuilder();
        for (String[] pair : pairs) {
            if (filter.length() > 0) filter.append(" or ");
            filter.append("(Customer eq '").append(pair[0]).append("'")
                  .append(" and Material eq '").append(pair[1]).append("')");
        }

        String path = ENTITY_PATH
            + "?$filter=" + S4HttpClient.encode(filter.toString())
            + "&$select=Customer,Material,PackagingText";

        try {
            JsonNode root = http.getOData(path);
            // OData V4: la lista è in "value", non in "d.results" come nelle API V2
            JsonNode results = root.path("value");
            if (results.isArray()) {
                for (JsonNode n : results) {
                    String customer = n.path("Customer").asText(null);
                    String material = n.path("Material").asText(null);
                    String text     = n.path("PackagingText").asText(null);
                    if (customer != null && !customer.isBlank()
                            && material != null && !material.isBlank()
                            && text != null && !text.isBlank()) {
                        result.put(customer.strip() + "|" + material.strip(), text.strip());
                    }
                }
            }
            System.out.println("PackagingInfoClient: trovati " + result.size()
                + " imballi su " + pairs.size() + " coppie richieste");
        } catch (IOException e) {
            available = false;
            System.err.println("PackagingInfoClient: servizio custom ZZ_PACKAGINGINFO_SRV "
                + "non raggiungibile — disattivato per il resto dell'estrazione (imballo non "
                + "stampato). Dettaglio: " + e.getMessage());
        }
    }

    /**
     * Legge il testo imballo per una singola coppia cliente/materiale, o
     * null se non esiste ancora — usato per pre-compilare il popup di
     * manutenzione in modalità modifica.
     */
    public String fetchOne(String customer, String material) throws IOException, InterruptedException {
        Map<String, String> found = fetchPackaging(List.<String[]>of(new String[]{customer, material}));
        return found.get(customer + "|" + material);
    }

    /**
     * Crea (o prova a creare) un nuovo record imballo. Se la combinazione
     * cliente/materiale esiste già, il servizio risponderà con un errore
     * (di solito 409/400) — questo metodo non fa un controllo preventivo,
     * lascia decidere al backend.
     */
    public void create(String customer, String material, String packagingText)
            throws IOException, InterruptedException {
        String json = "{"
            + "\"Customer\":\"" + jsonEscape(customer) + "\","
            + "\"Material\":\"" + jsonEscape(material) + "\","
            + "\"PackagingText\":\"" + jsonEscape(packagingText) + "\""
            + "}";
        http.postOData(ENTITY_PATH, json);
        System.out.println("PackagingInfoClient: creato imballo per " + customer + "|" + material);
    }

    /** Modifica il testo imballo di un record già esistente. */
    public void update(String customer, String material, String packagingText)
            throws IOException, InterruptedException {
        String path = keyPath(customer, material);
        String json = "{\"PackagingText\":\"" + jsonEscape(packagingText) + "\"}";
        http.patchOData(path, json);
        System.out.println("PackagingInfoClient: modificato imballo per " + customer + "|" + material);
    }

    /** Elimina un record imballo esistente. */
    public void delete(String customer, String material) throws IOException, InterruptedException {
        String path = keyPath(customer, material);
        http.deleteOData(path);
        System.out.println("PackagingInfoClient: eliminato imballo per " + customer + "|" + material);
    }

    private String keyPath(String customer, String material) {
        return ENTITY_PATH + "(Customer='" + S4HttpClient.encode(customer)
            + "',Material='" + S4HttpClient.encode(material) + "')";
    }

    private static String jsonEscape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
