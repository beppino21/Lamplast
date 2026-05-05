package com.eone.fcs.service;

import com.eone.fcs.model.EketLine;
import com.eone.fcs.model.PesoMateriale;
import com.eone.fcs.model.Umfor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arricchisce le righe EKET con i campi calcolati del Gruppo 2.
 *
 * Logica allineata al programma ABAP ZFCS_EKET_EXPORT:
 *
 *   CASO 1 — Parametrizzato (record in tabumfor per matnr+lifnr):
 *     qtaxbag      = umfor.mengexbstme          (qta materiale per imballo, in meins)
 *     bstmexpallet = umfor.bstmexpallet          (imballi per pallet)
 *     nrbag        = CEIL(menge_open / qtaxbag)
 *     mengexbstme  = nrbag × qtaxbag             ← ABAP: nrbag * qtaxbag (non la divisione!)
 *     qtaxtag      = qtaxbag × bstmexpallet       (qta materiale per pallet intero)
 *     nrtag        = CEIL(menge_open / qtaxtag)
 *     tag_filler   = mengexbstme - menge_open     ← ABAP: eccedenza rispetto all'ordinato
 *
 *   CASO 2 — Sfuso (nessun record in tabumfor):
 *     bstme        = meins
 *     mengexbstme  = 1,  bstmexpallet = 1         ← ABAP: default a 1 (non null!)
 *     → stesse formule del Caso 1
 *
 *   PESI (entrambi i casi, da tabfcsmara):
 *     brgew_row = menge_open × mara.brgew
 *     ntgew_row = menge_open × mara.ntgew
 *     gewei     = mara.gewei
 */
public class EketEnricher {

    private static final Logger log = LoggerFactory.getLogger(EketEnricher.class);

    private EketEnricher() { /* utility class */ }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * @param lines       righe estratte da S/4HC (Gruppo 2 = null)
     * @param umforByKey  mappa fattori di conversione, chiave "matnr|lifnr"
     * @param pesiByMatnr mappa pesi unitari, chiave matnr
     * @return nuova lista con i campi del Gruppo 2 valorizzati
     */
    public static List<EketLine> enrich(
            List<EketLine> lines,
            Map<String, Umfor> umforByKey,
            Map<String, PesoMateriale> pesiByMatnr) {

        int parametrizzati = 0;
        int sfusi          = 0;

        List<EketLine> result = new ArrayList<>(lines.size());

        for (EketLine line : lines) {

            String key   = Umfor.key(trimmed(line.matnr()), trimmed(line.lifnr()));
            Umfor  umfor = umforByKey.get(key);

            EketLine enriched;
            if (umfor != null) {
                enriched = calculate(line, umfor, pesiByMatnr);
                parametrizzati++;
            } else {
                // Fallback sfuso: mengexbstme=1, bstmexpallet=1  (come da ABAP)
                log.debug("UMFOR non trovato per matnr={} lifnr={} → fallback sfuso (meins={}).",
                        line.matnr(), line.lifnr(), line.meins());
                enriched = calculateBulk(line, pesiByMatnr);
                sfusi++;
            }
            result.add(enriched);
        }

        log.info("EketEnricher completato: parametrizzati={}, sfusi={}",
                parametrizzati, sfusi);
        return result;
    }

    // -------------------------------------------------------------------------
    // CASO 1 — materiale con imballo parametrizzato (tabumfor presente)
    // -------------------------------------------------------------------------

    private static EketLine calculate(EketLine line, Umfor umfor,
                                      Map<String, PesoMateriale> pesiByMatnr) {
        Double  qtaxbag      = umfor.mengexbstme();   // qta materiale per imballo
        Integer bstmexpallet = umfor.bstmexpallet();  // imballi per pallet
        Double  mengeOpen    = line.mengeOpen();

        return buildEnriched(line, qtaxbag, bstmexpallet, mengeOpen,
                umfor.bstme(), pesiByMatnr);
    }

    // -------------------------------------------------------------------------
    // CASO 2 — materiale sfuso (nessuna parametrizzazione in tabumfor)
    // -------------------------------------------------------------------------

    /**
     * ABAP: IF sy-subrc <> 0 OR zfcs_umfor-mengexbstme = 0.
     *         bstme = ekpo-meins.  mengexbstme = 1.  bstmexpallet = 1.
     *       ENDIF.
     */
    private static EketLine calculateBulk(EketLine line,
                                          Map<String, PesoMateriale> pesiByMatnr) {
        return buildEnriched(line, 1.0, 1, line.mengeOpen(),
                line.meins(), pesiByMatnr);
    }

    // -------------------------------------------------------------------------
    // Calcolo comune (formule ABAP)
    // -------------------------------------------------------------------------

    /**
     * Implementa esattamente le formule ABAP:
     *
     *   nrbag       = CEIL( menge_open / qtaxbag )
     *   mengexbstme = nrbag * qtaxbag
     *   qtaxtag     = qtaxbag * bstmexpallet
     *   nrtag       = CEIL( menge_open / qtaxtag )
     *   tag_filler  = mengexbstme - menge_open
     *   brgew_row   = menge_open * mara.brgew
     *   ntgew_row   = menge_open * mara.ntgew
     */
    private static EketLine buildEnriched(EketLine line,
                                          Double  qtaxbag,
                                          Integer bstmexpallet,
                                          Double  mengeOpen,
                                          String  bstme,
                                          Map<String, PesoMateriale> pesiByMatnr) {
        // Guardie
        if (qtaxbag == null || qtaxbag == 0.0) qtaxbag = 1.0;
        if (bstmexpallet == null || bstmexpallet == 0) bstmexpallet = 1;

        // nrbag = CEIL( menge_open / qtaxbag )
        Integer nrbag = mengeOpen != null
                ? (int) Math.ceil(mengeOpen / qtaxbag) : null;

        // mengexbstme = nrbag * qtaxbag  (← ABAP, non menge_open / qtaxbag)
        Double mengexbstme = nrbag != null
                ? nrbag * qtaxbag : null;

        // qtaxtag = qtaxbag * bstmexpallet
        Double qtaxtag = qtaxbag * bstmexpallet;

        // nrtag = CEIL( menge_open / qtaxtag )
        Integer nrtag = (mengeOpen != null && qtaxtag > 0)
                ? (int) Math.ceil(mengeOpen / qtaxtag) : null;

        // tag_filler = mengexbstme - menge_open  (← ABAP)
        Double tagFiller = (mengexbstme != null && mengeOpen != null)
                ? mengexbstme - mengeOpen : null;

        // Pesi da tabfcsmara
        PesoMateriale peso = pesiByMatnr.get(trimmed(line.matnr()));
        Double brgewRow = null;
        Double ntgewRow = null;
        String gewei    = null;
        if (peso != null) {
            brgewRow = (mengeOpen != null && peso.brgew() != null)
                    ? mengeOpen * peso.brgew() : null;
            ntgewRow = (mengeOpen != null && peso.ntgew() != null)
                    ? mengeOpen * peso.ntgew() : null;
            gewei    = peso.gewei();
        }

        return EketLine.Builder.from(line)
                .mengexbstme(mengexbstme)
                .qtaxtag(qtaxtag)
                .bstmexpallet(bstmexpallet)
                .qtaxbag(qtaxbag)
                .tagFiller(tagFiller)
                .nrtag(nrtag)
                .nrbag(nrbag)
                .brgewRow(brgewRow)
                .ntgewRow(ntgewRow)
                .gewei(gewei)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String trimmed(String s) {
        return s == null ? null : s.strip();
    }
}
