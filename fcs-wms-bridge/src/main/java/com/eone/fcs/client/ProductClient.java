package com.eone.fcs.client;

import com.eone.fcs.config.AppConfig;
import com.eone.fcs.model.Product;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client per API_PRODUCT_SRV (OData V2).
 *
 * Legge:
 *   - A_Product            → matnr, mtart, meins, bstme, matkl, brgew, ntgew, gewei
 *   - A_ProductDescription → maktx (filtro lingua configurata)
 *
 * Chiamate separate + join in memoria su matnr.
 *
 * [DELTA] Aggiunto: fetchModifiedSince(OffsetDateTime)
 *   Usa il campo LastChangeDate di A_Product (precisione al giorno) per recuperare
 *   solo i prodotti modificati dopo il timestamp indicato.
 *   LastChangeDateTime non è filtrabile su questo tenant S/4HC Public Edition.
 */
public class ProductClient extends AbstractS4Client {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);

    private static final String SERVICE_PATH = "/sap/opu/odata/SAP/API_PRODUCT_SRV";

    // LastChangeDate (solo data, senza orario) è il campo disponibile
    // su A_Product in S/4HC Public Edition per il filtro delta.
    // LastChangeDateTime non è filtrabile in questo tenant (HTTP 400).
    private static final String SELECT_PRODUCT =
            "Product,ProductType,BaseUnit,PurchaseOrderQuantityUnit," +
            "ProductGroup,GrossWeight,NetWeight,WeightUnit,LastChangeDate";

    public ProductClient(AppConfig config) {
        super(config);
    }

    // -------------------------------------------------------------------------
    // API pubblica - estrazione completa (invariata)
    // -------------------------------------------------------------------------

    public List<Product> fetchAllProducts() {
        log.info("Avvio estrazione prodotti (lingua: {})", config.s4Language);

        Map<String, Product.Builder> builders = fetchProductBase(null);
        log.info("Prodotti base recuperati: {}", builders.size());

        fetchProductDescriptions(builders, null);
        log.info("Descrizioni prodotti recuperate");

        List<Product> products = new ArrayList<>();
        for (Product.Builder b : builders.values()) {
            products.add(b.build());
        }

        log.info("Estrazione prodotti completata: {} prodotti totali", products.size());
        return products;
    }

    // -------------------------------------------------------------------------
    // [DELTA] API pubblica - estrazione differenziale
    // -------------------------------------------------------------------------

    /**
     * Recupera solo i prodotti modificati dopo il timestamp indicato.
     *
     * Usa il filtro OData: LastChangeDate gt datetime'yyyy-MM-ddT00:00:00'
     * (LastChangeDateTime non è filtrabile su questo tenant S/4HC Public Edition).
     * Precisione al giorno: vengono estratti tutti i prodotti modificati
     * nel giorno del last_sync e nei giorni successivi.
     *
     * @param since timestamp UTC di riferimento (esclusivo: gt, non ge)
     * @return lista di prodotti modificati dopo il timestamp
     */
    public List<Product> fetchModifiedSince(OffsetDateTime since) {
        log.info("[delta-MARA] Avvio estrazione prodotti modificati dopo: {}", since);

        Map<String, Product.Builder> builders = fetchProductBase(since);
        log.info("[delta-MARA] Prodotti modificati trovati: {}", builders.size());

        if (builders.isEmpty()) {
            log.info("[delta-MARA] Nessuna modifica rilevata.");
            return List.of();
        }

        // Le descrizioni non hanno un campo LastChangeDateTime proprio:
        // le recuperiamo per i soli matnr modificati (join in memoria).
        fetchProductDescriptions(builders, builders.keySet());
        log.info("[delta-MARA] Descrizioni recuperate per {} prodotti", builders.size());

        List<Product> products = new ArrayList<>();
        for (Product.Builder b : builders.values()) {
            products.add(b.build());
        }

        log.info("[delta-MARA] Estrazione delta completata: {} prodotti", products.size());
        return products;
    }

    // -------------------------------------------------------------------------
    // Fetch singolo (invariato)
    // -------------------------------------------------------------------------

    public Product fetchByMaterial(String matnr) {
        log.debug("Fetch prodotto singolo: {}", matnr);

        String url = buildUrl(SERVICE_PATH, "A_Product('" + matnr + "')") +
                "?$select=" + SELECT_PRODUCT;

        List<JsonNode> nodes = fetchSinglePage(url);
        if (nodes.isEmpty()) return null;

        Product.Builder b = toProductBuilder(nodes.get(0));

        String descUrl = buildUrl(SERVICE_PATH, "A_ProductDescription") +
                "?$filter=" + enc("Product eq '" + matnr + "' and Language eq '" + config.s4Language + "'") +
                "&$select=Product,Language,ProductDescription&$top=1";
        List<JsonNode> descNodes = fetchSinglePage(descUrl);
        if (!descNodes.isEmpty()) {
            b.maktx(str(descNodes.get(0), "ProductDescription"));
        }

        return b.build();
    }

    // -------------------------------------------------------------------------
    // Implementazione interna
    // -------------------------------------------------------------------------

    /**
     * Recupera i dati base dei prodotti.
     *
     * @param since se non null, aggiunge il filtro LastChangeDateTime gt ...
     *              per il fetch delta; se null, recupera tutti i prodotti.
     */
    private Map<String, Product.Builder> fetchProductBase(OffsetDateTime since) {
        StringBuilder url = new StringBuilder(
                buildUrl(SERVICE_PATH, "A_Product") +
                "?$select=" + SELECT_PRODUCT +
                "&$top=" + config.s4PageSize);

        if (since != null) {
            // LastChangeDate ha precisione al giorno — formato datetime'yyyy-MM-ddT00:00:00'
            String formatted = since.atZoneSameInstant(java.time.ZoneOffset.UTC)
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00";
            url.append("&$filter=").append(enc("LastChangeDate gt datetime'" + formatted + "'"));
            log.debug("[delta-MARA] Filtro OData: LastChangeDate gt datetime'{}'", formatted);
        }

        List<JsonNode> nodes = fetchAllPages(url.toString());
        Map<String, Product.Builder> builders = new HashMap<>();
        for (JsonNode n : nodes) {
            Product.Builder b = toProductBuilder(n);
            builders.put(b.getMatnr(), b);
        }
        return builders;
    }

    /**
     * Recupera le descrizioni nella lingua configurata.
     *
     * @param builders  mappa matnr → builder da arricchire
     * @param matnrSet  se non null, filtra solo i matnr specificati (delta);
     *                  se null, recupera tutte le descrizioni (full load)
     */
    private void fetchProductDescriptions(Map<String, Product.Builder> builders,
                                          java.util.Set<String> matnrSet) {
        if (matnrSet != null && matnrSet.isEmpty()) return;

        if (matnrSet == null) {
            // Full load: tutte le descrizioni nella lingua configurata
            String url = buildUrl(SERVICE_PATH, "A_ProductDescription") +
                    "?$filter=" + enc("Language eq '" + config.s4Language + "'") +
                    "&$select=Product,Language,ProductDescription" +
                    "&$top=" + config.s4PageSize;
            List<JsonNode> nodes = fetchAllPages(url);
            applyDescriptions(nodes, builders);
        } else {
            // Delta: descrizioni solo per i matnr modificati (batch da 50)
            List<String> list = new ArrayList<>(matnrSet);
            int batchSize = 50;
            for (int i = 0; i < list.size(); i += batchSize) {
                List<String> batch = list.subList(i, Math.min(i + batchSize, list.size()));
                StringBuilder filter = new StringBuilder("Language eq '" + config.s4Language + "'");
                filter.append(" and (");
                for (int j = 0; j < batch.size(); j++) {
                    if (j > 0) filter.append(" or ");
                    filter.append("Product eq '").append(batch.get(j)).append("'");
                }
                filter.append(")");

                String url = buildUrl(SERVICE_PATH, "A_ProductDescription") +
                        "?$filter=" + enc(filter.toString()) +
                        "&$select=Product,Language,ProductDescription" +
                        "&$top=" + batchSize;
                List<JsonNode> nodes = fetchAllPages(url);
                applyDescriptions(nodes, builders);
            }
        }
    }

    private void applyDescriptions(List<JsonNode> nodes, Map<String, Product.Builder> builders) {
        for (JsonNode n : nodes) {
            String matnr = str(n, "Product");
            if (matnr == null) continue;
            Product.Builder b = builders.get(matnr);
            if (b != null) {
                b.maktx(str(n, "ProductDescription"));
            }
        }
    }

    private Product.Builder toProductBuilder(JsonNode n) {
        return new Product.Builder()
                .matnr(str(n, "Product", ""))
                .mtart(str(n, "ProductType"))
                .meins(str(n, "BaseUnit"))
                .bstme(str(n, "PurchaseOrderQuantityUnit"))
                .matkl(str(n, "ProductGroup"))
                .brgew(dbl(n, "GrossWeight"))
                .ntgew(dbl(n, "NetWeight"))
                .gewei(str(n, "WeightUnit"));
    }

    // -------------------------------------------------------------------------
    // Lookup tipo materiale per lista di matnr (invariato)
    // -------------------------------------------------------------------------

    /**
     * Recupera il tipo materiale (ProductType → mtart) per un insieme di matnr.
     * Chiamata in batch da 50 matnr alla volta per rispettare i limiti URL OData V2.
     */
    public Map<String, String> fetchMaterialTypes(java.util.Set<String> matnrs) {
        if (matnrs == null || matnrs.isEmpty()) return java.util.Map.of();

        Map<String, String> result = new HashMap<>();
        List<String> list = new ArrayList<>(matnrs);
        int batchSize = 50;

        for (int i = 0; i < list.size(); i += batchSize) {
            List<String> batch = list.subList(i, Math.min(i + batchSize, list.size()));

            StringBuilder filter = new StringBuilder();
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0) filter.append(" or ");
                filter.append("Product eq '").append(batch.get(j)).append("'");
            }

            String url = buildUrl(SERVICE_PATH, "A_Product") +
                    "?$select=" + enc("Product,ProductType") +
                    "&$filter=" + enc(filter.toString()) +
                    "&$top=" + batchSize;

            List<JsonNode> nodes = fetchAllPages(url);
            for (JsonNode n : nodes) {
                String matnr = str(n, "Product");
                String mtart = str(n, "ProductType");
                if (matnr != null && mtart != null) {
                    result.put(matnr.strip(), mtart.strip());
                }
            }
        }

        log.info("Tipi materiale recuperati: {} su {} richiesti", result.size(), matnrs.size());
        return result;
    }
}
