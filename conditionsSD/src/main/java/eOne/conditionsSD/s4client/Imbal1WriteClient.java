package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Scrittura CRUD per ZIMBAL_1 (ImballoParte1, draft-enabled) — usato dalla
 * gestione "Parametri Imballo 1".
 *
 * Stesso ciclo draft già collaudato per ZIMBAL_3 (Imbal3WriteClient):
 *   - create: POST sull'entity set (crea una bozza) + azione Activate
 *   - update: azione Edit sull'entità attiva (crea/riapre la bozza) + PATCH
 *             sulla bozza + azione Activate
 *   - delete: DELETE diretta sull'entità attiva (nessun passaggio da bozza)
 *
 * NOTA: nome dell'azione e formato della chiave IsActiveEntity dedotti dal
 * path noto (Imbal1ReadClient) e dall'esempio già documentato nel Javadoc di
 * S4HttpClient.postAction per questo stesso servizio — non riverificati via
 * ADT. Testare su un record di prova prima dell'uso con dati reali (stesso
 * approccio prudente già seguito per ZIMBAL_3).
 */
public class Imbal1WriteClient {

    private static final String ENTITY_SET =
        "/sap/opu/odata4/sap/zsb_zimbal_1/srvd/sap/zsd_zimbal_1/0001/ImballoParte1";
    private static final String ACTION_NAMESPACE =
        "com.sap.gateway.srvd.zsd_zimbal_1.v0001.";

    private final S4HttpClient http;

    public Imbal1WriteClient(S4HttpClient http) { this.http = http; }

    /** Crea un nuovo codice ZIMBAL_1 con le due descrizioni IT/EN. */
    public void create(String codImballo, String descrIt, String descrEn)
            throws IOException, InterruptedException {
        String body = "{"
            + "\"cod_imballo\":\"" + esc(codImballo) + "\","
            + "\"descr_text_it\":\"" + esc(nvl(descrIt)) + "\","
            + "\"descr_text_en\":\"" + esc(nvl(descrEn)) + "\""
            + "}";
        http.postOData(ENTITY_SET, body);
        String draftKeyPath = draftKeyPath(codImballo);
        http.postAction(draftKeyPath, ACTION_NAMESPACE + "Activate", "{}");
    }

    /** Aggiorna le descrizioni IT/EN di un codice ZIMBAL_1 esistente. */
    public void update(String codImballo, String descrIt, String descrEn)
            throws IOException, InterruptedException {
        String activeKeyPath = activeKeyPath(codImballo);
        String draftKeyPath  = draftKeyPath(codImballo);

        http.postAction(activeKeyPath, ACTION_NAMESPACE + "Edit", "{\"PreserveChanges\":false}");

        String body = "{"
            + "\"descr_text_it\":\"" + esc(nvl(descrIt)) + "\","
            + "\"descr_text_en\":\"" + esc(nvl(descrEn)) + "\""
            + "}";
        http.patchOData(draftKeyPath, body);
        http.postAction(draftKeyPath, ACTION_NAMESPACE + "Activate", "{}");
    }

    /** Elimina un codice ZIMBAL_1 (DELETE diretta sull'entità attiva). */
    public void delete(String codImballo) throws IOException, InterruptedException {
        http.deleteOData(activeKeyPath(codImballo));
    }

    private static String activeKeyPath(String codImballo) {
        return ENTITY_SET + "(cod_imballo='" + codImballo + "',IsActiveEntity=true)";
    }

    private static String draftKeyPath(String codImballo) {
        return ENTITY_SET + "(cod_imballo='" + codImballo + "',IsActiveEntity=false)";
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}