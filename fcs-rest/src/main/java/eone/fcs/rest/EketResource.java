package eone.fcs.rest;

import eone.fcs.repository.EketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint REST che replica il contratto del servizio SICF ZFCS_INBOUND di SAP R/3.
 *
 * URL base: /api/inbound
 *
 * Chiamata via GET con parametri in query string:
 *   ?ACTION=X&UUID=...&ID_EKET=...&...
 *
 * Action disponibili:
 *   H - Health check
 *   C - Associa bemid (UUID) alla riga EKET, wmsst=1
 *   D - Annulla scarico: azzera campi in_*, wmsst=0
 *   T - Aggiorna dati testata (targa, data arrivo) su tutte le righe del bemid
 *   U - Aggiorna dati di riga (DDT, pesi, colli, menge, lotto...)
 *   I - Inizio scarico fisico: wmsst=2
 *   F - Fine scarico: triggera creazione EM su S/4HC (stub)
 *
 * Convenzione parametri:
 *   - Valore '-' su un campo opzionale significa "cancella il dato"
 *   - Valori numerici accettano sia '.' che ',' come separatore decimale
 */
@Path("/inbound")
public class EketResource {

    private static final Logger log = LoggerFactory.getLogger(EketResource.class);

//    @Inject
//    private EketRepository repo;
    private final EketRepository repo = new EketRepository();    

