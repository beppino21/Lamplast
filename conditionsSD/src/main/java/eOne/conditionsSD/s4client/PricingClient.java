package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;
import eOne.conditionsSD.model.ExtractParams;
import eOne.conditionsSD.model.PricingRecord;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Legge PPR0 e ZTRA da A_SlsPrcgCndnRecdValidity con expand a
 * to_SlsPrcgConditionRecord/to_SlsPrcgCndnRecordScale.
 *
 * Se l'expand delle scale restituisce array vuoto (comportamento
 * osservato su questo tenant), recupera le scale con una chiamata
 * separata a A_SlsPrcgCndnRecordScale filtrando per ConditionRecord.
 */
public class PricingClient {

    private static final String BASE_PATH =
        "/sap/opu/odata/sap/API_SLSPRICINGCONDITIONRECORD_SRV/A_SlsPrcgCndnRecdValidity";

    private static final String SCALE_PATH =
        "/sap/opu/odata/sap/API_SLSPRICINGCONDITIONRECORD_SRV/A_SlsPrcgCndnRecordScale";

    private static final DateTimeFormatter SAP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final S4HttpClient http;

    public PricingClient(S4HttpClient http) { this.http = http; }

    // ── API pubblica ──────────────────────────────────────────────────────

    public List<PricingRecord> readPPR0(ExtractParams params)
            throws IOException, InterruptedException {
        return readConditions("PPR0", PricingRecord.ConditionType.PPR0, params);
    }

    public List<PricingRecord> readZTRA(ExtractParams params)
            throws IOException, InterruptedException {
        return readConditions("ZTRA", PricingRecord.ConditionType.ZTRA, params);
    }

    // ── Logica interna ────────────────────────────────────────────────────

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

    // ── Parsing ───────────────────────────────────────────────────────────

    private List<PricingRecord> parseResults(
            JsonNode root,
            PricingRecord.ConditionType type,
            ExtractParams params) throws IOException, InterruptedException {

        List<PricingRecord> records = new ArrayList<>();
        JsonNode results = root.path("d").path("results");
        if (!results.isArray()) return records;

        for (JsonNode validity : results) {
            // Date dalla Validity
            LocalDate validFrom = parseDate(validity.path("ConditionValidityStartDate").asText());
            LocalDate validTo   = parseDate(validity.path("ConditionValidityEndDate").asText());

            // Customer dalla Validity
            String customer = validity.path("Customer").asText(null);
            if (customer == null || customer.trim().isEmpty()) continue;

            // Record espanso
            JsonNode condRec = validity.path("to_SlsPrcgConditionRecord");
            if (condRec.isMissingNode() || condRec.isNull()) continue;

            // Salta record marcati come cancellati
            boolean isDeleted = condRec.path("ConditionIsDeleted").asBoolean(false);
            if (isDeleted) continue;

            String conditionRecord = condRec.path("ConditionRecord").asText(null);
            String currency        = condRec.path("ConditionRateValueUnit").asText(null);
            double conditionQty    = condRec.path("ConditionQuantity").asDouble(1.0);
            String conditionUnit   = condRec.path("ConditionQuantityUnit").asText(null);
            // scaleType: "B"=Descending(fino a), "A"=Ascending(da), ""=flat
            String scaleType       = condRec.path("PricingScaleType").asText("");

            // Chiave dipendente dal tipo
            String material = null;
            String zone     = null;
            if (type == PricingRecord.ConditionType.PPR0) {
                material = validity.path("Material").asText(null);
                if (material == null || material.trim().isEmpty()) continue;
                if (!params.isAllMaterials() && !params.getMaterials().contains(material)) continue;
            } else {
                zone = validity.path("SalesDistrict").asText(null);
                if (zone == null || zone.trim().isEmpty()) continue;
            }

            // ── Scale: prima prova dall'expand, poi chiamata separata ─────
            double[] qty   = new double[5];
            double[] price = new double[5];

            JsonNode scaleResults = condRec.path("to_SlsPrcgCndnRecordScale").path("results");
            if (scaleResults.isArray() && scaleResults.size() > 0) {
                // Scale già presenti nell'expand
                parseScales(scaleResults, qty, price, scaleType);
            } else if (conditionRecord != null && !conditionRecord.trim().isEmpty()) {
                // Chiamata separata per le scale
                JsonNode scaleData = fetchScales(conditionRecord);
                if (scaleData != null && scaleData.isArray() && scaleData.size() > 0) {
                    parseScales(scaleData, qty, price, scaleType);
                } else {
                    // Prezzo flat
                    price[4] = condRec.path("ConditionRateValue").asDouble(0.0);
                    
                }
            } else {
                // Prezzo flat
                price[4] = condRec.path("ConditionRateValue").asDouble(0.0);
                
            }

            // Log debug
            System.out.println("Record " + type
                + " mat/zone=" + (material != null ? material : zone)
                + " scaleQty=" + Arrays.toString(qty)
                + " scalePrice=" + Arrays.toString(price));

            // Costruzione record
            PricingRecord pr = new PricingRecord();
            pr.setConditionType(type);
            pr.setCustomer(customer);
            pr.setMaterial(material);
            pr.setZone(zone);
            pr.setCurrency(currency);
            pr.setConditionQty(conditionQty);
            pr.setConditionUnit(conditionUnit);
            pr.setScaleType(scaleType);
            pr.setValidFrom(validFrom);
            pr.setValidTo(validTo);
            pr.setScaleQty(qty);
            pr.setScalePrice(price);

            records.add(pr);
        }

        System.out.println("PricingClient [" + type + "] parsed " + records.size() + " records");
        return records;
    }

