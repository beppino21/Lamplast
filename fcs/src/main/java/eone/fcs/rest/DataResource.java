package eone.fcs.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eone.fcs.repository.DataRepository;
import eone.fcs.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Endpoint REST di lettura per le tabelle anagrafiche e schedulazioni OdA.
 *
 * URL base: /api/data
 *
 * Endpoint disponibili:
 *
 *   GET /api/data/mara
 *       → tutti i materiali (tabfcsmara)
 *
 *   GET /api/data/kna1
 *       → tutti i clienti (tabfcskna1)
 *
 *   GET /api/data/lfa1
 *       → tutti i fornitori (tabfcslfa1)
 *
 *   GET /api/data/eket
 *       → schedulazioni OdA (tabfcseket), con filtri opzionali combinabili:
 *           ?lifnr=<codice-fornitore>   filtra per fornitore
 *           ?ebeln=<numero-oda>         filtra per ordine d'acquisto
 *           ?wmsst=<stato>              filtra per stato WMS
 *                                       (0=libero, 1=assegnato, 2=in scarico,
 *                                        3=completato, E=errore)
 *
 * Formato risposta: JSON (application/json)
 * In caso di errore: 500 Internal Server Error con body JSON {"error": "<messaggio>"}
 */
@Path("/data")
public class DataResource {

    private static final Logger log = LoggerFactory.getLogger(DataResource.class);

    private final DataRepository repo = new DataRepository();

    // ObjectMapper condiviso e thread-safe: registra JavaTimeModule per
    // serializzare LocalDate/LocalTime come stringhe ISO (non come array).
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // =========================================================================
    // MARA - Anagrafica materiali
    // =========================================================================

    /**
     * GET /api/data/mara
     *
     * Restituisce tutti i materiali presenti in tabfcsmara.
     *
     * Esempio risposta:
     * [
     *   {
     *     "matnr": "000000000000001234",
     *     "maktx": "BULLONE M8 INOX",
     *     "mtart": "ROH",
     *     "matkl": "001",
     *     "meins": "PZ",
     *     "bstme": "CT",
     *     "datum": "2025-03-15",
     *     "uzeit": "10:23:00",
     *     "uname": "BATCHJOB",
     *     "updfl": false
     *   },
     *   ...
     * ]
     */
    @GET
    @Path("/mara")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMara() {
        log.info("GET /data/mara");
        try {
            List<Map<String, Object>> rows = repo.findAllMara();
            log.info("GET /data/mara → {} record", rows.size());
            return ok(rows);
        } catch (RepositoryException e) {
            return serverError("Errore lettura MARA: " + e.getMessage());
        }
    }

    // =========================================================================
    // KNA1 - Clienti
    // =========================================================================

    /**
     * GET /api/data/kna1
     *
     * Restituisce tutti i clienti presenti in tabfcskna1.
     *
     * Esempio risposta:
     * [
     *   {
     *     "kunnr": "0000001234",
     *     "name1": "CLIENTE ROSSI SRL",
     *     "name2": null,
     *     "stcd1": "12345678901",
     *     "stcd2": "MI-123456",
     *     "stceg": "IT12345678901",
     *     "datum": "2025-03-15",
     *     "uzeit": "10:23:00",
     *     "uname": "BATCHJOB",
     *     "updfl": false
     *   },
     *   ...
     * ]
     */
    @GET
    @Path("/kna1")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getKna1() {
        log.info("GET /data/kna1");
        try {
            List<Map<String, Object>> rows = repo.findAllKna1();
            log.info("GET /data/kna1 → {} record", rows.size());
            return ok(rows);
        } catch (RepositoryException e) {
            return serverError("Errore lettura KNA1: " + e.getMessage());
        }
    }

    // =========================================================================
    // LFA1 - Fornitori
    // =========================================================================

    /**
     * GET /api/data/lfa1
     *
     * Restituisce tutti i fornitori presenti in tabfcslfa1.
     *
     * Esempio risposta:
     * [
     *   {
     *     "lifnr": "0000005678",
     *     "name1": "FORNITORE BIANCHI SPA",
     *     "name2": null,
     *     "stcd1": "98765432101",
     *     "stcd2": "TO-654321",
     *     "stceg": "IT98765432101",
     *     "datum": "2025-03-15",
     *     "uzeit": "10:23:00",
     *     "uname": "BATCHJOB",
     *     "updfl": false
     *   },
     *   ...
     * ]
     */
    @GET
    @Path("/lfa1")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLfa1() {
        log.info("GET /data/lfa1");
        try {
            List<Map<String, Object>> rows = repo.findAllLfa1();
            log.info("GET /data/lfa1 → {} record", rows.size());
            return ok(rows);
        } catch (RepositoryException e) {
            return serverError("Errore lettura LFA1: " + e.getMessage());
        }
    }

