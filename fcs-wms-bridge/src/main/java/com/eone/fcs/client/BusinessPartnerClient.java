package com.eone.fcs.client;

import com.eone.fcs.config.AppConfig;
import com.eone.fcs.model.Customer;
import com.eone.fcs.model.Supplier;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client per API_BUSINESS_PARTNER (OData V2).
 *
 * Dati fiscali in A_BusinessPartnerTaxNumber:
 *   IT0 = Partita IVA italiana → stceg
 *   IT1 = Codice Fiscale italiano → stcd1
 *   XX0 = Partita IVA estera → stceg
 */
public class BusinessPartnerClient extends AbstractS4Client {

    private static final Logger log = LoggerFactory.getLogger(BusinessPartnerClient.class);

    private static final String SERVICE_PATH = "/sap/opu/odata/SAP/API_BUSINESS_PARTNER";

    private static final String SELECT_BP =
            "BusinessPartner,BusinessPartnerFullName,BusinessPartnerName,Customer,Supplier";

    public BusinessPartnerClient(AppConfig config) {
        super(config);
    }

    // -------------------------------------------------------------------------
    // Fornitori
    // -------------------------------------------------------------------------

    public List<Supplier> fetchAllSuppliers() {
        log.info("Avvio estrazione fornitori");

        String url = buildUrl(SERVICE_PATH, "A_BusinessPartner") +
                "?$filter=" + enc("Supplier ne ''") +
                "&$select=" + SELECT_BP +
                "&$top=" + config.s4PageSize;

        List<JsonNode> nodes = fetchAllPages(url);
        Map<String, Map<String, String>> taxNumbers = fetchTaxNumbers();

        List<Supplier> suppliers = new ArrayList<>();
        for (JsonNode n : nodes) {
            Supplier s = toSupplier(n, taxNumbers);
            if (s != null) suppliers.add(s);
        }

        log.info("Estrazione fornitori completata: {} fornitori", suppliers.size());
        return suppliers;
    }

    public Supplier fetchSupplierByLifnr(String lifnr) {
        log.debug("Fetch fornitore singolo: {}", lifnr);
        String url = buildUrl(SERVICE_PATH, "A_BusinessPartner") +
                "?$filter=" + enc("Supplier eq '" + lifnr + "'") +
                "&$select=" + SELECT_BP +
                "&$top=1";
        List<JsonNode> nodes = fetchSinglePage(url);
        return nodes.isEmpty() ? null : toSupplier(nodes.get(0), Map.of());
    }

    // -------------------------------------------------------------------------
    // Clienti
    // -------------------------------------------------------------------------

    public List<Customer> fetchAllCustomers() {
        log.info("Avvio estrazione clienti");

        String url = buildUrl(SERVICE_PATH, "A_BusinessPartner") +
                "?$filter=" + enc("Customer ne ''") +
                "&$select=" + SELECT_BP +
                "&$top=" + config.s4PageSize;

        List<JsonNode> nodes = fetchAllPages(url);
        Map<String, Map<String, String>> taxNumbers = fetchTaxNumbers();

        List<Customer> customers = new ArrayList<>();
        for (JsonNode n : nodes) {
            Customer c = toCustomer(n, taxNumbers);
            if (c != null) customers.add(c);
        }

        log.info("Estrazione clienti completata: {} clienti", customers.size());
        return customers;
    }

    public Customer fetchCustomerByKunnr(String kunnr) {
        log.debug("Fetch cliente singolo: {}", kunnr);
        String url = buildUrl(SERVICE_PATH, "A_BusinessPartner") +
                "?$filter=" + enc("Customer eq '" + kunnr + "'") +
                "&$select=" + SELECT_BP +
                "&$top=1";
        List<JsonNode> nodes = fetchSinglePage(url);
        return nodes.isEmpty() ? null : toCustomer(nodes.get(0), Map.of());
    }

    // -------------------------------------------------------------------------
    // Dati fiscali
    // -------------------------------------------------------------------------

    private Map<String, Map<String, String>> fetchTaxNumbers() {
        log.debug("Lettura dati fiscali Business Partner");

        String url = buildUrl(SERVICE_PATH, "A_BusinessPartnerTaxNumber") +
                "?$select=BusinessPartner,BPTaxType,BPTaxNumber" +
                "&$top=" + config.s4PageSize;

        List<JsonNode> nodes = fetchAllPages(url);
        Map<String, Map<String, String>> result = new HashMap<>();

        for (JsonNode n : nodes) {
            String bp      = str(n, "BusinessPartner");
            String taxType = str(n, "BPTaxType");
            String taxNum  = str(n, "BPTaxNumber");
            if (bp == null || taxType == null || taxNum == null) continue;
            result.computeIfAbsent(bp, k -> new HashMap<>()).put(taxType, taxNum);
        }

        log.debug("Dati fiscali recuperati per {} business partner", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private Supplier toSupplier(JsonNode n, Map<String, Map<String, String>> taxNumbers) {
        String lifnr = str(n, "Supplier");
        if (lifnr == null || lifnr.isBlank()) return null;

        String name1 = str(n, "BusinessPartnerFullName");
        if (name1 == null) name1 = str(n, "BusinessPartnerName");

        String bp = str(n, "BusinessPartner", "");
        Map<String, String> taxes = taxNumbers.getOrDefault(bp, Map.of());

        String stcd1 = taxes.get("IT1");
        String stceg = resolveVat(taxes);

        return new Supplier(lifnr.trim(), name1, null, stcd1, null, stceg);
    }

    private Customer toCustomer(JsonNode n, Map<String, Map<String, String>> taxNumbers) {
        String kunnr = str(n, "Customer");
        if (kunnr == null || kunnr.isBlank()) return null;

        String name1 = str(n, "BusinessPartnerFullName");
        if (name1 == null) name1 = str(n, "BusinessPartnerName");

        String bp = str(n, "BusinessPartner", "");
        Map<String, String> taxes = taxNumbers.getOrDefault(bp, Map.of());

        String stcd1 = taxes.get("IT1");
        String stceg = resolveVat(taxes);

        return new Customer(kunnr.trim(), name1, null, stcd1, null, stceg);
    }

    private String resolveVat(Map<String, String> taxes) {
        if (taxes.containsKey("IT0")) return taxes.get("IT0");
        return taxes.entrySet().stream()
                .filter(e -> e.getKey().endsWith("0"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
