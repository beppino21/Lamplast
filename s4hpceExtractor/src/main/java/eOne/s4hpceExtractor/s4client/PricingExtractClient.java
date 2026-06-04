package eOne.s4hpceExtractor.s4client;

import com.fasterxml.jackson.databind.JsonNode;
import eOne.s4hpceExtractor.model.ConditionRecord;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PricingExtractClient extends S4HttpClient {

    private static final String SERVICE    = "/sap/opu/odata/sap/API_SLSPRICINGCONDITIONRECORD_SRV";
    private static final String VALIDITY   = SERVICE + "/A_SlsPrcgCndnRecdValidity";
    private static final String COND_REC   = SERVICE + "/A_SlsPrcgConditionRecord";
    private static final String SCALES     = SERVICE + "/A_SlsPrcgCndnRecordScale";

    private static final DateTimeFormatter SAP_DATE  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISP_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public PricingExtractClient(S4Config config) { super(config); }

    public List<ConditionRecord> extract(String conditionType, LocalDate referenceDate)
            throws IOException, InterruptedException {

        String dateStr = referenceDate.format(SAP_DATE);

        // Step 1: legge A_SlsPrcgCndnRecdValidity per avere i ConditionRecord IDs
        // e i campi chiave (SoldToParty, Material, SalesDistrict, date validità)
        String filter = "ConditionType eq '" + conditionType + "'"
            + " and ConditionValidityStartDate le datetime'" + dateStr + "T00:00:00'"
            + " and ConditionValidityEndDate   ge datetime'" + dateStr + "T00:00:00'";

        String path = VALIDITY
            + "?$filter=" + encode(filter)
            + "&$select=ConditionRecord,ConditionType,SoldToParty,Customer,Material,SalesDistrict,"
            +   "ConditionValidityStartDate,ConditionValidityEndDate"
            + "&$top=" + getPageSize()
            + "&$format=json";

        List<JsonNode> validityNodes = fetchAllPages(path);
        System.out.println("[" + conditionType + "] Validity records: " + validityNodes.size());

        if (validityNodes.isEmpty()) return new ArrayList<>();

        // Raccoglie IDs e mappa validity per ID
        List<String> ids = new ArrayList<>();
        Map<String, JsonNode> validityById = new HashMap<>();
        for (JsonNode n : validityNodes) {
            String id = str(n, "ConditionRecord");
            if (id != null && !id.isBlank()) {
                ids.add(id);
                validityById.put(id, n);
            }
        }

        // Legge prezzo/divisa/qty/scaleType da A_SlsPrcgConditionRecord in batch
        Map<String, JsonNode> condRecById = fetchConditionRecords(ids);
        System.out.println("[" + conditionType + "] ConditionRecord fetched: " + condRecById.size());

        List<ConditionRecord> result = new ArrayList<>();

        for (String id : ids) {
            JsonNode validity = validityById.get(id);
            JsonNode condRec  = condRecById.get(id);

            // Salta cancellati
            if (condRec != null && condRec.path("ConditionIsDeleted").asBoolean(false)) continue;

            String scaleType = condRec != null ? str(condRec, "PricingScaleType") : "";
            List<ScaleRow> scales = fetchScales(id);

            if (scales.isEmpty()) {
                ConditionRecord rec = buildRecord(id, validity, condRec, conditionType);
                rec.setScaleQtyFrom(0);
                rec.setScaleQtyTo(0);
                rec.setScaleUnit(null);
                rec.setPrice(condRec != null ? dbl(condRec, "ConditionRateValue") : 0);
                result.add(rec);
            } else {
                for (int i = 0; i < scales.size(); i++) {
                    ScaleRow scale = scales.get(i);
                    ConditionRecord rec = buildRecord(id, validity, condRec, conditionType);
                    if ("B".equals(scaleType)) {
                        rec.setScaleQtyFrom(i == 0 ? 0 : scales.get(i - 1).qty);
                        rec.setScaleQtyTo(scale.qty);
                    } else {
                        rec.setScaleQtyFrom(scale.qty);
                        rec.setScaleQtyTo(i < scales.size() - 1 ? scales.get(i + 1).qty : 0);
                    }
                    rec.setScaleUnit(scale.unit);
                    rec.setPrice(scale.price);
                    result.add(rec);
                }
            }
        }

        System.out.println("[" + conditionType + "] Righe totali: " + result.size());
        return result;
    }

    /**
     * Estrae le condizioni TTX1 (determinazione IVA vendite) valide alla data.
     * Nessuno scaglione — una riga per condizione.
     */
    public List<eOne.s4hpceExtractor.model.TaxRecord> extractTTX1(LocalDate referenceDate)
            throws IOException, InterruptedException {

        String dateStr = referenceDate.format(SAP_DATE);

        String filter = "ConditionType eq 'TTX1'"
            + " and ConditionValidityStartDate le datetime'" + dateStr + "T00:00:00'"
            + " and ConditionValidityEndDate   ge datetime'" + dateStr + "T00:00:00'";

        String path = VALIDITY
            + "?$filter=" + encode(filter)
            + "&$select=ConditionRecord,ConditionType,DepartureCountry,DestinationCountry,"
            +   "CustomerTaxClassification1,ProductTaxClassification1,"
            +   "ConditionValidityStartDate,ConditionValidityEndDate"
            + "&$top=" + getPageSize()
            + "&$format=json";

        List<JsonNode> nodes = fetchAllPages(path);
        System.out.println("[TTX1] Validity records: " + nodes.size());

        // Legge il codice IVA da A_SlsPrcgConditionRecord in batch
        List<String> ids = new ArrayList<>();
        java.util.Map<String, JsonNode> validityById = new java.util.HashMap<>();
        for (JsonNode n : nodes) {
            String id = str(n, "ConditionRecord");
            if (id != null && !id.isBlank()) { ids.add(id); validityById.put(id, n); }
        }
        java.util.Map<String, JsonNode> condRecById = fetchConditionRecords(ids);

        List<eOne.s4hpceExtractor.model.TaxRecord> result = new ArrayList<>();
        for (String id : ids) {
            JsonNode validity = validityById.get(id);
            JsonNode condRec  = condRecById.get(id);
            if (condRec != null && condRec.path("ConditionIsDeleted").asBoolean(false)) continue;

            eOne.s4hpceExtractor.model.TaxRecord rec = new eOne.s4hpceExtractor.model.TaxRecord();
            rec.setConditionType("TTX1");
            rec.setDepartureCountry(str(validity, "DepartureCountry"));
            rec.setDestinationCountry(str(validity, "DestinationCountry"));
            rec.setCustomerTaxClass(str(validity, "CustomerTaxClassification1"));
            rec.setProductTaxClass(str(validity, "ProductTaxClassification1"));
            rec.setTaxCode(condRec != null ? str(condRec, "ConditionRateValue") : "");
            rec.setValidFrom(formatDate(str(validity, "ConditionValidityStartDate")));
            rec.setValidTo(formatDate(str(validity, "ConditionValidityEndDate")));
            result.add(rec);
        }

        System.out.println("[TTX1] Righe totali: " + result.size());
        return result;
    }
    private Map<String, JsonNode> fetchConditionRecords(List<String> ids)
            throws IOException, InterruptedException {
        Map<String, JsonNode> map = new HashMap<>();
        int batchSize = 50;
        for (int i = 0; i < ids.size(); i += batchSize) {
            List<String> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
            StringBuilder filter = new StringBuilder();
            for (String id : batch) {
                if (filter.length() > 0) filter.append(" or ");
                filter.append("ConditionRecord eq '").append(id).append("'");
            }
            String path = COND_REC
                + "?$filter=" + encode(filter.toString())
                + "&$select=ConditionRecord,ConditionRateValue,ConditionRateValueUnit,"
                +   "ConditionQuantity,ConditionQuantityUnit,PricingScaleType,ConditionIsDeleted"
                + "&$format=json";
            try {
                JsonNode root = getOData(path);
                JsonNode results = root.path("d").path("results");
                if (results.isArray()) {
                    for (JsonNode n : results) {
                        String id = str(n, "ConditionRecord");
                        if (id != null) map.put(id, n);
                    }
                }
            } catch (IOException e) {
                System.err.println("fetchConditionRecords batch errore: " + e.getMessage());
            }
        }
        return map;
    }

    private ConditionRecord buildRecord(String id, JsonNode validity, JsonNode condRec,
                                        String conditionType) {
        ConditionRecord rec = new ConditionRecord();
        rec.setConditionType(conditionType);
        // Campi chiave dall'entità di validità
        // Il cliente può stare in "Customer" o "SoldToParty" a seconda della tabella condizioni
        String customer = str(validity, "Customer");
        if (customer == null || customer.isBlank()) customer = str(validity, "SoldToParty");
        rec.setCustomer(customer);
        rec.setMaterial(str(validity, "Material"));
        rec.setSalesDistrict(str(validity, "SalesDistrict"));
        rec.setValidFrom(formatDate(str(validity, "ConditionValidityStartDate")));
        rec.setValidTo(formatDate(str(validity, "ConditionValidityEndDate")));
        // Campi prezzo da A_SlsPrcgConditionRecord
        if (condRec != null) {
            rec.setCurrency(str(condRec, "ConditionRateValueUnit"));
            rec.setConditionQty(dbl(condRec, "ConditionQuantity"));
            rec.setConditionUnit(str(condRec, "ConditionQuantityUnit"));
            rec.setScaleType(str(condRec, "PricingScaleType"));
        }
        return rec;
    }

    private List<ScaleRow> fetchScales(String conditionRecord)
            throws IOException, InterruptedException {
        List<ScaleRow> scales = new ArrayList<>();
        if (conditionRecord == null || conditionRecord.isBlank()) return scales;
        String path = SCALES
            + "?$filter=" + encode("ConditionRecord eq '" + conditionRecord + "'")
            + "&$select=ConditionRecord,ConditionScaleQuantity,ConditionRateValue"
            + "&$orderby=ConditionScaleQuantity+asc"
            + "&$format=json";
        try {
            JsonNode root = getOData(path);
            JsonNode results = root.path("d").path("results");
            if (results.isArray()) {
                for (JsonNode s : results) {
                    ScaleRow row = new ScaleRow();
                    row.qty   = dbl(s, "ConditionScaleQuantity");
                    row.unit  = str(s, "ConditionScaleUoM");
                    row.price = dbl(s, "ConditionRateValue");
                    scales.add(row);
                }
            }
        } catch (IOException e) {
            System.err.println("fetchScales errore per " + conditionRecord + ": " + e.getMessage());
        }
        return scales;
    }

    private String formatDate(String sapDate) {
        if (sapDate == null) return "";
        if (sapDate.startsWith("/Date(")) {
            try {
                long ms = Long.parseLong(sapDate.replaceAll("[^0-9]", ""));
                return java.time.Instant.ofEpochMilli(ms)
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                    .format(DISP_DATE);
            } catch (Exception e) { return sapDate; }
        }
        return sapDate;
    }

    private static class ScaleRow {
        double qty;
        String unit;
        double price;
    }
}
