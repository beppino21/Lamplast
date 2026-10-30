package lamplast.mbxa.logic.service;

/** Errore "di business" con un messaggio da mostrare all'operatore così com'è. */
public class DdtException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DdtException(String message) {
        super(message);
    }

    /**
     * Numero DDT fuori sequenza in modo AVVISO: l'emissione è sospesa finché
     * l'operatore non conferma.
     */
    public static class SequenzaDaConfermare extends DdtException {
        private static final long serialVersionUID = 1L;

        public SequenzaDaConfermare(String message) {
            super(message);
        }
    }
}
