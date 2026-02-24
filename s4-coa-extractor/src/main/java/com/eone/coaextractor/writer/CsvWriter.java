package com.eone.coaextractor.writer;

import com.eone.coaextractor.config.AppConfig;
import com.eone.coaextractor.model.GlAccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Scrive la lista di conti in formato CSV.
 *
 * Colonne prodotte:
 *   CodiceConto ; DescrizioneBreve ; DescrizioneLunga
 *
 * Le celle che contengono il separatore o doppi apici vengono
 * automaticamente quotate secondo lo standard RFC 4180.
 */
public class CsvWriter {

    private static final Logger log = LoggerFactory.getLogger(CsvWriter.class);

    // Header CSV (nomi colonne)
    private static final String[] HEADERS = {"CodiceConto", "DescrizioneBreve", "DescrizioneLunga"};

    private final AppConfig config;

    public CsvWriter(AppConfig config) {
        this.config = config;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Scrive i conti su file CSV e restituisce il Path del file creato.
     */
    public Path write(List<GlAccount> accounts) throws IOException {
        // Assicura che la directory di output esista
        Files.createDirectories(config.outputDirectory);

        // Risolve il nome file (con eventuale pattern di data)
        String filename = resolveFilename(config.outputFilenamePattern);
        Path outputPath = config.outputDirectory.resolve(filename);

        log.info("Scrittura {} conti su: {}", accounts.size(), outputPath.toAbsolutePath());

        Charset charset = Charset.forName(config.outputCharset);
        String sep = config.outputSeparator;

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, charset,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            // BOM per UTF-8 (utile per apertura in Excel su Windows)
            if ("UTF-8".equalsIgnoreCase(config.outputCharset)) {
                writer.write('\uFEFF');
            }

            // Header
            if (config.outputIncludeHeader) {
                writer.write(buildRow(sep, HEADERS[0], HEADERS[1], HEADERS[2]));
                writer.newLine();
            }

            // Righe dati
            int written = 0;
            for (GlAccount account : accounts) {
                String shortText = nullToEmpty(account.shortText());
                String longText  = nullToEmpty(account.longText());

                writer.write(buildRow(sep, account.glAccount(), shortText, longText));
                writer.newLine();
                written++;
            }

            log.info("CSV scritto correttamente: {} righe", written);
        }

        return outputPath;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Costruisce una riga CSV con i campi separati da {@code sep}.
     * Applica quoting RFC 4180 se necessario.
     */
    private String buildRow(String sep, String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(sep);
            sb.append(csvEscape(fields[i], sep));
        }
        return sb.toString();
    }

    /**
     * Quota un campo CSV se contiene il separatore, doppi apici o a capo.
     * Raddoppia i doppi apici interni (RFC 4180).
     */
    private String csvEscape(String value, String sep) {
        if (value == null) return "";
        boolean needsQuoting = value.contains(sep)
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r");
        if (!needsQuoting) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * Risolve il pattern del nome file sostituendo eventuali token di data.
     * Esempio: "coa_export_yyyyMMdd_HHmm.csv" -> "coa_export_20260218_1045.csv"
     *
     * I caratteri non-formato (underscore, punto, trattino, ecc.) vengono
     * automaticamente escapati con apici singoli per DateTimeFormatter.
     */
    private String resolveFilename(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "coa_export.csv";
        }
        // Se il pattern non contiene token di data, usarlo come nome fisso
        if (!pattern.matches(".*[yMdHmsSE].*")) {
            return pattern;
        }
        try {
            // Separa nome ed estensione: applica il formatter solo al nome
            int dotIndex = pattern.lastIndexOf('.');
            String namePart = dotIndex >= 0 ? pattern.substring(0, dotIndex) : pattern;
            String extPart  = dotIndex >= 0 ? pattern.substring(dotIndex) : "";

            String dtfPattern = toDateTimeFormatterPattern(namePart);
            log.debug("Pattern data risolto: '{}' -> '{}{}'", pattern, dtfPattern, extPart);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dtfPattern);
            return LocalDateTime.now().format(formatter) + extPart;
        } catch (Exception e) {
            log.warn("Pattern nome file non valido '{}': {}. Uso nome fisso.", pattern, e.getMessage());
            return pattern;
        }
    }

    /**
     * Converte un pattern semplice tipo "coa_yyyyMMdd_HHmm.csv"
     * nel formato atteso da DateTimeFormatter: "'coa_'yyyyMMdd'_'HHmm'.csv'"
     *
     * I caratteri di formato data vengono lasciati inalterati.
     * Tutti gli altri caratteri vengono raggruppati e wrappati in apici singoli.
     */
    private String toDateTimeFormatterPattern(String pattern) {
        // Solo i token utili in un nome file: anno, mese, giorno, ora, minuto, secondo
        final String DATE_LETTERS = "yMdHms";
        StringBuilder result = new StringBuilder();
        StringBuilder literal = new StringBuilder();

        for (char c : pattern.toCharArray()) {
            if (DATE_LETTERS.indexOf(c) >= 0) {
                if (literal.length() > 0) {
                    result.append("'").append(literal.toString().replace("'", "''")).append("'");
                    literal.setLength(0);
                }
                result.append(c);
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) {
            result.append("'").append(literal.toString().replace("'", "''")).append("'");
        }
        return result.toString();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
