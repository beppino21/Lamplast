package com.eone.fcs.service;

import com.eone.fcs.model.EketLine;
import com.eone.fcs.model.PesoMateriale;
import com.eone.fcs.model.Umcli;
import com.eone.fcs.model.Umfor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arricchisce le righe EKET/VBEP con i campi calcolati del Gruppo 2
 * e con il nome del partner (fornitore o cliente).
 *
 * Logica allineata al programma ABAP ZFCS_EKET_EXPORT:
 *
 *   CASO 1 — Parametrizzato:
 *     OdA (kappl != 'V'): lookup in umforByKey  (matnr|lifnr → tabumfor)
 *     Reso (kappl = 'V'): lookup in umcliByKey  (matnr|kunnr → tabumcli)
 *                         dove kunnr = lifnr della EketLine (coerenza modello)
 *
 *     qtaxbag      = umfor/umcli.mengexbstme
 *     bstmexpallet = umfor/umcli.bstmexpallet
 *     nrbag        = CEIL(menge_open / qtaxbag)
 *     mengexbstme  = nrbag × qtaxbag
 *     qtaxtag      = qtaxbag × bstmexpallet
 *     nrtag        = CEIL(menge_open / qtaxtag)
 *     tag_filler   = mengexbstme - menge_open
 *
 *   CASO 2 — Sfuso (nessun record in tabumfor/tabumcli):
 *     bstme = meins, mengexbstme = 1, bstmexpallet = 1 → stesse formule
 *
 *   PESI (entrambi i casi, da tabfcsmara):
 *     brgew_row = menge_open × mara.brgew
 *     ntgew_row = menge_open × mara.ntgew
 *     gewei     = mara.gewei
 *
 *   NOME PARTNER:
 *     OdA (kappl != 'V'): name1 = nomiFornitori[lifnr]  (da tabfcslfa1)
 *     Reso (kappl = 'V'): name1 = nomiClienti[lifnr]    (da tabfcskna1,
 *                                                         lifnr = kunnr per coerenza modello)
 */
public class EketEnricher {

    private static final Logger log = LoggerFactory.getLogger(EketEnricher.class);

    private EketEnricher() { /* utility class */ }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * @param lines          righe estratte da S/4HC (Gruppo 2 = null, name1 = null)
     * @param umforByKey     fattori conversione OdA,  chiave "matnr|lifnr"
     * @param umcliByKey     fattori conversione resi, chiave "matnr|kunnr"
     *                       (kunnr = lifnr nelle EketLine di reso per coerenza modello)
     * @param pesiByMatnr    pesi unitari, chiave matnr
     * @param nomiFornitori  nome1 fornitore, chiave lifnr  (per OdA)
     * @param nomiClienti    nome1 cliente,   chiave kunnr  (per resi, kunnr = lifnr)
     * @return nuova lista con Gruppo 2, name1 valorizzati
     */
    public static List<EketLine> enrich(
            List<EketLine>            lines,
            Map<String, Umfor>        umforByKey,
            Map<String, Umcli>        umcliByKey,
            Map<String, PesoMateriale> pesiByMatnr,
            Map<String, String>       nomiFornitori,
            Map<String, String>       nomiClienti) {

        int parametrizzati = 0;
        int sfusi          = 0;

        List<EketLine> result = new ArrayList<>(lines.size());

        for (EketLine line : lines) {

            boolean isReso = config_kapplReso.equals(line.kappl());

            // --- Lookup fattori conversione ---
            String   partner = trimmed(line.lifnr());
            String   matnr   = trimmed(line.matnr());
            EketLine enriched;

            if (isReso) {
                // Reso: tabumcli, chiave matnr|kunnr (kunnr = lifnr)
                String  keyUmcli = Umcli.key(matnr, partner);
                Umcli   umcli    = umcliByKey.get(keyUmcli);

                if (umcli != null) {
                    enriched = calculateFromUmcli(line, umcli, pesiByMatnr);
                    parametrizzati++;
                } else {
                    log.debug("UMCLI non trovato per matnr={} kunnr={} → fallback sfuso.",
                            matnr, partner);
                    enriched = calculateBulk(line, pesiByMatnr);
                    sfusi++;
                }
            } else {
                // OdA: tabumfor, chiave matnr|lifnr
                String keyUmfor = Umfor.key(matnr, partner);
                Umfor  umfor    = umforByKey.get(keyUmfor);

                if (umfor != null) {
                    enriched = calculateFromUmfor(line, umfor, pesiByMatnr);
                    parametrizzati++;
                } else {
                    log.debug("UMFOR non trovato per matnr={} lifnr={} → fallback sfuso (meins={}).",
                            matnr, partner, line.meins());
                    enriched = calculateBulk(line, pesiByMatnr);
                    sfusi++;
                }
            }

            // --- Popolamento name1 ---
            String nome = isReso
                    ? nomiClienti.get(partner)
                    : nomiFornitori.get(partner);

            if (nome != null && !nome.isBlank()) {
                enriched = EketLine.Builder.from(enriched).name1(nome).build();
            }

            result.add(enriched);
        }

        log.info("EketEnricher completato: parametrizzati={}, sfusi={}", parametrizzati, sfusi);
        return result;
    }

