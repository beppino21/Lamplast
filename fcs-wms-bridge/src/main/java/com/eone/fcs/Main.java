package com.eone.fcs;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eone.fcs.client.BusinessPartnerClient;
import com.eone.fcs.client.ProductClient;
import com.eone.fcs.client.PurchaseOrderClient;
import com.eone.fcs.config.AppConfig;
import com.eone.fcs.config.ConfigException;
import com.eone.fcs.model.Customer;
import com.eone.fcs.model.EketLine;
import com.eone.fcs.model.PesoMateriale;
import com.eone.fcs.model.Product;
import com.eone.fcs.model.Supplier;
import com.eone.fcs.model.Umfor;
import com.eone.fcs.repository.FcsRepository;
import com.eone.fcs.service.EketEnricher;

/**
 * Entry point dell'applicazione FCS WMS Bridge - Extractor.
 *
 * Utilizzo:
 *   java -jar fcs-wms-bridge-1.0.0.jar [percorso/config.properties] [modalita] [parametro]
 *
 * Modalità:
 *   all            → estrae tutto: prodotti, fornitori, clienti, EKET (default)
 *   products       → solo prodotti
 *   suppliers      → solo fornitori
 *   customers      → solo clienti
 *   eket           → tutte le schedulazioni OdA aperte
 *   eket <EBELN>   → schedulazioni di un singolo OdA (es: eket 4500000123)
 *
 * Esempi:
 *   java -jar fcs-wms-bridge-1.0.0.jar config.properties eket
 *   java -jar fcs-wms-bridge-1.0.0.jar config.properties eket 4500000123
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

        // 2. Modalità di estrazione (args[1]) e parametro opzionale (args[2])
        String mode      = args.length >= 2 ? args[1].toLowerCase().trim() : "all";
        String extraArg  = args.length >= 3 ? args[2].trim()               : null;
        log.info("Modalità estrazione: {}{}", mode,
                 extraArg != null ? " [parametro: " + extraArg + "]" : "");

        // 3. Esecuzione
        try (FcsRepository repo = new FcsRepository(config)) {

            switch (mode) {
                case "products"  -> extractProducts(config, repo);
                case "suppliers" -> extractSuppliers(config, repo);
                case "customers" -> extractCustomers(config, repo);

                case "eket" -> {
                    // extraArg presente → estrazione puntuale per singolo OdA
                    // extraArg assente  → estrazione massiva di tutti gli OdA aperti
                    if (extraArg != null && !extraArg.isBlank()) {
                        extractEketByOrder(config, repo, extraArg);
                    } else {
                        extractEketAll(config, repo);
                    }
                }

                case "all" -> {
                    extractProducts(config, repo);
                    extractSuppliers(config, repo);
                    extractCustomers(config, repo);
                    extractEketAll(config, repo);
                }

                default -> {
                    log.error("Modalità non riconosciuta: '{}'. " +
                              "Usare: all, products, suppliers, customers, eket [EBELN]", mode);
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

    // =========================================================================
    // Estrazione PRODOTTI
    // =========================================================================

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

    // =========================================================================
    // Estrazione FORNITORI
    // =========================================================================

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

    // =========================================================================
    // Estrazione CLIENTI
    // =========================================================================

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

    // =========================================================================
    // Estrazione EKET - massiva (tutti gli OdA aperti)
    // =========================================================================

    private static void extractEketAll(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione SCHEDULAZIONI OdA (tutti gli OdA aperti) ---");

        PurchaseOrderClient client = new PurchaseOrderClient(config);
        List<EketLine> lines = client.fetchAllOpenScheduleLines();

        if (lines.isEmpty()) {
            log.warn("Nessuna schedulazione aperta trovata.");
            return;
        }

        List<EketLine> enriched = enrichLines(lines, repo);

        // Sync massivo: DELETE in attesa per tutti gli EBELN estratti + INSERT
        repo.syncEketLines(enriched);
    }

    // =========================================================================
    // Estrazione EKET - puntuale (singolo OdA)
    //
    // Usato dal servizio REST dopo registrazione EM, per riallineare le
    // schedulazioni dell'OdA appena ricevuto senza toccare gli altri.
    // =========================================================================

    private static void extractEketByOrder(AppConfig config, FcsRepository repo,
                                           String ebeln) throws Exception {
        log.info("--- Estrazione SCHEDULAZIONI OdA puntuale per OdA: {} ---", ebeln);

        PurchaseOrderClient client = new PurchaseOrderClient(config);
        List<EketLine> lines = client.fetchByPurchaseOrder(ebeln);

        if (lines.isEmpty()) {
            // Può capitare se l'OdA è stato completamente evaso: il sync
            // cancella comunque le righe in attesa residue su quell'OdA.
            log.warn("Nessuna schedulazione aperta trovata per OdA {}. " +
                     "Pulizia righe in attesa residue.", ebeln);
        } else {
            log.info("Schedulazioni recuperate per OdA {}: {} righe", ebeln, lines.size());
        }

        List<EketLine> enriched = lines.isEmpty()
                ? List.of()
                : enrichLines(lines, repo);

        // Sync puntuale: DELETE in attesa per questo solo OdA + INSERT
        repo.syncEketLinesForOrder(ebeln, enriched);
    }

    // =========================================================================
    // Arricchimento comune (UMFOR + pesi + EketEnricher)
    // =========================================================================

    /**
     * Dato un insieme di righe EKET grezze (uscite dal client S/4),
     * carica i fattori di conversione UMFOR e i pesi da tabfcsmara
     * e restituisce le righe arricchite con i campi Gruppo 2.
     */
    private static List<EketLine> enrichLines(List<EketLine> lines,
                                              FcsRepository repo) throws Exception {
        Set<String> matnrs = lines.stream()
                .map(EketLine::matnr)
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.toSet());

        // Fattori di conversione UMFOR (matnr+lifnr → imballo/pallet)
        Map<String, Umfor> umforMap = repo.loadUmfor(matnrs);
        log.info("UMFOR caricati: {} record", umforMap.size());

        // Pesi unitari da tabfcsmara (matnr → brgew/ntgew/gewei)
        Map<String, PesoMateriale> pesiMap = repo.loadPesi(matnrs);
        log.info("Pesi materiale caricati: {} record", pesiMap.size());

        return EketEnricher.enrich(lines, umforMap, pesiMap);
    }
}
