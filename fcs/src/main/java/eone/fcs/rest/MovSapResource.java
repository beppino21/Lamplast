package eone.fcs.rest;

import eone.fcs.client.MovementClient;
import eone.fcs.client.MovementException;
import eone.fcs.client.MovementResult;
import eone.fcs.repository.MovSapRepository;
import eone.fcs.repository.MovSapRepository.MovsapRiga;
import eone.fcs.repository.RepositoryException;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Endpoint REST che replica il contratto del vecchio servizio SICF su R/3
 * per i movimenti MM (ZFCS_MOVSAP).
 *
 * Risposta in TEXT_PLAIN per compatibilità con il WMS RFID.
 *
 * Flusso (singola chiamata GET, come su R/3):
 *  1. Validazione parametri obbligatori (per bwart)
 *  2. UPSERT in TABFCSMOVSAP (staging)
 *  3. Elaborazione immediata via MovementClient → S/4HC
 *  4a. Successo → archivia in HST + DELETE live, risposta "Documenti inviati a SAP"
 *  4b. Errore   → wmsst='E', risposta con messaggio di errore
 */
@Path("/movement")
public class MovSapResource {

    private final MovSapRepository repo   = new MovSapRepository();
    private final MovementClient   client = new MovementClient();

    // -----------------------------------------------------------------------
    // Health check
    // -----------------------------------------------------------------------

    @GET
    @Path("/H")
    @Produces(MediaType.TEXT_PLAIN)
    public Response handleH() {
        return ok("FCS Movement Service OK");
    }

    // -----------------------------------------------------------------------
    // Movimento MM (unico endpoint operativo)
    // -----------------------------------------------------------------------

