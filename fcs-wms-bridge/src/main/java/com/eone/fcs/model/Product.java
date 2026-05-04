package com.eone.fcs.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Rappresenta un prodotto/materiale SAP.
 * Corrisponde a tabfcsmara nel DB PostgreSQL.
 */
public record Product(
        String    matnr,    // codice materiale (PK)
        String    maktx,    // descrizione (da A_ProductDescription)
        String    mtart,    // tipo materiale
        String    matkl,    // gruppo merceologico
        String    meins,    // unità di misura base
        String    bstme,    // unità di misura ordine acquisto
        LocalDate datum,    // data ultimo aggiornamento
        LocalTime uzeit,    // ora ultimo aggiornamento
        String    uname     // utente ultimo aggiornamento
) {

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {
        private String matnr;
        private String maktx;
        private String mtart;
        private String matkl;
        private String meins;
        private String bstme;

        public String getMatnr() { return matnr; }

        public Builder matnr(String v)  { this.matnr = v; return this; }
        public Builder maktx(String v)  { this.maktx = v; return this; }
        public Builder mtart(String v)  { this.mtart = v; return this; }
        public Builder matkl(String v)  { this.matkl = v; return this; }
        public Builder meins(String v)  { this.meins = v; return this; }
        public Builder bstme(String v)  { this.bstme = v; return this; }

        public Product build() {
            return new Product(matnr, maktx, mtart, matkl, meins, bstme,
                    LocalDate.now(), LocalTime.now(), "S4HC_SYNC");
        }
    }
}
