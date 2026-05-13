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
import com.eone.fcs.client.SalesReturnClient;
import com.eone.fcs.config.AppConfig;
import com.eone.fcs.config.ConfigException;
import com.eone.fcs.model.Customer;
import com.eone.fcs.model.EketLine;
import com.eone.fcs.model.PesoMateriale;
import com.eone.fcs.model.Product;
import com.eone.fcs.model.Supplier;
import com.eone.fcs.model.Umcli;
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
 *   all            → estrae tutto: prodotti, fornitori, clienti, EKET, VBEP (default)
 *   products       → solo prodotti
 *   suppliers      → solo fornitori
 *   customers      → solo clienti
 *   eket           → tutte le schedulazioni OdA aperte
 *   eket <EBELN>   → schedulazioni di un singolo OdA
 *   vbep           → tutte le schedulazioni OdV di reso aperte
 *   vbep <VBELN>   → schedulazioni di un singolo OdV di reso
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

        AppConfig config;
        try {
            config = AppConfig.load(args);
            log.info("Configurazione caricata: {}", config);
        } catch (ConfigException e) {
            log.error("Errore di configurazione: {}", e.getMessage());
            System.exit(1);
            return;
        }

        String mode     = args.length >= 2 ? args[1].toLowerCase().trim() : "all";
        String extraArg = args.length >= 3 ? args[2].trim()               : null;
        log.info("Modalità estrazione: {}{}", mode,
                 extraArg != null ? " [parametro: " + extraArg + "]" : "");

        try (FcsRepository repo = new FcsRepository(config)) {

            switch (mode) {
                case "products"  -> extractProducts(config, repo);
                case "suppliers" -> extractSuppliers(config, repo);
                case "customers" -> extractCustomers(config, repo);

                case "eket" -> {
                    if (extraArg != null && !extraArg.isBlank()) {
                        extractEketByOrder(config, repo, extraArg);
                    } else {
                        extractEketAll(config, repo);
                    }
                }

                case "vbep" -> {
                    if (extraArg != null && !extraArg.isBlank()) {
                        extractVbepByOrder(config, repo, extraArg);
                    } else {
                        extractVbepAll(config, repo);
                    }
                }

                case "all" -> {
                    extractProducts(config, repo);
                    extractSuppliers(config, repo);
                    extractCustomers(config, repo);
                    extractEketAll(config, repo);
                    extractVbepAll(config, repo);
                }

                default -> {
                    log.error("Modalità non riconosciuta: '{}'. " +
                              "Usare: all, products, suppliers, customers, " +
                              "eket [EBELN], vbep [VBELN]", mode);
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
        if (products.isEmpty()) { log.warn("Nessun prodotto trovato."); return; }
        repo.upsertProducts(products);
    }

    // =========================================================================
    // Estrazione FORNITORI
    // =========================================================================

    private static void extractSuppliers(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione FORNITORI ---");
        BusinessPartnerClient client = new BusinessPartnerClient(config);
        List<Supplier> suppliers = client.fetchAllSuppliers();
        if (suppliers.isEmpty()) { log.warn("Nessun fornitore trovato."); return; }
        repo.upsertSuppliers(suppliers);
    }

    // =========================================================================
    // Estrazione CLIENTI
    // =========================================================================

    private static void extractCustomers(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione CLIENTI ---");
        BusinessPartnerClient client = new BusinessPartnerClient(config);
        List<Customer> customers = client.fetchAllCustomers();
        if (customers.isEmpty()) { log.warn("Nessun cliente trovato."); return; }
        repo.upsertCustomers(customers);
    }

    // =========================================================================
    // Estrazione EKET - massiva
    // =========================================================================

    private static void extractEketAll(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione SCHEDULAZIONI OdA (tutti gli OdA aperti) ---");

        PurchaseOrderClient client = new PurchaseOrderClient(config);
        List<EketLine> lines = client.fetchAllOpenScheduleLines();

        if (lines.isEmpty()) {
            log.warn("Nessuna schedulazione OdA aperta trovata.");
            return;
        }

        List<EketLine> enriched = enrichLinesOda(lines, repo);
        repo.syncEketLines(enriched);
    }

    // =========================================================================
    // Estrazione EKET - puntuale
    // =========================================================================

    private static void extractEketByOrder(AppConfig config, FcsRepository repo,
                                           String ebeln) throws Exception {
        log.info("--- Estrazione SCHEDULAZIONI OdA puntuale per OdA: {} ---", ebeln);

        PurchaseOrderClient client = new PurchaseOrderClient(config);
        List<EketLine> lines = client.fetchByPurchaseOrder(ebeln);

        if (lines.isEmpty()) {
            log.warn("Nessuna schedulazione aperta per OdA {}. Pulizia righe residue.", ebeln);
        } else {
            log.info("Schedulazioni recuperate per OdA {}: {} righe", ebeln, lines.size());
        }

        List<EketLine> enriched = lines.isEmpty() ? List.of() : enrichLinesOda(lines, repo);
        repo.syncEketLinesForOrder(ebeln, enriched);
    }

    // =========================================================================
    // Estrazione VBEP - massiva
    // =========================================================================

    private static void extractVbepAll(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione SCHEDULAZIONI OdV RESO (tutti gli OdV aperti) ---");

        SalesReturnClient client = new SalesReturnClient(config);
        List<EketLine> lines = client.fetchAllOpenReturnScheduleLines();

        if (lines.isEmpty()) {
            log.warn("Nessuna schedulazione OdV reso aperta trovata.");
            return;
        }

        List<EketLine> enriched = enrichLinesReso(lines, repo);
        repo.syncEketLines(enriched);
    }

    // =========================================================================
    // Estrazione VBEP - puntuale
    // =========================================================================

    private static void extractVbepByOrder(AppConfig config, FcsRepository repo,
                                           String vbeln) throws Exception {
        log.info("--- Estrazione SCHEDULAZIONI OdV RESO puntuale per OdV: {} ---", vbeln);

        SalesReturnClient client = new SalesReturnClient(config);
        List<EketLine> lines = client.fetchByReturnOrder(vbeln);

        if (lines.isEmpty()) {
            log.warn("Nessuna schedulazione aperta per OdV reso {}. Pulizia righe residue.", vbeln);
        } else {
            log.info("Schedulazioni recuperate per OdV reso {}: {} righe", vbeln, lines.size());
        }

        List<EketLine> enriched = lines.isEmpty() ? List.of() : enrichLinesReso(lines, repo);
        repo.syncEketLinesForOrder(vbeln, enriched);
    }

    // =========================================================================
    // Arricchimento OdA (UMFOR + pesi + nomi fornitori)
    // =========================================================================

    private static List<EketLine> enrichLinesOda(List<EketLine> lines,
                                                  FcsRepository repo) throws Exception {
        Set<String> matnrs = lines.stream()
                .map(EketLine::matnr)
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.toSet());

        Set<String> lifnrs = lines.stream()
                .map(EketLine::lifnr)
                .filter(l -> l != null && !l.isBlank())
                .collect(Collectors.toSet());

        Map<String, Umfor>         umforMap  = repo.loadUmfor(matnrs);
        Map<String, PesoMateriale> pesiMap   = repo.loadPesi(matnrs);
        Map<String, String>        nomiForni = repo.loadNomiFornitori(lifnrs);

        log.info("OdA — UMFOR: {} record, Pesi: {} record, Fornitori: {} record",
                 umforMap.size(), pesiMap.size(), nomiForni.size());

        // Mappa UMCLI e nomiClienti vuote — non servono per gli OdA
        return EketEnricher.enrich(lines,
                umforMap, java.util.Map.of(),
                pesiMap,
                nomiForni, java.util.Map.of());
    }

    // =========================================================================
    // Arricchimento Resi (UMCLI + pesi + nomi clienti)
    // =========================================================================

    private static List<EketLine> enrichLinesReso(List<EketLine> lines,
                                                   FcsRepository repo) throws Exception {
        Set<String> matnrs = lines.stream()
                .map(EketLine::matnr)
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.toSet());

        // Per i resi lifnr = kunnr (cliente) per coerenza modello
        Set<String> kunnrs = lines.stream()
                .map(EketLine::lifnr)
                .filter(k -> k != null && !k.isBlank())
                .collect(Collectors.toSet());

        Map<String, Umcli>         umcliMap  = repo.loadUmcli(matnrs);
        Map<String, PesoMateriale> pesiMap   = repo.loadPesi(matnrs);
        Map<String, String>        nomiCli   = repo.loadNomiClienti(kunnrs);

        log.info("Resi — UMCLI: {} record, Pesi: {} record, Clienti: {} record",
                 umcliMap.size(), pesiMap.size(), nomiCli.size());

        // Mappa UMFOR e nomiFornitori vuote — non servono per i resi
        return EketEnricher.enrich(lines,
                java.util.Map.of(), umcliMap,
                pesiMap,
                java.util.Map.of(), nomiCli);
    }
}