    // =========================================================================
    // EKET - Schedulazioni OdA con filtri opzionali
    // =========================================================================

    /**
     * GET /api/data/eket
     *
     * Restituisce le schedulazioni OdA da tabfcseket.
     * Tutti i parametri di filtro sono opzionali e combinabili tra loro.
     *
     * Parametri query string:
     *   lifnr  → filtra per codice fornitore (es. ?lifnr=0000005678)
     *   ebeln  → filtra per numero OdA (es. ?ebeln=4500000042)
     *   wmsst  → filtra per stato WMS:
     *              0 = libero (nessun bemid assegnato)
     *              1 = bemid assegnato, in attesa scarico
     *              2 = scarico fisico in corso
     *              3 = scarico completato e registrato su SAP
     *              E = errore in registrazione EM
     *
     * Esempi:
     *   GET /api/data/eket                          → tutte le schedulazioni
     *   GET /api/data/eket?wmsst=0                  → solo righe libere
     *   GET /api/data/eket?lifnr=0000005678         → per fornitore
     *   GET /api/data/eket?ebeln=4500000042         → per singolo OdA
     *   GET /api/data/eket?lifnr=0000005678&wmsst=2 → fornitore + in scarico
     *   GET /api/data/eket?ebeln=4500000042&wmsst=E → OdA in errore
     *
     * Esempio risposta (estratto di una riga):
     * [
     *   {
     *     "ebeln": "4500000042",
     *     "ebelp": "00010",
     *     "etenr": "0001",
     *     "id_eket": "0000000000001234567",
     *     "kappl": "EF",
     *     "xchpf": true,
     *     "eindt": "2025-11-20",
     *     "lifnr": "0000005678",
     *     "name1": "FORNITORE BIANCHI SPA",
     *     "matnr": "000000000000001234",
     *     "maktx": "BULLONE M8 INOX",
     *     "werks": "1000",
     *     "lgort": "0001",
     *     "menge": 500.0,
     *     "menge_open": 500.0,
     *     "meins": "PZ",
     *     "wmsst": "0",
     *     "bemid": null,
     *     "in_menge": null,
     *     "in_charg": null,
     *     ...
     *   }
     * ]
     */
    @GET
    @Path("/eket")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEket(
            @QueryParam("lifnr") String lifnr,
            @QueryParam("ebeln") String ebeln,
            @QueryParam("wmsst") String wmsst) {

        log.info("GET /data/eket lifnr={} ebeln={} wmsst={}", lifnr, ebeln, wmsst);

        // Validazione wmsst: deve essere uno dei valori ammessi
        if (wmsst != null && !wmsst.isBlank()) {
            String w = wmsst.trim().toUpperCase();
            if (!w.matches("[0123E]")) {
                return badRequest("Parametro wmsst non valido: '" + wmsst +
                                  "'. Valori ammessi: 0, 1, 2, 3, E");
            }
            wmsst = w; // normalizza (es. 'e' → 'E')
        }

        try {
            List<Map<String, Object>> rows = repo.findEket(lifnr, ebeln, wmsst);
            log.info("GET /data/eket → {} record", rows.size());
            return ok(rows);
        } catch (RepositoryException e) {
            return serverError("Errore lettura EKET: " + e.getMessage());
        }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private Response ok(List<Map<String, Object>> data) {
        try {
            String json = JSON.writeValueAsString(data);
            return Response.ok(json, MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            log.error("Errore serializzazione JSON: {}", e.getMessage(), e);
            return serverError("Errore serializzazione risposta: " + e.getMessage());
        }
    }

    private Response badRequest(String message) {
        log.warn("400 Bad Request: {}", message);
        String json = "{\"error\":\"" + escapeJson(message) + "\"}";
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(json)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private Response serverError(String message) {
        log.error("500 Internal Server Error: {}", message);
        String json = "{\"error\":\"" + escapeJson(message) + "\"}";
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(json)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /** Escaping minimale per costruire JSON di errore senza dipendere da Jackson. */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
