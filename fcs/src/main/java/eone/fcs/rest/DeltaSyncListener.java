package eone.fcs.rest;

import eone.fcs.FcsConfig;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler del delta sync FCS WMS Bridge.
 *
 * Si aggancia al ciclo di vita di Tomcat: parte al deploy della webapp,
 * si ferma all'undeploy.
 *
 * Due schedulazioni distinte:
 *
 *   TRANSAZIONALE (EKET + VBEP) — ogni 5 minuti
 *     LastChangeDateTime disponibile al secondo → polling frequente ha senso.
 *     Modalità bridge: "delta-trx"
 *
 *   ANAGRAFICA (MARA + LFA1 + KNA1) — una volta al giorno alle 06:00
 *     LastChangeDate disponibile solo al giorno → polling frequente è inutile.
 *     Modalità bridge: "delta-ana"
 *
 * Nota: le due modalità "delta-trx" e "delta-ana" vengono gestite dal Main
 * del bridge che esegue solo le entità pertinenti in base all'argomento.
 */
@WebListener
public class DeltaSyncListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(DeltaSyncListener.class);

    private static final String BRIDGE_JAR_PATH    = FcsConfig.getInstance().getBridgeJarPath();
    private static final String BRIDGE_CONFIG_PATH = FcsConfig.getInstance().getBridgeConfigPath();

    // Schedulazione transazionale (EKET + VBEP)
    private static final int TRX_INITIAL_DELAY_MINUTES = 2;
    private static final int TRX_PERIOD_MINUTES        = 5;

    // Schedulazione anagrafica (MARA + LFA1 + KNA1)
    // Primo run: calcola i minuti mancanti alle 06:00 di oggi (o domani se già passate)
    private static final int ANA_PERIOD_HOURS = 24;

    private ScheduledExecutorService schedulerTrx;
    private ScheduledExecutorService schedulerAna;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println(">>> DeltaSyncListener: contextInitialized chiamato <<<");

        // --- Scheduler transazionale: EKET + VBEP ogni 5 minuti ---
        log.info("DeltaSyncListener: avvio scheduler TRANSAZIONALE " +
                 "(primo run tra {}min, poi ogni {}min)",
                 TRX_INITIAL_DELAY_MINUTES, TRX_PERIOD_MINUTES);

        schedulerTrx = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fcs-delta-trx");
            t.setDaemon(true);
            return t;
        });

        schedulerTrx.scheduleAtFixedRate(
            DeltaSyncListener::runDeltaTrx,
            TRX_INITIAL_DELAY_MINUTES,
            TRX_PERIOD_MINUTES,
            TimeUnit.MINUTES
        );

        // --- Scheduler anagrafico: MARA + LFA1 + KNA1 una volta al giorno alle 06:00 ---
        long minutesToSixAM = minutesUntilSixAM();
        log.info("DeltaSyncListener: avvio scheduler ANAGRAFICO " +
                 "(primo run tra {}min, poi ogni {}h — target: 06:00)",
                 minutesToSixAM, ANA_PERIOD_HOURS);

        schedulerAna = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fcs-delta-ana");
            t.setDaemon(true);
            return t;
        });

        schedulerAna.scheduleAtFixedRate(
            DeltaSyncListener::runDeltaAna,
            minutesToSixAM,
            ANA_PERIOD_HOURS * 60L,
            TimeUnit.MINUTES
        );
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("DeltaSyncListener: arresto scheduler delta");
        if (schedulerTrx != null) schedulerTrx.shutdownNow();
        if (schedulerAna != null) schedulerAna.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Run delta transazionale: EKET + VBEP
    // -------------------------------------------------------------------------

    private static void runDeltaTrx() {
        log.info("[delta-trx] Avvio run delta transazionale (EKET + VBEP)...");
        runBridge("delta-trx");
    }

    // -------------------------------------------------------------------------
    // Run delta anagrafico: MARA + LFA1 + KNA1
    // -------------------------------------------------------------------------

    private static void runDeltaAna() {
        log.info("[delta-ana] Avvio run delta anagrafico (MARA + LFA1 + KNA1)...");
        runBridge("delta-ana");
    }

    // -------------------------------------------------------------------------
    // Esecuzione bridge JAR
    // -------------------------------------------------------------------------

    private static void runBridge(String mode) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", BRIDGE_JAR_PATH,
                    BRIDGE_CONFIG_PATH,
                    mode
            );
            pb.redirectErrorStream(true);

            Process proc = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                reader.lines().forEach(line ->
                    log.debug("[bridge-{}] {}", mode, line));
            }

            int exitCode = proc.waitFor();
            if (exitCode == 0) {
                log.info("[{}] Run completato con successo.", mode);
            } else {
                log.warn("[{}] Bridge terminato con exit code {}", mode, exitCode);
            }

        } catch (Exception e) {
            // CRITICO: non rilanciare mai — ScheduledExecutorService
            // annulla i run futuri se il Runnable propaga un'eccezione.
            log.error("[{}] Errore durante il run: {}", mode, e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Utility: minuti mancanti alle 06:00 (ora locale)
    // -------------------------------------------------------------------------

    private static long minutesUntilSixAM() {
        java.time.LocalDateTime now  = java.time.LocalDateTime.now();
        java.time.LocalDateTime next = now.toLocalDate().atTime(6, 0);
        if (!now.isBefore(next)) {
            next = next.plusDays(1);
        }
        return java.time.Duration.between(now, next).toMinutes();
    }
}
