package eOne.conditionsSD.logic;

import eOne.conditionsSD.model.ExtractParams;
import eOne.conditionsSD.model.ListinoRow;
import eOne.conditionsSD.model.PricingRecord;
import eOne.conditionsSD.s4client.CustomerClient;
import eOne.conditionsSD.s4client.CustomerClient.CustomerInfo;
import eOne.conditionsSD.s4client.MaterialClient;
import eOne.conditionsSD.s4client.PricingClient;
import eOne.conditionsSD.s4client.S4Config;
import eOne.conditionsSD.s4client.S4HttpClient;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ListinoExtractor {

    private final S4HttpClient   httpClient;
    private final PricingClient  pricingClient;
    private final CustomerClient customerClient;
    private final MaterialClient materialClient;
    private final ListinoBuilder builder;

    private List<String> lastWarnings = List.of();

    public ListinoExtractor(S4Config config) {
        this.httpClient     = new S4HttpClient(config);
        this.pricingClient  = new PricingClient(httpClient);
        this.customerClient = new CustomerClient(httpClient);
        this.materialClient = new MaterialClient(httpClient, config.getLanguage());
        this.builder        = new ListinoBuilder();
    }

    public List<ListinoRow> extract(ExtractParams params)
            throws IOException, InterruptedException {

        // 1. Dati cliente (nome + BZIRK)
        Map<String, CustomerInfo> customers = customerClient.loadCustomers(params);

        // 2. Condizioni PPR0 e ZTRA
        List<PricingRecord> ppr0 = pricingClient.readPPR0(params);
        List<PricingRecord> ztra = pricingClient.readZTRA(params);

        // 3. Descrizioni materiali — raccoglie tutti i codici materiale unici
        Set<String> materialCodes = ppr0.stream()
            .map(PricingRecord::getMaterial)
            .filter(m -> m != null && !m.trim().isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, String> materialDescriptions = materialCodes.isEmpty()
            ? Map.of()
            : materialClient.fetchDescriptions(materialCodes);

        // 4. Build righe listino
        List<ListinoRow> rows = builder.build(
            customers, ppr0, ztra, materialDescriptions, params);
        lastWarnings = builder.getWarnings();

        return rows;
    }

    public List<String> getLastWarnings() { return lastWarnings; }
}
