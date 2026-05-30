package eone.listinoSD.logic;

import eone.listinoSD.model.ExtractParams;
import eone.listinoSD.model.ListinoRow;
import eone.listinoSD.model.PricingRecord;
import eone.listinoSD.s4client.CustomerClient.CustomerInfo;

import java.time.LocalDate;
import java.util.*;

public class ListinoBuilder {

    private final List<String> warnings = new ArrayList<>();

    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }

    public List<ListinoRow> build(
            Map<String, CustomerInfo> customers,
            List<PricingRecord>       ppr0List,
            List<PricingRecord>       ztraList,
            ExtractParams             params) {

        warnings.clear();
        Map<String, Map<String, PricingRecord>> ppr0Index  = index(ppr0List, true);
        Map<String, Map<String, PricingRecord>> ztraIndex  = index(ztraList, false);
        List<ListinoRow> rows = new ArrayList<>();

        List<String> sortedCustomers = new ArrayList<>(customers.keySet());
        Collections.sort(sortedCustomers);

        for (String custCode : sortedCustomers) {
            CustomerInfo info    = customers.get(custCode);
            Map<String, PricingRecord> matMap  = ppr0Index.getOrDefault(custCode, Map.of());
            Map<String, PricingRecord> zoneMap = ztraIndex.getOrDefault(custCode, Map.of());

            if (matMap.isEmpty() && zoneMap.isEmpty()) {
                warnings.add("Cliente " + custCode + ": nessuna condizione PPR0/ZTRA — saltato.");
                continue;
            }
            String kStar = info.getBzirk();
            if (kStar == null || kStar.isBlank()) {
                warnings.add("Cliente " + custCode + ": BZIRK non valorizzato — saltato.");
                continue;
            }
            if (!zoneMap.containsKey(kStar)) {
                warnings.add("Cliente " + custCode + ": BZIRK='" + kStar
                    + "' non presente nelle condizioni ZTRA — saltato.");
                continue;
            }

            PricingRecord ztraKStar = zoneMap.get(kStar);

            // Intestazione cliente
            rows.add(ListinoRow.customerRow(custCode, info.getName()));

            // Blocco A: materiali
            rows.add(ListinoRow.headerMaterialRow(custCode, ztraKStar.getScaleQty()));
            List<String> sortedMat = new ArrayList<>(matMap.keySet());
            Collections.sort(sortedMat);
            for (String mat : sortedMat)
                rows.add(buildMaterialRow(custCode, matMap.get(mat), ztraKStar));

            // Blocco B: zone alternative
            if (zoneMap.size() > 1) {
                rows.add(ListinoRow.headerZoneRow(custCode));
                List<String> sortedZones = new ArrayList<>(zoneMap.keySet());
                Collections.sort(sortedZones);
                for (String zone : sortedZones)
                    rows.add(buildZoneRow(custCode, zone, zoneMap.get(zone),
                             ztraKStar, zone.equals(kStar)));
            }
        }
        return rows;
    }

    private ListinoRow buildMaterialRow(String custCode,
                                        PricingRecord ppr0,
                                        PricingRecord ztraKStar) {
        ListinoRow row = new ListinoRow();
        row.setRowType(ListinoRow.RowType.MATERIAL);
        row.setCustomerCode(custCode);
        row.setDescription(ppr0.getMaterial());
        row.setCurrency(ppr0.getCurrency());
        row.setConditionQty(ppr0.getConditionQty());
        row.setConditionUnit(ppr0.getConditionUnit());
        row.setScaleQty(ppr0.getScaleQty());

        // Prezzi sommati PPR0 + ZTRA(k*)
        double[] price = new double[5];
        for (int i = 0; i < 5; i++)
            price[i] = ppr0.getScalePrice()[i] + ztraKStar.getScalePrice()[i];
        row.setPrice(price);

        // Date: MAX(dataIn) MIN(dataFin)
        row.setValidFrom(maxDate(ppr0.getValidFrom(), ztraKStar.getValidFrom()));
        row.setValidTo  (minDate(ppr0.getValidTo(),   ztraKStar.getValidTo()));

        // Warning UM: se PPR0 e ZTRA hanno UM diversa la somma è concettualmente errata
        String umPpr0 = ppr0.getConditionUnit();
        String umZtra = ztraKStar.getConditionUnit();
        boolean mismatch = umPpr0 != null && umZtra != null
                        && !umPpr0.isBlank() && !umZtra.isBlank()
                        && !umPpr0.equalsIgnoreCase(umZtra);
        row.setUnitMismatch(mismatch);
        if (mismatch)
            warnings.add("UM divergente per materiale " + ppr0.getMaterial()
                + " cliente " + custCode
                + ": PPR0=" + umPpr0 + " ZTRA=" + umZtra);

        return row;
    }

    private ListinoRow buildZoneRow(String custCode, String zone,
                                    PricingRecord ztraK,
                                    PricingRecord ztraKStar,
                                    boolean isPreferred) {
        ListinoRow row = new ListinoRow();
        row.setRowType(ListinoRow.RowType.ZONE);
        row.setCustomerCode(custCode);
        row.setDescription(zone);
        row.setCurrency(ztraK.getCurrency());
        row.setConditionQty(ztraK.getConditionQty());
        row.setConditionUnit(ztraK.getConditionUnit());
        row.setPreferredZone(isPreferred);

        double[] delta = new double[5];
        for (int i = 0; i < 5; i++)
            delta[i] = ztraK.getScalePrice()[i] - ztraKStar.getScalePrice()[i];
        row.setPrice(delta);

        row.setValidFrom(maxDate(ztraK.getValidFrom(), ztraKStar.getValidFrom()));
        row.setValidTo  (minDate(ztraK.getValidTo(),   ztraKStar.getValidTo()));
        return row;
    }

    private Map<String, Map<String, PricingRecord>> index(
            List<PricingRecord> records, boolean byMaterial) {
        Map<String, Map<String, PricingRecord>> idx = new LinkedHashMap<>();
        for (PricingRecord rec : records) {
            String cust = rec.getCustomer();
            String key  = byMaterial ? rec.getMaterial() : rec.getZone();
            if (cust == null || key == null) continue;
            idx.computeIfAbsent(cust, k -> new LinkedHashMap<>()).put(key, rec);
        }
        return idx;
    }

    private LocalDate maxDate(LocalDate a, LocalDate b) {
        if (a == null) return b; if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
    private LocalDate minDate(LocalDate a, LocalDate b) {
        if (a == null) return b; if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }
}
