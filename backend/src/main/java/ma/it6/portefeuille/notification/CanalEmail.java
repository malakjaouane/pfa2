package ma.it6.portefeuille.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Canal de notification par email (4D).
 * Utilise {@link JavaMailSender} (spring-boot-starter-mail déjà présent).
 * En mode développement sans SMTP configuré, Spring Mail bascule en mode
 * log et n’envoie aucun email réel.
 */
@Component
public class CanalEmail implements CanalNotification {

    private final JavaMailSender mailSender;
    private final String expediteur;

    public CanalEmail(JavaMailSender mailSender,
                      @Value("${spring.mail.username:notifications@it6.ma}") String expediteur) {
        this.mailSender = mailSender;
        this.expediteur = expediteur;
    }

    @Override
    public String getCode() {
        return "email";
    }

    @Override
    public boolean envoyer(String destinataire, String sujet, String corps) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(expediteur);
            message.setTo(destinataire);
            message.setSubject(sujet);
            message.setText(corps);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
