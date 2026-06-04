package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.*;

/**
 * Legge le descrizioni dei Sales District (zone ZTRA) da API_BUSINESS_PARTNER.
 * Entità: A_SalesDistrict con navigazione to_Text filtrato per lingua.
 *
 * Fallback: se una zona non ha descrizione nella lingua configurata,
 * riprova con "IT".
 */
public class SalesDistrictClient {

    private static final String SERVICE_PATH =
        "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_SalesDistrictText";

    private static final int BATCH_SIZE = 20;

    private final S4HttpClient http;
    private final String       language;

    public SalesDistrictClient(S4HttpClient http, String language) {
        this.http     = http;
        this.language = (language != null && !language.trim().isEmpty()) ? language : "IT";
    }

    /**
     * Restituisce mappa codice zona → descrizione.
     * Per le zone senza descrizione nella lingua configurata,
     * tenta il fallback su "IT".
     */
    public Map<String, String> fetchDescriptions(Set<String> zones)
            throws IOException, InterruptedException {

        if (zones == null || zones.isEmpty()) return Map.of();

        Map<String, String> result = new LinkedHashMap<>();

        // Prima passata: lingua configurata
        fetchBatch(zones, language, result);

        // Fallback IT per le zone non trovate
        if (!language.equalsIgnoreCase("IT")) {
            Set<String> missing = new LinkedHashSet<>(zones);
            missing.removeAll(result.keySet());
            if (!missing.isEmpty()) {
                fetchBatch(missing, "IT", result);
            }
        }

        System.out.println("SalesDistrictClient: descrizioni caricate "
            + result.size() + "/" + zones.size());
        return result;
    }

    private void fetchBatch(Set<String> zones, String lang,
                            Map<String, String> result)
            throws IOException, InterruptedException {

        List<String> list = new ArrayList<>(zones);
        for (int i = 0; i < list.size(); i += BATCH_SIZE) {
            List<String> batch = list.subList(i, Math.min(i + BATCH_SIZE, list.size()));

            StringBuilder filter = new StringBuilder();
            filter.append("Language eq '").append(lang).append("'");
            filter.append(" and (");
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0) filter.append(" or ");
                filter.append("SalesDistrict eq '").append(batch.get(j)).append("'");
            }
            filter.append(")");

            String path = SERVICE_PATH
                + "?$filter=" + S4HttpClient.encode(filter.toString())
                + "&$select=SalesDistrict,Language,SalesDistrictName"
                + "&$top=" + BATCH_SIZE
                + "&$format=json";

            try {
                JsonNode root    = http.getOData(path);
                JsonNode results = root.path("d").path("results");
                if (results.isArray()) {
                    for (JsonNode n : results) {
                        String code = n.path("SalesDistrict").asText(null);
                        String desc = n.path("SalesDistrictName").asText(null);
                        if (code != null && desc != null
                                && !desc.isBlank()
                                && !result.containsKey(code))
                            result.put(code.strip(), desc.strip());
                    }
                }
            } catch (IOException e) {
                System.err.println("SalesDistrictClient: errore batch [" + lang
                    + "]: " + e.getMessage());
            }
        }
    }
}
