package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.*;

/**
 * Legge le descrizioni materiale da API_PRODUCT_SRV / A_ProductDescription.
 * Stessa API usata in fcs/ProductClient, adattata per conditionsSD.
 *
 * Lingua: configurabile in ccee_config.properties → s4hc.language (default: IT)
 * Chiamate in batch da 20 materiali alla volta per rispettare i limiti URL OData V2.
 */
public class MaterialClient {

    private static final String SERVICE_PATH =
        "/sap/opu/odata/SAP/API_PRODUCT_SRV/A_ProductDescription";

    private static final int BATCH_SIZE = 20;

    private final S4HttpClient http;
    private final String       language;

    public MaterialClient(S4HttpClient http, String language) {
        this.http     = http;
        this.language = (language != null && !language.trim().isEmpty()) ? language : "IT";
    }

    /**
     * Restituisce una mappa codice materiale → descrizione
     * per tutti i materiali nell'insieme fornito.
     * I materiali non trovati non appaiono nella mappa.
     */
    public Map<String, String> fetchDescriptions(Set<String> materials)
            throws IOException, InterruptedException {

        Map<String, String> result = new LinkedHashMap<>();
        if (materials == null || materials.isEmpty()) return result;

        List<String> list = new ArrayList<>(materials);

        // Chiamate in batch
        for (int i = 0; i < list.size(); i += BATCH_SIZE) {
            List<String> batch = list.subList(i, Math.min(i + BATCH_SIZE, list.size()));

            StringBuilder filter = new StringBuilder();
            filter.append("Language eq '").append(language).append("'");
            filter.append(" and (");
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0) filter.append(" or ");
                filter.append("Product eq '").append(batch.get(j)).append("'");
            }
            filter.append(")");

            String path = SERVICE_PATH
                + "?$filter=" + S4HttpClient.encode(filter.toString())
                + "&$select=Product,Language,ProductDescription"
                + "&$top=" + BATCH_SIZE
                + "&$format=json";

            try {
                JsonNode root    = http.getOData(path);
                JsonNode results = root.path("d").path("results");
                if (results.isArray()) {
                    for (JsonNode n : results) {
                        String code = n.path("Product").asText(null);
                        String desc = n.path("ProductDescription").asText(null);
                        if (code != null && desc != null && !desc.isBlank())
                            result.put(code.strip(), desc.strip());
                    }
                }
            } catch (IOException e) {
                System.err.println("MaterialClient: errore batch descrizioni: " + e.getMessage());
            }
        }

        System.out.println("MaterialClient: descrizioni caricate " + result.size()
            + "/" + materials.size());
        return result;
    }
}
