package eone.fcs.client;

/**
 * Eccezione lanciata da GoodsReceiptClient in caso di:
 *   - errore di configurazione (credenziali mancanti)
 *   - errore di comunicazione con S/4HC (timeout, rete)
 *   - risposta HTTP non 2xx da SAP
 *   - impossibilità di parsare la risposta
 *
 * Essendo una RuntimeException non richiede dichiarazione nel throws,
 * ma EketResource.handleF la intercetta esplicitamente per gestire
 * il fallback (setWmsstErrore) in modo pulito.
 */
public class GoodsReceiptException extends RuntimeException {
    public GoodsReceiptException(String message) {
        super(message);
    }
    public GoodsReceiptException(String message, Throwable cause) {
        super(message, cause);
    }
}
