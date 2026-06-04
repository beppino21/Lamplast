package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legge le codifiche cliente-materiale da API_CUSTOMER_MATERIAL_SRV.
 * Comunicazione: SAP_COM_0134
 * SalesOrganization e DistributionChannel sono fissi per Lamplast.
 */
public class CustomerMaterialClient extends S4HttpClient {

    private static final String SERVICE = "/sap/opu/odata/sap/API_CUSTOMER_MATERIAL_SRV";
    private static final String ENTITY  = SERVICE + "/A_CustomerMaterial";

    private static final String SALES_ORG  = "VD01";
    private static final String DIST_CHAN  = "00";

    public CustomerMaterialClient(S4HttpClient httpClient) { super(httpClient.getConfig()); }

    /**
     * Dato un insieme di coppie (customer, material), restituisce una mappa
     * "customer|material" → MaterialByCustomer.
     * Fa chiamate in batch da 50 coppie per evitare URL troppo lunghe.
     */
    public Map<String, String> loadMaterialByCustomer(List<String[]> pairs)
            throws IOException, InterruptedException {

        Map<String, String> result = new HashMap<>();
        if (pairs == null || pairs.isEmpty()) return result;

        int batchSize = 30; // coppie per batch
        for (int i = 0; i < pairs.size(); i += batchSize) {
            List<String[]> batch = pairs.subList(i, Math.min(i + batchSize, pairs.size()));
            fetchBatch(batch, result);
        }
        return result;
    }

    private void fetchBatch(List<String[]> pairs, Map<String, String> result)
            throws IOException, InterruptedException {

        StringBuilder filter = new StringBuilder();
        for (String[] pair : pairs) {
            if (filter.length() > 0) filter.append(" or ");
            filter.append("(SalesOrganization eq '").append(SALES_ORG).append("'")
                  .append(" and DistributionChannel eq '").append(DIST_CHAN).append("'")
                  .append(" and Customer eq '").append(pair[0]).append("'")
                  .append(" and Material eq '").append(pair[1]).append("')");
        }

        String path = ENTITY
            + "?$filter=" + encode(filter.toString())
            + "&$select=Customer,Material,MaterialByCustomer"
            + "&$format=json";

        try {
            JsonNode root = getOData(path);
            JsonNode results = root.path("d").path("results");
            if (results.isArray()) {
                for (JsonNode n : results) {
                    String customer  = n.path("Customer").asText(null);
                    String material  = n.path("Material").asText(null);
                    String matByCust = n.path("MaterialByCustomer").asText(null);
                    if (customer != null && !customer.isBlank()
                            && material != null && !material.isBlank()
                            && matByCust != null && !matByCust.isBlank()) {
                        result.put(customer + "|" + material, matByCust);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("CustomerMaterialClient batch errore: " + e.getMessage());
        }
    }

    /** Chiave di lookup: customer + "|" + material */
    public static String key(String customer, String material) {
        return customer + "|" + material;
    }
}
