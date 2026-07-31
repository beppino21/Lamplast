package eOne.conditionsSD.model;

import java.time.LocalDate;

public class ListinoRow {

    public enum RowType {
        CUSTOMER,       // intestazione cliente
        HEADER_SCALE,   // riga soglie scaglione
        MATERIAL,       // riga materiale
        HEADER_ZONE,    // intestazione "Zone alternative"
        ZONE,           // riga zona con delta
        ALERT           // riga allarme scaglioni non allineati
    }

    private RowType   rowType;
    private String    customerCode;
    private String    customerName;
    private String    description;

    // Soglie scaglione (usate in HEADER_SCALE e MATERIAL)
    // scaleQty[i] = soglia della colonna i (0 = nessuna soglia = prezzo base)
    private double[]  scaleQty    = new double[5];
    private String    scaleUnit   = "";   // UM delle soglie (es. "TO")

    // Prezzi per colonna (MATERIAL: PPR0+ZTRA; ZONE: delta)
    private double[]  price       = new double[5];
    private int       activeCols  = 1;    // numero colonne effettivamente usate

    // Metadati riga
    private String    currency;
    private double    conditionQty;
    private String    conditionUnit;
    private LocalDate validFrom;
    private LocalDate validTo;
    private boolean   preferredZone;
    private boolean   absolutePrice;
    private String    customerMaterialCode = ""; // true per ZTRA puro: prezzo assoluto, non delta
    private boolean   unitMismatch;
    private String    language = "IT";   // lingua cliente (CUSTOMER row) per la stampa
    private double    minDeliveryQuantity;        // MATERIAL: lotto minimo (Customer-Material Info Record)
    private String    minDeliveryQuantityUnit = ""; // MATERIAL: UM del lotto minimo (BaseUnit, non l'UM di prezzo)
    private String    packagingNote = "";           // MATERIAL: imballo di default (Materiale Cliente Supplementare "IMBALLO"), non tradotto
    private String    paymentTerms = "";              // CUSTOMER: condizioni di pagamento (anagrafica)
    private String    incotermsClassification = "";   // CUSTOMER: Incoterms (codice, es. "FCA")
    private String    incotermsLocation = "";          // CUSTOMER: Incoterms (località)
    private String    paymentTermsText = "";            // CUSTOMER: testo descrittivo condizione di pagamento (custom OData)

    public ListinoRow() {}

    // ── Factory methods ───────────────────────────────────────────────────

    public static ListinoRow customerRow(String code, String name) {
        return customerRow(code, name, "IT");
    }

    public static ListinoRow customerRow(String code, String name, String language) {
        ListinoRow r = new ListinoRow();
        r.rowType      = RowType.CUSTOMER;
        r.customerCode = code;
        r.customerName = name;
        r.description  = code + "  —  " + name;
        r.language      = (language != null && !language.trim().isEmpty()) ? language : "IT";
        return r;
    }

    public static ListinoRow headerScaleRow(String customerCode,
                                             double[] scaleQty,
                                             String scaleUnit,
                                             int activeCols) {
        ListinoRow r    = new ListinoRow();
        r.rowType       = RowType.HEADER_SCALE;
        r.customerCode  = customerCode;
        r.description   = "Scaglioni:";
        r.scaleQty      = scaleQty != null ? scaleQty : new double[5];
        r.scaleUnit     = scaleUnit != null ? scaleUnit : "";
        r.activeCols    = activeCols;
        return r;
    }

    public static ListinoRow headerZoneRow(String customerCode) {
        ListinoRow r   = new ListinoRow();
        r.rowType      = RowType.HEADER_ZONE;
        r.customerCode = customerCode;
        r.description  = "Zone alternative";
        return r;
    }

