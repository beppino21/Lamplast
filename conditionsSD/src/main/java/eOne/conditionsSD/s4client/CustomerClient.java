package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;
import eOne.conditionsSD.model.ExtractParams;

import java.io.IOException;
import java.util.*;

public class CustomerClient {

    private static final String BP_PATH      = "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_Customer";
    private static final String SA_PATH      = "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_CustomerSalesArea";

    private final S4HttpClient http;

    public CustomerClient(S4HttpClient http) { this.http = http; }

    // ─────────────────────────────────────────────────────────────────────
    // Carica clienti per codice (comportamento originale)
    // ─────────────────────────────────────────────────────────────────────
    public Map<String, CustomerInfo> loadCustomers(ExtractParams params)
            throws IOException, InterruptedException {
        Map<String, CustomerInfo> result = new LinkedHashMap<>();
        if (params.isAllCustomers()) {
            String path = BP_PATH + "?$expand=to_CustomerSalesArea"
                + "&$select=Customer,CustomerName,to_CustomerSalesArea/SalesDistrict,to_CustomerSalesArea/CustomerPriceGroup&$format=json";
            parseCustomers(http.getOData(path), result, null);
        } else {
            for (String code : params.getCustomers()) {
                String path = BP_PATH + "('" + code + "')?$expand=to_CustomerSalesArea"
                    + "&$select=Customer,CustomerName,to_CustomerSalesArea/SalesDistrict,to_CustomerSalesArea/CustomerPriceGroup&$format=json";
                try {
                    JsonNode root = http.getOData(path);
                    JsonNode d = root.path("d");
                    if (!d.isMissingNode()) {
                        CustomerInfo info = buildInfo(d, null);
                        if (info != null) result.put(code, info);
                    }
                } catch (IOException e) {
                    System.err.println("CustomerClient: errore cliente " + code + ": " + e.getMessage());
                }
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Carica clienti per Price Group (KONDA)
    // ─────────────────────────────────────────────────────────────────────
    public Map<String, CustomerInfo> loadCustomersByPriceGroup(String priceGroup)
            throws IOException, InterruptedException {

        // 1. Legge i codici cliente del gruppo da A_CustomerSalesArea
        String saPath = SA_PATH
            + "?$filter=" + S4HttpClient.encode("CustomerPriceGroup eq '" + priceGroup + "'")
            + "&$select=Customer,CustomerPriceGroup"
            + "&$top=500&$format=json";

        Set<String> customerCodes = new LinkedHashSet<>();
        JsonNode saRoot = http.getOData(saPath);
        JsonNode saResults = saRoot.path("d").path("results");
        if (saResults.isArray()) {
            for (JsonNode n : saResults) {
                String code = n.path("Customer").asText(null);
                if (code != null && !code.isBlank()) customerCodes.add(code.strip());
            }
        }
        System.out.println("CustomerClient: Price Group " + priceGroup
            + " → " + customerCodes.size() + " clienti trovati");

        if (customerCodes.isEmpty()) return Map.of();

        // 2. Carica i dati master per ogni cliente
        Map<String, CustomerInfo> result = new LinkedHashMap<>();
        for (String code : customerCodes) {
            String path = BP_PATH + "('" + code + "')?$expand=to_CustomerSalesArea"
                + "&$select=Customer,CustomerName,to_CustomerSalesArea/SalesDistrict,to_CustomerSalesArea/CustomerPriceGroup&$format=json";
            try {
                JsonNode root = http.getOData(path);
                JsonNode d = root.path("d");
                if (!d.isMissingNode()) {
                    CustomerInfo info = buildInfo(d, priceGroup);
                    if (info != null) result.put(code, info);
                }
            } catch (IOException e) {
                System.err.println("CustomerClient: errore cliente " + code + ": " + e.getMessage());
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Autocomplete Price Group — restituisce codici univoci
    // ─────────────────────────────────────────────────────────────────────
    public List<String> searchPriceGroups(String term) throws IOException, InterruptedException {
        String path = SA_PATH
            + "?$filter=" + S4HttpClient.encode("startswith(CustomerPriceGroup,'" + term + "')")
            + "&$select=CustomerPriceGroup"
            + "&$top=100&$format=json";
        Set<String> codes = new LinkedHashSet<>();
        JsonNode root = http.getOData(path);
        JsonNode results = root.path("d").path("results");
        if (results.isArray()) {
            for (JsonNode n : results) {
                String code = n.path("CustomerPriceGroup").asText(null);
                if (code != null && !code.isBlank()) codes.add(code.strip());
            }
        }
        return new ArrayList<>(codes);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Metodi privati
    // ─────────────────────────────────────────────────────────────────────
    private void parseCustomers(JsonNode root, Map<String, CustomerInfo> result, String priceGroup) {
        JsonNode results = root.path("d").path("results");
        if (!results.isArray()) return;
        for (JsonNode node : results) {
            CustomerInfo info = buildInfo(node, priceGroup);
            if (info != null) result.put(info.getCode(), info);
        }
    }

    private CustomerInfo buildInfo(JsonNode node, String priceGroupOverride) {
        String code = node.path("Customer").asText(null);
        if (code == null || code.isBlank()) return null;
        String name  = node.path("CustomerName").asText("");
        String bzirk = null;
        String priceGroup = priceGroupOverride;
        JsonNode salesAreas = node.path("to_CustomerSalesArea").path("results");
        if (salesAreas.isArray()) {
            for (JsonNode sa : salesAreas) {
                String sd = sa.path("SalesDistrict").asText(null);
                if (sd != null && !sd.isBlank() && bzirk == null) bzirk = sd;
                if (priceGroup == null) {
                    String pg = sa.path("CustomerPriceGroup").asText(null);
                    if (pg != null && !pg.isBlank()) priceGroup = pg;
                }
            }
        }
        return new CustomerInfo(code, name, bzirk, priceGroup);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Model
    // ─────────────────────────────────────────────────────────────────────
    public static class CustomerInfo {
        private final String code, name, bzirk, priceGroup;
        public CustomerInfo(String code, String name, String bzirk, String priceGroup) {
            this.code = code; this.name = name;
            this.bzirk = bzirk; this.priceGroup = priceGroup;
        }
        public String  getCode()        { return code; }
        public String  getName()        { return name; }
        public String  getBzirk()       { return bzirk; }
        public String  getPriceGroup()  { return priceGroup; }
        public boolean hasBzirk()       { return bzirk != null && !bzirk.isBlank(); }
        public boolean hasPriceGroup()  { return priceGroup != null && !priceGroup.isBlank(); }
    }
}
