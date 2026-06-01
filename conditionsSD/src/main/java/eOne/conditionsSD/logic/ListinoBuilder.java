package eOne.conditionsSD.logic;

import eOne.conditionsSD.model.ExtractParams;
import eOne.conditionsSD.model.ListinoRow;
import eOne.conditionsSD.model.PricingRecord;
import eOne.conditionsSD.s4client.CustomerClient.CustomerInfo;

import java.time.LocalDate;
import java.util.*;

public class ListinoBuilder {

    private final List<String> warnings = new ArrayList<>();

    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }

    // ═══════════════════════════════════════════════════════════════════════
    // Entry point
    // ═══════════════════════════════════════════════════════════════════════

    public List<ListinoRow> build(
            Map<String, CustomerInfo> customers,
            List<PricingRecord>       ppr0List,
            List<PricingRecord>       ztraList,
            Map<String, String>       materialDescriptions,
            ExtractParams             params) {

        warnings.clear();
        Map<String, Map<String, PricingRecord>> ppr0Index = index(ppr0List, true);
        Map<String, Map<String, PricingRecord>> ztraIndex = index(ztraList, false);
        List<ListinoRow> rows    = new ArrayList<>();
        List<ListinoRow> alerts  = new ArrayList<>(); // righe allarme in coda

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
            if (kStar == null || kStar.trim().isEmpty()) {
                warnings.add("Cliente " + custCode + ": BZIRK non valorizzato — saltato.");
                continue;
            }
            if (!zoneMap.containsKey(kStar)) {
                warnings.add("Cliente " + custCode + ": BZIRK='" + kStar
                    + "' non presente nelle condizioni ZTRA — saltato.");
                continue;
            }

            PricingRecord ztraKStar = zoneMap.get(kStar);

            // ── Intestazione cliente ──────────────────────────────────────
            rows.add(ListinoRow.customerRow(custCode, info.getName()));

            // ── Blocco A: Prezzi materiale (scala = merge PPR0 + ZTRA k*) ─
            Map<String, List<PricingRecord>> byScale   = new LinkedHashMap<>();
            Map<String, double[]>            scaleQtyMap  = new LinkedHashMap<>();
            Map<String, String>              scaleUnitMap = new LinkedHashMap<>();

            List<String> sortedMat = new ArrayList<>(matMap.keySet());
            Collections.sort(sortedMat);

            for (String mat : sortedMat) {
                PricingRecord ppr0 = matMap.get(mat);
                double[] mergedQty = mergeScaleQty(
                    ppr0.getScaleQty(), ppr0.getScaleType(),
                    ztraKStar.getScaleQty(), ztraKStar.getScaleType());
                String key = Arrays.toString(mergedQty);
                byScale.computeIfAbsent(key, k -> new ArrayList<>()).add(ppr0);
                scaleQtyMap.put(key, mergedQty);
                String unit = nvl(ppr0.getConditionUnit(), ztraKStar.getConditionUnit());
                scaleUnitMap.put(key, unit);

                // Verifica allineamento soglie PPR0 vs ZTRA k*
                if (hasScaleConflict(ppr0, ztraKStar)) {
                    alerts.add(buildAlertRow(custCode, info.getName(), mat, ppr0, ztraKStar));
                }
            }

            for (String key : byScale.keySet()) {
                double[] mergedQty  = scaleQtyMap.get(key);
                String   scaleUnit  = scaleUnitMap.get(key);
                int      activeCols = countActive(mergedQty);
                rows.add(ListinoRow.headerScaleRow(custCode, mergedQty, scaleUnit, activeCols));
                for (PricingRecord ppr0 : byScale.get(key))
                    rows.add(buildMaterialRow(custCode, ppr0, ztraKStar,
                             mergedQty, activeCols, materialDescriptions));
            }

            // ── Blocco B: Zone alternative (scala = unione soglie ZTRA) ───
            if (zoneMap.size() > 1) {
                double[] ztraScale  = buildZoneScale(zoneMap);
                int      ztraActive = countActive(ztraScale);
                String   ztraUnit   = nvl(ztraKStar.getConditionUnit(), "");

                rows.add(ListinoRow.headerZoneRow(custCode));
                // Header scala zone (separato dall'header materiali)
                rows.add(ListinoRow.headerScaleRow(custCode, ztraScale, ztraUnit, ztraActive));

                List<String> sortedZones = new ArrayList<>(zoneMap.keySet());
                Collections.sort(sortedZones);
                for (String zone : sortedZones) {
                    rows.add(buildZoneRow(custCode, zone, zoneMap.get(zone),
                             ztraKStar, zone.equals(kStar), ztraScale, ztraActive));
                }
            }
        }

        // ── Righe allarme in coda ─────────────────────────────────────────
        if (!alerts.isEmpty()) {
            rows.add(ListinoRow.alertHeaderRow());
            rows.addAll(alerts);
        }

        return rows;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Rilevazione conflitti di scala
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Verifica se PPR0 e ZTRA k* hanno scaglioni non allineati.
     * Un conflitto esiste quando ENTRAMBI hanno scaglioni E le soglie
     * non coincidono — in quel caso il delta per zona potrebbe non
     * essere rappresentativo.
     */
    private boolean hasScaleConflict(PricingRecord ppr0, PricingRecord ztraKStar) {
        boolean ppr0Flat  = isFlat(ppr0.getScaleQty());
        boolean ztraFlat  = isFlat(ztraKStar.getScaleQty());
        if (ppr0Flat || ztraFlat) return false; // almeno uno flat = nessun conflitto
        // Entrambi scaglionati: conflitto se le soglie non coincidono
        return !Arrays.equals(
            activeSoglie(ppr0.getScaleQty()),
            activeSoglie(ztraKStar.getScaleQty()));
    }

    private boolean isFlat(double[] qty) {
        for (double q : qty) if (q > 0) return false;
        return true;
    }

    private double[] activeSoglie(double[] qty) {
        List<Double> active = new ArrayList<>();
        for (double q : qty) if (q > 0) active.add(q);
        double[] result = new double[active.size()];
        for (int i = 0; i < active.size(); i++) result[i] = active.get(i);
        return result;
    }

    private ListinoRow buildAlertRow(String custCode, String custName,
                                     String material,
                                     PricingRecord ppr0,
                                     PricingRecord ztraKStar) {
        ListinoRow row = new ListinoRow();
        row.setRowType(ListinoRow.RowType.ALERT);
        row.setCustomerCode(custCode);
        row.setCustomerName(custName);
        String sogliePPR0 = formatSoglie(ppr0.getScaleQty(), ppr0.getConditionUnit());
        String soglieZTRA = formatSoglie(ztraKStar.getScaleQty(), ztraKStar.getConditionUnit());
        row.setDescription("Cliente " + custCode + " — " + custName
            + "  |  Materiale: " + material
            + "  |  Soglie PPR0: " + sogliePPR0
            + "  |  Soglie ZTRA (" + ztraKStar.getZone() + "): " + soglieZTRA
            + "  →  I delta per zona potrebbero non essere rappresentativi.");
        return row;
    }

    private String formatSoglie(double[] qty, String unit) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (double q : qty) {
            if (q <= 0) continue;
            if (!first) sb.append(", ");
            sb.append(q == Math.floor(q)
                ? String.format("%.0f", q)
                : String.format("%.3f", q).replaceAll("0+$", ""));
            first = false;
        }
        sb.append("]");
        if (unit != null && !unit.trim().isEmpty()) sb.append(" ").append(unit);
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Merge scale
    // ═══════════════════════════════════════════════════════════════════════

    private double[] mergeScaleQty(double[] ppr0Qty, String ppr0Type,
                                    double[] ztraQty, String ztraType) {
        TreeSet<Double> all = new TreeSet<>();
        if ("B".equals(ppr0Type) || "".equals(ppr0Type))
            for (double q : ppr0Qty) if (q > 0) all.add(q);
        if ("B".equals(ztraType) || "".equals(ztraType))
            for (double q : ztraQty) if (q > 0) all.add(q);
        if (all.isEmpty()) {
            for (double q : ppr0Qty) if (q > 0) all.add(q - 0.001);
            for (double q : ztraQty) if (q > 0) all.add(q - 0.001);
        }
        double[] merged = new double[5];
        int idx = 0;
        for (double q : all) { if (idx >= 5) break; merged[idx++] = q; }
        return merged;
    }

    private double[] buildZoneScale(Map<String, PricingRecord> zoneMap) {
        TreeSet<Double> all = new TreeSet<>();
        for (PricingRecord z : zoneMap.values())
            if ("B".equals(z.getScaleType()) || "".equals(z.getScaleType()))
                for (double q : z.getScaleQty()) if (q > 0) all.add(q);
        if (all.isEmpty())
            for (PricingRecord z : zoneMap.values())
                for (double q : z.getScaleQty()) if (q > 0) all.add(q - 0.001);
        double[] merged = new double[5];
        int idx = 0;
        for (double q : all) { if (idx >= 5) break; merged[idx++] = q; }
        return merged;
    }

    private double priceAt(double[] scaleQty, double[] scalePrice, String scaleType, double atQty) {
        boolean isFlat = true;
        for (double q : scaleQty) if (q > 0) { isFlat = false; break; }
        if (isFlat) {
            // Prezzo flat: sta nell'ultimo slot non zero (slot 4 per flat in col.5)
            for (int i = 4; i >= 0; i--)
                if (scalePrice[i] != 0.0) return scalePrice[i];
            return 0.0;
        }

        if ("A".equals(scaleType)) {
            double result = scalePrice[0];
            for (int i = 1; i < 5; i++) {
                if (scaleQty[i] <= 0) break;
                if (atQty >= scaleQty[i]) result = scalePrice[i];
                else break;
            }
            return result;
        } else {
            for (int i = 0; i < 5; i++) {
                if (scaleQty[i] <= 0) break;
                if (atQty <= scaleQty[i]) return scalePrice[i];
            }
            for (int i = 4; i >= 0; i--)
                if (scaleQty[i] > 0) return scalePrice[i];
            return scalePrice[0];
        }
    }

    private int countActive(double[] scaleQty) {
        // Conta le soglie > 0
        int count = 0;
        for (double q : scaleQty) if (q > 0) count++;
        // Se nessuna soglia (flat): il prezzo sta in slot 4 → servono 5 colonne
        return count > 0 ? count : 5;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Costruzione righe
    // ═══════════════════════════════════════════════════════════════════════

    private ListinoRow buildMaterialRow(String custCode, PricingRecord ppr0,
                                        PricingRecord ztraKStar,
                                        double[] mergedQty, int activeCols,
                                        Map<String, String> materialDescriptions) {
        ListinoRow row = new ListinoRow();
        row.setRowType(ListinoRow.RowType.MATERIAL);
        row.setCustomerCode(custCode);
        // Descrizione: "codice — testo" se disponibile, solo codice altrimenti
        String mat  = ppr0.getMaterial();
        String desc = materialDescriptions != null ? materialDescriptions.get(mat) : null;
        row.setDescription(desc != null && !desc.trim().isEmpty()
            ? mat + " — " + desc
            : mat);
        row.setCurrency(ppr0.getCurrency());
        row.setConditionQty(ppr0.getConditionQty());
        row.setConditionUnit(ppr0.getConditionUnit());
        row.setScaleQty(mergedQty);
        row.setActiveCols(activeCols);

        double[] price = new double[5];
        boolean bothFlat = isFlat(ppr0.getScaleQty()) && isFlat(ztraKStar.getScaleQty());
        if (bothFlat) {
            // Entrambi flat: prezzo unico in slot 4 (colonna 5)
            double pPPR0 = priceAt(ppr0.getScaleQty(), ppr0.getScalePrice(), ppr0.getScaleType(), 0);
            double pZTRA = priceAt(ztraKStar.getScaleQty(), ztraKStar.getScalePrice(), ztraKStar.getScaleType(), 0);
            price[4] = pPPR0 + pZTRA;
        } else {
            for (int i = 0; i < activeCols; i++) {
                double atQty = mergedQty[i];
                double pPPR0 = priceAt(ppr0.getScaleQty(), ppr0.getScalePrice(), ppr0.getScaleType(), atQty);
                double pZTRA = priceAt(ztraKStar.getScaleQty(), ztraKStar.getScalePrice(), ztraKStar.getScaleType(), atQty);
                price[i] = pPPR0 + pZTRA;
            }
        }
        row.setPrice(price);
        row.setValidFrom(maxDate(ppr0.getValidFrom(), ztraKStar.getValidFrom()));
        row.setValidTo  (minDate(ppr0.getValidTo(),   ztraKStar.getValidTo()));

        String umPpr0 = ppr0.getConditionUnit();
        String umZtra = ztraKStar.getConditionUnit();
        boolean mismatch = umPpr0 != null && umZtra != null
                        && !umPpr0.trim().isEmpty() && !umZtra.trim().isEmpty()
                        && !umPpr0.equalsIgnoreCase(umZtra);
        row.setUnitMismatch(mismatch);
        if (mismatch)
            warnings.add("UM divergente: materiale " + ppr0.getMaterial()
                + " cliente " + custCode
                + " PPR0=" + umPpr0 + " ZTRA=" + umZtra);
        return row;
    }

    private ListinoRow buildZoneRow(String custCode, String zone,
                                    PricingRecord ztraK, PricingRecord ztraKStar,
                                    boolean isPreferred,
                                    double[] ztraQty, int activeCols) {
        ListinoRow row = new ListinoRow();
        row.setRowType(ListinoRow.RowType.ZONE);
        row.setCustomerCode(custCode);
        row.setDescription(zone);
        row.setCurrency(ztraK.getCurrency());
        row.setConditionQty(ztraK.getConditionQty());
        row.setConditionUnit(ztraK.getConditionUnit());
        row.setPreferredZone(isPreferred);
        row.setScaleQty(ztraQty);
        row.setActiveCols(activeCols);

        double[] delta = new double[5];
        for (int i = 0; i < activeCols; i++) {
            double atQty  = ztraQty[i];
            double pK     = priceAt(ztraK.getScaleQty(),     ztraK.getScalePrice(),     ztraK.getScaleType(),     atQty);
            double pKStar = priceAt(ztraKStar.getScaleQty(), ztraKStar.getScalePrice(), ztraKStar.getScaleType(), atQty);
            delta[i] = pK - pKStar;
        }
        row.setPrice(delta);
        row.setValidFrom(maxDate(ztraK.getValidFrom(), ztraKStar.getValidFrom()));
        row.setValidTo  (minDate(ztraK.getValidTo(),   ztraKStar.getValidTo()));
        return row;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

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
    private String nvl(String a, String b) {
        return (a != null && !a.trim().isEmpty()) ? a : (b != null ? b : "");
    }
}
