package com.eone.fcs.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Rappresenta un fornitore SAP.
 * Corrisponde a tabfcslfa1 nel DB PostgreSQL (replica LFA1).
 */
public record Supplier(
        String lifnr,   // codice fornitore (PK)
        String name1,   // ragione sociale
        String name2,   // ragione sociale 2
        String stcd1,   // codice fiscale
        String stcd2,   // partita IVA (IT)
        String stceg    // partita IVA UE
) {
    // Aggiunge i campi di audit per scrittura su DB
    public LocalDate datum()  { return LocalDate.now(); }
    public LocalTime uzeit()  { return LocalTime.now(); }
    public String    uname()  { return "S4HC_SYNC"; }
    public boolean   updfl()  { return true; }
}
