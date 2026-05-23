package lamplast.utility.model;

import java.time.LocalDate;

/**
 * Rappresenta i dati di una schedulazione
 */
public class ScheduleLineData {

    private String    orderNumber;
    private Integer   itemNumber;
    private Integer   scheduleLine;
    private String    material;
    private String    materialText;
    private String    quantity;
    private LocalDate productionDate;

    // Esito elaborazione SAP
    private String processingResult;   // "OK", "KO", "PENDING", null
    private String errorMessage;

    // Esito dry-run (verifica preventiva — separato dall'esito reale)
    private String dryRunResult;

    /**
     * Numero schedulazione assegnato da SAP dopo un INSERT riuscito.
     * Valorizzato dalla response HTTP 201 (campo d.ScheduleLine).
     * Per cancellazioni o errori: "0".
     * Per PATCH riusciti: null (non mostrato in griglia).
     */
    private String createdScheduleLine;

    public ScheduleLineData() {}

    public ScheduleLineData(String orderNumber, Integer itemNumber,
                            Integer scheduleLine, String quantity,
                            LocalDate productionDate) {
        this.orderNumber  = orderNumber;
        this.itemNumber   = itemNumber;
        this.scheduleLine = scheduleLine;
        this.quantity     = quantity;
        this.productionDate = productionDate;
    }

    // -------------------------------------------------------
    // GETTERS / SETTERS
    // -------------------------------------------------------

    public String    getOrderNumber()    { return orderNumber; }
    public void      setOrderNumber(String v)    { this.orderNumber = v; }

    public Integer   getItemNumber()     { return itemNumber; }
    public void      setItemNumber(Integer v)    { this.itemNumber = v; }

    public Integer   getScheduleLine()   { return scheduleLine; }
    public void      setScheduleLine(Integer v)  { this.scheduleLine = v; }

    public String    getMaterial()       { return material; }
    public void      setMaterial(String v)       { this.material = v; }

    public String    getMaterialText()   { return materialText; }
    public void      setMaterialText(String v)   { this.materialText = v; }

    public String    getQuantity()       { return quantity; }
    public void      setQuantity(String v)       { this.quantity = v; }

    public LocalDate getProductionDate() { return productionDate; }
    public void      setProductionDate(LocalDate v) { this.productionDate = v; }

    public String    getProcessingResult()       { return processingResult; }
    public void      setProcessingResult(String v) { this.processingResult = v; }

    public String    getErrorMessage()           { return errorMessage; }
    public void      setErrorMessage(String v)   { this.errorMessage = v; }

    public String    getDryRunResult()           { return dryRunResult; }
    public void      setDryRunResult(String v)   { this.dryRunResult = v; }

    public String    getCreatedScheduleLine()    { return createdScheduleLine; }
    public void      setCreatedScheduleLine(String v) { this.createdScheduleLine = v; }

    // -------------------------------------------------------
    // LOGICA
    // -------------------------------------------------------

    /** Schedulazione negativa = inserimento di una nuova schedulazione. */
    public boolean isInsert() {
        return scheduleLine != null && scheduleLine < 0;
    }

    /** Quantità = 0 (o blank) su schedulazione esistente = cancellazione. */
    public boolean isDelete() {
        if (isInsert()) return false;
        if (quantity == null || quantity.isBlank()) return true;
        try { return Double.parseDouble(quantity.replace(",", ".")) == 0.0; }
        catch (Exception e) { return false; }
    }

    /** Validazione base dei dati obbligatori. */
    public String validate() {
        if (orderNumber == null || orderNumber.isBlank()) return "Ordine mancante";
        if (itemNumber   == null)                          return "Posizione mancante";
        if (scheduleLine == null)                          return "Schedulazione mancante";
        if (quantity     == null || quantity.isBlank())    return "Quantità mancante";
        if (productionDate == null)                        return "Data produzione mancante";
        return null; // OK
    }

    @Override
    public String toString() {
        return "ScheduleLineData{order=" + orderNumber
             + ", item="  + itemNumber
             + ", sched=" + scheduleLine
             + ", qty="   + quantity
             + ", date="  + productionDate + '}';
    }
}
