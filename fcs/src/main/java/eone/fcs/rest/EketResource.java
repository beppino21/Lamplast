package eone.fcs.rest;

import eone.fcs.repository.EketRepository;
import eone.fcs.repository.EketRepository.EketRiga;
import eone.fcs.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eone.fcs.client.GoodsReceiptClient;
import eone.fcs.client.GoodsReceiptException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
 *   F - Fine scarico: popola MSEG, registra EM su S/4HC, archivia, aggiorna EKET
 *
 * Convenzione parametri:
 *   - Valore '-' su un campo opzionale significa "cancella il dato"
 *   - Valori numerici accettano sia '.' che ',' come separatore decimale
 *
 * Configurazione (da ccee_config.properties o variabile d'ambiente):
 *   bridge.jar.path    = percorso assoluto del JAR fcs-wms-bridge (es: /opt/fcs/fcs-wms-bridge-1.0.0.jar)
 *   bridge.config.path = percorso assoluto del config.properties del bridge (es: /opt/fcs/config.properties)
 */
@Path("/inbound")
public class EketResource {

    private static final Logger log = LoggerFactory.getLogger(EketResource.class);

    // Percorsi del bridge JAR — letti da sistema, con fallback su valori di default.
    // In produzione impostare come variabili d'ambiente o proprietà di sistema.
    private static final String BRIDGE_JAR_PATH =
            System.getProperty("bridge.jar.path",
            System.getenv("BRIDGE_JAR_PATH") != null
                ? System.getenv("BRIDGE_JAR_PATH")
                : "/opt/fcs/fcs-wms-bridge-1.0.0.jar");

    private static final String BRIDGE_CONFIG_PATH =
            System.getProperty("bridge.config.path",
            System.getenv("BRIDGE_CONFIG_PATH") != null
                ? System.getenv("BRIDGE_CONFIG_PATH")
                : "/opt/fcs/config.properties");

//    @Inject
//    private EketRepository repo;
    private final EketRepository repo = new EketRepository();

    // -------------------------------------------------------------------------
    // Endpoint principale
    // -------------------------------------------------------------------------

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response handleRequest(
            @QueryParam("ACTION")         String action,
            @QueryParam("ID_EKET")        String idEket,
            @QueryParam("UUID")           String uuid,
            @QueryParam("DDT")            String ddt,
            @QueryParam("DATA_DDT")       String dataDdt,
            @QueryParam("TARGA")          String targa,
            @QueryParam("IN_MENGE")       String inMenge,
            @QueryParam("IN_WERKS")       String inWerks,
            @QueryParam("IN_LGORT")       String inLgort,
            @QueryParam("COLLI_TOT")      String colliTot,
            @QueryParam("COLLI_ROW")      String colliRow,
            @QueryParam("PESO_LORDO_TOT") String pesoLordoTot,
            @QueryParam("PESO_LORDO_ROW") String pesoLordoRow,
            @QueryParam("PESO_NETTO_TOT") String pesoNettoTot,
            @QueryParam("PESO_NETTO_ROW") String pesoNettoRow,
            @QueryParam("QTAXTAG")        String qtaxtag,
            @QueryParam("DATA_ARRIVO")    String dataArrivo,
            @QueryParam("IN_CHARG")       String inCharg
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
            updated = repo.cancelByBemid(uuid);
        } else {
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
    // F - Fine scarico: popola MSEG, registra EM su S/4HC, archivia, refresh EKET
    // -------------------------------------------------------------------------

    private Response handleF(String uuid) {
        if (isEmpty(uuid)) return error("Parametri non compilati: UUID");

        // 1. Verifica che esistano righe in stato wmsst=2 (scarico avviato)
        if (!repo.existsBemidWithStatus(uuid, "2")) {
            return error("UUID con stato 2 non presente su EKET");
        }

        // 2. Carica le righe dello scarico
        List<EketRiga> righe = repo.loadRigheByBemid(uuid);
        if (righe.isEmpty()) {
            return error("Nessuna riga trovata per UUID: " + uuid);
        }
        log.info("handleF: {} righe caricate per uuid={}", righe.size(), uuid);

        // 3. Popola tabfcsmseg (staging per la GR)
        //    In caso di fallimento successivo le righe restano in MSEG per analisi.
        repo.upsertMseg(righe);
        log.info("handleF: tabfcsmseg popolata per uuid={}", uuid);

        // 4. Registra Goods Receipt su S/4HC
        String mblnr;
        try {
            mblnr = createGoodsReceipt(uuid, righe);
        } catch (GoodsReceiptException e) {
            // errore GR atteso → fallback controllato
            log.error("handleF: GR fallita per uuid={} — {}", uuid, e.getMessage(), e);
            repo.setWmsstErrore(uuid);
            return error("Errore registrazione EM su SAP: " + e.getMessage());
        } catch (Exception e) {
            // errore inatteso → non marchiamo 'E', rilanciamo al handler globale
            log.error("handleF: errore inatteso per uuid={}", uuid, e);
            throw e;
        }

        // 5. GR riuscita: archiviazione atomica
        //    EKET → EKETHST, MSEG → MSEGHST, poi DELETE da MSEG e EKET.
        //    Se questa operazione fallisce il mblnr è già registrato in SAP
        //    ma i dati locali restano "live" — situazione da gestire manualmente.
        //    Per questo il metodo logga in modo prominente e rilancia.
        try {
            repo.archiviaDopoGr(uuid, mblnr);
            log.info("handleF: archiviazione completata per uuid={} mblnr={}", uuid, mblnr);
        } catch (RepositoryException e) {
            // Caso critico: GR ok su SAP ma archiviazione locale fallita.
            // Logghiamo a ERROR con tutti i dettagli — non tentiamo retry automatico.
            log.error("handleF: ATTENZIONE — GR registrata su SAP (mblnr={}) " +
                      "ma archiviazione locale fallita per uuid={}: {}",
                      mblnr, uuid, e.getMessage(), e);
            // Segnaliamo comunque il mblnr nella risposta per permettere
            // la riconciliazione manuale.
            return error("EM registrata su SAP (mblnr=" + mblnr +
                         ") ma archiviazione locale fallita: " + e.getMessage());
        }

        // 6. Refresh EKET per ogni OdA distinto coinvolto (sincrono, best-effort)
        //    Eseguito DOPO l'archiviazione — il bridge legge gli OdA aggiornati da S/4HC.
        Set<String> ebelns = righe.stream()
                .map(r -> r.ebeln)
                .filter(e -> e != null && !e.isBlank())
                .collect(Collectors.toSet());

        for (String ebeln : ebelns) {
            refreshEket(ebeln);
        }

        return ok("Documento SAP registrato: " + mblnr);
    }

    // -------------------------------------------------------------------------
    // Goods Receipt — chiamata reale a S/4HC tramite GoodsReceiptClient
    // -------------------------------------------------------------------------

    /**
     * Registra il Goods Receipt su S/4HC tramite API_MATERIAL_DOCUMENT_SRV (OData V2).
     * Communication Scenario richiesto sul tenant: SAP_COM_0108.
     *
     * Lancia GoodsReceiptException (RuntimeException) in caso di errore —
     * intercettata da handleF che provvede al fallback (setWmsstErrore).
     */
    private String createGoodsReceipt(String uuid, List<EketRiga> righe) {
        GoodsReceiptClient client = new GoodsReceiptClient();
        return client.postGoodsReceipt(uuid, righe);
    }

    // -------------------------------------------------------------------------
    // Refresh EKET tramite bridge JAR (sincrono, best-effort)
    // -------------------------------------------------------------------------

    /**
     * Esegue il JAR fcs-wms-bridge in modalità "eket <EBELN>" per riallineare
     * le schedulazioni dell'OdA appena ricevuto con i dati aggiornati da S/4HC.
     *
     * L'operazione è sincrona (attende il completamento del processo) ma
     * best-effort: un eventuale fallimento viene loggato come WARNING senza
     * bloccare o annullare la registrazione EM già completata.
     *
     * Prerequisiti (configurare prima del go-live):
     *   - BRIDGE_JAR_PATH    → percorso del JAR (proprietà sistema o variabile env)
     *   - BRIDGE_CONFIG_PATH → percorso del config.properties del bridge
     */
    private void refreshEket(String ebeln) {
        log.info("refreshEket: avvio bridge per OdA={}", ebeln);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", BRIDGE_JAR_PATH,
                    BRIDGE_CONFIG_PATH,
                    "eket",
                    ebeln
            );
            pb.redirectErrorStream(true); // stderr confluisce in stdout

            Process proc = pb.start();

            // Consuma l'output per evitare che il buffer si blocchi
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                reader.lines().forEach(line ->
                    log.debug("[bridge-eket|{}] {}", ebeln, line));
            }

            int exitCode = proc.waitFor();
            if (exitCode == 0) {
                log.info("refreshEket: OdA={} aggiornato con successo", ebeln);
            } else {
                log.warn("refreshEket: bridge terminato con exit code {} per OdA={}",
                         exitCode, ebeln);
            }
        } catch (Exception e) {
            log.warn("refreshEket: errore avvio bridge per OdA={}: {}", ebeln, e.getMessage());
        }
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
