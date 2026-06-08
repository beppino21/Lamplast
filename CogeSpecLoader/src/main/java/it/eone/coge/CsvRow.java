package it.eone.coge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Rappresenta una riga del CSV S_BSID_COGE_SPEC.
 * Il CSV usa separatore ";" e valori racchiusi in "..." oppure ="..."
 * (formato SAP WS_DOWNLOAD con protezione zero iniziale).
 * Gli importi sono in formato italiano: 84.904,68
 * Le date sono in formato italiano: 14.01.2026
 */
public class CsvRow {

    private static final DateTimeFormatter DATE_IT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Campi chiave
    public final String bukrs;
    public final String xblnr;      // numero doc. riferimento (o chiave artificiale)
    public final String docln;
    public final String kunnr;
    public final String glAccount;  // conto riconciliazione
    public final String gkont;      // conto contropartita (transitorio)
    public final String blart;      // tipo documento
    public final String umskz;      // Co.Ge. Speciale (es. "S")

    // Date
    public final String bldatIso;   // data documento -> YYYY-MM-DD
    public final String zfbdtIso;   // data base/scadenza -> YYYY-MM-DD

    // Importi
    public final BigDecimal wrbtr;  // importo divisa documento
    public final String waers;      // divisa documento (es. EUR)
    public final BigDecimal dmbtr;  // importo divisa societa'

    // Pagamento
    public final String zterm;      // condizioni pagamento
    public final String zlsch;      // modalita' pagamento (R = ricevuta bancaria)
    public final String prctr;      // profit center

    // Riferimenti legacy (per log)
    public final String xref1;      // BELNR originale
    public final String xref2;      // GJAHR originale
    public final String sgtxt;
    public final String zuonr;      // testo posizione

    // Riga originale (per log errori)
    public final int lineNumber;
    public final String rawLine;

    public CsvRow(String[] fields, String[] headers, int lineNumber, String rawLine) {
        this.lineNumber = lineNumber;
        this.rawLine    = rawLine;

        bukrs      = get(fields, headers, "BUKRS");
        xblnr      = get(fields, headers, "XBLNR");
        docln      = get(fields, headers, "DOCLN");
        kunnr      = get(fields, headers, "KUNNR");
        glAccount  = get(fields, headers, "GL_ACCOUNT");
        gkont      = get(fields, headers, "GKONT");
        blart      = get(fields, headers, "BLART");
        umskz      = get(fields, headers, "UMSKZ");
        waers      = get(fields, headers, "WAERS");
        zterm      = get(fields, headers, "ZTERM");
        zlsch      = get(fields, headers, "ZLSCH");
        prctr      = get(fields, headers, "PRCTR");
        xref1      = get(fields, headers, "XREF1");
        xref2      = get(fields, headers, "XREF2");
        sgtxt      = get(fields, headers, "SGTXT");
        zuonr      = get(fields, headers, "ZUONR");

        bldatIso   = parseDate(get(fields, headers, "BLDAT"));
        zfbdtIso   = parseDate(get(fields, headers, "ZFBDT"));

        wrbtr      = parseAmount(get(fields, headers, "WRBTR"));
        dmbtr      = parseAmount(get(fields, headers, "DMBTR"));
    }

    /** Restituisce il valore pulito del campo (rimuove ="..." o "..."). */
    private static String get(String[] fields, String[] headers, String name) {
        for (int i = 0; i < headers.length && i < fields.length; i++) {
            if (headers[i].equalsIgnoreCase(name)) {
                return clean(fields[i]);
            }
        }
        return "";
    }

    /**
     * Pulisce il valore del campo:
     *   ="0000001234" -> 0000001234
     *   "EUR"         -> EUR
     *   ""            -> ""
     */
    public static String clean(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        if (raw.startsWith("=\"") && raw.endsWith("\"")) {
            return raw.substring(2, raw.length() - 1);
        }
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    /**
     * Converte data italiana DD.MM.YYYY in ISO YYYY-MM-DD.
     * Restituisce "" se vuota o non parsabile.
     */
    private static String parseDate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            return LocalDate.parse(raw.trim(), DATE_IT).format(DATE_ISO);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Converte importo italiano (84.904,68) in BigDecimal.
     * Gestisce anche valori vuoti -> BigDecimal.ZERO.
     */
    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        try {
            // Rimuove separatore migliaia (punto) e sostituisce virgola con punto
            String normalized = raw.trim()
                                   .replace(".", "")
                                   .replace(",", ".");
            return new BigDecimal(normalized);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** Chiave di log leggibile per questa riga. */
    public String key() {
        return "L" + lineNumber + " XBLNR=" + xblnr + " KUNNR=" + kunnr;
    }

    @Override
    public String toString() {
        return key() + " WRBTR=" + wrbtr + " " + waers
                + " ZFBDT=" + zfbdtIso + " UMSKZ=" + umskz;
    }
}
