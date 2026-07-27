package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legge le codifiche cliente-materiale (e i relativi dati logistici) da
 * API_CUSTOMER_MATERIAL_SRV.
 * Comunicazione: SAP_COM_0134
 * SalesOrganization e DistributionChannel sono fissi per Lamplast.
 */
public class CustomerMaterialClient extends S4HttpClient {

    private static final String SERVICE = "/sap/opu/odata/sap/API_CUSTOMER_MATERIAL_SRV";
    private static final String ENTITY  = SERVICE + "/A_CustomerMaterial";

    private static final String SALES_ORG  = "VD01";
    private static final String DIST_CHAN  = "00";

    // Se il tenant rifiuta il campo "MinimumDeliveryQuantity" (400), viene
    // disattivato per il resto dell'estrazione: meglio proseguire senza
    // lotto minimo che bloccare l'estrazione (stessa logica già adottata
    // per il campo Language su CustomerClient).
    private volatile boolean minQtyFieldAvailable = true;

    public CustomerMaterialClient(S4HttpClient httpClient) { super(httpClient.getConfig()); }

    /**
     * Dato un insieme di coppie (customer, material), restituisce una mappa
     * "customer|material" → {@link CustomerMaterialInfo}.
     * Fa chiamate in batch da 30 coppie per evitare URL troppo lunghe.
     */
    public Map<String, CustomerMaterialInfo> loadMaterialByCustomer(List<String[]> pairs)
            throws IOException, InterruptedException {

        Map<String, CustomerMaterialInfo> result = new HashMap<>();
        if (pairs == null || pairs.isEmpty()) return result;

        int batchSize = 30; // coppie per batch
        for (int i = 0; i < pairs.size(); i += batchSize) {
            List<String[]> batch = pairs.subList(i, Math.min(i + batchSize, pairs.size()));
            fetchBatch(batch, result);
        }
        return result;
    }

    private String select() {
        return "Customer,Material,MaterialByCustomer"
            + (minQtyFieldAvailable ? ",MinDeliveryQtyInBaseUnit" : "");
    }

    private void fetchBatch(List<String[]> pairs, Map<String, CustomerMaterialInfo> result)
            throws IOException, InterruptedException {

        StringBuilder filter = new StringBuilder();
        for (String[] pair : pairs) {
            if (filter.length() > 0) filter.append(" or ");
            filter.append("(SalesOrganization eq '").append(SALES_ORG).append("'")
                  .append(" and DistributionChannel eq '").append(DIST_CHAN).append("'")
                  .append(" and Customer eq '").append(pair[0]).append("'")
                  .append(" and Material eq '").append(pair[1]).append("')");
        }

        String basePath = ENTITY + "?$filter=" + encode(filter.toString()) + "&$select=";

        JsonNode root;
        try {
            root = getOData(basePath + select() + "&$format=json");
        } catch (IOException e) {
            if (minQtyFieldAvailable) {
                minQtyFieldAvailable = false;
                System.err.println("CustomerMaterialClient: il campo OData 'MinDeliveryQtyInBaseUnit' "
                    + "su A_CustomerMaterial non è disponibile su questo tenant — disattivato per il "
                    + "resto dell'estrazione (lotto minimo non stampato). Dettaglio: " + e.getMessage());
                try {
                    root = getOData(basePath + select() + "&$format=json");
                } catch (IOException e2) {
                    System.err.println("CustomerMaterialClient batch errore: " + e2.getMessage());
                    return;
                }
            } else {
                System.err.println("CustomerMaterialClient batch errore: " + e.getMessage());
                return;
            }
        }

        JsonNode results = root.path("d").path("results");
        if (results.isArray()) {
            for (JsonNode n : results) {
                String customer  = n.path("Customer").asText(null);
                String material  = n.path("Material").asText(null);
                String matByCust = n.path("MaterialByCustomer").asText(null);
                double minQty    = n.path("MinDeliveryQtyInBaseUnit").asDouble(0d);
                if (customer != null && !customer.isBlank()
                        && material != null && !material.isBlank()) {
                    result.put(customer + "|" + material,
                        new CustomerMaterialInfo(
                            (matByCust != null && !matByCust.isBlank()) ? matByCust : "",
                            minQty));
                }
            }
        }
    }

    /** Chiave di lookup: customer + "|" + material */
    public static String key(String customer, String material) {
        return customer + "|" + material;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Model
    // ─────────────────────────────────────────────────────────────────────
    public static class CustomerMaterialInfo {
        private final String materialByCustomer;
        private final double minDeliveryQuantity;
        // Testo esteso "imballo preferenziale" IT/EN: fonte OData da confermare,
        // per ora sempre vuoto — struttura pronta per quando la agganciamo.
        private String packagingNoteIT = "";
        private String packagingNoteEN = "";

        public CustomerMaterialInfo(String materialByCustomer, double minDeliveryQuantity) {
            this.materialByCustomer = materialByCustomer;
            this.minDeliveryQuantity = minDeliveryQuantity;
        }

        public String getMaterialByCustomer()   { return materialByCustomer; }
        public double getMinDeliveryQuantity()  { return minDeliveryQuantity; }
        public boolean hasMinDeliveryQuantity() { return minDeliveryQuantity > 0d; }
        public String getPackagingNoteIT()      { return packagingNoteIT; }
        public String getPackagingNoteEN()      { return packagingNoteEN; }
        public void setPackagingNoteIT(String v) { this.packagingNoteIT = v != null ? v : ""; }
        public void setPackagingNoteEN(String v) { this.packagingNoteEN = v != null ? v : ""; }
    }
}
