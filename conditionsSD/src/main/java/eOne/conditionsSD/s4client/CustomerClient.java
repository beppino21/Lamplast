package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;
import eOne.conditionsSD.model.ExtractParams;

import java.io.IOException;
import java.util.*;

public class CustomerClient {

    private static final String BP_PATH       = "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_Customer";
    private static final String SA_PATH       = "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_CustomerSalesArea";
    // "Language" non è proiettato sull'entità A_Customer (confermato: 404 "Resource not found
    // for the segment 'Language'"); il campo lingua di corrispondenza vive invece su
    // A_BusinessPartner col nome "CorrespondenceLanguage" (confermato via CDS I_BusinessPartner),
    // usando il codice cliente come numero Business Partner (numerazione unica in S/4HANA).
    private static final String BUSPART_PATH  = "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_BusinessPartner";

    private static final int LANG_BATCH_SIZE = 30;

    private final S4HttpClient http;

    // Se anche A_BusinessPartner rifiuta "CorrespondenceLanguage", viene disattivato per
    // il resto dell'estrazione: meglio un cliente senza lingua (default IT)
    // che nessuna estrazione.
    private volatile boolean languageFieldAvailable = true;

    // Condizioni di pagamento e Incoterms su to_CustomerSalesArea: nomi campo
    // NON verificati via ADT su questo tenant (a differenza di Language/lotto
    // minimo) — ipotesi standard SAP. Se il tenant li rifiuta, si disattivano
    // per il resto dell'estrazione (stesso meccanismo di fallback).
    private volatile boolean paymentIncotermsFieldsAvailable = true;

    public CustomerClient(S4HttpClient http) { this.http = http; }

    private String customerSelect() {
        return "Customer,CustomerName,to_CustomerSalesArea/SalesDistrict,to_CustomerSalesArea/CustomerPriceGroup"
            + (paymentIncotermsFieldsAvailable
                ? ",to_CustomerSalesArea/CustomerPaymentTerms,to_CustomerSalesArea/IncotermsClassification,to_CustomerSalesArea/IncotermsLocation1"
                : "");
    }

