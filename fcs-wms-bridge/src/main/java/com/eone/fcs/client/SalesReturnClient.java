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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Client per la lettura delle schedulazioni di OdV di reso (VBEP) da
 * SAP S/4HANA Cloud tramite API_CUSTOMER_RETURN_SRV (OData V2).
 *
 * Communication Scenario richiesto: SAP_COM_0157 (Customer Return Integration)
 *
 * I dati vengono trattati come EKET con kappl=config.kapplReso ('V')
 * e scritti nella stessa tabella tabfcseket.
 *
 * [DELTA] Aggiunto: fetchModifiedReturnsSince(OffsetDateTime)
 *   Stessa strategia di PurchaseOrderClient:
 *   1. Recupera VBELN modificati tramite LastChangeDateTime su A_CustomerReturn
 *   2. Per ciascun VBELN riestrare le schedulazioni (fetchByReturnOrder)
 *
 * Struttura chiamate (3 step):
 *   1. A_CustomerReturnScheduleLine → schedulazioni (etenr, wemng, mengeOpen)
 *   2. A_CustomerReturnItem         → posizioni (matnr, menge, werks, lgort, meins)
 *   3. A_CustomerReturn             → testata (eindt=RequestedDeliveryDate, lifnr=SoldToParty)
 *                                     + filtro per CustomerReturnType (config.salesOrderTypesReso)
 */
public class SalesReturnClient extends AbstractS4Client {

    private static final Logger log = LoggerFactory.getLogger(SalesReturnClient.class);

    private static final String SERVICE_PATH =
            "/sap/opu/odata/SAP/API_CUSTOMER_RETURN_SRV";

    private static final String SELECT_SCHEDLINE =
            "CustomerReturn,CustomerReturnItem,ScheduleLine," +
            "ConfdOrderQtyByMatlAvailCheck,OpenConfdDelivQtyInOrdQtyUnit," +
            "OrderQuantityUnit";

    private static final String SELECT_ITEM =
            "CustomerReturn,CustomerReturnItem,Material,ProductType,CustomerReturnItemText," +
            "ProductionPlant,StorageLocation,RequestedQuantity,RequestedQuantityUnit,Batch";

    // Aggiunto LastChangeDateTime per il delta
    private static final String SELECT_HEADER =
            "CustomerReturn,CustomerReturnType,SoldToParty,RequestedDeliveryDate,LastChangeDateTime";

    public SalesReturnClient(AppConfig config) {
        super(config);
    }

    // -------------------------------------------------------------------------
    // API pubblica - estrazione completa (invariata)
    // -------------------------------------------------------------------------

    public List<EketLine> fetchAllOpenReturnScheduleLines() {
        return fetchScheduleLines(null);
    }

    public List<EketLine> fetchByReturnOrder(String vbeln) {
        return fetchScheduleLines(vbeln);
    }

    // -------------------------------------------------------------------------
    // [DELTA] API pubblica - estrazione differenziale
    // -------------------------------------------------------------------------

    /**
     * Recupera le schedulazioni aperte degli OdV di reso modificati
     * dopo il timestamp indicato.
     *
     * Strategia in due step:
     * 1. Recupera i VBELN con LastChangeDateTime gt {since} su A_CustomerReturn
     *    (già filtrato per CustomerReturnType dalla configurazione).
     * 2. Per ciascun VBELN riestrare le schedulazioni via fetchByReturnOrder.
     *
     * @param since timestamp UTC di riferimento (esclusivo)
     * @return Map<VBELN, List<EketLine>> — può contenere liste vuote
     *         (OdV modificato ma senza schedulazioni aperte → pulizia righe)
     */
    public Map<String, List<EketLine>> fetchModifiedReturnsSince(OffsetDateTime since) {
        log.info("[delta-VBEP] Avvio ricerca OdV reso modificati dopo: {}", since);

        Set<String> modifiedReturns = fetchModifiedReturnNumbers(since);
        log.info("[delta-VBEP] OdV reso modificati trovati: {}", modifiedReturns.size());

        if (modifiedReturns.isEmpty()) {
            log.info("[delta-VBEP] Nessun OdV reso modificato rilevato.");
            return Map.of();
        }

        Map<String, List<EketLine>> result = new HashMap<>();
        int totalLines = 0;

        for (String vbeln : modifiedReturns) {
            log.debug("[delta-VBEP] Estrazione schedulazioni per OdV reso: {}", vbeln);
            List<EketLine> lines = fetchByReturnOrder(vbeln);
            result.put(vbeln, lines);
            totalLines += lines.size();

            if (lines.isEmpty()) {
                log.debug("[delta-VBEP] OdV {} senza schedulazioni aperte → righe residue verranno rimosse.", vbeln);
            }
        }

        log.info("[delta-VBEP] Estrazione delta completata: {} OdV, {} righe totali",
                 result.size(), totalLines);
        return result;
    }

