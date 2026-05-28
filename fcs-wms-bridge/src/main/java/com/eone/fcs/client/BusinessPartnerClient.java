package com.eone.fcs.client;

import com.eone.fcs.config.AppConfig;
import com.eone.fcs.model.Customer;
import com.eone.fcs.model.Supplier;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 *
 * [DELTA] Aggiunto:
 *   fetchSuppliersModifiedSince(OffsetDateTime)  → delta LFA1
 *   fetchCustomersModifiedSince(OffsetDateTime)  → delta KNA1
 *
 * Il campo LastChangeDate (precisione al giorno) è disponibile su A_BusinessPartner
 * in S/4HC Public Edition. LastChangeDateTime non è esposto in questo tenant.
 */
public class BusinessPartnerClient extends AbstractS4Client {

    private static final Logger log = LoggerFactory.getLogger(BusinessPartnerClient.class);

    private static final String SERVICE_PATH = "/sap/opu/odata/SAP/API_BUSINESS_PARTNER";

    // LastChangeDate (solo data, senza orario) è il campo disponibile
    // su A_BusinessPartner in S/4HC Public Edition per il filtro delta.
    // LastChangeDateTime non è esposto in questo tenant.
    private static final String SELECT_BP =
            "BusinessPartner,BusinessPartnerFullName,BusinessPartnerName," +
            "Customer,Supplier,LastChangeDate";

    public BusinessPartnerClient(AppConfig config) {
        super(config);
    }

    // -------------------------------------------------------------------------
    // Fornitori - estrazione completa (invariata)
    // -------------------------------------------------------------------------

    public List<Supplier> fetchAllSuppliers() {
        log.info("Avvio estrazione fornitori");
        return fetchSuppliers(null);
    }

    // -------------------------------------------------------------------------
    // [DELTA] Fornitori - estrazione differenziale
    // -------------------------------------------------------------------------

    /**
     * Recupera solo i fornitori (Business Partner con ruolo Supplier)
     * modificati dopo il timestamp indicato.
     *
     * Filtro OData: LastChangeDateTime gt datetime'...' AND Supplier ne ''
     *
     * @param since timestamp UTC di riferimento (esclusivo)
     * @return lista di fornitori modificati
     */
    public List<Supplier> fetchSuppliersModifiedSince(OffsetDateTime since) {
        log.info("[delta-LFA1] Avvio estrazione fornitori modificati dopo: {}", since);
        List<Supplier> result = fetchSuppliers(since);
        log.info("[delta-LFA1] Fornitori modificati trovati: {}", result.size());
        return result;
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
    // Clienti - estrazione completa (invariata)
    // -------------------------------------------------------------------------

    public List<Customer> fetchAllCustomers() {
        log.info("Avvio estrazione clienti");
        return fetchCustomers(null);
    }

    // -------------------------------------------------------------------------
    // [DELTA] Clienti - estrazione differenziale
    // -------------------------------------------------------------------------

    /**
     * Recupera solo i clienti (Business Partner con ruolo Customer)
     * modificati dopo il timestamp indicato.
     *
     * @param since timestamp UTC di riferimento (esclusivo)
     * @return lista di clienti modificati
     */
    public List<Customer> fetchCustomersModifiedSince(OffsetDateTime since) {
        log.info("[delta-KNA1] Avvio estrazione clienti modificati dopo: {}", since);
        List<Customer> result = fetchCustomers(since);
        log.info("[delta-KNA1] Clienti modificati trovati: {}", result.size());
        return result;
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
    // Implementazione interna condivisa
    // -------------------------------------------------------------------------

    /**
     * Recupera i fornitori applicando opzionalmente il filtro delta.
     *
     * @param since se non null, aggiunge il filtro LastChangeDateTime
     */
    private List<Supplier> fetchSuppliers(OffsetDateTime since) {
        String filter = "Supplier ne ''";
        if (since != null) {
            filter += " and LastChangeDate gt datetime'" + formatOdata(since) + "'";
            log.debug("[delta-LFA1] Filtro OData: {}", filter);
        }

        String url = buildUrl(SERVICE_PATH, "A_BusinessPartner") +
                "?$filter=" + enc(filter) +
                "&$select=" + SELECT_BP +
                "&$top=" + config.s4PageSize;

        List<JsonNode> nodes = fetchAllPages(url);

        // Ottimizzazione delta: se non ci sono BP modificati, salta il fetch
        // dei dati fiscali (14.000+ record) che sarebbe inutile.
        if (nodes.isEmpty()) {
            log.debug("Nessun fornitore trovato — skip fetch dati fiscali.");
            log.info("Fornitori recuperati: 0{}", since != null ? " (delta da " + since + ")" : " (full)");
            return List.of();
        }

        Map<String, Map<String, String>> taxNumbers = fetchTaxNumbers();

        List<Supplier> suppliers = new ArrayList<>();
        for (JsonNode n : nodes) {
            Supplier s = toSupplier(n, taxNumbers);
            if (s != null) suppliers.add(s);
        }

        log.info("Fornitori recuperati: {}{}", suppliers.size(),
                 since != null ? " (delta da " + since + ")" : " (full)");
        return suppliers;
    }

    /**
     * Recupera i clienti applicando opzionalmente il filtro delta.
     */
    private List<Customer> fetchCustomers(OffsetDateTime since) {
        String filter = "Customer ne ''";
        if (since != null) {
            filter += " and LastChangeDate gt datetime'" + formatOdata(since) + "'";
            log.debug("[delta-KNA1] Filtro OData: {}", filter);
        }

        String url = buildUrl(SERVICE_PATH, "A_BusinessPartner") +
                "?$filter=" + enc(filter) +
                "&$select=" + SELECT_BP +
                "&$top=" + config.s4PageSize;

        List<JsonNode> nodes = fetchAllPages(url);

        // Ottimizzazione delta: se non ci sono BP modificati, salta il fetch
        // dei dati fiscali (14.000+ record) che sarebbe inutile.
        if (nodes.isEmpty()) {
            log.debug("Nessun cliente trovato — skip fetch dati fiscali.");
            log.info("Clienti recuperati: 0{}", since != null ? " (delta da " + since + ")" : " (full)");
            return List.of();
        }

        Map<String, Map<String, String>> taxNumbers = fetchTaxNumbers();

        List<Customer> customers = new ArrayList<>();
        for (JsonNode n : nodes) {
            Customer c = toCustomer(n, taxNumbers);
            if (c != null) customers.add(c);
        }

        log.info("Clienti recuperati: {}{}", customers.size(),
                 since != null ? " (delta da " + since + ")" : " (full)");
        return customers;
    }

    // -------------------------------------------------------------------------
    // Dati fiscali (invariato)
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
    // Mapping (invariato)
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

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Formatta un OffsetDateTime nel formato OData V2 datetime per LastChangeDate.
     * Il campo LastChangeDate su A_BusinessPartner ha precisione al giorno,
     * quindi usiamo yyyy-MM-ddT00:00:00 come formato del filtro.
     */
    private static String formatOdata(OffsetDateTime dt) {
        return dt.atZoneSameInstant(ZoneOffset.UTC)
                 .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00";
    }
}
