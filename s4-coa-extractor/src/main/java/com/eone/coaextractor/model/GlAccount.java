package com.eone.coaextractor.model;

/**
 * Rappresenta un conto del piano dei conti con le sue descrizioni.
 *
 *   - GLAccountName     → testo breve (20 car.)
 *   - GLAccountLongName → testo esteso (50 car.)
 */
public record GlAccount(
    String chartOfAccounts,
    String glAccount,
    String shortText,
    String longText
) {
    public String bestDescription() {
        if (longText != null && !longText.isBlank()) return longText.trim();
        if (shortText != null && !shortText.isBlank()) return shortText.trim();
        return "";
    }
}
