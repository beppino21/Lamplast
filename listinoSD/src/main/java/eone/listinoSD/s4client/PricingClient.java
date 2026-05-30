package eone.listinoSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;
import eone.listinoSD.model.ExtractParams;
import eone.listinoSD.model.PricingRecord;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PricingClient {

    private static final String BASE_PATH =
        "/sap/opu/odata/sap/API_SLSPRICINGCONDITIONRECORD_SRV/A_SlsPrcgCndnRecdValidity";

    private static final DateTimeFormatter SAP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final S4HttpClient http;

    public PricingClient(S4HttpClient http) { this.http = http; }

    public List<PricingRecord> readPPR0(ExtractParams params)
            throws IOException, InterruptedException {
        return readConditions("PPR0", PricingRecord.ConditionType.PPR0, params);
    }

    public List<PricingRecord> readZTRA(ExtractParams params)
            throws IOException, InterruptedException {
        return readConditions("ZTRA", PricingRecord.ConditionType.ZTRA, params);
    }

    private List<PricingRecord> readConditions(
            String conditionType,
            PricingRecord.ConditionType type,
            ExtractParams params) throws IOException, InterruptedException {

        List<PricingRecord> result = new ArrayList<>();
        if (params.isAllCustomers()) {
            result.addAll(fetchPage(conditionType, type, null, params));
        } else {
            for (String customer : params.getCustomers()) {
                result.addAll(fetchPage(conditionType, type, customer, params));
            }
        }
        return result;
    }

    private List<PricingRecord> fetchPage(
            String conditionType,
            PricingRecord.ConditionType type,
            String customer,
            ExtractParams params) throws IOException, InterruptedException {

        StringBuilder filter = new StringBuilder();
        filter.append("ConditionType eq '").append(conditionType).append("'");
        if (customer != null)
            filter.append(" and Customer eq '").append(customer).append("'");

        String refDate = params.getReferenceDate().format(SAP_DATE);
        filter.append(" and ConditionValidityStartDate le datetime'")
              .append(refDate).append("T00:00:00'");
        filter.append(" and ConditionValidityEndDate ge datetime'")
              .append(refDate).append("T00:00:00'");

        String path = BASE_PATH
            + "?$filter=" + S4HttpClient.encode(filter.toString())
            + "&$expand=to_SlsPrcgConditionRecord/to_SlsPrcgCndnRecordScale"
            + "&$format=json";

        return parseResults(http.getOData(path), type, params);
    }

    private List<PricingRecord> parseResults(
            JsonNode root,
            PricingRecord.ConditionType type,
            ExtractParams params) {

        List<PricingRecord> records = new ArrayList<>();
        JsonNode results = root.path("d").path("results");
        if (!results.isArray()) return records;

        for (JsonNode validity : results) {

            // Date dalla Validity
            LocalDate validFrom = parseDate(validity.path("ConditionValidityStartDate").asText());
            LocalDate validTo   = parseDate(validity.path("ConditionValidityEndDate").asText());

            // Customer sempre dalla Validity
            String customer = validity.path("Customer").asText(null);
            if (customer == null || customer.isBlank()) continue;

            // Record espanso per importi e UM
            JsonNode condRec = validity.path("to_SlsPrcgConditionRecord");
            if (condRec.isMissingNode() || condRec.isNull()) continue;

            String currency     = condRec.path("ConditionRateValueUnit").asText(null);
            double conditionQty = condRec.path("ConditionQuantity").asDouble(1.0);
            String conditionUnit= condRec.path("ConditionQuantityUnit").asText(null);

            // Chiave dipendente dal tipo — entrambi in Validity
            String material = null;
            String zone     = null;
            if (type == PricingRecord.ConditionType.PPR0) {
                material = validity.path("Material").asText(null);
                if (material == null || material.isBlank()) continue;
                if (!params.isAllMaterials() && !params.getMaterials().contains(material)) continue;
            } else {
                zone = validity.path("SalesDistrict").asText(null);
                if (zone == null || zone.isBlank()) continue;
            }

            // ── Scaglioni ──────────────────────────────────────────────────
            double[] qty   = new double[5];
            double[] price = new double[5];

            JsonNode scaleResults = condRec.path("to_SlsPrcgCndnRecordScale").path("results");
            if (scaleResults.isArray() && scaleResults.size() > 0) {
                // Prezzi scaglionati
                int idx = 0;
                for (JsonNode scale : scaleResults) {
                    if (idx >= 5) break;
                    qty  [idx] = scale.path("ConditionScaleQuantity").asDouble(0.0);
                    // Il prezzo dello scaglione può stare in ConditionRateValue
                    // oppure in ConditionScaleAmount a seconda della configurazione
                    double rv = scale.path("ConditionRateValue").asDouble(0.0);
                    double sa = scale.path("ConditionScaleAmount").asDouble(0.0);
                    price[idx] = rv != 0.0 ? rv : sa;
                    idx++;
                }
            } else {
                // Prezzo flat: nessuna scala → ConditionRateValue del record principale
                // Lo mettiamo in price[0], qty[0]=1
                double flatPrice = condRec.path("ConditionRateValue").asDouble(0.0);
                price[0] = flatPrice;
                qty  [0] = conditionQty > 0 ? conditionQty : 1.0;

                System.out.println("PricingClient [" + type + "] flat price "
                    + (material != null ? material : zone)
                    + " = " + flatPrice + " " + currency);
            }

            // ── Costruzione record ────────────────────────────────────────
            PricingRecord pr = new PricingRecord();
            pr.setConditionType(type);
            pr.setCustomer(customer);
            pr.setMaterial(material);
            pr.setZone(zone);
            pr.setCurrency(currency);
            pr.setConditionQty(conditionQty);
            pr.setConditionUnit(conditionUnit);
            pr.setValidFrom(validFrom);
            pr.setValidTo(validTo);
            pr.setScaleQty(qty);
            pr.setScalePrice(price);

            records.add(pr);
        }

        System.out.println("PricingClient [" + type + "] parsed " + records.size() + " records");
        return records;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            if (raw.startsWith("/Date(")) {
                long ms = Long.parseLong(raw.replaceAll("[^0-9]", ""));
                return LocalDate.ofEpochDay(ms / 86_400_000L);
            }
            return LocalDate.parse(raw.substring(0, 10), SAP_DATE);
        } catch (Exception e) { return null; }
    }
}
