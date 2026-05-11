package eone.fcs.client;

/**
 * Risultato di una chiamata riuscita a MovementClient.
 * Contiene il numero documento materiale e l'anno restituiti da S/4HC.
 */
public class MovementResult {

    public final String mblnr;
    public final String mjahr;

    public MovementResult(String mblnr, String mjahr) {
        this.mblnr = mblnr;
        this.mjahr = mjahr;
    }

    @Override
    public String toString() {
        return "MovementResult{mblnr='" + mblnr + "', mjahr='" + mjahr + "'}";
    }
}
