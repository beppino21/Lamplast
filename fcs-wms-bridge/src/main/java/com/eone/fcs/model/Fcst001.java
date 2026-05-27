package com.eone.fcs.model;

/**
 * Rappresenta una riga di configurazione in TABFCST001.
 *
 * Determina quali combinazioni Tipo materiale / Divisione devono essere
 * considerate nell'export verso tabfcseket.
 *
 * Campi:
 *   mtart   - Tipo materiale (chiave, max 4 caratteri)
 *   werks   - Divisione / Plant (chiave, max 4 caratteri)
 *   exp2fcs - Flag: se true la combinazione è abilitata all'export FCS
 *
 * Corrisponde all'entity DOFCST001 del progetto fcs
 * (tabella TABFCST001, tenantColumn="tenant").
 */
public record Fcst001(
        String  mtart,
        String  werks,
        Boolean exp2fcs
) {
    /**
     * Chiave composita utilizzata come chiave nelle Map di lookup.
     * Formato: "mtart|werks"
     */
    public String key() {
        return key(mtart, werks);
    }

    public static String key(String mtart, String werks) {
        return trimmed(mtart) + "|" + trimmed(werks);
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.strip();
    }

    /**
     * Restituisce true se la riga è attiva (exp2fcs = true).
     * Un record con exp2fcs null o false disabilita l'export.
     */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(exp2fcs);
    }
}
