package lamplast.mbxa.view.managedbeans;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.eclnt.jsfserver.util.HttpSessionAccess;

import lamplast.mbxa.logic.service.DdtException;
import lamplast.mbxa.logic.service.Postazioni;

/** Utilità comuni alle maschere. */
final class UiUtil {

    static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    static final String FONT_OK = "color:#1B5E20";
    static final String FONT_ERR = "weight:bold;color:#B71C1C";
    static final String FONT_WARN = "weight:bold;color:#E65100";

    private UiUtil() {}

    /**
     * Postazione per il registro eventi (non c'è login): nome del PC ricavato dal DNS
     * interno, con l'indirizzo IP tra parentesi (vedi {@link Postazioni}).
     */
    static String postazione() {
        try {
            var req = HttpSessionAccess.getCurrentRequest();
            if (req == null) return null;
            String fwd = req.getHeader("X-Forwarded-For");
            String ip = fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : req.getRemoteAddr();
            return Postazioni.descrivi(ip);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Messaggio da mostrare: quello di business così com'è, gli altri con il tipo di errore. */
    static String messaggio(Throwable t) {
        if (t instanceof DdtException) return t.getMessage();
        t.printStackTrace();
        String m = t.getMessage();
        return "Errore: " + (m == null || m.isBlank() ? t.getClass().getSimpleName() : m);
    }

    static Date toDate(LocalDate d) {
        return d == null ? null : Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    static LocalDate toLocalDate(Date d) {
        return d == null ? null : d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    static String fmt(LocalDate d) {
        return d == null ? "" : d.format(D);
    }

    static String nvl(String s) {
        return s == null ? "" : s;
    }
}
