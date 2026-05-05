package com.eone.fcs.model;

/**
 * Pesi unitari di un materiale, letti da tabfcsmara.
 * Usato da EketEnricher per calcolare brgew_row e ntgew_row.
 */
public record PesoMateriale(
        String matnr,   // codice materiale
        Double brgew,   // peso lordo unitario
        Double ntgew,   // peso netto unitario
        String gewei    // UM peso (es. KG)
) {}