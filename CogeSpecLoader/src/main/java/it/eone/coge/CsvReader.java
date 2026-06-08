package it.eone.coge;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

/**
 * Legge il CSV esportato da ZFI_COGE_SPEC_MIG (WS_DOWNLOAD, codepage 1100).
 *
 * Formato atteso:
 *   Riga 1: vuota (artefatto WS_DOWNLOAD)
 *   Riga 2: header con nomi colonne separati da ";"
 *   Riga 3+: dati
 *
 * Separatore: ";"
 * Valori: racchiusi in "..." oppure ="..." (protezione zero iniziale SAP)
 */
public class CsvReader {

    private final Path csvPath;
    private final String filterKunnr;

    public CsvReader(Path csvPath, String filterKunnr) {
        this.csvPath     = csvPath;
        this.filterKunnr = filterKunnr == null ? "" : filterKunnr.trim();
    }

    public List<CsvRow> read() throws IOException {
        // WS_DOWNLOAD scrive in Windows-1252 (codepage SAP 1100 = cp1252)
        List<String> lines = Files.readAllLines(csvPath, Charset.forName("windows-1252"));

        if (lines.size() < 3) {
            throw new IOException("CSV vuoto o malformato: " + csvPath);
        }

        // Riga 1 vuota, riga 2 header
        String headerLine = lines.get(1);
        String[] headers  = splitLine(headerLine);
        // Pulisce i nomi colonna (rimuove eventuali virgolette)
        for (int i = 0; i < headers.length; i++) {
            headers[i] = CsvRow.clean(headers[i]).toUpperCase();
        }

        System.out.println("[CSV] Colonne: " + headers.length
                + " | File: " + csvPath.getFileName());

        List<CsvRow> rows = new ArrayList<>();
        int skipped = 0;

        for (int i = 2; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            String[] fields = splitLine(line);
            CsvRow row = new CsvRow(fields, headers, i + 1, line);

            // Filtro cliente opzionale
            if (!filterKunnr.isEmpty() && !filterKunnr.equals(row.kunnr)) {
                skipped++;
                continue;
            }

            // Scarta righe senza importo significativo
            if (row.wrbtr.signum() == 0) {
                System.out.println("[CSV] Riga " + row.lineNumber
                        + " ignorata: importo zero (" + row.key() + ")");
                skipped++;
                continue;
            }

            rows.add(row);
        }

        System.out.println("[CSV] Righe caricate: " + rows.size()
                + " | Ignorate: " + skipped);
        return rows;
    }

    /**
     * Split su ";" rispettando i valori quoted (incluso ="...").
     * Non usa opencsv per il separatore ";" non standard.
     */
    private static String[] splitLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb    = new StringBuilder();
        boolean inQuote     = false;
        boolean escape      = false; // per ="..."

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (!inQuote && c == '=' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                // Inizio ="..."
                sb.append(c);
                escape  = true;
                inQuote = true;
                i++;  // salta il "
                sb.append('"');
                continue;
            }

            if (c == '"') {
                if (inQuote) {
                    inQuote = false;
                    escape  = false;
                    sb.append(c);
                } else {
                    inQuote = true;
                    sb.append(c);
                }
                continue;
            }

            if (c == ';' && !inQuote) {
                tokens.add(sb.toString());
                sb.setLength(0);
                continue;
            }

            sb.append(c);
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }
}
