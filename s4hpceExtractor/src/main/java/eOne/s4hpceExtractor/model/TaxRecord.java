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
    private String taxCode;               // da A_SlsPrcgConditionRecord
    private String validFrom;
    private String validTo;

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

    public String getTaxCode()            { return taxCode; }
    public void   setTaxCode(String v)    { this.taxCode = v; }

    public String getValidFrom()          { return validFrom; }
    public void   setValidFrom(String v)  { this.validFrom = v; }

    public String getValidTo()            { return validTo; }
    public void   setValidTo(String v)    { this.validTo = v; }
}
