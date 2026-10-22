package ma.it6.portefeuille.exception;

/**
 * Levée lorsque l'assistant IA ne peut pas répondre (HTTP 503) :
 * interrupteur global coupé, clé API absente, ou appel au modèle en
 * échec. Le message est toujours explicite et actionnable — jamais une
 * trace technique ni la clé.
 */
public class IaIndisponibleException extends RuntimeException {

    public IaIndisponibleException(String message) {
        super(message);
    }
}