    /**
     * Esegue la fetch OData su A_Customer. Se il tenant rifiuta i campi
     * condizioni di pagamento/Incoterms, li disattiva e riprova una volta
     * senza, invece di far fallire l'intera estrazione.
     *
     * @param pathPrefix es. BP_PATH + "?$expand=to_CustomerSalesArea"
     *                   oppure BP_PATH + "('CODICE')?$expand=to_CustomerSalesArea"
     */
    private JsonNode fetchCustomerOData(String pathPrefix) throws IOException, InterruptedException {
        try {
            return http.getOData(pathPrefix + "&$select=" + customerSelect() + "&$format=json");
        } catch (IOException e) {
            if (paymentIncotermsFieldsAvailable) {
                paymentIncotermsFieldsAvailable = false;
                System.err.println("CustomerClient: i campi OData 'CustomerPaymentTerms'/'IncotermsClassification'/"
                    + "'IncotermsLocation1' su A_CustomerSalesArea non sono disponibili su questo tenant — "
                    + "disattivati per il resto dell'estrazione (condizioni di pagamento e Incoterms non stampati). "
                    + "Dettaglio: " + e.getMessage());
                return http.getOData(pathPrefix + "&$select=" + customerSelect() + "&$format=json");
            }
            throw e;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Carica clienti per codice (comportamento originale)
    // ─────────────────────────────────────────────────────────────────────
    public Map<String, CustomerInfo> loadCustomers(ExtractParams params)
            throws IOException, InterruptedException {
        Map<String, CustomerInfo> result = new LinkedHashMap<>();
        if (params.isAllCustomers()) {
            parseCustomers(fetchCustomerOData(BP_PATH + "?$expand=to_CustomerSalesArea"), result, null);
        } else {
            for (String code : params.getCustomers()) {
                try {
                    JsonNode root = fetchCustomerOData(BP_PATH + "('" + code + "')?$expand=to_CustomerSalesArea");
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
        applyLanguages(result);
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
            try {
                JsonNode root = fetchCustomerOData(BP_PATH + "('" + code + "')?$expand=to_CustomerSalesArea");
                JsonNode d = root.path("d");
                if (!d.isMissingNode()) {
                    CustomerInfo info = buildInfo(d, priceGroup);
                    if (info != null) result.put(code, info);
                }
            } catch (IOException e) {
                System.err.println("CustomerClient: errore cliente " + code + ": " + e.getMessage());
            }
        }
        applyLanguages(result);
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
    // Lingua cliente — fetch separata su A_BusinessPartner (best-effort)
    // ─────────────────────────────────────────────────────────────────────
    private void applyLanguages(Map<String, CustomerInfo> customers) {
        if (!languageFieldAvailable || customers.isEmpty()) return;
        try {
            Map<String, String> langs = fetchLanguages(customers.keySet());
            for (Map.Entry<String, String> e : langs.entrySet()) {
                CustomerInfo info = customers.get(e.getKey());
                if (info != null) info.setLanguage(e.getValue());
            }
            System.out.println("CustomerClient: lingua trovata per " + langs.size()
                + "/" + customers.size() + " clienti (codici richiesti: " + customers.keySet() + ")");
        } catch (IOException | InterruptedException e) {
            System.err.println("CustomerClient: errore lettura lingua clienti: " + e.getMessage());
        }
    }

    private Map<String, String> fetchLanguages(Set<String> customerCodes)
            throws IOException, InterruptedException {
        Map<String, String> result = new HashMap<>();
        if (!languageFieldAvailable || customerCodes == null || customerCodes.isEmpty()) return result;

        List<String> codes = new ArrayList<>(customerCodes);
        for (int i = 0; i < codes.size(); i += LANG_BATCH_SIZE) {
            List<String> batch = codes.subList(i, Math.min(i + LANG_BATCH_SIZE, codes.size()));

            StringBuilder filter = new StringBuilder();
            for (String code : batch) {
                if (filter.length() > 0) filter.append(" or ");
                filter.append("BusinessPartner eq '").append(code).append("'");
            }

            String path = BUSPART_PATH
                + "?$filter=" + S4HttpClient.encode(filter.toString())
                + "&$select=BusinessPartner,CorrespondenceLanguage"
                + "&$top=" + LANG_BATCH_SIZE + "&$format=json";

            System.out.println("CustomerClient: lettura lingua batch, path=" + path);

            try {
                JsonNode root = http.getOData(path);
                JsonNode results = root.path("d").path("results");
                int found = 0;
                if (results.isArray()) {
                    for (JsonNode n : results) {
                        String bp   = n.path("BusinessPartner").asText(null);
                        String lang = n.path("CorrespondenceLanguage").asText(null);
                        if (bp != null && !bp.isBlank() && lang != null && !lang.isBlank()) {
                            result.put(bp.strip(), lang.strip());
                            found++;
                        } else {
                            System.out.println("CustomerClient: nodo BP senza lingua valorizzata, contenuto grezzo: " + n.toString());
                        }
                    }
                }
                System.out.println("CustomerClient: batch lingua — righe restituite="
                    + (results.isArray() ? results.size() : 0) + ", con lingua valorizzata=" + found);
            } catch (IOException e) {
                if (languageFieldAvailable) {
                    languageFieldAvailable = false;
                    System.err.println("CustomerClient: il campo OData 'CorrespondenceLanguage' su A_BusinessPartner "
                        + "non è disponibile su questo tenant — disattivato per il resto dell'estrazione "
                        + "(lingua cliente non stampata, verrà usato IT di default). Dettaglio: " + e.getMessage());
                }
                return result; // quanto raccolto finora; il resto resta IT di default
            }
        }
        return result;
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
        String name = node.path("CustomerName").asText("");
        String bzirk = null;
        String priceGroup = priceGroupOverride;
        String paymentTerms = null;
        String incotermsClassification = null;
        String incotermsLocation = null;
        JsonNode salesAreas = node.path("to_CustomerSalesArea").path("results");
        if (salesAreas.isArray()) {
            for (JsonNode sa : salesAreas) {
                String sd = sa.path("SalesDistrict").asText(null);
                if (sd != null && !sd.isBlank() && bzirk == null) bzirk = sd;
                if (priceGroup == null) {
                    String pg = sa.path("CustomerPriceGroup").asText(null);
                    if (pg != null && !pg.isBlank()) priceGroup = pg;
                }
                if (paymentTerms == null) {
                    String pt = sa.path("CustomerPaymentTerms").asText(null);
                    if (pt != null && !pt.isBlank()) paymentTerms = pt;
                }
                if (incotermsClassification == null) {
                    String ic = sa.path("IncotermsClassification").asText(null);
                    if (ic != null && !ic.isBlank()) incotermsClassification = ic;
                }
                if (incotermsLocation == null) {
                    String il = sa.path("IncotermsLocation1").asText(null);
                    if (il != null && !il.isBlank()) incotermsLocation = il;
                }
            }
        }
        CustomerInfo info = new CustomerInfo(code, name, bzirk, priceGroup, null);
        info.setPaymentTerms(paymentTerms);
        info.setIncotermsClassification(incotermsClassification);
        info.setIncotermsLocation(incotermsLocation);
        return info;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Model
    // ─────────────────────────────────────────────────────────────────────
    public static class CustomerInfo {
        private final String code, name, bzirk, priceGroup;
        private String language;
        private String paymentTerms = "";
        private String incotermsClassification = "";
        private String incotermsLocation = "";

        public CustomerInfo(String code, String name, String bzirk, String priceGroup, String language) {
            this.code = code; this.name = name; this.bzirk = bzirk; this.priceGroup = priceGroup;
            this.language = (language != null && !language.isBlank()) ? language.strip() : "IT";
        }
        public String  getCode()        { return code; }
        public String  getName()        { return name; }
        public String  getBzirk()       { return bzirk; }
        public String  getPriceGroup()  { return priceGroup; }
        public String  getLanguage()    { return language; }
        public void    setLanguage(String v) { this.language = (v != null && !v.isBlank()) ? v.strip() : "IT"; }
        public boolean hasBzirk()       { return bzirk != null && !bzirk.isBlank(); }
        public boolean hasPriceGroup()  { return priceGroup != null && !priceGroup.isBlank(); }
        public String  getPaymentTerms() { return paymentTerms; }
        public void    setPaymentTerms(String v) { this.paymentTerms = v != null ? v : ""; }
        public String  getIncotermsClassification() { return incotermsClassification; }
        public void    setIncotermsClassification(String v) { this.incotermsClassification = v != null ? v : ""; }
        public String  getIncotermsLocation() { return incotermsLocation; }
        public void    setIncotermsLocation(String v) { this.incotermsLocation = v != null ? v : ""; }
    }
}
