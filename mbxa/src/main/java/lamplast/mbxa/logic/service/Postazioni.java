package lamplast.mbxa.logic.service;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Nome leggibile della postazione per il registro eventi (non c'è login).
 * <p>
 * Dall'indirizzo del client ricava, tramite il DNS interno, il nome del PC:
 * "PC-SPEDIZIONI (192.168.1.23)". Se il nome non si trova resta l'indirizzo;
 * le chiamate dal server stesso diventano "server (locale)".
 * <p>
 * La ricerca DNS ha un tempo massimo: se il DNS è lento l'operazione non aspetta,
 * registra l'indirizzo e il nome trovato in seguito vale per le operazioni successive.
 * I nomi restano in memoria per un'ora.
 */
public final class Postazioni {

    static final String LOCALE = "server (locale)";
    private static final long ATTESA_MS = 1500;
    private static final long VALIDITA_MS = 60L * 60 * 1000;

    private static final Map<String, Voce> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService DNS = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "mbxa-dns");
        t.setDaemon(true);
        return t;
    });

    private static final class Voce {
        final String testo;
        final long scade;
        Voce(String testo) {
            this.testo = testo;
            this.scade = System.currentTimeMillis() + VALIDITA_MS;
        }
    }

    private Postazioni() {}

    /** Testo da registrare per l'indirizzo del client (null se l'indirizzo non è noto). */
    public static String descrivi(String indirizzo) {
        if (indirizzo == null || indirizzo.isBlank()) return null;
        String ip = pulisci(indirizzo);
        if (isLocale(ip)) return LOCALE;

        Voce v = CACHE.get(ip);
        if (v != null && v.scade > System.currentTimeMillis()) return v.testo;

        CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> {
            String testo = componi(ip, nomeDaDns(ip));
            CACHE.put(ip, new Voce(testo));
            return testo;
        }, DNS);
        try {
            return f.get(ATTESA_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // DNS lento o non raggiungibile: per ora l'indirizzo, il nome arriverà in cache
            return ip;
        }
    }

    /** "PC-SPEDIZIONI (192.168.1.23)", oppure solo l'indirizzo se il nome non c'è. */
    static String componi(String ip, String nome) {
        if (nome == null || nome.isBlank() || nome.equalsIgnoreCase(ip) || sembraIndirizzo(nome)) return ip;
        String breve = nome.contains(".") ? nome.substring(0, nome.indexOf('.')) : nome;
        String testo = breve.toUpperCase() + " (" + ip + ")";
        return testo.length() > 100 ? testo.substring(0, 100) : testo;
    }

    static boolean isLocale(String ip) {
        return ip.startsWith("127.") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1");
    }

    private static String pulisci(String s) {
        String ip = s.trim();
        if (ip.startsWith("[") && ip.endsWith("]")) ip = ip.substring(1, ip.length() - 1);
        int zona = ip.indexOf('%'); // IPv6 con zona (fe80::1%eth0)
        if (zona > 0) ip = ip.substring(0, zona);
        if (ip.startsWith("::ffff:") && ip.indexOf('.') > 0) ip = ip.substring(7); // IPv4 mappato in IPv6
        return ip;
    }

    private static boolean sembraIndirizzo(String s) {
        return s.matches("[0-9.]+") || s.contains(":");
    }

    private static String nomeDaDns(String ip) {
        try {
            return InetAddress.getByName(ip).getCanonicalHostName();
        } catch (Exception e) {
            return null;
        }
    }
}
