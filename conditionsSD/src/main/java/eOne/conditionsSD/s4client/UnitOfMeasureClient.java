package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Legge le unità di misura SAP valide, per validare lato client il campo
 * "meins" prima di scriverlo su ZIMBAL_3 — evita che SAP restituisca un
 * errore 500/dump ABAP su un valore inesistente (invece di un rifiuto pulito).
 *
 * IMPORTANTE — servizio ancora da creare: non esiste un'API standard "già
 * pronta" per l'elenco unità di misura verificata su questo tenant. Stesso
 * schema già seguito con successo per condizioni di pagamento e zone di
 * trasporto: esporre come Custom CDS View su I_UnitOfMeasure (CDS standard
 * rilasciata, campo chiave UnitOfMeasure) + Service Definition + Service
 * Binding OData V4, aggiunta come quarto Inbound Service a
 * ZCS_EXTENSION_LISTINI_SD. ENTITY_PATH qui sotto è un PLACEHOLDER — va
 * aggiornato con il path reale una volta creati gli oggetti (ZZ_UNITOFMEASURE
 * → ZZ_UNITOFMEASURE_SRV → ZZ_UNITOFMEASURE_BND).
 *
 * Fino a quel momento il servizio risulterà irraggiungibile: isValid()
 * degrada automaticamente ad "accetta tutto" (non blocca nessun salvataggio,
 * stesso principio resiliente di Imbal3ReadClient/SalesDistrictClient) — la
 * validazione vera e propria si attiva da sola non appena il servizio esiste.
 */
public class UnitOfMeasureClient {

    private static final String ENTITY_PATH =
        "/sap/opu/odata4/sap/zsb_unitofmeasure/srvd/sap/zz_unitofmeasure_srv/0001/ZZ_UNITOFMEASURE";

    private final S4HttpClient http;

    private List<String> cachedCodes;          // cache in-memory per la vita di questa istanza
    private volatile boolean available = true;

    public UnitOfMeasureClient(S4HttpClient http) { this.http = http; }

    /** Elenco di tutti i codici UM validi (uppercase, trim) — vuoto se il servizio non è raggiungibile. */
    public List<String> fetchAll() throws InterruptedException {
        if (cachedCodes != null) return cachedCodes;
        List<String> result = new ArrayList<>();
        if (available) {
            try {
                String path = ENTITY_PATH + "?$select=UnitOfMeasure&$top=5000";
                JsonNode root = http.getOData(path);
                JsonNode values = root.path("value");
                if (values.isArray())
                    for (JsonNode n : values)
                        result.add(n.path("UnitOfMeasure").asText("").trim().toUpperCase());
            } catch (IOException e) {
                available = false;
                System.err.println("UnitOfMeasureClient: servizio ZZ_UNITOFMEASURE_SRV non raggiungibile "
                    + "(probabilmente non ancora creato) — validazione UM disattivata, nessun blocco sul "
                    + "salvataggio. Dettaglio: " + e.getMessage());
            }
        }
        cachedCodes = result;
        return result;
    }

    /**
     * true se la UM è tra quelle valide, OPPURE se il servizio non è (ancora)
     * raggiungibile — in quest'ultimo caso non blocchiamo il salvataggio,
     * stesso principio resiliente del resto del progetto.
     */
    public boolean isValid(String code) throws InterruptedException {
        if (code == null || code.isBlank()) return false;
        List<String> all = fetchAll();
        if (!available) return true; // servizio non pronto: non blocchiamo
        return all.contains(code.trim().toUpperCase());
    }

    public boolean isServiceAvailable() { return available; }
}
