package ma.it6.portefeuille.service.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envoi d'e-mails via SMTP (Spring {@link JavaMailSender}).
 *
 * <p>Si aucun serveur SMTP n'est configure (pas de {@code spring.mail.host}),
 * l'e-mail n'est pas envoye mais seulement journalise : cela permet de
 * developper et tester le parcours client sans infrastructure mail, tout en
 * rendant une configuration SMTP obligatoire en production (une tentative
 * d'envoi reelle echoue explicitement au lieu de disparaitre en silence).
 */
@Service
public class SmtpEmailService implements EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String expediteur;
    private final String nomExpediteur;

    public SmtpEmailService(@Autowired(required = false) JavaMailSender mailSender,
                            @Value("${mail.from:noreply@it6.ma}") String expediteur,
                            @Value("${mail.from-name:Portefeuille IT6}") String nomExpediteur) {
        this.mailSender = mailSender;
        this.expediteur = expediteur;
        this.nomExpediteur = nomExpediteur;
    }

    @Override
    public void envoyer(String destinataire, String sujet, String corpsHtml, String corpsTexte) {
        if (mailSender == null) {
            LOG.warn("Aucun serveur SMTP configure : e-mail non envoye a {} (sujet: '{}'). "
                    + "Configurez spring.mail.host pour activer l'envoi reel.", destinataire, sujet);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Corps de l'e-mail non envoye:\n{}", corpsTexte);
            }
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(expediteur, nomExpediteur);
            helper.setTo(destinataire);
            helper.setSubject(sujet);
            helper.setText(corpsTexte, corpsHtml);
            mailSender.send(message);
            LOG.info("E-mail envoye a {} (sujet: '{}')", destinataire, sujet);
        } catch (jakarta.mail.MessagingException | java.io.UnsupportedEncodingException e) {
            throw new EmailEnvoiException("Impossible d'envoyer l'e-mail a " + destinataire, e);
        }
    }
}
