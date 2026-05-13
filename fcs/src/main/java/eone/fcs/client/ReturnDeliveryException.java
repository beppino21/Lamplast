package eone.fcs.client;

/**
 * Eccezione lanciata da ReturnDeliveryClient in caso di errore
 * durante la creazione della consegna reso o il PGI su S/4HC.
 *
 * Analoga a GoodsReceiptException — intercettata da EketResource.handleF
 * che provvede a marcare le righe con wmsst='E' e a restituire HTTP 400.
 */
public class ReturnDeliveryException extends RuntimeException {

    public ReturnDeliveryException(String message) {
        super(message);
    }

    public ReturnDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