    public static ListinoRow alertHeaderRow() {
        ListinoRow r  = new ListinoRow();
        r.rowType     = RowType.ALERT;
        r.description = "⚠  ATTENZIONE — Scaglioni non allineati: i delta per zona potrebbero non essere rappresentativi per i seguenti materiali:";
        return r;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public RowType   getRowType()                        { return rowType; }
    public void      setRowType(RowType v)               { this.rowType = v; }
    public String    getCustomerCode()                   { return customerCode; }
    public void      setCustomerCode(String v)           { this.customerCode = v; }
    public String    getCustomerName()                   { return customerName; }
    public void      setCustomerName(String v)           { this.customerName = v; }
    public String    getDescription()                    { return description; }
    public void      setDescription(String v)            { this.description = v; }
    public double[]  getScaleQty()                       { return scaleQty; }
    public void      setScaleQty(double[] v)             { this.scaleQty = v; }
    public String    getScaleUnit()                      { return scaleUnit; }
    public void      setScaleUnit(String v)              { this.scaleUnit = v; }
    public double[]  getPrice()                          { return price; }
    public void      setPrice(double[] v)                { this.price = v; }
    public int       getActiveCols()                     { return activeCols; }
    public void      setActiveCols(int v)                { this.activeCols = v; }
    public String    getCurrency()                       { return currency; }
    public void      setCurrency(String v)               { this.currency = v; }
    public double    getConditionQty()                   { return conditionQty; }
    public void      setConditionQty(double v)           { this.conditionQty = v; }
    public String    getConditionUnit()                  { return conditionUnit; }
    public void      setConditionUnit(String v)          { this.conditionUnit = v; }
    public LocalDate getValidFrom()                      { return validFrom; }
    public void      setValidFrom(LocalDate v)           { this.validFrom = v; }
    public LocalDate getValidTo()                        { return validTo; }
    public void      setValidTo(LocalDate v)             { this.validTo = v; }
    public boolean   isPreferredZone()                   { return preferredZone; }
    public void      setPreferredZone(boolean v)         { this.preferredZone = v; }
    public String    getLanguage()                       { return language; }
    public void      setLanguage(String v)                { this.language = (v != null && !v.trim().isEmpty()) ? v : "IT"; }
    public double    getMinDeliveryQuantity()            { return minDeliveryQuantity; }
    public void      setMinDeliveryQuantity(double v)    { this.minDeliveryQuantity = v; }
    public String    getMinDeliveryQuantityUnit()        { return minDeliveryQuantityUnit; }
    public void      setMinDeliveryQuantityUnit(String v) { this.minDeliveryQuantityUnit = v != null ? v : ""; }
    public String    getPackagingNote()                  { return packagingNote; }
    public void      setPackagingNote(String v)          { this.packagingNote = v != null ? v : ""; }
    public String    getPaymentTerms()                   { return paymentTerms; }
    public void      setPaymentTerms(String v)           { this.paymentTerms = v != null ? v : ""; }
    public String    getIncotermsClassification()        { return incotermsClassification; }
    public void      setIncotermsClassification(String v) { this.incotermsClassification = v != null ? v : ""; }
    public String    getIncotermsLocation()              { return incotermsLocation; }
    public void      setIncotermsLocation(String v)       { this.incotermsLocation = v != null ? v : ""; }
    public String    getPaymentTermsText()                { return paymentTermsText; }
    public void      setPaymentTermsText(String v)        { this.paymentTermsText = v != null ? v : ""; }

    /** Setter fluente, comodo in fase di costruzione della riga cliente. */
    public ListinoRow withPaymentTermsText(String v) { setPaymentTermsText(v); return this; }

    /** Setter fluente, comodo in fase di costruzione della riga cliente. */
    public ListinoRow withPaymentTerms(String v) { setPaymentTerms(v); return this; }
    public ListinoRow withIncoterms(String classification, String location) {
        setIncotermsClassification(classification);
        setIncotermsLocation(location);
        return this;
    }
    public boolean   isAbsolutePrice()                   { return absolutePrice; }
    public void      setAbsolutePrice(boolean v)         { this.absolutePrice = v; }
    public String    getCustomerMaterialCode()           { return customerMaterialCode != null ? customerMaterialCode : ""; }
    public void      setCustomerMaterialCode(String v)   { this.customerMaterialCode = v != null ? v : ""; }
    public boolean   isUnitMismatch()                    { return unitMismatch; }
    public void      setUnitMismatch(boolean v)          { this.unitMismatch = v; }

    // ── Utilità ──────────────────────────────────────────────────────────

    public boolean isCustomerRow()    { return rowType == RowType.CUSTOMER; }
    public boolean isHeaderScaleRow() { return rowType == RowType.HEADER_SCALE; }
    public boolean isMaterialRow()    { return rowType == RowType.MATERIAL; }
    public boolean isZoneRow()        { return rowType == RowType.ZONE; }
    public boolean isHeaderZoneRow()  { return rowType == RowType.HEADER_ZONE; }
    public boolean isAlertRow()       { return rowType == RowType.ALERT; }
    public boolean isHeaderRow()      { return rowType == RowType.HEADER_SCALE
                                            || rowType == RowType.HEADER_ZONE; }
}
