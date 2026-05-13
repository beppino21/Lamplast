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
 * Client per la lettura delle schedulazioni di OdV di reso (VBEP) da
 * SAP S/4HANA Cloud tramite API_CUSTOMER_RETURN_SRV (OData V2).
 *
 * Communication Scenario richiesto: SAP_COM_0157 (Customer Return Integration)
 *
 * I dati vengono trattati come EKET con kappl=config.kapplReso ('V')
 * e scritti nella stessa tabella tabfcseket.
 *
 * Struttura chiamate (3 step):
 *   1. A_CustomerReturnScheduleLine → schedulazioni (etenr, wemng, mengeOpen)
 *   2. A_CustomerReturnItem         → posizioni (matnr, menge, werks, lgort, meins)
 *   3. A_CustomerReturn             → testata (eindt=RequestedDeliveryDate, lifnr=SoldToParty)
 *                                     + filtro per CustomerReturnType (config.salesOrderTypesReso)
 *
 * Mapping campi verificato sui $metadata reali del tenant my434383:
 *   CustomerReturn              → ebeln
 *   CustomerReturnItem          → ebelp   (6 cifre zero-padded)
 *   ScheduleLine                → etenr   (4 cifre zero-padded)
 *   RequestedDeliveryDate       → eindt   (dalla testata, uguale per tutte le righe del reso)
 *   RequestedQuantity           → menge   (dalla posizione)
 *   ConfdOrderQtyByMatlAvailCheck → wemng
 *   OpenConfdDelivQtyInOrdQtyUnit → mengeOpen
 *   RequestedQuantityUnit       → meins / bstme
 *   Material                    → matnr
 *   CustomerReturnItemText      → maktx
 *   ProductionPlant             → werks
 *   StorageLocation             → lgort
 *   SoldToParty                 → lifnr
 *   config.kapplReso            → kappl  ('V')
 *
 * Configurazione (da config.properties):
 *   reso.kappl              = V
 *   reso.delivery.type      = LR
 *   reso.sales.order.types  = CBRE,RE,ZRE
 */
public class SalesReturnClient extends AbstractS4Client {

    private static final Logger log = LoggerFactory.getLogger(SalesReturnClient.class);

    private static final String SERVICE_PATH =
            "/sap/opu/odata/SAP/API_CUSTOMER_RETURN_SRV";

    // Campi schedulazione verificati sui $metadata reali
    private static final String SELECT_SCHEDLINE =
            "CustomerReturn,CustomerReturnItem,ScheduleLine," +
            "ConfdOrderQtyByMatlAvailCheck,OpenConfdDelivQtyInOrdQtyUnit," +
            "OrderQuantityUnit";

    // Campi posizione verificati sui $metadata reali
    private static final String SELECT_ITEM =
            "CustomerReturn,CustomerReturnItem,Material,CustomerReturnItemText," +
            "ProductionPlant,StorageLocation,RequestedQuantity,RequestedQuantityUnit,Batch";

    // Campi testata verificati sui $metadata reali
    private static final String SELECT_HEADER =
            "CustomerReturn,CustomerReturnType,SoldToParty,RequestedDeliveryDate";

    public SalesReturnClient(AppConfig config) {
        super(config);
    }

    // -------------------------------------------------------------------------
    // API pubblica
    // -------------------------------------------------------------------------

    /**
     * Recupera tutte le schedulazioni aperte di OdV di reso.
     * Filtra per CustomerReturnType tramite config.salesOrderTypesReso.
     */
    public List<EketLine> fetchAllOpenReturnScheduleLines() {
        return fetchScheduleLines(null);
    }

    /**
     * Recupera le schedulazioni di un singolo OdV di reso.
     * Chiamato dal bridge in mode "vbep <VBELN>" dopo la registrazione del reso.
     *
     * @param vbeln numero OdV di reso (corrisponde a ebeln in tabfcseket)
     */
    public List<EketLine> fetchByReturnOrder(String vbeln) {
        return fetchScheduleLines(vbeln);
    }

    // -------------------------------------------------------------------------
    // Implementazione interna
    // -------------------------------------------------------------------------

