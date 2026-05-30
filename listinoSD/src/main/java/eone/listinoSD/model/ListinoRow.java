package eone.listinoSD.model;

import java.time.LocalDate;

public class ListinoRow {

    public enum RowType { CUSTOMER, HEADER_MAT, MATERIAL, HEADER_ZONE, ZONE }

    private RowType   rowType;
    private String    customerCode;
    private String    customerName;
    private String    description;
    private double[]  scaleQty  = new double[5];
    private double[]  price     = new double[5];
    private String    currency;
    private double    conditionQty;
    private String    conditionUnit;
    private LocalDate validFrom;
    private LocalDate validTo;
    private boolean   preferredZone;
    private boolean   unitMismatch;   // true se UM PPR0 ≠ UM ZTRA

    public ListinoRow() {}

    public static ListinoRow customerRow(String code, String name) {
        ListinoRow r = new ListinoRow();
        r.rowType      = RowType.CUSTOMER;
        r.customerCode = code;
        r.customerName = name;
        r.description  = code + " — " + name;
        return r;
    }

    public static ListinoRow headerMaterialRow(String customerCode, double[] scaleQty) {
        ListinoRow r   = new ListinoRow();
        r.rowType      = RowType.HEADER_MAT;
        r.customerCode = customerCode;
        r.scaleQty     = scaleQty != null ? scaleQty : new double[5];
        r.description  = "Materiale";
        return r;
    }

    public static ListinoRow headerZoneRow(String customerCode) {
        ListinoRow r   = new ListinoRow();
        r.rowType      = RowType.HEADER_ZONE;
        r.customerCode = customerCode;
        r.description  = "Zone alternative";
        return r;
    }

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
    public double[]  getPrice()                          { return price; }
    public void      setPrice(double[] v)                { this.price = v; }
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
    public boolean   isUnitMismatch()                    { return unitMismatch; }
    public void      setUnitMismatch(boolean v)          { this.unitMismatch = v; }

    public boolean isCustomerRow()  { return rowType == RowType.CUSTOMER; }
    public boolean isMaterialRow()  { return rowType == RowType.MATERIAL; }
    public boolean isZoneRow()      { return rowType == RowType.ZONE; }
    public boolean isHeaderRow()    { return rowType == RowType.HEADER_MAT
                                          || rowType == RowType.HEADER_ZONE; }
}
