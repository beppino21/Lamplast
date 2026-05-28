package com.eone.fcs.client;

import com.eone.fcs.config.AppConfig;
import com.eone.fcs.model.EketLine;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Client per API_PURCHASEORDER_PROCESS_SRV (OData V2) e API V4.
 *
 * Schedulazioni: OData V4 (supporta $select e OpenPurchaseOrderQuantity)
 * Posizioni e Header: OData V2
 *
 * Tre chiamate separate + join in memoria:
 *   1. V4 PurchaseOrderScheduleLine → eindt, menge, mengeOpen
 *   2. V2 A_PurchaseOrderItem       → matnr, werks, lgort, meins, bstme, ...
 *   3. V2 A_PurchaseOrder           → lifnr (Supplier)
 *
 * NOTA: il filtro OpenPurchaseOrderQuantity gt 0 viene applicato in Java
 * perché S/4HC V4 non permette filtri su campi quantità senza unità di misura.
 *
 * Le posizioni con LOEKZ (cancellate) ed ELIKZ (consegna finale) vengono escluse
 * implicitamente: entrambi i flag azzerano OpenPurchaseOrderQuantity in S/4HANA,
 * quindi il filtro Java openQty <= 0 le scarta già a monte.
 *
 * [DELTA] Aggiunto: fetchModifiedOrdersSince(OffsetDateTime)
 *   Strategia: filtra gli OdA modificati dopo il timestamp via LastChangeDateTime
 *   sul header A_PurchaseOrder (V2), poi riestrae le schedulazioni solo
 *   per quegli OdA. Se le schedulazioni risultano tutte evase, le righe
 *   vengono rimosse da tabfcseket tramite syncEketLinesForOrder (chiamante).
 */
