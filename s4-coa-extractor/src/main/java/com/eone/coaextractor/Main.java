package com.eone.coaextractor;

import com.eone.coaextractor.client.S4HanaClient;
import com.eone.coaextractor.client.S4HanaClientException;
import com.eone.coaextractor.config.AppConfig;
import com.eone.coaextractor.config.ConfigException;
import com.eone.coaextractor.model.GlAccount;
import com.eone.coaextractor.writer.CsvWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Entry point dell'applicazione.
 *
 * Utilizzo:
 *   java -jar s4-coa-extractor-1.0.0.jar [percorso/config.properties]
 *
 * Se il percorso non è specificato, il programma cerca:
 *   1. Variabile d'ambiente COA_CONFIG_FILE
 *   2. config.properties nella directory corrente
 *   3. config.properties nel classpath
 *
 * Exit code:
 *   0 = successo
 *   1 = errore di configurazione
 *   2 = errore di comunicazione con S/4HANA
 *   3 = errore di scrittura file
 *   99 = errore generico inatteso
 *
 * I codici di uscita sono importanti per il monitoraggio dei job schedulati
 * (cron, Task Scheduler, Control-M, ecc.)
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("=== S/4HANA Chart of Accounts Extractor ===");

        // 1. Caricamento configurazione
        AppConfig config;
        try {
            config = AppConfig.load(args);
            log.info("Configurazione caricata: {}", config);
        } catch (ConfigException e) {
            log.error("Errore di configurazione: {}", e.getMessage());
            System.exit(1);
            return;
        }

        // 2. Estrazione dati da S/4HANA
        List<GlAccount> accounts;
        try {
            S4HanaClient client = new S4HanaClient(config);
            accounts = client.fetchAllAccounts();

            if (accounts.isEmpty()) {
                log.warn("Nessun conto trovato per il piano dei conti '{}' in lingua '{}'." +
                         " Verificare i parametri di configurazione.",
                         config.chartOfAccounts, config.language);
                // Non è un errore fatale: scriviamo un CSV vuoto (solo header)
            }

        } catch (S4HanaClientException e) {
            log.error("Errore durante l'estrazione da S/4HANA: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Dettaglio eccezione:", e);
            }
            System.exit(2);
            return;
        }

        // 3. Scrittura CSV
        try {
            CsvWriter writer = new CsvWriter(config);
            Path outputFile = writer.write(accounts);
            log.info("File CSV creato con successo: {}", outputFile.toAbsolutePath());
        } catch (Exception e) {
            log.error("Errore durante la scrittura del file CSV: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Dettaglio eccezione:", e);
            }
            System.exit(3);
            return;
        }

        log.info("=== Estrazione completata con successo ===");
        System.exit(0);
    }
}
