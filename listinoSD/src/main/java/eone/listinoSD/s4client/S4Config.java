package eone.listinoSD.s4client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configurazione connessione S/4HC.
 * Autenticazione Basic (User ID and Password) — Communication Arrangement SAP_COM_0294.
 * Stesso pattern di MovementClient in fcs.
 *
 * Voci in eone/ccee_config.properties:
 *   s4hc.baseUrl   = https://my434879-api.s4hana.cloud.sap
 *   s4hc.username  = JAVA_APP_USER
 *   s4hc.password  = xxxxxx
 */
public class S4Config {

    private final String baseUrl;
    private final String username;
    private final String password;

    public S4Config(String baseUrl, String username, String password) {
        this.baseUrl  = baseUrl;
        this.username = username;
        this.password = password;
    }

    public static S4Config fromCCConfig() {
        try (InputStream is = S4Config.class.getClassLoader()
                .getResourceAsStream("eone/ccee_config.properties")) {
            if (is == null)
                throw new RuntimeException("ccee_config.properties non trovato nel classpath (eone/)");
            Properties props = new Properties();
            props.load(is);
            return new S4Config(
                props.getProperty("s4hc.baseUrl"),
                props.getProperty("s4hc.username"),
                props.getProperty("s4hc.password")
            );
        } catch (IOException e) {
            throw new RuntimeException("Errore lettura ccee_config.properties", e);
        }
    }

    public String getBaseUrl()  { return baseUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
}
