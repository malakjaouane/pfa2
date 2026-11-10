package ma.it6.portefeuille.notification;

/**
 * Canal de notification (4D).
 * Une implémentation par canal (email, WhatsApp…).
 * Le canal WhatsApp est désactivable par configuration et non configuré
 * par défaut.
 */
public interface CanalNotification {

    /** Code du canal (ex. "email", "whatsapp"). */
    String getCode();

    /** Envoie une notification. Retourne true si l’envoi a réussi. */
    boolean envoyer(String destinataire, String sujet, String corps);
}
