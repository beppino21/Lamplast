package eone.fcs.view.managedbeans;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;
import org.eclnt.jsfserver.base.faces.event.ActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CCGenClass(expressionBase = "#{d.ExtractBridgeDataUI}")
public class ExtractBridgeDataUI
        extends PageBean
        implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ExtractBridgeDataUI.class);

    // Percorsi del bridge JAR — stessa logica di EketResource:
    // 1° JVM property (-Dbridge.jar.path=...), 2° variabile d'ambiente, 3° fallback Linux.
    private static final String BRIDGE_JAR_PATH =
            System.getProperty("bridge.jar.path",
            System.getenv("BRIDGE_JAR_PATH") != null
                ? System.getenv("BRIDGE_JAR_PATH")
                : "/opt/fcs/fcs-wms-bridge-1.0.0.jar");

    private static final String BRIDGE_CONFIG_PATH =
            System.getProperty("bridge.config.path",
            System.getenv("BRIDGE_CONFIG_PATH") != null
                ? System.getenv("BRIDGE_CONFIG_PATH")
                : "/opt/fcs/config.properties");

    // Output del bridge mostrato nella textarea della pagina
    private String m_bridgeOutput = "";

    // ------------------------------------------------------------------------
    // Handlers dei bottoni
    // ------------------------------------------------------------------------

    public void onExtractAll(ActionEvent event) {
        runBridge("all", null);
    }

    public void onExtractEket(ActionEvent event) {
        runBridge("eket", null);
    }

    public void onExtractVbep(ActionEvent event) {
        runBridge("vbep", null);
    }

    public void onExtractCustomers(ActionEvent event) {
        runBridge("customers", null);
    }

    public void onExtractSuppliers(ActionEvent event) {
        runBridge("suppliers", null);
    }

    public void onExtractProducts(ActionEvent event) {
        runBridge("products", null);
    }

    // ------------------------------------------------------------------------
    // Esecuzione bridge JAR
    // ------------------------------------------------------------------------

    private void runBridge(String mode, String param) {
        log.info("ExtractBridgeDataUI: avvio bridge mode={} param={}", mode, param);
        StringBuilder sb = new StringBuilder();
        sb.append(">>> Avvio estrazione modalità: ").append(mode).append("\n");

        try {
            ProcessBuilder pb;
            if (param != null && !param.isBlank()) {
                pb = new ProcessBuilder(
                        "java", "-jar", BRIDGE_JAR_PATH,
                        BRIDGE_CONFIG_PATH,
                        mode,
                        param
                );
            } else {
                pb = new ProcessBuilder(
                        "java", "-jar", BRIDGE_JAR_PATH,
                        BRIDGE_CONFIG_PATH,
                        mode
                );
            }
            pb.redirectErrorStream(true);

            Process proc = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[bridge-{}] {}", mode, line);
                    sb.append(line).append("\n");
                }
            }

            int exitCode = proc.waitFor();
            if (exitCode == 0) {
                log.info("runBridge: mode={} completato con successo", mode);
                sb.append("\n>>> Completato con successo (exit code 0)");
            } else {
                log.warn("runBridge: bridge terminato con exit code {} per mode={}", exitCode, mode);
                sb.append("\n>>> Terminato con errore (exit code ").append(exitCode).append(")");
            }

        } catch (Exception e) {
            log.warn("runBridge: errore avvio bridge mode={}: {}", mode, e.getMessage());
            sb.append("\n>>> ERRORE: ").append(e.getMessage());
        }

        m_bridgeOutput = sb.toString();
    }

    // ------------------------------------------------------------------------
    // Getter / Setter per il binding sulla pagina
    // ------------------------------------------------------------------------

    public String getBridgeOutput() { return m_bridgeOutput; }
    public void setBridgeOutput(String value) { m_bridgeOutput = value; }

    // ------------------------------------------------------------------------
    // Inner classes
    // ------------------------------------------------------------------------

    public interface IListener extends Serializable {
    }

    // ------------------------------------------------------------------------
    // Members
    // ------------------------------------------------------------------------

    private IListener m_listener;

    // ------------------------------------------------------------------------
    // Constructors & initialization
    // ------------------------------------------------------------------------

    public ExtractBridgeDataUI() {
    }

    public String getPageName() { return "/ExtractBridgeData.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.ExtractBridgeDataUI}"; }

    // ------------------------------------------------------------------------
    // Public usage
    // ------------------------------------------------------------------------

    public void prepare(IListener listener) {
        m_listener = listener;
    }
}
