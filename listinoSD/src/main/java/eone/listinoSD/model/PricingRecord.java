package eone.listinoSD.model;

import java.time.LocalDate;

public class PricingRecord {

    public enum ConditionType { PPR0, ZTRA }

    private ConditionType conditionType;
    private String    customer;
    private String    material;
    private String    zone;
    private double[]  scaleQty   = new double[5];
    private double[]  scalePrice = new double[5];
    private String    currency;
    private double    conditionQty;
    private String    conditionUnit;
    private LocalDate validFrom;
    private LocalDate validTo;

    public PricingRecord() {}

    public ConditionType getConditionType()              { return conditionType; }
    public void setConditionType(ConditionType t)        { this.conditionType = t; }
    public String getCustomer()                          { return customer; }
    public void setCustomer(String v)                    { this.customer = v; }
    public String getMaterial()                          { return material; }
    public void setMaterial(String v)                    { this.material = v; }
    public String getZone()                              { return zone; }
    public void setZone(String v)                        { this.zone = v; }
    public double[] getScaleQty()                        { return scaleQty; }
    public void setScaleQty(double[] v)                  { this.scaleQty = v; }
    public double[] getScalePrice()                      { return scalePrice; }
    public void setScalePrice(double[] v)                { this.scalePrice = v; }
    public String getCurrency()                          { return currency; }
    public void setCurrency(String v)                    { this.currency = v; }
    public double getConditionQty()                      { return conditionQty; }
    public void setConditionQty(double v)                { this.conditionQty = v; }
    public String getConditionUnit()                     { return conditionUnit; }
    public void setConditionUnit(String v)               { this.conditionUnit = v; }
    public LocalDate getValidFrom()                      { return validFrom; }
    public void setValidFrom(LocalDate v)                { this.validFrom = v; }
    public LocalDate getValidTo()                        { return validTo; }
    public void setValidTo(LocalDate v)                  { this.validTo = v; }
}