    // -------------------------------------------------------------------------
    // Implementazione interna
    // -------------------------------------------------------------------------

    /**
     * Recupera i VBELN degli OdV di reso modificati dopo {since},
     * già filtrati per CustomerReturnType dalla configurazione.
     */
    private Set<String> fetchModifiedReturnNumbers(OffsetDateTime since) {
        // LastChangeDateTime su A_CustomerReturn è Edm.DateTimeOffset:
        // il filtro corretto è datetimeoffset'yyyy-MM-ddTHH:mm:ssZ' (non datetime'...')
        String formatted = since.atZoneSameInstant(ZoneOffset.UTC)
                               .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) + "Z";

        // Filtro: data modifica + tipi OdV di reso
        List<String> filters = new ArrayList<>();
        filters.add("LastChangeDateTime gt datetimeoffset'" + formatted + "'");

        if (!config.salesOrderTypesReso.isEmpty()) {
            String typeFilter = config.salesOrderTypesReso.stream()
                    .map(t -> "CustomerReturnType eq '" + t + "'")
                    .collect(Collectors.joining(" or "));
            filters.add("(" + typeFilter + ")");
        }

        String url = buildUrl(SERVICE_PATH, "A_CustomerReturn") +
                "?$select=" + enc("CustomerReturn,CustomerReturnType,LastChangeDateTime") +
                "&$filter=" + enc(String.join(" and ", filters)) +
                "&$top=" + config.s4PageSize;

        log.debug("[delta-VBEP] Filtro OData: {}", String.join(" and ", filters));

