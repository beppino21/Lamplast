package eOne.s4hpceExtractor.model;

/**
 * Rappresenta una singola riga di condizione PPR0 o ZTRA
 * così come estratta da A_SlsPrcgCndnRecdValidity.
 */
public class ConditionRecord {

    private String conditionType;      // PPR0 / ZTRA
    private String customer;           // SoldToParty
    private String material;           // Material (PPR0)
    private String salesDistrict;      // SalesDistrict (ZTRA)
    private double scaleQtyFrom;       // ConditionScaleQuantity (soglia da)
    private double scaleQtyTo;         // soglia a (calcolata)
    private String scaleUnit;          // ConditionScaleUoM
    private double price;              // ConditionRateValue
    private String currency;           // ConditionRateValueCurrency
    private double conditionQty;       // ConditionQuantity
    private String conditionUnit;      // ConditionQuantityUnit
    private String scaleType;          // PricingScaleType (A/B)
    private String validFrom;          // ValidityStartDate
    private String validTo;            // ValidityEndDate

    // Getters e Setters
    public String getConditionType()    { return conditionType; }
    public void   setConditionType(String v) { this.conditionType = v; }

    public String getCustomer()         { return customer; }
    public void   setCustomer(String v) { this.customer = v; }

    public String getMaterial()         { return material; }
    public void   setMaterial(String v) { this.material = v; }

    public String getSalesDistrict()    { return salesDistrict; }
    public void   setSalesDistrict(String v) { this.salesDistrict = v; }

    public double getScaleQtyFrom()     { return scaleQtyFrom; }
    public void   setScaleQtyFrom(double v) { this.scaleQtyFrom = v; }

    public double getScaleQtyTo()       { return scaleQtyTo; }
    public void   setScaleQtyTo(double v) { this.scaleQtyTo = v; }

    public String getScaleUnit()        { return scaleUnit; }
    public void   setScaleUnit(String v) { this.scaleUnit = v; }

    public double getPrice()            { return price; }
    public void   setPrice(double v)    { this.price = v; }

    public String getCurrency()         { return currency; }
    public void   setCurrency(String v) { this.currency = v; }

    public double getConditionQty()     { return conditionQty; }
    public void   setConditionQty(double v) { this.conditionQty = v; }

    public String getConditionUnit()    { return conditionUnit; }
    public void   setConditionUnit(String v) { this.conditionUnit = v; }

    public String getScaleType()        { return scaleType; }
    public void   setScaleType(String v) { this.scaleType = v; }

    public String getValidFrom()        { return validFrom; }
    public void   setValidFrom(String v) { this.validFrom = v; }

    public String getValidTo()          { return validTo; }
    public void   setValidTo(String v)  { this.validTo = v; }
}
