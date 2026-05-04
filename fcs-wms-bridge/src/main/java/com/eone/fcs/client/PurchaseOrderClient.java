package com.eone.fcs.client;

import com.eone.fcs.config.AppConfig;
import com.eone.fcs.model.EketLine;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    // API pubblica
    // -------------------------------------------------------------------------

    /**
     * Recupera tutte le schedulazioni aperte e le arricchisce
     * con i dati di posizione e testata OdA.
     */
    public List<EketLine> fetchAllOpenScheduleLines() {
        return fetchScheduleLines(null, null);
    }

    /**
     * Recupera schedulazioni con filtro opzionale sulla data consegna.
     *
     * @param dateFrom    data consegna minima (null = nessun filtro)
     * @param singleEbeln OdA specifico (null = tutti)
     */
    public List<EketLine> fetchAllOpenScheduleLines(LocalDate dateFrom, String singleEbeln) {
        return fetchScheduleLines(dateFrom, singleEbeln);
    }

    /**
     * Recupera le schedulazioni di un singolo OdA.
     */
    public List<EketLine> fetchByPurchaseOrder(String ebeln) {
        return fetchScheduleLines(null, ebeln);
    }

    // -------------------------------------------------------------------------
    // Implementazione interna
    // -------------------------------------------------------------------------

    private List<EketLine> fetchScheduleLines(LocalDate dateFrom, String singleEbeln) {
        log.info("Avvio estrazione schedulazioni OdA{}{}",
                singleEbeln != null ? " per OdA: " + singleEbeln : " (tutti)",
                dateFrom    != null ? " da data: " + dateFrom    : "");

        // 1. Schedulazioni via API V4
        Map<String, Map<String, EketLine.Builder>> builders =
                fetchScheduleLinesV4(singleEbeln, dateFrom);
        log.info("Schedulazioni aperte recuperate: {} OdA con righe", builders.size());

        if (builders.isEmpty()) return List.of();

        // 2. Posizioni OdA via API V2
        fetchOrderItems(builders, singleEbeln);
        log.info("Posizioni OdA recuperate");

        // 3. Header OdA via API V2 (fornitore)
        fetchOrderHeaders(builders, singleEbeln);
        log.info("Header OdA recuperati");

        // 4. Build risultato
        List<EketLine> result = buildResult(builders);
        log.info("Estrazione completata: {} righe totali", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Schedulazioni - OData V4
    // -------------------------------------------------------------------------

    private Map<String, Map<String, EketLine.Builder>> fetchScheduleLinesV4(
            String singleEbeln, LocalDate dateFrom) {

        StringBuilder url = new StringBuilder(
                config.s4BaseUrl + SERVICE_PATH_V4 + "/PurchaseOrderScheduleLine" +
                "?$select=" + SELECT_SCHEDLINE_V4 +
                "&$top=" + config.s4PageSize);

        // Filtri OData (solo quelli consentiti da S/4HC V4)
        List<String> filters = new ArrayList<>();
        if (singleEbeln != null) {
            filters.add("PurchaseOrder eq '" + singleEbeln + "'");
        }
        if (dateFrom != null) {
            filters.add("ScheduleLineDeliveryDate ge " + dateFrom);
        }
        // NOTA: NON filtriamo OpenPurchaseOrderQuantity gt 0 qui —
        // S/4HC V4 non lo permette su campi quantità senza unità di misura.
        // Il filtro viene applicato in Java sotto.

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

            // Filtro in Java: salta schedulazioni completamente evase
            if (openQty != null && openQty <= 0) continue;

            // Quantità ricevuta = schedulata - aperta
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
    // Posizioni - OData V2
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
    // Header - OData V2
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
        // Se > 20 OdA carichiamo tutti e filtriamo in memoria

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
    // Build risultato
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
    // Helper data V4 (formato ISO: "2024-03-15")
    // -------------------------------------------------------------------------

    private LocalDate localDateV4(JsonNode node, String field) {
        String raw = str(node, field);
        if (raw == null) return null;
        try {
            // V4 restituisce date ISO: "2024-03-15"
            return LocalDate.parse(raw.substring(0, 10));
        } catch (Exception e) {
            // Fallback formato V2: /Date(ms)/
            return odataDate(node, field);
        }
    }
}