        List<JsonNode> nodes = fetchAllPages(url);
        Set<String> vbelns = nodes.stream()
                .map(n -> str(n, "CustomerReturn"))
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.toSet());

        log.debug("[delta-VBEP] VBELN modificati: {}", vbelns);
        return vbelns;
    }

    private List<EketLine> fetchScheduleLines(String singleVbeln) {
        log.info("Avvio estrazione schedulazioni OdV reso{} — tipi ammessi: {}",
                singleVbeln != null ? " per OdV: " + singleVbeln : " (tutti)",
                config.salesOrderTypesReso);

        Map<String, Map<String, EketLine.Builder>> builders =
                fetchScheduleLinesStep(singleVbeln);
        log.info("Schedulazioni OdV recuperate (pre-filtro): {} OdV con righe", builders.size());

        if (builders.isEmpty()) return List.of();

        fetchItemsStep(builders, singleVbeln);
        log.info("Posizioni OdV reso recuperate");

        fetchHeadersStep(builders, singleVbeln);
        log.info("Schedulazioni OdV reso dopo filtro tipo: {} OdV con righe", builders.size());

        List<EketLine> result = buildResult(builders);
        log.info("Estrazione OdV reso completata: {} righe totali", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Step 1: schedulazioni (invariato)
    // -------------------------------------------------------------------------

    private Map<String, Map<String, EketLine.Builder>> fetchScheduleLinesStep(
            String singleVbeln) {

        StringBuilder url = new StringBuilder(
                buildUrl(SERVICE_PATH, "A_CustomerReturnScheduleLine") +
                "?$select=" + enc(SELECT_SCHEDLINE) +
                "&$top=" + config.s4PageSize);

        List<String> filters = new ArrayList<>();
        if (singleVbeln != null) {
            filters.add("CustomerReturn eq '" + singleVbeln + "'");
        }

        if (!filters.isEmpty()) {
            url.append("&$filter=").append(enc(String.join(" and ", filters)));
        }

        List<JsonNode> nodes = fetchAllPages(url.toString());
        Map<String, Map<String, EketLine.Builder>> result = new HashMap<>();

        for (JsonNode n : nodes) {
            String vbeln  = str(n, "CustomerReturn",     "");
            String posnr  = str(n, "CustomerReturnItem", "");
            String etenr  = str(n, "ScheduleLine",       "");

            Double wemng   = dbl(n, "ConfdOrderQtyByMatlAvailCheck");
            Double openQty = dbl(n, "OpenConfdDelivQtyInOrdQtyUnit");

            if (openQty != null && openQty <= 0) continue;

            EketLine.Builder b = new EketLine.Builder()
                    .ebeln(vbeln)
                    .ebelp(padPosnr(posnr))
                    .etenr(padEtenr(etenr))
                    .wemng(wemng)
                    .mengeOpen(openQty)
                    .kappl(config.kapplReso);

            String key = posnr + "|" + etenr;
            result.computeIfAbsent(vbeln, k -> new HashMap<>()).put(key, b);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Step 2: posizioni (invariato)
    // -------------------------------------------------------------------------

    private void fetchItemsStep(
            Map<String, Map<String, EketLine.Builder>> builders,
            String singleVbeln) {

        StringBuilder url = new StringBuilder(
                buildUrl(SERVICE_PATH, "A_CustomerReturnItem") +
                "?$top=" + config.s4PageSize);

        if (singleVbeln != null) {
            url.append("&$filter=").append(
                    enc("CustomerReturn eq '" + singleVbeln + "'"));
        }

        List<JsonNode> nodes = fetchAllPages(url.toString());

        for (JsonNode n : nodes) {
            String vbeln = str(n, "CustomerReturn",     "");
            String posnr = str(n, "CustomerReturnItem", "");

            Map<String, EketLine.Builder> vbBuilders = builders.get(vbeln);
            if (vbBuilders == null) continue;

            Double menge = dbl(n, "RequestedQuantity");

            for (Map.Entry<String, EketLine.Builder> entry : vbBuilders.entrySet()) {
                if (entry.getKey().startsWith(posnr + "|")) {
                    entry.getValue()
                            .matnr(str(n, "Material"))
                            .mtart(str(n, "ProductType"))
                            .maktx(str(n, "CustomerReturnItemText"))
                            .werks(str(n, "ProductionPlant"))
                            .lgort(str(n, "StorageLocation"))
                            .menge(menge)
                            .meins(str(n, "RequestedQuantityUnit"))
                            .bstme(str(n, "RequestedQuantityUnit"))
                            .xchpf(str(n, "Batch") != null &&
                                   !str(n, "Batch").isBlank());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 3: testata (invariato)
    // -------------------------------------------------------------------------

    private void fetchHeadersStep(
            Map<String, Map<String, EketLine.Builder>> builders,
            String singleVbeln) {

        if (builders.isEmpty()) return;

        StringBuilder url = new StringBuilder(
                buildUrl(SERVICE_PATH, "A_CustomerReturn") +
                "?$select=" + enc(SELECT_HEADER) +
                "&$top=" + config.s4PageSize);

        List<String> filters = new ArrayList<>();

        if (singleVbeln != null) {
            filters.add("CustomerReturn eq '" + singleVbeln + "'");
        }

        if (!config.salesOrderTypesReso.isEmpty()) {
            String typeFilter = config.salesOrderTypesReso.stream()
                    .map(t -> "CustomerReturnType eq '" + t + "'")
                    .collect(Collectors.joining(" or "));
            filters.add("(" + typeFilter + ")");
        }

        if (!filters.isEmpty()) {
            url.append("&$filter=").append(enc(String.join(" and ", filters)));
        }

        List<JsonNode> nodes = fetchAllPages(url.toString());

        Set<String> vbelnsReso = new HashSet<>();

        for (JsonNode n : nodes) {
            String vbeln     = str(n, "CustomerReturn",     "");
            String orderType = str(n, "CustomerReturnType", "");
            String kunnr     = str(n, "SoldToParty");
            LocalDate eindt  = odataDate(n, "RequestedDeliveryDate");

            if (!config.salesOrderTypesReso.contains(orderType)) {
                log.debug("fetchHeadersStep: OdV={} tipo={} escluso", vbeln, orderType);
                continue;
            }

            vbelnsReso.add(vbeln);

            Map<String, EketLine.Builder> vbBuilders = builders.get(vbeln);
            if (vbBuilders == null) continue;

            for (EketLine.Builder b : vbBuilders.values()) {
                b.lifnr(kunnr).eindt(eindt);
            }
        }

        int prima = builders.size();
        builders.keySet().retainAll(vbelnsReso);
        int rimossi = prima - builders.size();
        if (rimossi > 0) {
            log.info("fetchHeadersStep: {} OdV esclusi (tipo non in lista resi {})",
                     rimossi, config.salesOrderTypesReso);
        }
    }

    // -------------------------------------------------------------------------
    // Build risultato (invariato)
    // -------------------------------------------------------------------------

    private List<EketLine> buildResult(
            Map<String, Map<String, EketLine.Builder>> builders) {
        List<EketLine> result = new ArrayList<>();
        for (Map<String, EketLine.Builder> vbMap : builders.values()) {
            for (EketLine.Builder b : vbMap.values()) {
                result.add(b.build());
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Utility zero-padding (invariato)
    // -------------------------------------------------------------------------

    private String padPosnr(String posnr) {
        if (posnr == null || posnr.isBlank()) return "000010";
        try { return String.format("%06d", Integer.parseInt(posnr.trim())); }
        catch (NumberFormatException e) { return posnr.trim(); }
    }

    private String padEtenr(String etenr) {
        if (etenr == null || etenr.isBlank()) return "0001";
        try { return String.format("%04d", Integer.parseInt(etenr.trim())); }
        catch (NumberFormatException e) { return etenr.trim(); }
    }
}
