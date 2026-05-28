package com.eone.fcs;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import com.eone.fcs.model.Fcst001;
import com.eone.fcs.model.PesoMateriale;
import com.eone.fcs.model.Product;
import com.eone.fcs.model.Supplier;
import com.eone.fcs.model.SyncRecord;
import com.eone.fcs.model.Umcli;
import com.eone.fcs.model.Umfor;
import com.eone.fcs.repository.FcsRepository;
import com.eone.fcs.repository.SyncRepository;

/**
 * Entry point dell'applicazione FCS WMS Bridge - Extractor.
 *
 * Utilizzo:
 *   java -jar fcs-wms-bridge-1.0.0.jar [percorso/config.properties] [modalita] [parametro]
 *
 * Modalità:
 *   all            → estrae tutto: prodotti, fornitori, clienti, EKET, VBEP (default)
 *   products       → solo prodotti (full)
 *   suppliers      → solo fornitori (full)
 *   customers      → solo clienti (full)
 *   eket           → tutte le schedulazioni OdA aperte (full)
 *   eket <EBELN>   → schedulazioni di un singolo OdA
 *   vbep           → tutte le schedulazioni OdV di reso aperte (full)
 *   vbep <VBELN>   → schedulazioni di un singolo OdV di reso
 *   delta          → sincronizzazione differenziale di tutte le entità
 *                    (MARA, LFA1, KNA1, EKET, VBEP)
 *   delta-trx      → sincronizzazione differenziale solo EKET + VBEP (ogni 5 min)
 *   delta-ana      → sincronizzazione differenziale solo MARA + LFA1 + KNA1 (giornaliero)
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

    /** Applicativo EKET: schedulazioni da OdA (tabella EKET in S/4H) */
    private static final String KAPPL_EKET = "ME";

    /** Applicativo VBEP: schedulazioni da OdV reso (tabella VBEP in S/4H) */
    private static final String KAPPL_VBEP = "V";

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

                // ---------------------------------------------------------------
                // [DELTA] Sincronizzazione differenziale — tutte le entità
                // ---------------------------------------------------------------
                case "delta" -> {
                    SyncRepository syncRepo = new SyncRepository(repo.getConnection(), config.dbTenant);
                    deltaProducts (config, repo, syncRepo);
                    deltaSuppliers(config, repo, syncRepo);
                    deltaCustomers(config, repo, syncRepo);
                    deltaEket     (config, repo, syncRepo);
                    deltaVbep     (config, repo, syncRepo);
                }

                // ---------------------------------------------------------------
                // [DELTA-TRX] Sincronizzazione differenziale — solo EKET + VBEP
                // Schedulato ogni 5 minuti da DeltaSyncListener (Tomcat)
                // ---------------------------------------------------------------
                case "delta-trx" -> {
                    SyncRepository syncRepo = new SyncRepository(repo.getConnection(), config.dbTenant);
                    deltaEket(config, repo, syncRepo);
                    deltaVbep(config, repo, syncRepo);
                }

                // ---------------------------------------------------------------
                // [DELTA-ANA] Sincronizzazione differenziale — solo MARA + LFA1 + KNA1
                // Schedulato una volta al giorno da DeltaSyncListener (Tomcat)
                // ---------------------------------------------------------------
                case "delta-ana" -> {
                    SyncRepository syncRepo = new SyncRepository(repo.getConnection(), config.dbTenant);
                    deltaProducts (config, repo, syncRepo);
                    deltaSuppliers(config, repo, syncRepo);
                    deltaCustomers(config, repo, syncRepo);
                }

                default -> {
                    log.error("Modalità non riconosciuta: '{}'. " +
                              "Usare: all, products, suppliers, customers, " +
                              "eket [EBELN], vbep [VBELN], delta, delta-trx, delta-ana", mode);
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
    // Estrazione PRODOTTI (full - invariata)
    // =========================================================================

    private static void extractProducts(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione PRODOTTI ---");
        ProductClient client = new ProductClient(config);
        List<Product> products = client.fetchAllProducts();
        if (products.isEmpty()) { log.warn("Nessun prodotto trovato."); return; }
        repo.upsertProducts(products);
    }

    // =========================================================================
    // Estrazione FORNITORI (full - invariata)
    // =========================================================================

    private static void extractSuppliers(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione FORNITORI ---");
        BusinessPartnerClient client = new BusinessPartnerClient(config);
        List<Supplier> suppliers = client.fetchAllSuppliers();
        if (suppliers.isEmpty()) { log.warn("Nessun fornitore trovato."); return; }
        repo.upsertSuppliers(suppliers);
    }

    // =========================================================================
    // Estrazione CLIENTI (full - invariata)
    // =========================================================================

    private static void extractCustomers(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione CLIENTI ---");
        BusinessPartnerClient client = new BusinessPartnerClient(config);
        List<Customer> customers = client.fetchAllCustomers();
        if (customers.isEmpty()) { log.warn("Nessun cliente trovato."); return; }
        repo.upsertCustomers(customers);
    }

    // =========================================================================
    // Estrazione EKET - massiva (invariata)
    // =========================================================================

    private static void extractEketAll(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione SCHEDULAZIONI OdA (tutti gli OdA aperti) ---");

        PurchaseOrderClient client = new PurchaseOrderClient(config);
        List<EketLine> lines = client.fetchAllOpenScheduleLines();

        if (lines.isEmpty()) {
            log.warn("Nessuna schedulazione OdA aperta trovata.");
            return;
        }

        List<EketLine>       withMtart = applyMaterialTypes(lines, config);
        Map<String, Fcst001> fcst001   = repo.loadFcst001();
        List<EketLine>       filtered  = filterByFcst001(withMtart, fcst001);
        List<EketLine>       enriched  = enrichLinesOda(filtered, repo);
        repo.syncEketLines(enriched, KAPPL_EKET);
    }

    // =========================================================================
    // Estrazione EKET - puntuale (invariata)
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

        List<EketLine>       withMtart = lines.isEmpty() ? List.of() : applyMaterialTypes(lines, config);
        Map<String, Fcst001> fcst001   = repo.loadFcst001();
        List<EketLine>       filtered  = withMtart.isEmpty() ? List.of() : filterByFcst001(withMtart, fcst001);
        List<EketLine>       enriched  = filtered.isEmpty() ? List.of() : enrichLinesOda(filtered, repo);
        repo.syncEketLinesForOrder(ebeln, enriched, KAPPL_EKET);
    }

    // =========================================================================
    // Estrazione VBEP - massiva (invariata)
    // =========================================================================

    private static void extractVbepAll(AppConfig config, FcsRepository repo) throws Exception {
        log.info("--- Estrazione SCHEDULAZIONI OdV RESO (tutti gli OdV aperti) ---");

        SalesReturnClient client = new SalesReturnClient(config);
        List<EketLine> lines = client.fetchAllOpenReturnScheduleLines();

        if (lines.isEmpty()) {
            log.warn("Nessuna schedulazione OdV reso aperta trovata.");
            return;
        }

        List<EketLine>       withMtart = applyMaterialTypes(lines, config);
        Map<String, Fcst001> fcst001   = repo.loadFcst001();
        List<EketLine>       filtered  = filterByFcst001(withMtart, fcst001);
        List<EketLine>       enriched  = enrichLinesReso(filtered, repo);
        repo.syncEketLines(enriched, KAPPL_VBEP);
    }

    // =========================================================================
    // Estrazione VBEP - puntuale (invariata)
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

        List<EketLine>       withMtart = lines.isEmpty() ? List.of() : applyMaterialTypes(lines, config);
        Map<String, Fcst001> fcst001   = repo.loadFcst001();
        List<EketLine>       filtered  = withMtart.isEmpty() ? List.of() : filterByFcst001(withMtart, fcst001);
        List<EketLine>       enriched  = filtered.isEmpty() ? List.of() : enrichLinesReso(filtered, repo);
        repo.syncEketLinesForOrder(vbeln, enriched, KAPPL_VBEP);
    }

    // =========================================================================
    // [DELTA] Sincronizzazione differenziale PRODOTTI (MARA)
    // =========================================================================

    private static void deltaProducts(AppConfig config, FcsRepository repo,
                                      SyncRepository syncRepo) throws Exception {
        log.info("--- [delta] PRODOTTI (MARA) ---");
        long start = System.currentTimeMillis();

        SyncRecord rec = syncRepo.load("MARA");
        syncRepo.markRunning("MARA");

        try {
            ProductClient client = new ProductClient(config);
            List<Product> products;

            if (rec.isFirstRun()) {
                // Prima esecuzione: carico completo
                log.info("[delta-MARA] Prima esecuzione — carico completo.");
                products = client.fetchAllProducts();
            } else {
                products = client.fetchModifiedSince(rec.nextSyncFrom());
            }

            int upserted = 0;
            if (!products.isEmpty()) {
                upserted = repo.upsertProducts(products);
            }

            syncRepo.markOk("MARA", products.size(), upserted,
                            System.currentTimeMillis() - start);

        } catch (Exception e) {
            syncRepo.markError("MARA", e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // [DELTA] Sincronizzazione differenziale FORNITORI (LFA1)
    // =========================================================================

    private static void deltaSuppliers(AppConfig config, FcsRepository repo,
                                       SyncRepository syncRepo) throws Exception {
        log.info("--- [delta] FORNITORI (LFA1) ---");
        long start = System.currentTimeMillis();

        SyncRecord rec = syncRepo.load("LFA1");
        syncRepo.markRunning("LFA1");

        try {
            BusinessPartnerClient client = new BusinessPartnerClient(config);
            List<Supplier> suppliers;

            if (rec.isFirstRun()) {
                log.info("[delta-LFA1] Prima esecuzione — carico completo.");
                suppliers = client.fetchAllSuppliers();
            } else {
                suppliers = client.fetchSuppliersModifiedSince(rec.nextSyncFrom());
            }

            int upserted = 0;
            if (!suppliers.isEmpty()) {
                upserted = repo.upsertSuppliers(suppliers);
            }

            syncRepo.markOk("LFA1", suppliers.size(), upserted,
                            System.currentTimeMillis() - start);

        } catch (Exception e) {
            syncRepo.markError("LFA1", e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // [DELTA] Sincronizzazione differenziale CLIENTI (KNA1)
    // =========================================================================

    private static void deltaCustomers(AppConfig config, FcsRepository repo,
                                       SyncRepository syncRepo) throws Exception {
        log.info("--- [delta] CLIENTI (KNA1) ---");
        long start = System.currentTimeMillis();

        SyncRecord rec = syncRepo.load("KNA1");
        syncRepo.markRunning("KNA1");

        try {
            BusinessPartnerClient client = new BusinessPartnerClient(config);
            List<Customer> customers;

            if (rec.isFirstRun()) {
                log.info("[delta-KNA1] Prima esecuzione — carico completo.");
                customers = client.fetchAllCustomers();
            } else {
                customers = client.fetchCustomersModifiedSince(rec.nextSyncFrom());
            }

            int upserted = 0;
            if (!customers.isEmpty()) {
                upserted = repo.upsertCustomers(customers);
            }

            syncRepo.markOk("KNA1", customers.size(), upserted,
                            System.currentTimeMillis() - start);

        } catch (Exception e) {
            syncRepo.markError("KNA1", e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // [DELTA] Sincronizzazione differenziale SCHEDULAZIONI OdA (EKET)
    // =========================================================================

    private static void deltaEket(AppConfig config, FcsRepository repo,
                                  SyncRepository syncRepo) throws Exception {
        log.info("--- [delta] SCHEDULAZIONI OdA (EKET) ---");
        long start = System.currentTimeMillis();

        SyncRecord rec = syncRepo.load("EKET");
        syncRepo.markRunning("EKET");

        try {
            if (rec.isFirstRun()) {
                // Prima esecuzione: carico completo con il metodo massivo esistente
                log.info("[delta-EKET] Prima esecuzione — carico completo.");
                extractEketAll(config, repo);
                syncRepo.markOk("EKET", -1, -1, System.currentTimeMillis() - start);
                return;
            }

            PurchaseOrderClient client = new PurchaseOrderClient(config);
            Map<String, List<EketLine>> byOrder =
                    client.fetchModifiedOrdersSince(rec.nextSyncFrom());

            if (byOrder.isEmpty()) {
                log.info("[delta-EKET] Nessun OdA modificato.");
                syncRepo.markOk("EKET", 0, 0, System.currentTimeMillis() - start);
                return;
            }

            Map<String, Fcst001> fcst001 = repo.loadFcst001();
            int totalFound    = 0;
            int totalUpserted = 0;

            for (Map.Entry<String, List<EketLine>> entry : byOrder.entrySet()) {
                String         ebeln = entry.getKey();
                List<EketLine> lines = entry.getValue();
                totalFound += lines.size();

                List<EketLine> withMtart = lines.isEmpty() ? List.of()
                        : applyMaterialTypes(lines, config);
                List<EketLine> filtered  = withMtart.isEmpty() ? List.of()
                        : filterByFcst001(withMtart, fcst001);
                List<EketLine> enriched  = filtered.isEmpty() ? List.of()
                        : enrichLinesOda(filtered, repo);

                // syncEketLinesForOrder: DELETE wmsst(0,3) per EBELN + INSERT nuove
                // Se enriched è vuoto, rimuove solo le righe residue in attesa.
                int inserted = repo.syncEketLinesForOrder(ebeln, enriched, KAPPL_EKET);
                totalUpserted += inserted;
            }

            syncRepo.markOk("EKET", totalFound, totalUpserted,
                            System.currentTimeMillis() - start);

        } catch (Exception e) {
            syncRepo.markError("EKET", e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // [DELTA] Sincronizzazione differenziale SCHEDULAZIONI OdV RESO (VBEP)
    // =========================================================================

    private static void deltaVbep(AppConfig config, FcsRepository repo,
                                  SyncRepository syncRepo) throws Exception {
        log.info("--- [delta] SCHEDULAZIONI OdV RESO (VBEP) ---");
        long start = System.currentTimeMillis();

        SyncRecord rec = syncRepo.load("VBEP");
        syncRepo.markRunning("VBEP");

        try {
            if (rec.isFirstRun()) {
                log.info("[delta-VBEP] Prima esecuzione — carico completo.");
                extractVbepAll(config, repo);
                syncRepo.markOk("VBEP", -1, -1, System.currentTimeMillis() - start);
                return;
            }

            SalesReturnClient client = new SalesReturnClient(config);
            Map<String, List<EketLine>> byOrder =
                    client.fetchModifiedReturnsSince(rec.nextSyncFrom());

            if (byOrder.isEmpty()) {
                log.info("[delta-VBEP] Nessun OdV reso modificato.");
                syncRepo.markOk("VBEP", 0, 0, System.currentTimeMillis() - start);
                return;
            }

            Map<String, Fcst001> fcst001 = repo.loadFcst001();
            int totalFound    = 0;
            int totalUpserted = 0;

            for (Map.Entry<String, List<EketLine>> entry : byOrder.entrySet()) {
                String         vbeln = entry.getKey();
                List<EketLine> lines = entry.getValue();
                totalFound += lines.size();

                List<EketLine> withMtart = lines.isEmpty() ? List.of()
                        : applyMaterialTypes(lines, config);
                List<EketLine> filtered  = withMtart.isEmpty() ? List.of()
                        : filterByFcst001(withMtart, fcst001);
                List<EketLine> enriched  = filtered.isEmpty() ? List.of()
                        : enrichLinesReso(filtered, repo);

                int inserted = repo.syncEketLinesForOrder(vbeln, enriched, KAPPL_VBEP);
                totalUpserted += inserted;
            }

            syncRepo.markOk("VBEP", totalFound, totalUpserted,
                            System.currentTimeMillis() - start);

        } catch (Exception e) {
            syncRepo.markError("VBEP", e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // Arricchimento mtart da API_PRODUCT_SRV (invariato)
    // =========================================================================

    private static List<EketLine> applyMaterialTypes(List<EketLine> lines, AppConfig config) {
        java.util.Set<String> matnrsDaMappare = lines.stream()
                .filter(l -> l.mtart() == null || l.mtart().isBlank())
                .map(EketLine::matnr)
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.toSet());

        if (matnrsDaMappare.isEmpty()) {
            log.debug("mtart già valorizzato per tutte le righe — skip lookup API_PRODUCT_SRV.");
            return lines;
        }

        log.info("Recupero mtart da API_PRODUCT_SRV per {} matnr distinti", matnrsDaMappare.size());
        Map<String, String> mtartMap = new com.eone.fcs.client.ProductClient(config)
                .fetchMaterialTypes(matnrsDaMappare);

        List<EketLine> result = new ArrayList<>(lines.size());
        for (EketLine line : lines) {
            String mtart = line.mtart();
            if (mtart == null || mtart.isBlank()) {
                String matnr = line.matnr() != null ? line.matnr().strip() : "";
                mtart = mtartMap.get(matnr);
            }
            if (mtart != null && !mtart.isBlank()) {
                result.add(EketLine.Builder.from(line).mtart(mtart).build());
            } else {
                result.add(line);
            }
        }

        long filled = result.stream().filter(l -> l.mtart() != null && !l.mtart().isBlank()).count();
        log.info("mtart valorizzato: {} su {} righe totali", filled, result.size());
        return result;
    }

    // =========================================================================
    // Filtro per configurazione tabfcst001 (invariato)
    // =========================================================================

    private static List<EketLine> filterByFcst001(List<EketLine> lines,
                                                   Map<String, Fcst001> fcst001) {
        if (fcst001.isEmpty()) {
            log.warn("tabfcst001 vuota: nessun filtro per tipo materiale applicato.");
            return lines;
        }

        List<EketLine> included = new ArrayList<>();
        int skipped = 0;

        for (EketLine line : lines) {
            String mtart = line.mtart() != null ? line.mtart().strip() : "";
            String werks = line.werks() != null ? line.werks().strip() : "";

            if (mtart.isBlank() || werks.isBlank()) {
                log.debug("Riga esclusa (mtart o werks assente): ebeln={} ebelp={} etenr={}",
                        line.ebeln(), line.ebelp(), line.etenr());
                skipped++;
                continue;
            }

            Fcst001 cfg = fcst001.get(Fcst001.key(mtart, werks));
            if (cfg != null && cfg.isEnabled()) {
                included.add(line);
            } else {
                log.debug("Riga esclusa da tabfcst001 (mtart={} werks={} exp2fcs={}): ebeln={} ebelp={}",
                        mtart, werks, cfg != null ? cfg.exp2fcs() : "N/A",
                        line.ebeln(), line.ebelp());
                skipped++;
            }
        }

        log.info("Filtro tabfcst001: {} righe incluse, {} escluse su {} totali",
                included.size(), skipped, lines.size());
        return included;
    }

    // =========================================================================
    // Arricchimento OdA (invariato)
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

        return com.eone.fcs.service.EketEnricher.enrich(lines,
                umforMap, java.util.Map.of(),
                pesiMap,
                nomiForni, java.util.Map.of());
    }

    // =========================================================================
    // Arricchimento Resi (invariato)
    // =========================================================================

    private static List<EketLine> enrichLinesReso(List<EketLine> lines,
                                                   FcsRepository repo) throws Exception {
        Set<String> matnrs = lines.stream()
                .map(EketLine::matnr)
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.toSet());

        Set<String> kunnrs = lines.stream()
                .map(EketLine::lifnr)
                .filter(k -> k != null && !k.isBlank())
                .collect(Collectors.toSet());

        Map<String, Umcli>         umcliMap  = repo.loadUmcli(matnrs);
        Map<String, PesoMateriale> pesiMap   = repo.loadPesi(matnrs);
        Map<String, String>        nomiCli   = repo.loadNomiClienti(kunnrs);

        log.info("Resi — UMCLI: {} record, Pesi: {} record, Clienti: {} record",
                 umcliMap.size(), pesiMap.size(), nomiCli.size());

        return com.eone.fcs.service.EketEnricher.enrich(lines,
                java.util.Map.of(), umcliMap,
                pesiMap,
                java.util.Map.of(), nomiCli);
    }
}
