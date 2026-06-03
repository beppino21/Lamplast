package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;
import eOne.conditionsSD.model.ExtractParams;

import java.io.IOException;
import java.util.*;

public class CustomerClient {

    private static final String BP_PATH = "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_Customer";

    private final S4HttpClient http;

    public CustomerClient(S4HttpClient http) { this.http = http; }

    public Map<String, CustomerInfo> loadCustomers(ExtractParams params)
            throws IOException, InterruptedException {
        Map<String, CustomerInfo> result = new LinkedHashMap<>();
        if (params.isAllCustomers()) {
            String path = BP_PATH + "?$expand=to_CustomerSalesArea"
                + "&$select=Customer,CustomerName,to_CustomerSalesArea/SalesDistrict&$format=json";
            parseCustomers(http.getOData(path), result);
        } else {
            for (String code : params.getCustomers()) {
                String path = BP_PATH + "('" + code + "')?$expand=to_CustomerSalesArea"
                    + "&$select=Customer,CustomerName,to_CustomerSalesArea/SalesDistrict&$format=json";
                try {
                    JsonNode root = http.getOData(path);
                    JsonNode d = root.path("d");
                    if (!d.isMissingNode()) {
                        CustomerInfo info = buildInfo(d);
                        if (info != null) result.put(code, info);
                    }
                } catch (IOException e) {
                    System.err.println("CustomerClient: errore cliente " + code + ": " + e.getMessage());
                }
            }
        }
        return result;
    }

    private void parseCustomers(JsonNode root, Map<String, CustomerInfo> result) {
        JsonNode results = root.path("d").path("results");
        if (!results.isArray()) return;
        for (JsonNode node : results) {
            CustomerInfo info = buildInfo(node);
            if (info != null) result.put(info.getCode(), info);
        }
    }

    private CustomerInfo buildInfo(JsonNode node) {
        String code = node.path("Customer").asText(null);
        if (code == null || code.isBlank()) return null;
        String name  = node.path("CustomerName").asText("");
        String bzirk = null;
        JsonNode salesAreas = node.path("to_CustomerSalesArea").path("results");
        if (salesAreas.isArray()) {
            for (JsonNode sa : salesAreas) {
                String sd = sa.path("SalesDistrict").asText(null);
                if (sd != null && !sd.isBlank()) { bzirk = sd; break; }
            }
        }
        return new CustomerInfo(code, name, bzirk);
    }

    public static class CustomerInfo {
        private final String code, name, bzirk;
        public CustomerInfo(String code, String name, String bzirk) {
            this.code = code; this.name = name; this.bzirk = bzirk;
        }
        public String getCode()   { return code; }
        public String getName()   { return name; }
        public String getBzirk()  { return bzirk; }
        public boolean hasBzirk() { return bzirk != null && !bzirk.isBlank(); }
    }
}
