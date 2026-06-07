package eOne.s4hpceExtractor.model;

/**
 * Rappresenta una singola riga di condizione TTX1 (determinazione IVA vendite).
 */
public class TaxRecord {

    private String conditionType;
    private String departureCountry;       // DepartureCountry
    private String destinationCountry;     // DestinationCountry
    private String customerTaxClass;       // CustomerTaxClassification1
    private String productTaxClass;        // ProductTaxClassification1
    private String conditionTaxCode;    // ConditionTaxCode (codice IVA es. A1, V1)
    private String taxRate;             // ConditionRateValue (aliquota %)
    private String validFrom;
    private String validTo;
    private boolean deleted;

    public String getConditionType()      { return conditionType; }
    public void   setConditionType(String v)  { this.conditionType = v; }

    public String getDepartureCountry()   { return departureCountry; }
    public void   setDepartureCountry(String v) { this.departureCountry = v; }

    public String getDestinationCountry() { return destinationCountry; }
    public void   setDestinationCountry(String v) { this.destinationCountry = v; }

    public String getCustomerTaxClass()   { return customerTaxClass; }
    public void   setCustomerTaxClass(String v) { this.customerTaxClass = v; }

    public String getProductTaxClass()    { return productTaxClass; }
    public void   setProductTaxClass(String v) { this.productTaxClass = v; }

    public String getConditionTaxCode()      { return conditionTaxCode; }
    public void   setConditionTaxCode(String v) { this.conditionTaxCode = v; }

    public String getTaxRate()               { return taxRate; }
    public void   setTaxRate(String v)       { this.taxRate = v; }

    public String getValidFrom()          { return validFrom; }
    public void   setValidFrom(String v)  { this.validFrom = v; }

    public String getValidTo()            { return validTo; }
    public void   setValidTo(String v)    { this.validTo = v; }
    public boolean isDeleted()            { return deleted; }
    public void    setDeleted(boolean v)  { this.deleted = v; }
}
