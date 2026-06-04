package eOne.conditionsSD.logic;

import eOne.conditionsSD.model.ExtractParams;
import eOne.conditionsSD.model.ListinoRow;
import eOne.conditionsSD.model.PricingRecord;
import eOne.conditionsSD.s4client.CustomerClient;
import eOne.conditionsSD.s4client.CustomerClient.CustomerInfo;
import eOne.conditionsSD.s4client.CustomerMaterialClient;
import eOne.conditionsSD.s4client.MaterialClient;
import eOne.conditionsSD.s4client.PricingClient;
import eOne.conditionsSD.s4client.S4Config;
import eOne.conditionsSD.s4client.S4HttpClient;
import eOne.conditionsSD.s4client.SalesDistrictClient;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ListinoExtractor {

    private final S4HttpClient        httpClient;
    private final PricingClient       pricingClient;
    private final CustomerClient      customerClient;
    private final CustomerMaterialClient customerMaterialClient;
    private final MaterialClient      materialClient;
    private final SalesDistrictClient districtClient;
    private final ListinoBuilder      builder;

    private List<String> lastWarnings = List.of();

    public ListinoExtractor(S4Config config) {
        this.httpClient             = new S4HttpClient(config);
        this.pricingClient          = new PricingClient(httpClient);
        this.customerClient         = new CustomerClient(httpClient);
        this.customerMaterialClient = new CustomerMaterialClient(httpClient);
        this.materialClient         = new MaterialClient(httpClient, config.getLanguage());
        this.districtClient         = new SalesDistrictClient(httpClient, config.getLanguage());
        this.builder                = new ListinoBuilder();
    }

    public List<ListinoRow> extract(ExtractParams params)
            throws IOException, InterruptedException {

        // 1. Dati cliente — se priceGroup valorizzato, risolve prima i clienti del gruppo
        Map<String, CustomerInfo> customers;
        if (params.hasPriceGroup()) {
            customers = customerClient.loadCustomersByPriceGroup(params.getPriceGroup());
            if (customers.isEmpty()) {
                lastWarnings = List.of("Nessun cliente trovato per il Price Group '"
                    + params.getPriceGroup() + "'.");
                return List.of();
            }
            // Costruisce ExtractParams derivato con i clienti risolti
            List<String> resolvedCodes = new ArrayList<>(customers.keySet());
            params = ExtractParams.builder()
                .customers(resolvedCodes)
                .materials(params.getMaterials())
                .referenceDate(params.getReferenceDate())
                .priceGroup(params.getPriceGroup())
                .extractMode(params.getExtractMode())
                .build();
        } else {
            customers = customerClient.loadCustomers(params);
        }

        // 2. Condizioni PPR0 e ZTRA
        List<PricingRecord> ppr0 = pricingClient.readPPR0(params);
        List<PricingRecord> ztra = pricingClient.readZTRA(params);

        // 3. Descrizioni materiali
        Set<String> materialCodes = ppr0.stream()
            .map(PricingRecord::getMaterial)
            .filter(m -> m != null && !m.trim().isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, String> materialDescriptions = materialCodes.isEmpty()
            ? Map.of()
            : materialClient.fetchDescriptions(materialCodes);

        // 4. Codifiche cliente-materiale (MaterialByCustomer) — solo per PPR0
        Map<String, String> materialByCustomer = Map.of();
        if (!ppr0.isEmpty()) {
            List<String[]> pairs = new ArrayList<>();
            for (PricingRecord r : ppr0) {
                String cust = r.getCustomer();
                String mat  = r.getMaterial();
                if (cust != null && !cust.isBlank() && mat != null && !mat.isBlank())
                    pairs.add(new String[]{cust, mat});
            }
            if (!pairs.isEmpty())
                materialByCustomer = customerMaterialClient.loadMaterialByCustomer(pairs);
        }

        // 5. Descrizioni zone ZTRA — API non ancora disponibile su questo tenant
        Map<String, String> zoneDescriptions = Map.of();

        // 6. Build righe listino
        List<ListinoRow> rows = builder.build(
            customers, ppr0, ztra, materialDescriptions, zoneDescriptions,
            materialByCustomer, params);
        lastWarnings = builder.getWarnings();

        return rows;
    }

    public CustomerClient getCustomerClient() { return customerClient; }
    public List<String>   getLastWarnings()   { return lastWarnings; }
}