    /**
     * Riceve i parametri del movimento via query string.
     * Parametri obbligatori per tutti i bwart: MOVID, BWART, WERKS, LGORT, MATNR.
     * Parametri aggiuntivi per bwart specifici (coerente con HANDLE_REQUEST.abap):
     *   309 → MATNR_TO obbligatorio
     *   311 → MENGE + (WERKS_TO o LGORT_TO) obbligatori
     *   551/552/561/562 → MENGE obbligatorio
     */
    @GET
    @Path("/M")
    @Produces(MediaType.TEXT_PLAIN)
    public Response handleM(
            @QueryParam("MOVID")    String movid,
            @QueryParam("BWART")    String bwart,
            @QueryParam("LIFNR")    String lifnr,
            @QueryParam("KUNNR")    String kunnr,
            @QueryParam("KOSTL")    String kostl,
            @QueryParam("NUMORD")   String aufnr,    // NUMORD → aufnr come in R/3
            @QueryParam("PRCTR")    String prctr,
            @QueryParam("SOBKZ")    String sobkz,
            @QueryParam("WERKS")    String werks,
            @QueryParam("LGORT")    String lgort,
            @QueryParam("MATNR")    String matnr,
            @QueryParam("CHARG")    String charg,
            @QueryParam("MENGE")    String mengeStr,
            @QueryParam("WERKS_TO") String werks_to,
            @QueryParam("LGORT_TO") String lgort_to,
            @QueryParam("MATNR_TO") String matnr_to,
            @QueryParam("CHARG_TO") String charg_to,
            @QueryParam("MENGE_TO") String mengeToStr,
            @QueryParam("MEINS")    String meins
    ) {
        // Tutto uppercase come in R/3
        movid    = upper(movid);
        bwart    = upper(bwart);
        lifnr    = upper(lifnr);
        kunnr    = upper(kunnr);
        kostl    = upper(kostl);
        aufnr    = upper(aufnr);
        prctr    = upper(prctr);
        sobkz    = upper(sobkz);
        werks    = upper(werks);
        lgort    = upper(lgort);
        matnr    = upper(matnr);
        charg    = upper(charg);
        werks_to = upper(werks_to);
        lgort_to = upper(lgort_to);
        matnr_to = upper(matnr_to);
        charg_to = upper(charg_to);
        meins    = upper(meins);

        // --- Validazione campi obbligatori base ---
        StringBuilder missingFields = new StringBuilder();
        if (isEmpty(movid))  append(missingFields, "MOVID");
        if (isEmpty(bwart))  append(missingFields, "BWART");
        if (isEmpty(werks))  append(missingFields, "WERKS");
        if (isEmpty(lgort))  append(missingFields, "LGORT");
        if (isEmpty(matnr))  append(missingFields, "MATNR");

        // --- Validazione per bwart ---
        if (!isEmpty(bwart)) {
            switch (bwart) {
                case "309":
                    if (isEmpty(matnr_to))
                        append(missingFields, "MATNR_TO");
                    break;
                case "311":
                    if (isEmpty(mengeStr))
                        append(missingFields, "MENGE");
                    if (isEmpty(werks_to) && isEmpty(lgort_to))
                        append(missingFields, "WERKS_TO/LGORT_TO");
                    break;
                case "551":
                case "552":
                case "561":
                case "562":
                    if (isEmpty(mengeStr))
                        append(missingFields, "MENGE");
                    break;
                default:
                    return ok("Tipo movimento non gestito: " + bwart);
            }
        }

        if (missingFields.length() > 0) {
            return ok("Parametri non compilati: " + missingFields);
        }

        // --- Parsing quantità ---
        Float menge   = parseFloat(mengeStr);
        Float menge_to = parseFloat(mengeToStr);

        // --- Popolamento riga staging ---
        MovsapRiga riga = new MovsapRiga();
        riga.movid    = movid;
        riga.bwart    = bwart;
        riga.lifnr    = lifnr;
        riga.kunnr    = kunnr;
        riga.kostl    = kostl;
        riga.aufnr    = aufnr;
        riga.prctr    = prctr;
        riga.sobkz    = sobkz;
        riga.werks    = werks;
        riga.lgort    = lgort;
        riga.matnr    = matnr;
        riga.charg    = charg;
        riga.menge    = menge;
        riga.werks_to = werks_to;
        riga.lgort_to = lgort_to;
        riga.matnr_to = matnr_to;
        riga.charg_to = charg_to;
        riga.menge_to = menge_to;
        riga.meins    = meins;
        riga.datum    = LocalDate.now();
        riga.uzeit    = LocalTime.now();

        // Uname: non arriva dal WMS, usiamo un valore fisso identificativo
        riga.uname = "WMS";

        // --- Fase 1: UPSERT staging ---
        try {
            repo.upsertMovsap(riga);
        } catch (RepositoryException e) {
            return ok("Errore su inserimento TABFCSMOVSAP: " + e.getMessage());
        }

        // --- Fase 2: Elaborazione → S/4HC ---
        try {
            MovementResult result = client.postMovement(riga);

            // Successo → archivia e rimuovi dallo staging
            repo.archiviaDopoMovimento(movid, result.mblnr, result.mjahr);
            return ok("Documenti inviati a SAP | Documento: " + result.mblnr + " / " + result.mjahr);

        } catch (MovementException e) {
            // Errore SAP → marca wmsst='E', la riga rimane in staging
            try {
                repo.setWmsstErrore(movid);
            } catch (Exception ignored) { /* non nascondere l'errore principale */ }
            return ok("Errore elaborazione movimento: " + e.getMessage());

        } catch (RepositoryException e) {
            // Errore archiviazione post-SAP
            return ok("Errore archiviazione post-movimento: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Response ok(String text) {
        return Response.ok(text, MediaType.TEXT_PLAIN).build();
    }

    private boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }

    private String upper(String s) {
        return s == null ? null : s.toUpperCase().strip();
    }

    private void append(StringBuilder sb, String field) {
        if (sb.length() > 0) sb.append("-");
        sb.append(field);
    }

    private Float parseFloat(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            // Gestisce sia virgola che punto come separatore decimale
            return Float.parseFloat(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