public class PurchaseOrderClient extends AbstractS4Client {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderClient.class);

    // OData V2 - header e posizioni
    private static final String SERVICE_PATH_V2 =
            "/sap/opu/odata/SAP/API_PURCHASEORDER_PROCESS_SRV";

    // OData V4 - schedulazioni
    private static final String SERVICE_PATH_V4 =
            "/sap/opu/odata4/sap/api_purchaseorder_2/srvd_a2x/sap/purchaseorder/0001";

    // Campi V4 schedulazioni
    private static final String SELECT_SCHEDLINE_V4 =
            "PurchaseOrder,PurchaseOrderItem,ScheduleLine," +
            "ScheduleLineDeliveryDate,ScheduleLineOrderQuantity," +
            "OpenPurchaseOrderQuantity,PurchaseOrderQuantityUnit";

    public PurchaseOrderClient(AppConfig config) {
        super(config);
    }

    // -------------------------------------------------------------------------
    // API pubblica - estrazione completa (invariata)
    // -------------------------------------------------------------------------

    public List<EketLine> fetchAllOpenScheduleLines() {
        return fetchScheduleLines(null, null);
    }

    public List<EketLine> fetchAllOpenScheduleLines(LocalDate dateFrom, String singleEbeln) {
        return fetchScheduleLines(dateFrom, singleEbeln);
    }

    public List<EketLine> fetchByPurchaseOrder(String ebeln) {
        return fetchScheduleLines(null, ebeln);
    }

    // -------------------------------------------------------------------------
    // [DELTA] API pubblica - estrazione differenziale
    // -------------------------------------------------------------------------

    /**
     * Recupera le schedulazioni aperte degli OdA modificati dopo il timestamp indicato.
     *
     * Strategia in due step:
     * 1. Recupera la lista di EBELN degli OdA con LastChangeDateTime gt {since}
     *    tramite A_PurchaseOrder (V2) — campo disponibile e affidabile.
     * 2. Per ciascun EBELN trovato, riestrae le schedulazioni via V4
     *    (stesso meccanismo della modalità puntuale fetchByPurchaseOrder).
     *
     * Nota: il chiamante (Main.delta) deve poi invocare syncEketLinesForOrder
     * per ciascun EBELN per aggiornare tabfcseket (delete+insert per OdA).
     * Questo garantisce che:
     *   - Le schedulazioni evase vengano rimosse
     *   - Le schedulazioni in lavorazione (wmsst 1/2/E) non vengano toccate
     *
     * @param since timestamp UTC di riferimento (esclusivo)
     * @return Map<EBELN, List<EketLine>> — può contenere liste vuote
     *         (OdA modificato ma senza schedulazioni aperte → pulizia righe)
     */
    public Map<String, List<EketLine>> fetchModifiedOrdersSince(OffsetDateTime since) {
        log.info("[delta-EKET] Avvio ricerca OdA modificati dopo: {}", since);

        // Step 1: trova gli EBELN modificati
        Set<String> modifiedOrders = fetchModifiedOrderNumbers(since);
        log.info("[delta-EKET] OdA modificati trovati: {}", modifiedOrders.size());

        if (modifiedOrders.isEmpty()) {
            log.info("[delta-EKET] Nessun OdA modificato rilevato.");
            return Map.of();
        }

        // Step 2: per ciascun EBELN recupera le schedulazioni aperte
        Map<String, List<EketLine>> result = new HashMap<>();
        int totalLines = 0;

        for (String ebeln : modifiedOrders) {
            log.debug("[delta-EKET] Estrazione schedulazioni per OdA: {}", ebeln);
            List<EketLine> lines = fetchByPurchaseOrder(ebeln);
            result.put(ebeln, lines);
            totalLines += lines.size();

            if (lines.isEmpty()) {
                log.debug("[delta-EKET] OdA {} senza schedulazioni aperte → le righe residue in tabfcseket verranno rimosse.", ebeln);
            }
        }

        log.info("[delta-EKET] Estrazione delta completata: {} OdA, {} righe totali",
                 result.size(), totalLines);
        return result;
    }

    // -------------------------------------------------------------------------
    // Implementazione interna
    // -------------------------------------------------------------------------

    /**
     * Recupera la lista degli EBELN degli OdA con LastChangeDateTime > since.
     * Usa A_PurchaseOrder (V2) che espone LastChangeDateTime sull'header.
     */
    private Set<String> fetchModifiedOrderNumbers(OffsetDateTime since) {
        // LastChangeDateTime su A_PurchaseOrder è Edm.DateTimeOffset:
        // il filtro corretto è datetimeoffset'yyyy-MM-ddTHH:mm:ssZ' (non datetime'...')
        String formatted = since.atZoneSameInstant(ZoneOffset.UTC)
                               .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) + "Z";

        String url = buildUrl(SERVICE_PATH_V2, "A_PurchaseOrder") +
                "?$select=" + enc("PurchaseOrder,LastChangeDateTime") +
                "&$filter=" + enc("LastChangeDateTime gt datetimeoffset'" + formatted + "'") +
                "&$top=" + config.s4PageSize;

        log.debug("[delta-EKET] Filtro OData header: LastChangeDateTime gt datetimeoffset'{}'", formatted);

        List<JsonNode> nodes = fetchAllPages(url);
        Set<String> orders = nodes.stream()
                .map(n -> str(n, "PurchaseOrder"))
                .filter(po -> po != null && !po.isBlank())
                .collect(Collectors.toSet());

        log.debug("[delta-EKET] EBELN modificati: {}", orders);
        return orders;
    }

    private List<EketLine> fetchScheduleLines(LocalDate dateFrom, String singleEbeln) {
        log.info("Avvio estrazione schedulazioni OdA{}{}",
                singleEbeln != null ? " per OdA: " + singleEbeln : " (tutti)",
                dateFrom    != null ? " da data: " + dateFrom    : "");
        log.debug("Filtri attivi: OpenQty>0, LOEKZ=false, ELIKZ=false");

        Map<String, Map<String, EketLine.Builder>> builders =
                fetchScheduleLinesV4(singleEbeln, dateFrom);
        log.info("Schedulazioni aperte recuperate: {} OdA con righe", builders.size());

        if (builders.isEmpty()) return List.of();

        fetchOrderItems(builders, singleEbeln);
        log.info("Posizioni OdA recuperate");

        fetchOrderHeaders(builders, singleEbeln);
        log.info("Header OdA recuperati");

        List<EketLine> result = buildResult(builders);
        log.info("Estrazione completata: {} righe totali", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Schedulazioni - OData V4 (invariato)
    // -------------------------------------------------------------------------

    private Map<String, Map<String, EketLine.Builder>> fetchScheduleLinesV4(
            String singleEbeln, LocalDate dateFrom) {

        StringBuilder url = new StringBuilder(
                config.s4BaseUrl + SERVICE_PATH_V4 + "/PurchaseOrderScheduleLine" +
                "?$select=" + SELECT_SCHEDLINE_V4 +
                "&$top=" + config.s4PageSize);

        List<String> filters = new ArrayList<>();
        if (singleEbeln != null) {
            filters.add("PurchaseOrder eq '" + singleEbeln + "'");
        }
        if (dateFrom != null) {
            filters.add("ScheduleLineDeliveryDate ge " + dateFrom);
        }

        if (!filters.isEmpty()) {
            url.append("&$filter=").append(enc(String.join(" and ", filters)));
        }

        List<JsonNode> nodes = fetchAllPagesV4(url.toString());

        Map<String, Map<String, EketLine.Builder>> result = new HashMap<>();

        for (JsonNode n : nodes) {
            String po    = str(n, "PurchaseOrder",     "");
            String item  = str(n, "PurchaseOrderItem", "");
            String sched = str(n, "ScheduleLine",      "");

            Double menge   = dbl(n, "ScheduleLineOrderQuantity");
            Double openQty = dbl(n, "OpenPurchaseOrderQuantity");

            if (openQty != null && openQty <= 0) continue;

            Double wemng = (menge != null && openQty != null)
                           ? menge - openQty : null;

            EketLine.Builder b = new EketLine.Builder()
                    .ebeln(po)
                    .ebelp(item)
                    .etenr(sched)
                    .eindt(localDateV4(n, "ScheduleLineDeliveryDate"))
                    .menge(menge)
                    .wemng(wemng)
                    .mengeOpen(openQty)
                    .meins(str(n, "PurchaseOrderQuantityUnit"));

            result.computeIfAbsent(po, k -> new HashMap<>())
                  .put(item + "|" + sched, b);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Posizioni - OData V2 (invariato)
    // -------------------------------------------------------------------------

    private void fetchOrderItems(
            Map<String, Map<String, EketLine.Builder>> builders, String singleEbeln) {

        StringBuilder url = new StringBuilder(
                buildUrl(SERVICE_PATH_V2, "A_PurchaseOrderItem") +
                "?$top=" + config.s4PageSize);

        if (singleEbeln != null) {
            url.append("&$filter=").append(enc("PurchaseOrder eq '" + singleEbeln + "'"));
        }

        List<JsonNode> nodes = fetchAllPages(url.toString());

        for (JsonNode n : nodes) {
            String po   = str(n, "PurchaseOrder",    "");
            String item = str(n, "PurchaseOrderItem","");

            Map<String, EketLine.Builder> poBuilders = builders.get(po);
            if (poBuilders == null) continue;

            for (Map.Entry<String, EketLine.Builder> entry : poBuilders.entrySet()) {
                if (entry.getKey().startsWith(item + "|")) {
                    entry.getValue()
                            .matnr(str(n, "Material"))
                            .mtart(str(n, "MaterialType"))
                            .maktx(str(n, "PurchaseOrderItemText"))
                            .werks(str(n, "Plant"))
                            .lgort(str(n, "StorageLocation"))
                            .bstme(str(n, "PurchaseOrderQuantityUnit"))
                            .xchpf("X".equals(str(n, "BatchManagementRequirement")));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Header - OData V2 (invariato)
    // -------------------------------------------------------------------------

    private void fetchOrderHeaders(
            Map<String, Map<String, EketLine.Builder>> builders, String singleEbeln) {

        List<String> orders = new ArrayList<>(builders.keySet());
        if (orders.isEmpty()) return;

        StringBuilder url = new StringBuilder(
                buildUrl(SERVICE_PATH_V2, "A_PurchaseOrder") +
                "?$top=" + config.s4PageSize);

        if (singleEbeln != null) {
            url.append("&$filter=").append(enc("PurchaseOrder eq '" + singleEbeln + "'"));
        } else if (orders.size() <= 20) {
            StringBuilder filter = new StringBuilder();
            for (int i = 0; i < orders.size(); i++) {
                if (i > 0) filter.append(" or ");
                filter.append("PurchaseOrder eq '").append(orders.get(i)).append("'");
            }
            url.append("&$filter=").append(enc(filter.toString()));
        }

        List<JsonNode> nodes = fetchAllPages(url.toString());

        for (JsonNode n : nodes) {
            String po       = str(n, "PurchaseOrder", "");
            String supplier = str(n, "Supplier");

            Map<String, EketLine.Builder> poBuilders = builders.get(po);
            if (poBuilders == null) continue;

            for (EketLine.Builder b : poBuilders.values()) {
                b.lifnr(supplier);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Build risultato (invariato)
    // -------------------------------------------------------------------------

    private List<EketLine> buildResult(Map<String, Map<String, EketLine.Builder>> builders) {
        List<EketLine> result = new ArrayList<>();
        for (Map<String, EketLine.Builder> poMap : builders.values()) {
            for (EketLine.Builder b : poMap.values()) {
                result.add(b.build());
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helper data V4 (invariato)
    // -------------------------------------------------------------------------

    private LocalDate localDateV4(JsonNode node, String field) {
        String raw = str(node, field);
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (Exception e) {
            return odataDate(node, field);
        }
    }
}
