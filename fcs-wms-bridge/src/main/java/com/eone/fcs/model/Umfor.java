package com.eone.fcs.model;

import java.time.LocalDate;

/**
 * Fattori di conversione UM specifici per coppia Materiale/Fornitore.
 * Corrisponde a tabumfor nel DB PostgreSQL (progetto parametrizzazioni FCS).
 *
 * Chiave di ricerca: (matnr, lifnr) — per ogni coppia materiale/fornitore
 * esiste un solo record attivo (bstme è attributo del record, non parte della chiave).
 * Validità: il record con datab <= oggi più recente è quello attivo.
 */
public record Umfor(
        String    matnr,          // codice materiale
        String    lifnr,          // codice fornitore
        String    bstme,          // UM imballo/movimentazione (es. BAG, SAC, GRO)
        LocalDate datab,          // data inizio validità
        String    meins,          // UM di base (es. KG, ST)
        Double    mengexbstme,    // qta materiale per imballo (in meins)
        Integer   bstmexpallet    // imballi per pallet
) {
    /**
     * Chiave di lookup: "matnr|lifnr"
     * bstme NON è parte della chiave — è un attributo (l'UM imballo del fornitore).
     */
    public static String key(String matnr, String lifnr) {
        return matnr + "|" + lifnr;
    }

    public String key() {
        return key(matnr, lifnr);
    }
}
