package ma.it6.portefeuille.service.email;

/** Erreur lors de l'envoi d'un e-mail (SMTP indisponible, adresse invalide…). */
public class EmailEnvoiException extends RuntimeException {

    public EmailEnvoiException(String message, Throwable cause) {
        super(message, cause);
    }
}
