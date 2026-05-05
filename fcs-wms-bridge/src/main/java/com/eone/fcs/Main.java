package com.eone.fcs;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eone.fcs.client.BusinessPartnerClient;
import com.eone.fcs.client.ProductClient;
import com.eone.fcs.client.PurchaseOrderClient;
import com.eone.fcs.config.AppConfig;
import com.eone.fcs.config.ConfigException;
import com.eone.fcs.model.Customer;
import com.eone.fcs.model.EketLine;
import com.eone.fcs.model.Product;
import com.eone.fcs.model.Supplier;
import com.eone.fcs.repository.FcsRepository;

/**
 * Entry point dell'applicazione FCS WMS Bridge - Extractor.
 *
 * Utilizzo:
 *   java -jar fcs-wms-bridge-1.0.0.jar [percorso/config.properties] [modalita]
 *
 * Modalità:
 *   all       → estrae tutto: prodotti, fornitori, clienti, EKET (default)
 *   products  → solo prodotti
 *   suppliers → solo fornitori
 *   customers → solo clienti
 *   eket      → solo schedulazioni OdA aperte
 *
 * Exit code:
 *   0  = successo
 *   1  = errore configurazione
 *   2  = errore S/4HANA
 *   3  = errore database
 *   99 = errore generico
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("=== FCS WMS Bridge - Extractor ===");

        // 1. Configurazione
        AppConfig config;
        try {
            config = AppConfig.load(args);
            log.info("Configurazione caricata: {}", config);
        } catch (ConfigException e) {
            log.error("Errore di configurazione: {}", e.getMessage());
            System.exit(1);
            return;
        }

        // 2. Modalità di estrazione
        String mode = args.length >= 2 ? args[1].toLowerCase() : "all";
        log.info("Modalità estrazione: {}", mode);

        // 3. Esecuzione
        try (FcsRepository repo = new FcsRepository(config)) {

            switch (mode) {
                case "products"  -> extractProducts(config, repo);
                case "suppliers" -> extractSuppliers(config, repo);
                case "customers" -> extractCustomers(config, repo);
                case "eket"      -> extractEket(config, repo);
                case "all"       -> {
                    extractProducts(config, repo);
                    extractSuppliers(config, repo);
                    extractCustomers(config, repo);
                    extractEket(config, repo);
                }
                default -> {
                    log.error("Modalità non riconosciuta: '{}'. Usare: all, products, suppliers, customers, eket", mode);
                    System.exit(1);
                }
            }

        } catch (com.eone.fcs.client.S4ClientException e) {
            log.error("Errore comunicazione S/4HANA: {}", e.getMessage());
            System.exit(2);
            return;
        } catch (java.sql.SQLException e) {
            log.error("Errore database PostgreSQL: {}", e.getMessage());
            System.exit(3);
            return;
        } catch (Exception e) {
            log.error("Errore inatteso: {}", e.getMessage(), e);
            System.exit(99);
            return;
        }

        log.info("=== Estrazione completata con successo ===");
        System.exit(0);
    }

    // -------------------------------------------------------------------------
    // Estrazioni
    // -------------------------------------------------------------------------

    private static void extractProducts(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione PRODOTTI ---");
        ProductClient client = new ProductClient(config);
        List<Product> products = client.fetchAllProducts();
        if (products.isEmpty()) {
            log.warn("Nessun prodotto trovato.");
            return;
        }
        repo.upsertProducts(products);
    }

    private static void extractSuppliers(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione FORNITORI ---");
        BusinessPartnerClient client = new BusinessPartnerClient(config);
        List<Supplier> suppliers = client.fetchAllSuppliers();
        if (suppliers.isEmpty()) {
            log.warn("Nessun fornitore trovato.");
            return;
        }
        repo.upsertSuppliers(suppliers);
    }

    private static void extractCustomers(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione CLIENTI ---");
        BusinessPartnerClient client = new BusinessPartnerClient(config);
        List<Customer> customers = client.fetchAllCustomers();
        if (customers.isEmpty()) {
            log.warn("Nessun cliente trovato.");
            return;
        }
        repo.upsertCustomers(customers);
    }

    private static void extractEket(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione SCHEDULAZIONI OdA (EKET) ---");
        PurchaseOrderClient client = new PurchaseOrderClient(config);
        List<EketLine> lines = client.fetchAllOpenScheduleLines();
        if (lines.isEmpty()) {
            log.warn("Nessuna schedulazione aperta trovata.");
            return;
        }
        // 2. Carica fattori di conversione UMFOR per i matnr estratti
        //    (solo per kappl='ME'; si estende con UMCLI quando necessario)
        java.util.Set<String> matnrs = lines.stream()
                .map(EketLine::matnr)
                .filter(m -> m != null && !m.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        
        // 2a. Fattori di conversione UMFOR (matnr+lifnr → imballo)
        Map<String, com.eone.fcs.model.Umfor> umforMap = repo.loadUmfor(matnrs);
        log.info("UMFOR caricati: {} record", umforMap.size());

        // 2b. Pesi unitari da tabfcsmara (matnr → brgew/ntgew/gewei)
        Map<String, com.eone.fcs.model.PesoMateriale> pesiMap = repo.loadPesi(matnrs);
        log.info("Pesi materiale caricati: {} record", pesiMap.size());

        // 3. Arricchisci le righe con i campi Gruppo 2
        List<EketLine> enriched = com.eone.fcs.service.EketEnricher.enrich(lines, umforMap, pesiMap);

        // 4. DELETE righe in attesa + INSERT nuove (con Gruppo 2 valorizzato)
        repo.syncEketLines(enriched);
    }
}
