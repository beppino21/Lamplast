package eOne.conditionsSD.s4client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class S4Config {

    private final String baseUrl;
    private final String username;
    private final String password;
    private final String language;

    public S4Config(String baseUrl, String username, String password, String language) {
        this.baseUrl  = baseUrl;
        this.username = username;
        this.password = password;
        this.language = (language != null && !language.trim().isEmpty()) ? language : "IT";
    }

    public static S4Config fromCCConfig() {
        try (InputStream is = S4Config.class.getClassLoader()
                .getResourceAsStream("eOne/ccee_config.properties")) {
            if (is == null)
                throw new RuntimeException("ccee_config.properties non trovato nel classpath (eOne/)");
            Properties props = new Properties();
            props.load(is);
            return new S4Config(
                props.getProperty("s4hc.baseUrl"),
                props.getProperty("s4hc.username"),
                props.getProperty("s4hc.password"),
                props.getProperty("s4hc.language", "IT")
            );
        } catch (IOException e) {
            throw new RuntimeException("Errore lettura ccee_config.properties", e);
        }
    }

    public String getBaseUrl()  { return baseUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getLanguage() { return language; }
}