    // ── Fetch scale separato ──────────────────────────────────────────────

    /**
     * Legge le scale per un ConditionRecord specifico.
     * Restituisce il JsonNode array dei risultati, o null se errore.
     */
    private JsonNode fetchScales(String conditionRecord)
            throws IOException, InterruptedException {
        String filter = "ConditionRecord eq '" + conditionRecord + "'"
                      + " and ConditionSequentialNumber eq '1'";
        String path = SCALE_PATH
            + "?$filter=" + S4HttpClient.encode(filter)
            + "&$orderby=ConditionScaleLine%20asc"
            + "&$format=json";
        try {
            JsonNode root = http.getOData(path);
            return root.path("d").path("results");
        } catch (IOException e) {
            System.err.println("PricingClient: errore fetch scale per " + conditionRecord
                + ": " + e.getMessage());
            return null;
        }
    }

    // ── Parsing scale ─────────────────────────────────────────────────────

    /**
     * Legge i nodi scala e popola qty[] e price[].
     *
     * Scale Descending (to/fino a, ScaleType="C"):
     *   qty[i] = soglia superiore della fascia i
     *   Prezzo valido per quantità <= qty[i]
     *
     * Scale Ascending (From, ScaleType="A"):
     *   Prima riga: qty=0 (da 0), prezzo base
     *   Righe successive: qty = limite inferiore della fascia
     *   Prezzo valido per quantità >= qty[i]
     *   Le memorizziamo come "fino a": qty[i] = limite inferiore fascia successiva - epsilon
     *   In pratica: qty[i] = scaleQty[i+1] - 0.001 per compatibilità con priceAt
     *
     * Semplificazione: memorizziamo le qty così come arrivano da SAP,
     * e il ListinoBuilder usa scaleType per scegliere la logica corretta.
     */
    private void parseScales(JsonNode scaleArray, double[] qty, double[] price, String scaleType) {
        int idx = 0;
        for (JsonNode scale : scaleArray) {
            if (idx >= 5) break;
            double scaleQty = scale.path("ConditionScaleQuantity").asDouble(0.0);
            double rv       = scale.path("ConditionRateValue").asDouble(0.0);
            double sa       = scale.path("ConditionScaleAmount").asDouble(0.0);
            qty  [idx] = scaleQty;
            price[idx] = rv != 0.0 ? rv : sa;
            idx++;
        }
    }

    // ── Utilità ───────────────────────────────────────────────────────────

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            if (raw.startsWith("/Date(")) {
                long ms = Long.parseLong(raw.replaceAll("[^0-9]", ""));
                return LocalDate.ofEpochDay(ms / 86_400_000L);
            }
            return LocalDate.parse(raw.substring(0, 10), SAP_DATE);
        } catch (Exception e) { return null; }
    }
}
