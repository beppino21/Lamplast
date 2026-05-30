package eone.listinoSD.logic;

import eone.listinoSD.model.ExtractParams;
import eone.listinoSD.model.ListinoRow;
import eone.listinoSD.model.PricingRecord;
import eone.listinoSD.s4client.CustomerClient;
import eone.listinoSD.s4client.CustomerClient.CustomerInfo;
import eone.listinoSD.s4client.PricingClient;
import eone.listinoSD.s4client.S4Config;
import eone.listinoSD.s4client.S4HttpClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ListinoExtractor {

    private final S4HttpClient   httpClient;
    private final PricingClient  pricingClient;
    private final CustomerClient customerClient;
    private final ListinoBuilder builder;
    private List<String> lastWarnings = List.of();

    public ListinoExtractor(S4Config config) {
        this.httpClient     = new S4HttpClient(config);
        this.pricingClient  = new PricingClient(httpClient);
        this.customerClient = new CustomerClient(httpClient);
        this.builder        = new ListinoBuilder();
    }

    public List<ListinoRow> extract(ExtractParams params) throws IOException, InterruptedException {
        Map<String, CustomerInfo> customers = customerClient.loadCustomers(params);
        List<PricingRecord> ppr0 = pricingClient.readPPR0(params);
        List<PricingRecord> ztra = pricingClient.readZTRA(params);
        List<ListinoRow> rows = builder.build(customers, ppr0, ztra, params);
        lastWarnings = builder.getWarnings();
        return rows;
    }

    public List<String> getLastWarnings() { return lastWarnings; }
}
