package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Legge, per codice condizione di pagamento + lingua, il testo descrittivo
 * (CDS standard I_PaymentTermsConditionsText) tramite un servizio OData V4
 * custom (ZZ_PAYMENTTERMSTEXT_SRV / ZZ_PAYMENTTERMSTEXT_BND) — creato ad hoc
 * perché nessuna API standard rilasciata espone questo testo su questo tenant,
 * e il campo PaymentTermsConditionDesc non era selezionabile nel tool
 * key-user "Custom CDS Views" (probabile esclusione dei campi @Semantics.text).
 *
 * IMPORTANTE — OData V4: la busta di risposta è {"value": [...]}, diversa da
 * quella V2 {"d": {"results": [...]}} usata da tutti gli altri client di
 * questo progetto.
 */
public class PaymentTermsTextClient {

    // Percorso relativo del servizio custom OData V4 (host preso da S4Config,
    // come per tutti gli altri client). Il servizio è stato creato e
    // pubblicato sul mandante di sviluppo (080); da verificare/aggiornare
    // dopo il trasporto sul sistema/mandante effettivo se il technical name
    // del binding dovesse differire.
    private static final String ENTITY_PATH =
        "/sap/opu/odata4/sap/zz_paymenttermstext_bnd/srvd_a2x/sap/zz_paymenttermstext_srv/0001/PaymentTermsText";

    private final S4HttpClient http;

    // Se il servizio custom non è (ancora) raggiungibile — non trasportato,
    // non pubblicato sul mandante giusto, problemi di autorizzazione — si
    // disattiva senza bloccare l'estrazione: il codice condizione di
    // pagamento resta comunque stampato da solo, senza testo descrittivo.
    private volatile boolean available = true;

    public PaymentTermsTextClient(S4HttpClient http) { this.http = http; }

    /**
     * @param codes       codici condizione di pagamento da tradurre (es. "C073")
     * @param sapLanguage lingua in formato SAP a 1 carattere (es. "I" italiano, "E" inglese)
     * @return mappa codice → testo descrittivo, solo per i codici trovati
     */
    public Map<String, String> fetchDescriptions(Set<String> codes, String sapLanguage) {
        Map<String, String> result = new HashMap<>();
        if (!available || codes == null || codes.isEmpty()
                || sapLanguage == null || sapLanguage.isBlank()) return result;

        StringBuilder filter = new StringBuilder();
        filter.append("Language eq '").append(sapLanguage).append("' and (");
        boolean first = true;
        for (String code : codes) {
            if (!first) filter.append(" or ");
            filter.append("PaymentTerms eq '").append(code).append("'");
            first = false;
        }
        filter.append(")");

        String path = ENTITY_PATH + "?$filter=" + S4HttpClient.encode(filter.toString())
            + "&$select=PaymentTerms,Language,PaymentTermsConditionDesc";

        try {
            JsonNode root = http.getOData(path);
            // OData V4: la lista è in "value", non in "d.results" come nei client V2
            JsonNode results = root.path("value");
            if (results.isArray()) {
                for (JsonNode n : results) {
                    String code = n.path("PaymentTerms").asText(null);
                    String desc = n.path("PaymentTermsConditionDesc").asText(null);
                    if (code != null && !code.isBlank() && desc != null && !desc.isBlank()) {
                        result.put(code.strip(), desc.strip());
                    }
                }
            }
            System.out.println("PaymentTermsTextClient: trovate " + result.size()
                + "/" + codes.size() + " descrizioni (lingua SAP '" + sapLanguage + "')");
        } catch (IOException | InterruptedException e) {
            available = false;
            System.err.println("PaymentTermsTextClient: servizio custom ZZ_PAYMENTTERMSTEXT_SRV "
                + "non raggiungibile — disattivato per il resto dell'estrazione (testo condizioni "
                + "di pagamento non stampato). Dettaglio: " + e.getMessage());
        }
        return result;
    }
}