    // Valore kappl per i resi — allineato a AppConfig.kapplReso default
    // Non leggiamo la config qui (utility class stateless) — confrontiamo col valore
    // standard 'V'. Se il cliente usa un kappl diverso andrà adattato.
    private static final String config_kapplReso = "V";

    // -------------------------------------------------------------------------
    // CASO 1a — OdA parametrizzato (da tabumfor)
    // -------------------------------------------------------------------------

    private static EketLine calculateFromUmfor(EketLine line, Umfor umfor,
                                               Map<String, PesoMateriale> pesiByMatnr) {
        Double  qtaxbag      = umfor.mengexbstme();
        Integer bstmexpallet = umfor.bstmexpallet();
        return buildEnriched(line, qtaxbag, bstmexpallet, line.mengeOpen(),
                umfor.bstme(), pesiByMatnr);
    }

    // -------------------------------------------------------------------------
    // CASO 1b — Reso parametrizzato (da tabumcli)
    // -------------------------------------------------------------------------

    private static EketLine calculateFromUmcli(EketLine line, Umcli umcli,
                                               Map<String, PesoMateriale> pesiByMatnr) {
        Double  qtaxbag      = umcli.mengexbstme() != null
                               ? umcli.mengexbstme().doubleValue() : null;
        Integer bstmexpallet = umcli.bstmexpallet();
        return buildEnriched(line, qtaxbag, bstmexpallet, line.mengeOpen(),
                umcli.bstme(), pesiByMatnr);
    }

    // -------------------------------------------------------------------------
    // CASO 2 — Sfuso (nessuna parametrizzazione)
    // -------------------------------------------------------------------------

    private static EketLine calculateBulk(EketLine line,
                                          Map<String, PesoMateriale> pesiByMatnr) {
        return buildEnriched(line, 1.0, 1, line.mengeOpen(),
                line.meins(), pesiByMatnr);
    }

    // -------------------------------------------------------------------------
    // Calcolo comune (formule ABAP)
    // -------------------------------------------------------------------------

    private static EketLine buildEnriched(EketLine line,
                                          Double  qtaxbag,
                                          Integer bstmexpallet,
                                          Double  mengeOpen,
                                          String  bstme,
                                          Map<String, PesoMateriale> pesiByMatnr) {
        if (qtaxbag == null || qtaxbag == 0.0) qtaxbag = 1.0;
        if (bstmexpallet == null || bstmexpallet == 0) bstmexpallet = 1;

        // nrbag = CEIL( menge_open / qtaxbag )
        Integer nrbag = mengeOpen != null
                ? (int) Math.ceil(mengeOpen / qtaxbag) : null;

        // mengexbstme = nrbag * qtaxbag  (← ABAP)
        Double mengexbstme = nrbag != null ? nrbag * qtaxbag : null;

        // qtaxtag = qtaxbag * bstmexpallet
        Double qtaxtag = qtaxbag * bstmexpallet;

        // nrtag = CEIL( menge_open / qtaxtag )
        Integer nrtag = (mengeOpen != null && qtaxtag > 0)
                ? (int) Math.ceil(mengeOpen / qtaxtag) : null;

        // tag_filler = mengexbstme - menge_open
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
                .bstme(bstme)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String trimmed(String s) {
        return s == null ? null : s.strip();
    }
}
