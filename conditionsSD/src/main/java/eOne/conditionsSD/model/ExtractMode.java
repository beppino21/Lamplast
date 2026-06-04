package eOne.conditionsSD.model;

/**
 * Modalità di estrazione del listino.
 * FULL     = PPR0 + ZTRA integrati (comportamento originale)
 * PPR0     = solo prezzi materiale, senza trasporto
 * ZTRA     = solo tariffe trasporto, prezzi assoluti per zona
 */
public enum ExtractMode {
    FULL, PPR0, ZTRA
}
