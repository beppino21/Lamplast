package lamplast.mbxa.logic.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calcolo dei pesi proposti (in KG) a partire dall'anagrafica prodotto. */
public final class Pesi {

    private static final BigDecimal MILLE = new BigDecimal("1000");
    private static final BigDecimal LIBBRA = new BigDecimal("0.45359237");

    private Pesi() {}

    /** Converte in KG; null se l'unità non è un'unità di peso riconosciuta. */
    public static BigDecimal toKg(BigDecimal value, String unit) {
        if (value == null || unit == null) return null;
        switch (unit.trim().toUpperCase()) {
            case "KG":
            case "KGM":
                return value;
            case "G":
            case "GR":
            case "GRM":
                return value.divide(MILLE);
            case "MG":
                return value.divide(MILLE).divide(MILLE);
            case "TO":
            case "T":
            case "TON":
            case "TNE":
                return value.multiply(MILLE);
            case "LB":
            case "LBR":
                return value.multiply(LIBBRA);
            default:
                return null;
        }
    }

    /**
     * Peso in KG di una riga.
     *
     * @param qtyBase    quantità nell'unità di misura di base
     * @param baseUnit   unità di misura di base del materiale
     * @param unitWeight peso (netto o lordo) per unità di base da anagrafica, può essere null
     * @param weightUnit unità del peso in anagrafica
     * @return peso arrotondato a 3 decimali, oppure null se non determinabile
     */
    public static BigDecimal rigaKg(BigDecimal qtyBase, String baseUnit, BigDecimal unitWeight, String weightUnit) {
        if (qtyBase == null) return null;
        BigDecimal kg = null;
        if (unitWeight != null && unitWeight.signum() > 0) {
            kg = toKg(qtyBase.multiply(unitWeight), weightUnit);
        }
        if (kg == null) {
            // materiale gestito a peso (es. base KG): il peso è la quantità stessa
            kg = toKg(qtyBase, baseUnit);
        }
        return kg == null ? null : kg.setScale(3, RoundingMode.HALF_UP);
    }
}
