package com.eone.fcs.client;

import com.eone.fcs.config.AppConfig;
import com.eone.fcs.model.Product;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public class ProductClient extends AbstractS4Client {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);

    private static final String SERVICE_PATH = "/sap/opu/odata/SAP/API_PRODUCT_SRV";

    public ProductClient(AppConfig config) {
        super(config);
    }

    // -------------------------------------------------------------------------
    // API pubblica
    // -------------------------------------------------------------------------

    public List<Product> fetchAllProducts() {
        log.info("Avvio estrazione prodotti (lingua: {})", config.s4Language);

        // 1. Dati base + pesi
        Map<String, Product.Builder> builders = fetchProductBase();
        log.info("Prodotti base recuperati: {}", builders.size());

        // 2. Descrizioni
        fetchProductDescriptions(builders);
        log.info("Descrizioni prodotti recuperate");

        // 3. Build
        List<Product> products = new ArrayList<>();
        for (Product.Builder b : builders.values()) {
            products.add(b.build());
        }

        log.info("Estrazione prodotti completata: {} prodotti totali", products.size());
        return products;
    }

    public Product fetchByMaterial(String matnr) {
        log.debug("Fetch prodotto singolo: {}", matnr);

        String url = buildUrl(SERVICE_PATH, "A_Product('" + matnr + "')") +
                "?$select=Product,ProductType,BaseUnit,PurchaseOrderQuantityUnit," +
                "ProductGroup,GrossWeight,NetWeight,WeightUnit";

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

    private Map<String, Product.Builder> fetchProductBase() {
        // GrossWeight, NetWeight, WeightUnit sono campi standard di A_Product
        String url = buildUrl(SERVICE_PATH, "A_Product") +
                "?$select=Product,ProductType,BaseUnit,PurchaseOrderQuantityUnit," +
                "ProductGroup,GrossWeight,NetWeight,WeightUnit" +
                "&$top=" + config.s4PageSize;

        List<JsonNode> nodes = fetchAllPages(url);
        Map<String, Product.Builder> builders = new HashMap<>();
        for (JsonNode n : nodes) {
            Product.Builder b = toProductBuilder(n);
            builders.put(b.getMatnr(), b);
        }
        return builders;
    }

    private void fetchProductDescriptions(Map<String, Product.Builder> builders) {
        String url = buildUrl(SERVICE_PATH, "A_ProductDescription") +
                "?$filter=" + enc("Language eq '" + config.s4Language + "'") +
                "&$select=Product,Language,ProductDescription" +
                "&$top=" + config.s4PageSize;

        List<JsonNode> nodes = fetchAllPages(url);
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
                .brgew(dbl(n, "GrossWeight"))   // peso lordo unitario
                .ntgew(dbl(n, "NetWeight"))      // peso netto unitario
                .gewei(str(n, "WeightUnit"));    // UM peso (es. KG)
    }
    // -------------------------------------------------------------------------
    // Lookup tipo materiale per lista di matnr (uso da PurchaseOrderClient / SalesReturnClient)
    // -------------------------------------------------------------------------

    /**
     * Recupera il tipo materiale (ProductType → mtart) per un insieme di matnr.
     *
     * Usa A_Product con $filter su Product in (...) e $select minimo.
     * Chiamata in batch da 50 matnr alla volta per rispettare i limiti URL OData V2.
     *
     * @param matnrs insieme di codici materiale
     * @return Map<matnr, mtart>
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