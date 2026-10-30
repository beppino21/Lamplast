package lamplast.mbxa.logic.sap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import lamplast.mbxa.logic.config.AppConfig;

/**
 * Descrizioni e pesi dei materiali da API_PRODUCT_SRV (SAP_COM_0009).
 * Resiliente: se una lettura fallisce si prosegue senza quel dato (descrizione o peso
 * restano vuoti e l'operatore li completa), e il motivo finisce nel log.
 */
public class ProductClient {

    private static final int CHUNK = 20;

    private final S4HttpClient m_http;
    private final String m_service;
    private final List<String> m_warnings = new ArrayList<>();

    /** Dati di un materiale. Pesi riferiti all'unità di misura di base. */
    public static class ProductInfo {
        public String description;
        public BigDecimal netWeight;
        public BigDecimal grossWeight;
        public String weightUnit;
    }

    public ProductClient(AppConfig cfg) {
        this(new S4HttpClient(cfg.getSapBaseUrl(), cfg.getSapProductUser(), cfg.getSapProductPassword(),
            cfg.getHttpTimeoutSeconds()), cfg.getProductServicePath());
    }

    public ProductClient(S4HttpClient http, String servicePath) {
        m_http = http;
        m_service = servicePath;
    }

    /** Avvisi dell'ultima lettura (letture fallite, descrizioni mancanti). */
    public List<String> getWarnings() { return m_warnings; }

    public Map<String, ProductInfo> fetch(Collection<String> materials, String language) {
        m_warnings.clear();
        Map<String, ProductInfo> result = new HashMap<>();
        List<String> list = new ArrayList<>(new java.util.LinkedHashSet<>(materials));
        for (String m : list) result.put(m, new ProductInfo());

        for (int i = 0; i < list.size(); i += CHUNK) {
            List<String> chunk = list.subList(i, Math.min(list.size(), i + CHUNK));
            readDescriptions(chunk, language, result);
            readWeights(chunk, result);
        }
        // seconda passata in inglese per i materiali senza descrizione nella lingua richiesta
        if (!"EN".equalsIgnoreCase(language)) {
            List<String> missing = new ArrayList<>();
            for (String m : list) if (result.get(m).description == null) missing.add(m);
            for (int i = 0; i < missing.size(); i += CHUNK) {
                readDescriptions(missing.subList(i, Math.min(missing.size(), i + CHUNK)), "EN", result);
            }
        }
        for (String m : list) {
            if (result.get(m).description == null) m_warnings.add("Descrizione non trovata per il materiale " + m);
        }
        return result;
    }

    private void readDescriptions(List<String> chunk, String language, Map<String, ProductInfo> result) {
        String filter = "Language eq " + S4HttpClient.lit(language) + " and (" + orProducts(chunk) + ")";
        String path = m_service + "/A_ProductDescription?$filter=" + S4HttpClient.enc(filter)
            + "&$select=Product,ProductDescription";
        try {
            for (JsonNode r : S4HttpClient.results(m_http.get(path))) {
                ProductInfo pi = result.get(S4HttpClient.text(r, "Product"));
                if (pi != null && pi.description == null) pi.description = S4HttpClient.text(r, "ProductDescription");
            }
        } catch (Exception e) {
            m_warnings.add("Lettura descrizioni materiale fallita: " + e.getMessage());
            log(e);
        }
    }

    private void readWeights(List<String> chunk, Map<String, ProductInfo> result) {
        String path = m_service + "/A_Product?$filter=" + S4HttpClient.enc(orProducts(chunk))
            + "&$select=Product,GrossWeight,NetWeight,WeightUnit";
        try {
            for (JsonNode r : S4HttpClient.results(m_http.get(path))) {
                ProductInfo pi = result.get(S4HttpClient.text(r, "Product"));
                if (pi == null) continue;
                pi.grossWeight = decimal(S4HttpClient.text(r, "GrossWeight"));
                pi.netWeight = decimal(S4HttpClient.text(r, "NetWeight"));
                pi.weightUnit = S4HttpClient.text(r, "WeightUnit");
            }
        } catch (Exception e) {
            m_warnings.add("Lettura pesi materiale fallita: " + e.getMessage());
            log(e);
        }
    }

    private static String orProducts(List<String> chunk) {
        StringBuilder sb = new StringBuilder();
        for (String m : chunk) {
            if (sb.length() > 0) sb.append(" or ");
            sb.append("Product eq ").append(S4HttpClient.lit(m));
        }
        return sb.toString();
    }

    private static BigDecimal decimal(String s) {
        if (s == null) return null;
        try {
            BigDecimal b = new BigDecimal(s);
            return b.signum() == 0 ? null : b;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void log(Exception e) {
        System.err.println("[MBXA] ProductClient: " + e.getMessage());
    }
}
