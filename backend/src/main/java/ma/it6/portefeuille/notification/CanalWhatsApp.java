package ma.it6.portefeuille.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Canal de notification WhatsApp (4D).
 * DÉSACTIVÉ PAR DÉFAUT : le bean n’est créé que si
 * {@code notification.whatsapp.actif=true}. Sans configuration, aucun appel
 * vers une API WhatsApp Business n’est effectué.
 */
@Component
@ConditionalOnProperty(name = "notification.whatsapp.actif", havingValue = "true", matchIfMissing = false)
public class CanalWhatsApp implements CanalNotification {

    private final String apiUrl;
    private final String token;

    public CanalWhatsApp(
            @Value("${notification.whatsapp.api-url:}") String apiUrl,
            @Value("${notification.whatsapp.token:}") String token) {
        this.apiUrl = apiUrl;
        this.token = token;
    }

    @Override
    public String getCode() {
        return "whatsapp";
    }

    @Override
    public boolean envoyer(String destinataire, String sujet, String corps) {
        // Implémentation réelle : appel HTTP vers l’API WhatsApp Business.
        // Ici, le canal est désactivé par défaut : aucune dépendance externe,
        // aucun appel réseau. Retourne false pour indiquer l’absence d’envoi.
        return false;
    }
}
