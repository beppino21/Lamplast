package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Scrittura CRUD per ZIMBAL_2 (ImballoParte2, draft-enabled) — usato dalla
 * gestione "Parametri Imballo 2". Stesso ciclo draft di Imbal1WriteClient,
 * con chiave cod_imballo2 (char10) al posto di cod_imballo.
 *
 * NOTA: stesse cautele di Imbal1WriteClient — nome azione/chiave draft non
 * riverificati via ADT per questo servizio specifico.
 */
public class Imbal2WriteClient {

    private static final String ENTITY_SET =
        "/sap/opu/odata4/sap/zsb_zimbal_2/srvd/sap/zsd_zimbal_2/0001/ImballoParte2";
    private static final String ACTION_NAMESPACE =
        "com.sap.gateway.srvd.zsd_zimbal_2.v0001.";

    private final S4HttpClient http;

    public Imbal2WriteClient(S4HttpClient http) { this.http = http; }

    /** Crea un nuovo codice ZIMBAL_2 con le due descrizioni IT/EN. */
    public void create(String codImballo2, String descrIt, String descrEn)
            throws IOException, InterruptedException {
        String body = "{"
            + "\"cod_imballo2\":\"" + esc(codImballo2) + "\","
            + "\"descr_text_it\":\"" + esc(nvl(descrIt)) + "\","
            + "\"descr_text_en\":\"" + esc(nvl(descrEn)) + "\""
            + "}";
        http.postOData(ENTITY_SET, body);
        String draftKeyPath = draftKeyPath(codImballo2);
        http.postAction(draftKeyPath, ACTION_NAMESPACE + "Activate", "{}");
    }

    /** Aggiorna le descrizioni IT/EN di un codice ZIMBAL_2 esistente. */
    public void update(String codImballo2, String descrIt, String descrEn)
            throws IOException, InterruptedException {
        String activeKeyPath = activeKeyPath(codImballo2);
        String draftKeyPath  = draftKeyPath(codImballo2);

        http.postAction(activeKeyPath, ACTION_NAMESPACE + "Edit", "{\"PreserveChanges\":false}");

        String body = "{"
            + "\"descr_text_it\":\"" + esc(nvl(descrIt)) + "\","
            + "\"descr_text_en\":\"" + esc(nvl(descrEn)) + "\""
            + "}";
        http.patchOData(draftKeyPath, body);
        http.postAction(draftKeyPath, ACTION_NAMESPACE + "Activate", "{}");
    }

    /** Elimina un codice ZIMBAL_2 (DELETE diretta sull'entità attiva). */
    public void delete(String codImballo2) throws IOException, InterruptedException {
        http.deleteOData(activeKeyPath(codImballo2));
    }

    private static String activeKeyPath(String codImballo2) {
        return ENTITY_SET + "(cod_imballo2='" + codImballo2 + "',IsActiveEntity=true)";
    }

    private static String draftKeyPath(String codImballo2) {
        return ENTITY_SET + "(cod_imballo2='" + codImballo2 + "',IsActiveEntity=false)";
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}