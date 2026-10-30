package lamplast.mbxa.logic.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regole sul numero DDT (formato della serie interna, normalizzazione del numero SAP). */
public final class Numerazione {

    private static final Pattern PROGRESSIVO = Pattern.compile("\\{PROGRESSIVO(?::(0+))?\\}");

    private Numerazione() {}

    /**
     * Numero della serie interna secondo il formato configurato, es.
     * "{TIPO}{ANNO}/{PROGRESSIVO:000000}" con TR, 2026, 15 = "TR2026/000015".
     */
    public static String formatta(String formato, String tipo, int anno, int progressivo) {
        String s = formato.replace("{TIPO}", tipo).replace("{ANNO}", String.valueOf(anno));
        Matcher m = PROGRESSIVO.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int width = m.group(1) == null ? 0 : m.group(1).length();
            String p = width == 0 ? String.valueOf(progressivo) : String.format("%0" + width + "d", progressivo);
            m.appendReplacement(sb, Matcher.quoteReplacement(p));
        }
        m.appendTail(sb);
        String result = sb.toString();
        if (result.length() > 20) {
            throw new DdtException("Il numero DDT \"" + result + "\" supera i 20 caratteri: rivedere ddt.numerazione.formato");
        }
        return result;
    }

    /**
     * Numero SAP inserito dall'operatore: solo cifre, zeri iniziali tolti
     * (SAP può mostrarli o meno a seconda della transazione).
     *
     * @throws DdtException se vuoto o non numerico
     */
    public static String normalizzaNumeroSap(String input) {
        if (input == null || input.isBlank()) {
            throw new DdtException("Inserire il numero DDT assegnato da SAP (campo \"Numero bolla\" del documento in MIGO).");
        }
        String s = input.trim();
        if (!s.matches("[0-9]+")) {
            throw new DdtException("Il numero DDT deve contenere solo cifre: \"" + s + "\"");
        }
        s = s.replaceFirst("^0+(?=.)", "");
        if (s.length() > 18) throw new DdtException("Numero DDT troppo lungo: " + s);
        return s;
    }
}
