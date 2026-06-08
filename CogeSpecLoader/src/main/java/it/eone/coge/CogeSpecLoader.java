package it.eone.coge;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

/**
 * CogeSpecLoader — migrazione PA Clienti Co.Ge. Speciale in S/4HANA Cloud PE.
 *
 * Utilizzo:
 *   java -jar CogeSpecLoader.jar <percorso-csv> [--config=<percorso-config>] [--kunnr=<cliente>]
 *
 * Esempi:
 *   java -jar CogeSpecLoader.jar C:\migr\S_BSID_COGE_SPEC_20260605.csv
 *   java -jar CogeSpecLoader.jar /data/migr/S_BSID_COGE_SPEC.csv --config=/data/migr/config.properties
 *   java -jar CogeSpecLoader.jar S_BSID_COGE_SPEC.csv --kunnr=45
 *
 * Parametri config.properties:
 *   s4hc.baseUrl              URL base S/4HC (es. https://my438840-api.s4hana.cloud.sap)
 *   s4hc.username             Utente communication arrangement SAP_COM_0002
 *   s4hc.password             Password
 *   s4hc.language             Lingua (default: IT)
 *   posting.companyCode       Codice societa' (es. S001)
 *   posting.documentType      Tipo documento FI (default: UG)
 *   posting.transitoryAccount Conto transitorio AVERE (non usato nel payload,
 *                             viene preso da GKONT nel CSV)
 *   posting.postingDate       Data registrazione YYYY-MM-DD (vuoto = oggi)
 *   posting.createdByUser     Utente da inserire nell'header SOAP (default: username)
 *   posting.dryRun            true = solo log senza postare (default: true)
 *   filter.kunnr              Cliente da elaborare (vuoto = tutti)
 */
public class CogeSpecLoader {

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("  CogeSpecLoader v1.0 — Migrazione PA Co.Ge. Speciale");
        System.out.println("  e-One S.p.A. — Progetto Lamplast S/4HC Migration");
        System.out.println("=".repeat(60));

        // --- Parsing argomenti ---
        String csvArg    = null;
        String configArg = null;
        String kunnrArg  = null;

        for (String arg : args) {
            if (arg.startsWith("--config=")) {
                configArg = arg.substring(9);
            } else if (arg.startsWith("--kunnr=")) {
                kunnrArg = arg.substring(8);
            } else if (!arg.startsWith("--")) {
                csvArg = arg;
            }
        }

        if (csvArg == null || csvArg.isBlank()) {
            System.err.println("[ERRORE] Specificare il percorso del file CSV come primo argomento.");
            System.err.println("  Uso: java -jar CogeSpecLoader.jar <percorso-csv> [--config=...] [--kunnr=...]");
            System.exit(1);
        }

        Path csvPath = Paths.get(csvArg);
        if (!Files.exists(csvPath)) {
            System.err.println("[ERRORE] File CSV non trovato: " + csvPath.toAbsolutePath());
            System.exit(1);
        }

        // --- Caricamento config ---
        AppConfig config;
        try {
            config = new AppConfig(configArg);
        } catch (IOException e) {
            System.err.println("[ERRORE] Configurazione: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Il filtro kunnr da riga di comando ha precedenza su quello in config
        String filterKunnr = (kunnrArg != null && !kunnrArg.isBlank())
                ? kunnrArg : config.filterKunnr;

        // --- Lettura CSV ---
        List<CsvRow> rows;
        try {
            CsvReader reader = new CsvReader(csvPath, filterKunnr);
            rows = reader.read();
        } catch (IOException e) {
            System.err.println("[ERRORE] Lettura CSV: " + e.getMessage());
            System.exit(1);
            return;
        }

        if (rows.isEmpty()) {
            System.out.println("[INFO] Nessuna riga da elaborare. Uscita.");
            return;
        }

        // --- Elaborazione ---
        SoapBuilder  soapBuilder = new SoapBuilder(config);

        try (ResultLogger logger = new ResultLogger(csvPath);
             S4HcClient  client  = config.dryRun ? null : new S4HcClient(config)) {

            if (config.dryRun) {
                System.out.println("[ATTENZIONE] Modalità DRY-RUN: nessun documento verrà postato.");
            } else {
                System.out.println("[INFO] Modalità REALE: i documenti verranno postati in S/4HC.");
                System.out.println("[INFO] Righe da elaborare: " + rows.size());
                System.out.print("[INFO] Premi INVIO per continuare, CTRL+C per annullare... ");
                try { System.in.read(); } catch (Exception ignored) {}
            }

            int counter = 0;
            for (CsvRow row : rows) {
                counter++;
                String msgId   = "COGS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String envelope = soapBuilder.buildSyncEnvelope(row, msgId);

                if (config.dryRun) {
                    // Dry-run: log del payload (prime 200 char per leggibilità)
                    String snippet = envelope.replaceAll("\\s+", " ");
                    snippet = snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet;
                    System.out.printf("[DRY-RUN] %3d/%d %s%n", counter, rows.size(), row);
                    logger.logDryRun(row, snippet);
                } else {
                    System.out.printf("[POST] %3d/%d %s ... ", counter, rows.size(), row);
                    try {
                        S4HcClient.SoapResponse resp = client.post(envelope);
                        if (resp.isSuccess()) {
                            String docNum = resp.extractDocumentNumber();
                            System.out.println("OK -> " + docNum);
                            logger.logSuccess(row, docNum);
                        } else {
                            String fault = resp.extractFaultMessage();
                            System.out.println("KO [HTTP " + resp.httpStatus + "] " + fault);
                            logger.logError(row, "HTTP " + resp.httpStatus + " | " + fault);
                            // Log del body completo per debug
                            if (resp.body.length() < 2000) {
                                System.out.println("    RESPONSE: " + resp.body);
                            }
                        }
                    } catch (IOException e) {
                        System.out.println("KO [connessione] " + e.getMessage());
                        logger.logError(row, "Errore connessione: " + e.getMessage());
                    }

                    // Breve pausa per non sovraccaricare S/4HC
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
            }

            logger.printSummary();

        } catch (IOException e) {
            System.err.println("[ERRORE] " + e.getMessage());
            System.exit(1);
        }
    }
}