    private List<EketLine> fetchScheduleLines(String singleVbeln) {
        log.info("Avvio estrazione schedulazioni OdV reso{} — tipi ammessi: {}",
                singleVbeln != null ? " per OdV: " + singleVbeln : " (tutti)",
                config.salesOrderTypesReso);

        // Step 1: schedulazioni
        Map<String, Map<String, EketLine.Builder>> builders =
                fetchScheduleLinesStep(singleVbeln);
        log.info("Schedulazioni OdV recuperate (pre-filtro): {} OdV con righe",
                builders.size());

        if (builders.isEmpty()) return List.of();

        // Step 2: posizioni (matnr, menge, werks, lgort, meins)
        fetchItemsStep(builders, singleVbeln);
        log.info("Posizioni OdV reso recuperate");

        // Step 3: testata (eindt, lifnr) + filtro per CustomerReturnType
        fetchHeadersStep(builders, singleVbeln);
        log.info("Schedulazioni OdV reso dopo filtro tipo: {} OdV con righe",
                builders.size());

        List<EketLine> result = buildResult(builders);
        log.info("Estrazione OdV reso completata: {} righe totali", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Step 1: schedulazioni — A_CustomerReturnScheduleLine
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
        // NOTA: filtro su OpenConfdDelivQtyInOrdQtyUnit rimosso —
        // SAP V2 non supporta filtri su campi quantità senza UdM.
        // Il controllo openQty <= 0 viene applicato in Java sotto.

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

            // Filtro in Java: salta schedulazioni completamente evase
            if (openQty != null && openQty <= 0) continue;

            EketLine.Builder b = new EketLine.Builder()
                    .ebeln(vbeln)
                    .ebelp(padPosnr(posnr))
                    .etenr(padEtenr(etenr))
                    .wemng(wemng)
                    .mengeOpen(openQty)
                    .kappl(config.kapplReso);

            // Chiave interna con posnr originale (non padded) per il join con Item
            String key = posnr + "|" + etenr;
            result.computeIfAbsent(vbeln, k -> new HashMap<>()).put(key, b);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Step 2: posizioni — A_CustomerReturnItem
    // -------------------------------------------------------------------------

    /**
     * Arricchisce i builder con i dati di posizione:
     * matnr, maktx, menge (quantità totale), werks, lgort, meins, xchpf.
     */
    private void fetchItemsStep(
            Map<String, Map<String, EketLine.Builder>> builders,
            String singleVbeln) {

        StringBuilder url = new StringBuilder(
                buildUrl(SERVICE_PATH, "A_CustomerReturnItem") +
                "?$select=" + enc(SELECT_ITEM) +
                "&$top=" + config.s4PageSize);

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
    // Step 3: testata — A_CustomerReturn
    // -------------------------------------------------------------------------

    /**
     * Arricchisce i builder con eindt (RequestedDeliveryDate) e lifnr (SoldToParty).
     * La data di reso è sulla testata e viene applicata a tutte le schedulazioni
     * dello stesso OdV — per i resi da cliente ha senso: una sola data attesa.
     *
     * Applica il filtro per CustomerReturnType:
     * gli OdV non presenti in config.salesOrderTypesReso vengono rimossi da builders.
     */
    private void fetchHeadersStep(
            Map<String, Map<String, EketLine.Builder>> builders,
            String singleVbeln) {

        if (builders.isEmpty()) return;

        StringBuilder url = new StringBuilder(
                buildUrl(SERVICE_PATH, "A_CustomerReturn") +
                "?$select=" + enc(SELECT_HEADER) +
                "&$top=" + config.s4PageSize);

        // Filtro OData: tipo reso + eventuale singolo OdV
        List<String> filters = new ArrayList<>();

        if (singleVbeln != null) {
            filters.add("CustomerReturn eq '" + singleVbeln + "'");
        }

        // Filtro dinamico per CustomerReturnType dalla config
        if (!config.salesOrderTypesReso.isEmpty()) {
            String typeFilter = config.salesOrderTypesReso.stream()
                    .map(t -> "CustomerReturnType eq '" + t + "'")
                    .reduce((a, b) -> a + " or " + b)
                    .orElse("");
            if (!typeFilter.isBlank()) {
                filters.add("(" + typeFilter + ")");
            }
        }

        if (!filters.isEmpty()) {
            url.append("&$filter=").append(enc(String.join(" and ", filters)));
        }

        List<JsonNode> nodes = fetchAllPages(url.toString());

        java.util.Set<String> vbelnsReso = new java.util.HashSet<>();

        for (JsonNode n : nodes) {
            String vbeln     = str(n, "CustomerReturn",     "");
            String orderType = str(n, "CustomerReturnType", "");
            String kunnr     = str(n, "SoldToParty");
            LocalDate eindt  = odataDate(n, "RequestedDeliveryDate");

            // Doppio controllo in Java
            if (!config.salesOrderTypesReso.contains(orderType)) {
                log.debug("fetchHeadersStep: OdV={} tipo={} escluso", vbeln, orderType);
                continue;
            }

            vbelnsReso.add(vbeln);

            Map<String, EketLine.Builder> vbBuilders = builders.get(vbeln);
            if (vbBuilders == null) continue;

            // eindt e lifnr uguali per tutte le schedulazioni del reso
            for (EketLine.Builder b : vbBuilders.values()) {
                b.lifnr(kunnr)
                 .eindt(eindt);
            }
        }

        // Rimuove da builders tutti gli OdV non-reso
        int prima = builders.size();
        builders.keySet().retainAll(vbelnsReso);
        int rimossi = prima - builders.size();
        if (rimossi > 0) {
            log.info("fetchHeadersStep: {} OdV esclusi (tipo non in lista resi {})",
                     rimossi, config.salesOrderTypesReso);
        }
    }

    // -------------------------------------------------------------------------
    // Build risultato
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
    // Utility zero-padding
    // -------------------------------------------------------------------------

    /** Posizione OdV (POSNR) — 6 cifre zero-padded. Es. "10" → "000010" */
    private String padPosnr(String posnr) {
        if (posnr == null || posnr.isBlank()) return "000010";
        try { return String.format("%06d", Integer.parseInt(posnr.trim())); }
        catch (NumberFormatException e) { return posnr.trim(); }
    }

    /** Numero schedulazione (ETENR) — 4 cifre zero-padded. Es. "1" → "0001" */
    private String padEtenr(String etenr) {
        if (etenr == null || etenr.isBlank()) return "0001";
        try { return String.format("%04d", Integer.parseInt(etenr.trim())); }
        catch (NumberFormatException e) { return etenr.trim(); }
    }
}
