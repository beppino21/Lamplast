package eone.fcs.rest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eone.fcs.client.FcsConfig;
import eone.fcs.client.GoodsReceiptClient;
import eone.fcs.client.GoodsReceiptException;
import eone.fcs.client.ReturnDeliveryClient;
import eone.fcs.client.ReturnDeliveryException;
import eone.fcs.repository.EketRepository;
import eone.fcs.repository.EketRepository.EketRiga;
import eone.fcs.repository.RepositoryException;
import eone.fcs.repository.RestLogRepository;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

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
 *       Per OdA (kappl != 'V') → GoodsReceiptClient  (movimento 101)
 *       Per resi (kappl  = 'V') → ReturnDeliveryClient (consegna reso + PGI)
 *       Scarichi misti (OdA + resi nello stesso UUID) → rifiutati con HTTP 400
 *
 * Convenzione parametri:
 *   - Valore '-' su un campo opzionale significa "cancella il dato"
 *   - Valori numerici accettano sia '.' che ',' come separatore decimale
 *
 * Configurazione (da ccee_config.properties o variabile d'ambiente):
 *   bridge.jar.path    = percorso assoluto del JAR fcs-wms-bridge
 *   bridge.config.path = percorso assoluto del config.properties del bridge
 */
@Path("/inbound")
public class EketResource {

    private static final Logger log = LoggerFactory.getLogger(EketResource.class);

    // Percorsi del bridge JAR — letti da ccee_config.properties tramite FcsConfig.
    private static final String BRIDGE_JAR_PATH    = FcsConfig.getInstance().getBridgeJarPath();
    private static final String BRIDGE_CONFIG_PATH = FcsConfig.getInstance().getBridgeConfigPath();

    // Valore kappl per i resi da cliente (OdV di reso)
    private static final String KAPPL_RESO = "V";

    private final EketRepository    repo    = new EketRepository();
    private final RestLogRepository restLog = new RestLogRepository();

    @Context
    UriInfo uriInfo;

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

        if (!repo.existsBemidWithStatus(uuid, "2")) {
            return error("UUID con stato 2 non presente su EKET", uuid);
        }

        if (repo.hasResiMisti(uuid)) {
            log.warn("handleF: uuid={} contiene mix di kappl (OdA + resi) — scarico rifiutato", uuid);
            return error("Scarico non valido: OdA e Resi da cliente non possono " +
                         "essere scaricati insieme. Procedere in due fasi separate.", uuid);
        }

        List<EketRiga> righe = repo.loadRigheByBemid(uuid);
        if (righe.isEmpty()) {
            return error("Nessuna riga trovata per UUID: " + uuid, uuid);
        }
        log.info("handleF: {} righe caricate per uuid={}", righe.size(), uuid);

        boolean isReso = repo.isReso(uuid);
        log.info("handleF: uuid={} tipo={}", uuid, isReso ? "RESO DA CLIENTE (kappl=V)" : "OdA");

        repo.upsertMseg(righe);
        log.info("handleF: tabfcsmseg popolata per uuid={}", uuid);

        String mblnr;
        try {
            if (isReso) {
                mblnr = createReturnDelivery(uuid, righe);
            } else {
                mblnr = createGoodsReceipt(uuid, righe);
            }
        } catch (GoodsReceiptException e) {
            log.error("handleF: GR fallita per uuid={} — {}", uuid, e.getMessage(), e);
            repo.setWmsstErrore(uuid);
            return error("Errore registrazione EM su SAP: " + e.getMessage(), uuid);
        } catch (ReturnDeliveryException e) {
            log.error("handleF: Consegna reso fallita per uuid={} — {}", uuid, e.getMessage(), e);
            repo.setWmsstErrore(uuid);
            return error("Errore registrazione reso su SAP: " + e.getMessage(), uuid);
        } catch (Exception e) {
            log.error("handleF: errore inatteso per uuid={}", uuid, e);
            throw e;
        }

        try {
            repo.archiviaDopoGr(uuid, mblnr);
            log.info("handleF: archiviazione completata per uuid={} mblnr={}", uuid, mblnr);
        } catch (RepositoryException e) {
            log.error("handleF: ATTENZIONE — documento SAP registrato (mblnr={}) " +
                      "ma archiviazione locale fallita per uuid={}: {}",
                      mblnr, uuid, e.getMessage(), e);
            return error("Documento SAP registrato (mblnr=" + mblnr +
                         ") ma archiviazione locale fallita: " + e.getMessage(), uuid);
        }

        if (isReso) {
            List<String> vbelns = repo.getEbelnsPerKappl(uuid, KAPPL_RESO);
            for (String vbeln : vbelns) {
                refreshVbep(vbeln);
            }
        } else {
            Set<String> ebelns = righe.stream()
                    .map(r -> r.ebeln)
                    .filter(e -> e != null && !e.isBlank())
                    .collect(Collectors.toSet());
            for (String ebeln : ebelns) {
                refreshEket(ebeln);
            }
        }

        return ok("Documento SAP registrato: " + mblnr, uuid);
    }

    // -------------------------------------------------------------------------
    // Goods Receipt — OdA (kappl != 'V')
    // -------------------------------------------------------------------------

    /**
     * Registra il Goods Receipt su S/4HC tramite API_MATERIAL_DOCUMENT_SRV (OData V2).
     * Communication Scenario richiesto: SAP_COM_0108.
     * Movimento 101 — Entrata merci da OdA.
     */
    private String createGoodsReceipt(String uuid, List<EketRiga> righe) {
        GoodsReceiptClient client = new GoodsReceiptClient();
        return client.postGoodsReceipt(uuid, righe);
    }

    // -------------------------------------------------------------------------
    // Return Delivery — Reso da cliente (kappl = 'V')
    // -------------------------------------------------------------------------

    /**
     * Crea la consegna reso su S/4HC e ne esegue il PGI tramite
     * API_OUTBOUND_DELIVERY_SRV (OData V2).
     * Communication Scenario richiesto: SAP_COM_0106.
     *
     * Restituisce il numero documento materiale (mblnr) generato dal PGI,
     * usato per l'archiviazione in tabfcsekethst / tabfcsmseghst.
     */
    private String createReturnDelivery(String uuid, List<EketRiga> righe) {
        ReturnDeliveryClient client = new ReturnDeliveryClient();
        return client.postReturnDelivery(uuid, righe);
    }

    // -------------------------------------------------------------------------
    // Refresh EKET tramite bridge JAR — OdA (sincrono, best-effort)
    // -------------------------------------------------------------------------

    /**
     * Esegue il JAR fcs-wms-bridge in modalità "eket <EBELN>" per riallineare
     * le schedulazioni dell'OdA appena ricevuto con i dati aggiornati da S/4HC.
     */
    private void refreshEket(String ebeln) {
        log.info("refreshEket: avvio bridge per OdA={}", ebeln);
        runBridge("eket", ebeln);
    }

    // -------------------------------------------------------------------------
    // Refresh VBEP tramite bridge JAR — Reso da cliente (sincrono, best-effort)
    // -------------------------------------------------------------------------

    /**
     * Esegue il JAR fcs-wms-bridge in modalità "vbep <VBELN>" per riallineare
     * le schedulazioni dell'OdV di reso appena ricevuto con i dati aggiornati
     * da S/4HC.
     * Il parametro vbeln corrisponde al campo ebeln di tabfcseket per i resi
     * (kappl='V'): l'extractor tratta i record VBEP come EKET usando lo stesso
     * campo ebeln per il numero ordine di vendita.
     */
    private void refreshVbep(String vbeln) {
        log.info("refreshVbep: avvio bridge per OdV reso={}", vbeln);
        runBridge("vbep", vbeln);
    }

    // -------------------------------------------------------------------------
    // Esecuzione bridge JAR (logica comune a refreshEket e refreshVbep)
    // -------------------------------------------------------------------------

    private void runBridge(String mode, String param) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", BRIDGE_JAR_PATH,
                    BRIDGE_CONFIG_PATH,
                    mode,
                    param
            );
            pb.redirectErrorStream(true);

            Process proc = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                reader.lines().forEach(line ->
                    log.debug("[bridge-{}|{}] {}", mode, param, line));
            }

            int exitCode = proc.waitFor();
            if (exitCode == 0) {
                log.info("runBridge: mode={} param={} completato con successo", mode, param);
            } else {
                log.warn("runBridge: bridge terminato con exit code {} per mode={} param={}",
                         exitCode, mode, param);
            }
        } catch (Exception e) {
            log.warn("runBridge: errore avvio bridge mode={} param={}: {}", mode, param, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private Response ok(String message) {
        log.info("INBOUND RESPONSE 200: {}", message);
        String qs = uriInfo != null ? uriInfo.getRequestUri().getQuery() : "";
        restLog.log("GET", qs, message);
        return Response.ok(message).build();
    }

    private Response ok(String message, String uuid) {
        log.info("INBOUND RESPONSE 200: {}", message);
        String qs = uriInfo != null ? uriInfo.getRequestUri().getQuery() : "";
        restLog.log("GET", qs, message, uuid, null);
        return Response.ok(message).build();
    }

    private Response error(String message) {
        log.warn("INBOUND RESPONSE 400: {}", message);
        String qs = uriInfo != null ? uriInfo.getRequestUri().getQuery() : "";
        restLog.log("GET", qs, message);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(message)
                .build();
    }

    private Response error(String message, String uuid) {
        log.warn("INBOUND RESPONSE 400: {}", message);
        String qs = uriInfo != null ? uriInfo.getRequestUri().getQuery() : "";
        restLog.log("GET", qs, message, uuid, null);
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
