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
    // Materiale Cliente Supplementare — letto come navigazione to_AdditionalCustomerMaterial
    // da A_CustomerMaterial (non raggiungibile come entità top-level su questo tenant).
    private static final String PACKAGING_CODE = "IMBALLO";

    private static final String SALES_ORG  = "VD01";
    private static final String DIST_CHAN  = "00";

    // Se il tenant rifiuta il campo "MinimumDeliveryQuantity" (400), viene
    // disattivato per il resto dell'estrazione: meglio proseguire senza
    // lotto minimo che bloccare l'estrazione (stessa logica già adottata
    // per il campo Language su CustomerClient).
    private volatile boolean minQtyFieldAvailable = true;

    // Confermato via $metadata (30/07): API_CUSTOMER_MATERIAL_SRV espone solo
    // A_CustomerMaterial, nessun'altra entità/navigazione — I_AdditionalCustomerMaterial
    // non è raggiungibile da qui. In attesa di un servizio OData custom
    // (Custom CDS View + Custom Communication Scenario) su I_AdditionalCustomerMaterial,
    // il tentativo resta disattivato di default per non sprecare una chiamata a vuoto.
    private volatile boolean additionalMaterialAvailable = false;

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
            fetchPackagingBatch(batch, result);
        }
        return result;
    }

    private String select() {
        return "Customer,Material,MaterialByCustomer"
            + (minQtyFieldAvailable ? ",MinDeliveryQtyInBaseUnit,BaseUnit" : "");
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
                System.err.println("CustomerMaterialClient: i campi OData 'MinDeliveryQtyInBaseUnit'/'BaseUnit' "
                    + "su A_CustomerMaterial non sono disponibili su questo tenant — disattivati per il "
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
                String baseUnit  = n.path("BaseUnit").asText(null);
                if (customer != null && !customer.isBlank()
                        && material != null && !material.isBlank()) {
                    result.put(customer + "|" + material,
                        new CustomerMaterialInfo(
                            (matByCust != null && !matByCust.isBlank()) ? matByCust : "",
                            minQty,
                            (baseUnit != null && !baseUnit.isBlank()) ? baseUnit.strip() : ""));
                }
            }
        }
    }

    /** Chiave di lookup: customer + "|" + material */
    public static String key(String customer, String material) {
        return customer + "|" + material;
    }

    /**
     * Legge, per ciascuna coppia cliente/materiale, l'eventuale riga
     * "Materiale Cliente Supplementare" con codice fisso {@link #PACKAGING_CODE}
     * ("IMBALLO"), e ne usa la descrizione come testo di imballo di default
     * (non tradotto). Se l'entità non è disponibile sul tenant, si disattiva
     * senza bloccare l'estrazione: l'imballo semplicemente non compare.
     */
    private void fetchPackagingBatch(List<String[]> pairs, Map<String, CustomerMaterialInfo> result)
            throws IOException, InterruptedException {

        if (!additionalMaterialAvailable) return;

        StringBuilder filter = new StringBuilder();
        for (String[] pair : pairs) {
            if (filter.length() > 0) filter.append(" or ");
            filter.append("(SalesOrganization eq '").append(SALES_ORG).append("'")
                  .append(" and DistributionChannel eq '").append(DIST_CHAN).append("'")
                  .append(" and Customer eq '").append(pair[0]).append("'")
                  .append(" and Material eq '").append(pair[1]).append("')");
        }

        // Non raggiungibile come entità top-level ("A_AdditionalCustomerMaterial": 404
        // "Resource not found for the segment") — provata come navigazione da
        // A_CustomerMaterial, coerente con il resto delle associazioni di questo servizio.
        String path = ENTITY + "?$filter=" + encode(filter.toString())
            + "&$expand=to_AdditionalCustomerMaterial"
            + "&$select=Customer,Material,to_AdditionalCustomerMaterial/MaterialByCustomer,"
            + "to_AdditionalCustomerMaterial/MaterialDescriptionByCustomer&$format=json";

        JsonNode root;
        try {
            root = getOData(path);
        } catch (IOException e) {
            additionalMaterialAvailable = false;
            System.err.println("CustomerMaterialClient: la navigazione 'to_AdditionalCustomerMaterial' "
                + "su A_CustomerMaterial non è disponibile su questo tenant — disattivata per il resto "
                + "dell'estrazione (imballo non stampato). Dettaglio: " + e.getMessage());
            return;
        }

        JsonNode results = root.path("d").path("results");
        int found = 0;
        if (results.isArray()) {
            for (JsonNode parent : results) {
                String customer = parent.path("Customer").asText(null);
                String material = parent.path("Material").asText(null);
                if (customer == null || customer.isBlank() || material == null || material.isBlank()) continue;

                JsonNode additional = parent.path("to_AdditionalCustomerMaterial").path("results");
                if (!additional.isArray()) continue;

                for (JsonNode add : additional) {
                    String matByCust = add.path("MaterialByCustomer").asText(null);
                    if (matByCust == null || !PACKAGING_CODE.equalsIgnoreCase(matByCust.trim())) continue;
                    String desc = add.path("MaterialDescriptionByCustomer").asText(null);
                    if (desc == null || desc.isBlank()) continue;

                    String k = customer + "|" + material;
                    CustomerMaterialInfo info = result.get(k);
                    if (info == null) {
                        info = new CustomerMaterialInfo("", 0d, "");
                        result.put(k, info);
                    }
                    info.setPackagingNote(desc.trim());
                    found++;
                }
            }
        }
        System.out.println("CustomerMaterialClient: imballo trovato per " + found + " coppie cliente/materiale");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Model
    // ─────────────────────────────────────────────────────────────────────
    public static class CustomerMaterialInfo {
        private final String materialByCustomer;
        private final double minDeliveryQuantity;
        private final String minDeliveryQuantityUnit;
        // Imballo di default (Materiale Cliente Supplementare "IMBALLO"): testo libero,
        // non tradotto — fonte OData da agganciare (I_AdditionalCustomerMaterial).
        private String packagingNote = "";

        public CustomerMaterialInfo(String materialByCustomer, double minDeliveryQuantity, String minDeliveryQuantityUnit) {
            this.materialByCustomer = materialByCustomer;
            this.minDeliveryQuantity = minDeliveryQuantity;
            this.minDeliveryQuantityUnit = minDeliveryQuantityUnit != null ? minDeliveryQuantityUnit : "";
        }

        public String getMaterialByCustomer()       { return materialByCustomer; }
        public double getMinDeliveryQuantity()      { return minDeliveryQuantity; }
        public String getMinDeliveryQuantityUnit()  { return minDeliveryQuantityUnit; }
        public boolean hasMinDeliveryQuantity()     { return minDeliveryQuantity > 0d; }
        public String getPackagingNote()      { return packagingNote; }
        public void setPackagingNote(String v) { this.packagingNote = v != null ? v : ""; }
    }
}
