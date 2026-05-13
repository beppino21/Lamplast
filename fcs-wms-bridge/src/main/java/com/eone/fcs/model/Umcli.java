package com.eone.fcs.model;

import java.time.LocalDate;

/**
 * Fattori di conversione UM specifici per coppia Materiale/Cliente.
 * Corrisponde a tabumcli nel DB PostgreSQL.
 *
 * Speculare a Umfor (che usa lifnr=fornitore), ma usa kunnr=cliente.
 * Usato da EketEnricher per le righe di reso da cliente (kappl='V').
 *
 * Chiave di ricerca: (matnr, kunnr) — per ogni coppia materiale/cliente
 * esiste un solo record attivo. Validità: il record con datab <= oggi
 * più recente è quello attivo.
 */
public record Umcli(
        String    matnr,          // codice materiale
        String    kunnr,          // codice cliente
        String    bstme,          // UM imballo/movimentazione (es. BAG, SAC, GRO)
        LocalDate datab,          // data inizio validità
        String    meins,          // UM di base (es. KG, ST)
        Double    mengexbstme,    // qta materiale per imballo (in meins)
        Integer   bstmexpallet    // imballi per pallet
) {
    /**
     * Chiave di lookup: "matnr|kunnr"
     * Speculare a Umfor.key(matnr, lifnr).
     */
    public static String key(String matnr, String kunnr) {
        return matnr + "|" + kunnr;
    }

    public String key() {
        return key(matnr, kunnr);
    }
}
