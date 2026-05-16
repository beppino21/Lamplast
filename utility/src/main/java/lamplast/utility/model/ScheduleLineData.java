package lamplast.utility.model;

import java.time.LocalDate;

/**
 * Rappresenta i dati di una schedulazione
 */
public class ScheduleLineData {
    
    private String orderNumber;
    private Integer itemNumber;
    private Integer scheduleLine;
    private String material;
    private String materialText;
    private String quantity;
    private LocalDate productionDate;
    
    // Esito elaborazione SAP
    private String processingResult;  // "OK", "KO", "PENDING", null
    private String errorMessage;

    // Esito dry-run (verifica preventiva — separato dall'esito reale)
    private String dryRunResult;
    
    public ScheduleLineData() {
    }
    
    public ScheduleLineData(String orderNumber, Integer itemNumber, 
                           Integer scheduleLine, String quantity, 
                           LocalDate productionDate) {
        this.orderNumber = orderNumber;
        this.itemNumber = itemNumber;
        this.scheduleLine = scheduleLine;
        this.quantity = quantity;
        this.productionDate = productionDate;
    }
    
    // Getters e Setters
    
    public String getOrderNumber() {
        return orderNumber;
    }
    
    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }
    
    public Integer getItemNumber() {
        return itemNumber;
    }
    
    public void setItemNumber(Integer itemNumber) {
        this.itemNumber = itemNumber;
    }
    
    public Integer getScheduleLine() {
        return scheduleLine;
    }
    
    public void setScheduleLine(Integer scheduleLine) {
        this.scheduleLine = scheduleLine;
    }
    
    public String getMaterial() {
        return material;
    }
    
    public void setMaterial(String material) {
        this.material = material;
    }
    
    public String getMaterialText() {
        return materialText;
    }
    
    public void setMaterialText(String materialText) {
        this.materialText = materialText;
    }
    
    public String getQuantity() {
        return quantity;
    }
    
    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }
    
    public LocalDate getProductionDate() {
        return productionDate;
    }
    
    public void setProductionDate(LocalDate productionDate) {
        this.productionDate = productionDate;
    }
    
    public String getProcessingResult() {
        return processingResult;
    }
    
    public void setProcessingResult(String processingResult) {
        this.processingResult = processingResult;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getDryRunResult()            { return dryRunResult; }
    public void   setDryRunResult(String v)      { this.dryRunResult = v; }

    /**
     * Verifica se questa schedulazione è un inserimento (negativo)
     */
    public boolean isInsert() {
        return scheduleLine != null && scheduleLine < 0;
    }
    
    /**
     * Validazione base dei dati obbligatori
     */
    public String validate() {
        if (orderNumber == null || orderNumber.isBlank()) {
            return "Ordine mancante";
        }
        if (itemNumber == null) {
            return "Posizione mancante";
        }
        if (scheduleLine == null) {
            return "Schedulazione mancante";
        }
        if (quantity == null || quantity.isBlank()) {
            return "Quantità mancante";
        }
        if (productionDate == null) {
            return "Data produzione mancante";
        }
        return null; // OK
    }
    
    @Override
    public String toString() {
        return "ScheduleLineData{" +
               "order=" + orderNumber +
               ", item=" + itemNumber +
               ", sched=" + scheduleLine +
               ", qty=" + quantity +
               ", date=" + productionDate +
               '}';
    }
}