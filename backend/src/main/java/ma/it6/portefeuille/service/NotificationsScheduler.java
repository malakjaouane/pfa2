package ma.it6.portefeuille.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tâches planifiées de notifications (4D).
 *
 * <p>Le <b>résumé quotidien</b> (08h30) part aux comptes en fréquence
 * « quotidienne » ayant activé l'email. Le <b>résumé hebdomadaire</b> n'est
 * pas géré ici : c'est le <b>rapport IA</b> du lundi 09h30
 * ({@link AssistantIaAvanceService#envoyerRapportHebdomadaire()}) qui occupe
 * ce créneau — un second digest ce même lundi serait un doublon. Le contenu
 * du résumé est un message générique invitant à consulter le portefeuille ;
 * les préférences de chaque compte (fréquence, canaux) sont respectées par
 * {@link NotificationService#envoyer}.</p>
 */
@Component
public class NotificationsScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationsScheduler.class);

    private final NotificationService notificationService;

    public NotificationsScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Résumé quotidien : envoyé chaque matin aux comptes en fréquence
     * « quotidienne » ayant activé l'email.
     */
    @Scheduled(cron = "0 30 8 * * *") // 08h30 tous les jours
    public void envoyerResumeQuotidien() {
        int contactes = notificationService.envoyerResume("quotidienne",
                "Résumé quotidien du portefeuille",
                "Bonjour, consultez votre portefeuille d'initiatives pour faire le point de la journée.");
        log.info("Résumé quotidien : {} compte(s) contacté(s)", contactes);
    }
}
