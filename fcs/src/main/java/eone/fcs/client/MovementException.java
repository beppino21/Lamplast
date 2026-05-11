package eone.fcs.client;

/**
 * RuntimeException dedicata per errori nella chiamata movimenti MM a S/4HC.
 * Analogo a GoodsReceiptException per il flusso di entrata merce.
 */
public class MovementException extends RuntimeException {

    public MovementException(String message) {
        super(message);
    }

    public MovementException(String message, Throwable cause) {
        super(message, cause);
    }
}