package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Scrive sull'associazione imballo cliente/materiale (ZIMBAL_3/ImballoClienteMat).
 * Il servizio è draft-enabled (imposto dallo strumento con cui è stato generato,
 * non evitabile) — questa classe nasconde interamente il ciclo bozza dietro
 * tre metodi semplici (create/update/delete), così che il resto dell'app
 * (popup di attribuzione) non debba mai saperne nulla.
 *
 * Sequenza reale dietro le quinte:
 *  - create: POST (crea bozza) → Activate (bozza diventa record attivo)
 *  - update: Edit (crea bozza dal record attivo) → PATCH (sulla bozza) → Activate
 *  - delete: DELETE diretta sul record attivo (i DeleteRestrictions del
 *    servizio lo consentono senza passare dal ciclo bozza)
 */
public class Imbal3WriteClient {

    private static final String ENTITY_PATH =
        "/sap/opu/odata4/sap/zsb_zimbal_3/srvd/sap/zsd_zimbal_3/0001/ImballoClienteMat";
    private static final String NS = "com.sap.gateway.srvd.zsd_zimbal_3.v0001";

    private final S4HttpClient http;

    public Imbal3WriteClient(S4HttpClient http) { this.http = http; }

    /** Crea una nuova associazione (customer vuoto = imballo generico di materiale). */
    public void create(String customer, String material, String codImballo, String codImballo2,
                        String meins, double quantita, int numerosita)
            throws IOException, InterruptedException {

        String json = "{"
            + "\"cliente\":\"" + esc(customer) + "\","
            + "\"materiale\":\"" + esc(material) + "\","
            + "\"cod_imballo\":\"" + esc(codImballo) + "\","
            + "\"cod_imballo2\":\"" + esc(codImballo2) + "\","
            + "\"meins\":\"" + esc(meins) + "\","
            + "\"quantita\":" + quantita + ","
            + "\"numerosita\":" + numerosita
            + "}";

        JsonNode draft = http.postOData(ENTITY_PATH, json);
        if (draft == null) throw new IOException("Creazione bozza fallita: risposta vuota");

        // La bozza appena creata ha le stesse chiavi cliente/materiale, solo IsActiveEntity=false
        String draftKey = keyPath(customer, material, false);
        try {
            http.postAction(draftKey, NS + ".Activate", "{}");
        } catch (IOException | InterruptedException e) {
            discardSilently(draftKey);
            throw e;
        }
        System.out.println("Imbal3WriteClient: creata associazione " + customer + "|" + material);
    }

    /** Modifica un'associazione già esistente. */
    public void update(String customer, String material, String codImballo, String codImballo2,
                        String meins, double quantita, int numerosita)
            throws IOException, InterruptedException {

        String activeKey = keyPath(customer, material, true);

        // 1) Edit sull'attivo -> crea/recupera la bozza
        http.postAction(activeKey, NS + ".Edit", "{\"PreserveChanges\":false}");

        // 2) PATCH sulla bozza con i nuovi valori
        String draftKey = keyPath(customer, material, false);
        String json = "{"
            + "\"cod_imballo\":\"" + esc(codImballo) + "\","
            + "\"cod_imballo2\":\"" + esc(codImballo2) + "\","
            + "\"meins\":\"" + esc(meins) + "\","
            + "\"quantita\":" + quantita + ","
            + "\"numerosita\":" + numerosita
            + "}";
        try {
            http.patchOData(draftKey, json);
            // 3) Activate -> fonde le modifiche nel record attivo
            http.postAction(draftKey, NS + ".Activate", "{}");
        } catch (IOException | InterruptedException e) {
            discardSilently(draftKey);
            throw e;
        }
        System.out.println("Imbal3WriteClient: modificata associazione " + customer + "|" + material);
    }

    /** Elimina un'associazione esistente. */
    public void delete(String customer, String material) throws IOException, InterruptedException {
        String activeKey = keyPath(customer, material, true);
        http.deleteOData(activeKey);
        System.out.println("Imbal3WriteClient: eliminata associazione " + customer + "|" + material);
    }

    private String keyPath(String customer, String material, boolean active) {
        return ENTITY_PATH + "(cliente='" + esc(customer) + "',materiale='" + esc(material)
            + "',IsActiveEntity=" + active + ")";
    }

    /**
     * Scarta la bozza indicata dopo un fallimento a metà ciclo (es. PATCH o
     * Activate falliti per un valore non valido, come una UM inesistente) —
     * senza questo, la bozza resta "appesa" e blocca i tentativi successivi
     * su quella stessa combinazione cliente/materiale finché non viene
     * scartata manualmente. L'eventuale errore del Discard stesso viene solo
     * loggato: non deve mai mascherare l'errore originale che ha causato il
     * fallimento (quello è già stato/sarà rilanciato dal chiamante).
     */
    private void discardSilently(String draftKey) {
        try {
            http.postAction(draftKey, NS + ".Discard", "{}");
            System.out.println("Imbal3WriteClient: bozza scartata dopo errore — " + draftKey);
        } catch (Exception discardEx) {
            System.err.println("Imbal3WriteClient: impossibile scartare la bozza orfana " + draftKey
                + " — potrebbe restare bloccata finché non scartata manualmente. Dettaglio: "
                + discardEx.getMessage());
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("'", "''").replace("\"", "\\\"");
    }
}