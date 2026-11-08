package ma.it6.portefeuille.service.email;

/**
 * Envoi d'e-mails transactionnels (verification de compte, reinitialisation
 * de mot de passe, invitation d'un collegue).
 *
 * <p>Une seule methode : le corps HTML et texte sont construits par
 * {@link EmailTemplateBuilder} selon la langue du destinataire.</p>
 */
public interface EmailService {

    /**
     * Envoie un e-mail.
     *
     * @param destinataire adresse e-mail du destinataire
     * @param sujet        sujet de l'e-mail
     * @param corpsHtml    corps au format HTML
     * @param corpsTexte   version texte simple (clients sans HTML)
     */
    void envoyer(String destinataire, String sujet, String corpsHtml, String corpsTexte);
}
