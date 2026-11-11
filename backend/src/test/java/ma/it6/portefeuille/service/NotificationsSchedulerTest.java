package ma.it6.portefeuille.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test du planificateur de notifications (4D) : le digest quotidien
 * 08h30 déclenche le service en fréquence « quotidienne ». Le résumé
 * hebdomadaire n'est pas géré ici (rapport IA du lundi 09h30).
 */
class NotificationsSchedulerTest {

    @Test
    @DisplayName("Digest quotidien : fréquence « quotidienne », sujet et corps génériques")
    void digestQuotidienDelegation() {
        NotificationService service = mock(NotificationService.class);

        new NotificationsScheduler(service).envoyerResumeQuotidien();

        verify(service).envoyerResume(eq("quotidienne"), anyString(), anyString());
    }
}
