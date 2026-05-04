package com.eone.fcs.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Rappresenta un cliente SAP.
 * Corrisponde a tabfcskna1 nel DB PostgreSQL (replica KNA1).
 */
public record Customer(
        String kunnr,   // codice cliente (PK)
        String name1,   // ragione sociale
        String name2,   // ragione sociale 2
        String stcd1,   // codice fiscale
        String stcd2,   // partita IVA (IT)
        String stceg    // partita IVA UE
) {
    public LocalDate datum()  { return LocalDate.now(); }
    public LocalTime uzeit()  { return LocalTime.now(); }
    public String    uname()  { return "S4HC_SYNC"; }
    public boolean   updfl()  { return true; }
}
