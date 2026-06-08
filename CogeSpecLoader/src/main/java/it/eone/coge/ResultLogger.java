package it.eone.coge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scrive il log dei risultati in un file CSV nella stessa directory
 * del file di input, con timestamp nel nome:
 *   S_BSID_COGE_SPEC_20260605_114417_LOG_20260608_143022.csv
 *
 * Colonne:
 *   RIGA | XBLNR | KUNNR | UMSKZ | WRBTR | WAERS | ZFBDT |
 *   ESITO | DOC_SAP | MESSAGGIO
 */
public class ResultLogger implements AutoCloseable {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String SEP = ";";

    private final BufferedWriter writer;
    private final Path logPath;
    private int ok = 0, ko = 0, skipped = 0;

    public ResultLogger(Path csvInputPath) throws IOException {
        String inputName   = csvInputPath.getFileName().toString();
        String baseName    = inputName.replaceAll("\\.csv$", "");
        String timestamp   = LocalDateTime.now().format(TS);
        String logName     = baseName + "_LOG_" + timestamp + ".csv";
        logPath            = csvInputPath.getParent().resolve(logName);

        writer = new BufferedWriter(
                new OutputStreamWriter(
                        Files.newOutputStream(logPath,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING),
                        StandardCharsets.UTF_8));

        // BOM UTF-8 per Excel
        writer.write('\uFEFF');
        writeHeader();
        System.out.println("[LOG] File di log: " + logPath.toAbsolutePath());
    }

    private void writeHeader() throws IOException {
        writeLine("RIGA", "XBLNR", "KUNNR", "UMSKZ", "WRBTR", "WAERS",
                  "ZFBDT", "ESITO", "DOC_SAP", "MESSAGGIO");
    }

    public void logSuccess(CsvRow row, String docNumber) throws IOException {
        ok++;
        writeLine(
                String.valueOf(row.lineNumber),
                row.xblnr, row.kunnr, row.umskz,
                row.wrbtr.toPlainString(), row.waers, row.zfbdtIso,
                "OK", docNumber, "");
    }

    public void logError(CsvRow row, String message) throws IOException {
        ko++;
        writeLine(
                String.valueOf(row.lineNumber),
                row.xblnr, row.kunnr, row.umskz,
                row.wrbtr.toPlainString(), row.waers, row.zfbdtIso,
                "KO", "", message);
    }

    public void logDryRun(CsvRow row, String soapSnippet) throws IOException {
        skipped++;
        writeLine(
                String.valueOf(row.lineNumber),
                row.xblnr, row.kunnr, row.umskz,
                row.wrbtr.toPlainString(), row.waers, row.zfbdtIso,
                "DRY-RUN", "", soapSnippet);
    }

    public void printSummary() {
        System.out.println("=".repeat(60));
        if (skipped > 0) {
            System.out.println("[RIEPILOGO] DRY-RUN — nessun documento postato");
            System.out.println("            Righe simulate: " + skipped);
        } else {
            System.out.println("[RIEPILOGO] OK: " + ok + " | KO: " + ko
                    + " | Totale: " + (ok + ko));
        }
        System.out.println("            Log: " + logPath.toAbsolutePath());
        System.out.println("=".repeat(60));
    }

    private void writeLine(String... cols) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(SEP);
            String v = cols[i] == null ? "" : cols[i].replace("\"", "\"\"");
            sb.append('"').append(v).append('"');
        }
        writer.write(sb.toString());
        writer.newLine();
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }
}
