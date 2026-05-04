package com.eone.fcs.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Rappresenta una riga di schedulazione OdA (EKET).
 * Corrisponde a tabfcseket nel DB PostgreSQL.
 *
 * I campi sono divisi in tre gruppi:
 *   - Gruppo 1: da S/4HC via API (popolati da PurchaseOrderClient)
 *   - Gruppo 2: calcolati dal middleware (logica pallet)
 *   - Gruppo 3: comunicati dal WMS RFID (prefisso in_) - NON toccati da questo extractor
 */
public record EketLine(

        // --- Chiave ---
        String    tenant,
        String    ebeln,        // numero OdA
        String    ebelp,        // posizione OdA
        String    etenr,        // numero schedulazione

        // --- Gruppo 1: da S/4HC ---
        String    kappl,        // applicazione (ME)
        String    idEket,       // ID univoco eket (ebeln+ebelp+etenr)
        Boolean   xchpf,        // gestione lotti
        LocalDate eindt,        // data consegna schedulata
        String    lifnr,        // codice fornitore
        String    name1,        // nome fornitore
        String    mtart,        // tipo materiale
        String    matnr,        // codice materiale
        String    maktx,        // descrizione materiale
        String    werks,        // plant
        String    lgort,        // magazzino
        Double    menge,        // quantità schedulata
        Double    wemng,        // quantità già consegnata
        Double    mengeOpen,    // quantità aperta (menge - wemng)
        String    meins,        // unità di misura base
        String    bstme,        // unità di misura OdA

        // --- Gruppo 2: calcolati (inizialmente null, calcolati dopo) ---
        Double    mengexbstme,  // conversione quantità in bstme
        Double    qtaxtag,      // quantità per pallet
        Integer   bstmexpallet, // bstme per pallet
        Double    qtaxbag,      // quantità per bag/collo
        Double    tagFiller,    // filler pallet
        Integer   nrtag,        // numero pallet attesi
        Integer   nrbag,        // numero bag attesi
        Double    brgewRow,     // peso lordo riga
        Double    ntgewRow,     // peso netto riga
        String    gewei,        // unità peso

        // --- Campi di gestione ---
        LocalDate datum,        // data creazione/aggiornamento record
        LocalTime uzeit,        // ora creazione/aggiornamento
        String    ernam,        // utente creazione
        String    wmsst         // stato WMS: ' '=attesa, 'I'=in scarico, 'C'=completata, 'E'=errore

        // NOTA: i campi in_ (WMS inbound) non sono inclusi nel record
        // perché vengono scritti esclusivamente dal sistema WMS RFID
        // e non devono mai essere sovrascritti da questo extractor.
) {

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {
        private String    tenant    = "FCS";
        private String    ebeln;
        private String    ebelp;
        private String    etenr;
        private String    kappl     = "ME";
        private Boolean   xchpf;
        private LocalDate eindt;
        private String    lifnr;
        private String    name1;
        private String    mtart;
        private String    matnr;
        private String    maktx;
        private String    werks;
        private String    lgort;
        private Double    menge;
        private Double    wemng;
        private Double    mengeOpen;
        private String    meins;
        private String    bstme;
        private String    wmsst     = " ";  // default: in attesa

        public Builder tenant(String v)    { this.tenant = v;    return this; }
        public Builder ebeln(String v)     { this.ebeln = v;     return this; }
        public Builder ebelp(String v)     { this.ebelp = v;     return this; }
        public Builder etenr(String v)     { this.etenr = v;     return this; }
        public Builder xchpf(Boolean v)    { this.xchpf = v;     return this; }
        public Builder eindt(LocalDate v)  { this.eindt = v;     return this; }
        public Builder lifnr(String v)     { this.lifnr = v;     return this; }
        public Builder name1(String v)     { this.name1 = v;     return this; }
        public Builder mtart(String v)     { this.mtart = v;     return this; }
        public Builder matnr(String v)     { this.matnr = v;     return this; }
        public Builder maktx(String v)     { this.maktx = v;     return this; }
        public Builder werks(String v)     { this.werks = v;     return this; }
        public Builder lgort(String v)     { this.lgort = v;     return this; }
        public Builder menge(Double v)     { this.menge = v;     return this; }
        public Builder wemng(Double v)     { this.wemng = v;     return this; }
        public Builder mengeOpen(Double v) { this.mengeOpen = v; return this; }
        public Builder meins(String v)     { this.meins = v;     return this; }
        public Builder bstme(String v)     { this.bstme = v;     return this; }
        public Builder wmsst(String v)     { this.wmsst = v;     return this; }

        public EketLine build() {
        	String id = String.format("%10s%5s%4s",
        	        ebeln != null ? ebeln : "",
        	        ebelp != null ? ebelp : "",
        	        etenr != null ? etenr : "")
        	        .replace(' ', '0');
            return new EketLine(
                    tenant, ebeln, ebelp, etenr,
                    kappl, id, xchpf, eindt,
                    lifnr, name1, mtart, matnr, maktx, werks, lgort,
                    menge, wemng, mengeOpen, meins, bstme,
                    null, null, null, null, null, null, null, null, null, null,
                    LocalDate.now(), LocalTime.now(), "S4HC_SYNC", wmsst
            );
        }
    }
}
