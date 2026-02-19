package com.example.coaextractor.model;

/**
 * Rappresenta un conto del piano dei conti con le sue descrizioni.
 *
 * Le due descrizioni corrispondono ai campi OData:
 *   - ShortText  → testo breve (20 car.) dal segment GLAccountText
 *   - LongText   → testo esteso (50 car.) dal segment GLAccountText
 *
 * Entrambi possono essere null se il record testo non esiste
 * per la lingua richiesta.
 */
public record GlAccount(
    String chartOfAccounts,
    String glAccount,
    String shortText,
    String longText
) {
    /**
     * Restituisce la descrizione "migliore" disponibile:
     * LongText se presente, altrimenti ShortText, altrimenti stringa vuota.
     */
    public String bestDescription() {
        if (longText != null && !longText.isBlank()) return longText.trim();
        if (shortText != null && !shortText.isBlank()) return shortText.trim();
        return "";
    }
}
