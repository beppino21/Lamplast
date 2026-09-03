package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Legge l'elenco dei codici ZIMBAL_1 (imballo — tipologia), solo record
 * attivi (non bozze) — usato per popolare la tendina "Imballo 1" nel popup
 * di attribuzione, e in futuro per la gestione "Parametri Imballo".
 */
public class Imbal1ReadClient {

    private static final String ENTITY_PATH =
        "/sap/opu/odata4/sap/zsb_zimbal_1/srvd/sap/zsd_zimbal_1/0001/ImballoParte1";

    public static class Item {
        public final String code;
        public final String descrIt;
        public final String descrEn;
        public Item(String code, String descrIt, String descrEn) {
            this.code = code; this.descrIt = descrIt; this.descrEn = descrEn;
        }
        public String getText(String language) {
            String d = "EN".equalsIgnoreCase(language) ? descrEn : descrIt;
            return (d != null && !d.isBlank()) ? d : code;
        }
    }

    private final S4HttpClient http;

    public Imbal1ReadClient(S4HttpClient http) { this.http = http; }

    public List<Item> fetchAll() throws IOException, InterruptedException {
        List<Item> result = new ArrayList<>();
        String path = ENTITY_PATH
            + "?$filter=" + S4HttpClient.encode("IsActiveEntity eq true")
            + "&$select=cod_imballo,descr_text_it,descr_text_en"
            + "&$orderby=cod_imballo";

        JsonNode root = http.getOData(path);
        JsonNode results = root.path("value");
        if (results.isArray()) {
            for (JsonNode n : results) {
                result.add(new Item(
                    n.path("cod_imballo").asText(""),
                    n.path("descr_text_it").asText(""),
                    n.path("descr_text_en").asText("")));
            }
        }
        return result;
    }
}