    // -------------------------------------------------------------------------
    // Endpoint principale
    // -------------------------------------------------------------------------

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response handleRequest(
            @QueryParam("ACTION")        String action,
            @QueryParam("ID_EKET")       String idEket,
            @QueryParam("UUID")          String uuid,
            @QueryParam("DDT")           String ddt,
            @QueryParam("DATA_DDT")      String dataDdt,
            @QueryParam("TARGA")         String targa,
            @QueryParam("IN_MENGE")      String inMenge,
            @QueryParam("IN_WERKS")      String inWerks,
            @QueryParam("IN_LGORT")      String inLgort,
            @QueryParam("COLLI_TOT")     String colliTot,
            @QueryParam("COLLI_ROW")     String colliRow,
            @QueryParam("PESO_LORDO_TOT") String pesoLordoTot,
            @QueryParam("PESO_LORDO_ROW") String pesoLordoRow,
            @QueryParam("PESO_NETTO_TOT") String pesoNettoTot,
            @QueryParam("PESO_NETTO_ROW") String pesoNettoRow,
            @QueryParam("QTAXTAG")       String qtaxtag,
            @QueryParam("DATA_ARRIVO")   String dataArrivo,
            @QueryParam("IN_CHARG")      String inCharg
    ) {
        log.info("INBOUND REQUEST: action={} idEket={} uuid={}", action, idEket, uuid);

        // Normalizza decimali (virgola → punto)
        inMenge      = normDecimal(inMenge);
        pesoLordoTot = normDecimal(pesoLordoTot);
        pesoLordoRow = normDecimal(pesoLordoRow);
        pesoNettoTot = normDecimal(pesoNettoTot);
        pesoNettoRow = normDecimal(pesoNettoRow);
        qtaxtag      = normDecimal(qtaxtag);

        // Validazione ACTION
        if (isEmpty(action)) {
            return error("Parametri non compilati: ACTION");
        }

        action = action.toUpperCase().trim();

        try {
            return switch (action) {
                case "H" -> handleH();
                case "C" -> handleC(idEket, uuid);
                case "D" -> handleD(uuid, idEket);
                case "T" -> handleT(uuid, targa, dataArrivo);
                case "U" -> handleU(idEket, ddt, dataDdt, inMenge, inWerks, inLgort,
                                    colliTot, colliRow, pesoLordoTot, pesoLordoRow,
                                    pesoNettoTot, pesoNettoRow, qtaxtag, inCharg);
                case "I" -> handleI(uuid);
                case "F" -> handleF(uuid);
                default  -> error("Parametro ACTION: Valore " + action + " non valido");
            };
        } catch (Exception e) {
            log.error("Errore action {}: {}", action, e.getMessage(), e);
            return error("Errore interno: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // H - Health check
    // -------------------------------------------------------------------------

    private Response handleH() {
        return ok("OK");
    }

    // -------------------------------------------------------------------------
    // C - Associa UUID/bemid alla riga EKET, wmsst=1
    // -------------------------------------------------------------------------

    private Response handleC(String idEket, String uuid) {
        if (isEmpty(idEket)) return error("Parametri non compilati: ID_EKET");
        if (isEmpty(uuid))   return error("Parametri non compilati: UUID");

        int updated = repo.assignBemid(idEket, uuid);
        if (updated == 0) {
            return error("Errore su aggiornamento: ID_EKET non presente");
        }
        return ok("Aggiornamento eseguito con successo");
    }

    // -------------------------------------------------------------------------
    // D - Annulla scarico: azzera campi in_*, wmsst=0
    // -------------------------------------------------------------------------

    private Response handleD(String uuid, String idEket) {
        if (isEmpty(uuid)) return error("Parametri non compilati: UUID");

        int updated;
        if (isEmpty(idEket)) {
            // Annulla tutte le righe del bemid
            updated = repo.cancelByBemid(uuid);
        } else {
            // Annulla riga specifica
            updated = repo.cancelByIdEket(idEket, uuid);
        }

        if (updated == 0) {
            return ok("Warning su annullamento: ID_EKET non presente");
        }
        return ok("Annullamento eseguito con successo");
    }

    // -------------------------------------------------------------------------
    // T - Aggiorna dati testata (targa, data arrivo) su tutte le righe del bemid
    // -------------------------------------------------------------------------

    private Response handleT(String uuid, String targa, String dataArrivo) {
        if (isEmpty(uuid)) return error("Parametri non compilati: UUID");
        if (isEmpty(targa) && isEmpty(dataArrivo)) {
            return error("Parametri non compilati: TARGA or DATA_ARRIVO");
        }

        // Convenzione: '-' = cancella il campo
        String targaVal      = "-".equals(targa)      ? "" : targa;
        String dataArrivoVal = "-".equals(dataArrivo) ? "" : dataArrivo;

        int updated = repo.updateTestata(uuid, targaVal, dataArrivoVal);
        if (updated == 0) {
            return ok("Nessun record aggiornato");
        }
        return ok("Aggiornamento eseguito con successo");
    }

    // -------------------------------------------------------------------------
    // U - Aggiorna dati di riga (DDT, pesi, colli, menge, lotto...)
    // -------------------------------------------------------------------------

    private Response handleU(String idEket, String ddt, String dataDdt,
                              String inMenge, String inWerks, String inLgort,
                              String colliTot, String colliRow,
                              String pesoLordoTot, String pesoLordoRow,
                              String pesoNettoTot, String pesoNettoRow,
                              String qtaxtag, String inCharg) {

        if (isEmpty(idEket)) return error("Parametri non compilati: ID_EKET");

        // Convenzione: '-' = cancella il campo
        String ddtVal     = "-".equals(ddt)     ? "" : ddt;
        String dataDdtVal = "-".equals(dataDdt) ? "" : dataDdt;

        int updated = repo.updateRiga(idEket, ddtVal, dataDdtVal, inMenge,
                inWerks, inLgort, colliTot, colliRow,
                pesoLordoTot, pesoLordoRow, pesoNettoTot, pesoNettoRow,
                qtaxtag, inCharg);

        if (updated == 0) {
            return error("Errore su aggiornamento: ID_EKET non presente");
        }
        return ok("Aggiornamento eseguito con successo");
    }

    // -------------------------------------------------------------------------
    // I - Inizio scarico fisico: wmsst=2
    // -------------------------------------------------------------------------

    private Response handleI(String uuid) {
        if (isEmpty(uuid)) return error("Parametri non compilati: UUID");

        int updated = repo.setWmsst(uuid, "2");
        if (updated == 0) {
            return error("UUID non presente su EKET o già completato");
        }
        return ok("Inizio scarico merce registrato");
    }

    // -------------------------------------------------------------------------
    // F - Fine scarico: triggera creazione EM su S/4HC
    // -------------------------------------------------------------------------

    private Response handleF(String uuid) {
        if (isEmpty(uuid)) return error("Parametri non compilati: UUID");

        // Verifica che esista almeno una riga con wmsst=2 per questo bemid
        boolean exists = repo.existsBemidWithStatus(uuid, "2");
        if (!exists) {
            return error("UUID con stato 2 non presente su EKET");
        }

        try {
            // ==============================================================
            // HOOK: qui andrà la chiamata API Goods Receipt su S/4HC
            // Per ora: stub che simula successo
            // ==============================================================
            log.info("TODO: Chiamata GR su S/4HC per bemid={}", uuid);
            String grResult = createGoodsReceiptStub(uuid);
            // ==============================================================

            // Aggiorna wmsst=3 (completato)
            repo.setWmsst(uuid, "3");

            // Scrive su tabfcsmseghst
            repo.insertMsegHst(uuid, grResult);

            return ok("Documenti inviati a SAP");

        } catch (Exception e) {
            log.error("Errore creazione GR per uuid={}: {}", uuid, e.getMessage(), e);
            return error("Errore creazione documento SAP: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Stub GR - da sostituire con chiamata reale S/4HC
    // -------------------------------------------------------------------------

    /**
     * STUB: simula la creazione del Goods Receipt su S/4HC.
     * Da sostituire con la chiamata OData/REST reale.
     *
     * @param uuid bemid dello scarico
     * @return numero documento materiale (mblnr) restituito da S/4HC
     */
    private String createGoodsReceiptStub(String uuid) {
        // TODO: implementare chiamata a GoodsReceiptClient
        // GoodsReceiptClient client = new GoodsReceiptClient(config);
        // return client.postGoodsReceipt(uuid, lines);
        log.warn("GR STUB: nessuna chiamata reale a S/4HC per uuid={}", uuid);
        return "STUB_MBLNR";
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private Response ok(String message) {
        log.info("INBOUND RESPONSE 200: {}", message);
        return Response.ok(message).build();
    }

    private Response error(String message) {
        log.warn("INBOUND RESPONSE 400: {}", message);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(message)
                .build();
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String normDecimal(String s) {
        if (s == null) return null;
        return s.replace(',', '.');
    }
}
