package lamplast.mbxa.logic.sap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Documento materiale letto da API_MATERIAL_DOCUMENT_SRV (testata + tutte le righe,
 * comprese le righe di contropartita generate automaticamente).
 */
public class SapMaterialDocument {

    public String year;
    public String number;
    public LocalDate postingDate;
    public LocalDate documentDate;
    public LocalDate creationDate;
    public LocalTime creationTime;
    public String createdByUser;
    public String headerText;
    public String referenceDocument;
    public final List<Item> items = new ArrayList<>();

    /** Riga del documento materiale. */
    public static class Item {
        public String item;
        public String material;
        public String plant;
        public String storageLocation;
        public String batch;
        public String movementType;
        /** H = avere (uscita), S = dare (entrata). */
        public String debitCredit;
        /** "X" per la riga di contropartita creata automaticamente. */
        public String autoCreated;
        public String receivingPlant;
        public String receivingStorageLoc;
        public BigDecimal qtyEntry;
        public String entryUnit;
        public BigDecimal qtyBase;
        public String baseUnit;
        public boolean cancelled;
        public String itemText;

        public boolean isAutomatic() { return "X".equalsIgnoreCase(autoCreated); }
        public boolean isIssue() { return "H".equalsIgnoreCase(debitCredit); }
    }

    /**
     * Righe da stampare sul DDT: solo le righe di USCITA del 301 inserite dall'utente.
     * La riga di entrata nello stabilimento ricevente (creata automaticamente) viene esclusa,
     * altrimenti ogni quantità comparirebbe due volte.
     */
    public List<Item> getOutgoingTransferItems() {
        List<Item> result = new ArrayList<>();
        for (Item it : items) {
            if (!"301".equals(it.movementType)) continue;
            if (it.isAutomatic()) continue;
            if (!it.isIssue()) continue;
            result.add(it);
        }
        return result;
    }

    /** True se almeno una riga di uscita risulta stornata (es. con un 302). */
    public boolean isCancelled() {
        for (Item it : getOutgoingTransferItems()) {
            if (it.cancelled) return true;
        }
        return false;
    }
}
