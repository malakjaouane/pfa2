package ma.it6.portefeuille.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.dto.PreferenceNotificationDTO;
import ma.it6.portefeuille.entity.Notification;
import ma.it6.portefeuille.entity.PreferenceNotification;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.notification.CanalNotification;
import ma.it6.portefeuille.repository.NotificationRepository;
import ma.it6.portefeuille.repository.PreferenceNotificationRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;

/**
 * Tests unitaires du service de notifications (4D) : préférences opt-in,
 * validation des fréquences, canaux conditionnels et digests planifiés.
 */
class NotificationServiceTest {

    private static final Long ID_ADMIN = 1L;

    private PreferenceNotificationRepository preferenceRepository;
    private NotificationRepository notificationRepository;
    private UtilisateurRepository utilisateurRepository;
    private CanalNotification emailCanal;
    private CanalNotification whatsappCanal;
    private NotificationService service;

    private Utilisateur admin;

    @BeforeEach
    void setup() {
        preferenceRepository = mock(PreferenceNotificationRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        utilisateurRepository = mock(UtilisateurRepository.class);

        emailCanal = mock(CanalNotification.class);
        when(emailCanal.getCode()).thenReturn("email");
        whatsappCanal = mock(CanalNotification.class);
        when(whatsappCanal.getCode()).thenReturn("whatsapp");

        admin = Utilisateur.builder()
                .idUser(ID_ADMIN)
                .nom("Admin")
                .prenom("IT6")
                .email("admin@it6.ma")
                .build();
        when(utilisateurRepository.findById(ID_ADMIN)).thenReturn(Optional.of(admin));

        // Mockito renvoie null par défaut : save doit rendre l'entité reçue
        // (le service fait p = save(p) avant de mapper en DTO).
        when(preferenceRepository.save(any(PreferenceNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service = nouveauService(true);
    }

    /** Service avec ou sans canal WhatsApp (kill switch de configuration). */
    private NotificationService nouveauService(boolean whatsappDisponible) {
        List<CanalNotification> canaux = whatsappDisponible
                ? List.of(emailCanal, whatsappCanal)
                : List.of(emailCanal);
        return new NotificationService(preferenceRepository, notificationRepository,
                utilisateurRepository, canaux);
    }

    private PreferenceNotification preferences(boolean emailActif, String frequence,
            boolean whatsappActif, String telephone) {
        return preferencesPour(admin, emailActif, frequence, whatsappActif, telephone);
    }

    private PreferenceNotification preferencesPour(Utilisateur u, boolean emailActif,
            String frequence, boolean whatsappActif, String telephone) {
        return PreferenceNotification.builder()
                .utilisateur(u)
                .emailActif(emailActif)
                .frequence(frequence)
                .whatsappActif(whatsappActif)
                .telephone(telephone)
                .dateCreation(LocalDateTime.now())
                .build();
    }

    // ---------- Préférences : lecture / création ----------

    @Test
    @DisplayName("Création : opt-in par défaut (email coupé, fréquence aucune, WhatsApp coupé)")
    void creationDefautsOptIn() {
        when(preferenceRepository.findByUtilisateurIdUser(ID_ADMIN)).thenReturn(Optional.empty());

        PreferenceNotificationDTO dto = service.getOuCreerPreferences(ID_ADMIN);

        ArgumentCaptor<PreferenceNotification> captor =
                ArgumentCaptor.forClass(PreferenceNotification.class);
        verify(preferenceRepository).save(captor.capture());
        assertThat(captor.getValue().getEmailActif()).isFalse();
        assertThat(captor.getValue().getFrequence()).isEqualTo("aucune");
        assertThat(captor.getValue().getWhatsappActif()).isFalse();
        assertThat(dto.getEmailActif()).isFalse();
        assertThat(dto.getFrequence()).isEqualTo("aucune");
    }

    @Test
    @DisplayName("Lecture : une préférence existante est retournée sans réenregistrement")
    void lectureExistante() {
        when(preferenceRepository.findByUtilisateurIdUser(ID_ADMIN))
                .thenReturn(Optional.of(preferences(true, "quotidienne", false, null)));

        PreferenceNotificationDTO dto = service.getOuCreerPreferences(ID_ADMIN);

        verify(preferenceRepository, never()).save(any());
        assertThat(dto.getEmailActif()).isTrue();
        assertThat(dto.getFrequence()).isEqualTo("quotidienne");
    }

    @Test
    @DisplayName("Lecture pour un compte introuvable : 404")
    void lectureCompteIntrouvable() {
        when(utilisateurRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOuCreerPreferences(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    // ---------- Préférences : mise à jour ----------

    @Test
    @DisplayName("Mise à jour : les quatre champs sont appliqués + date de modification")
    void majChamps() {
        PreferenceNotification p = preferences(false, "aucune", false, null);
        when(preferenceRepository.findByUtilisateurIdUser(ID_ADMIN)).thenReturn(Optional.of(p));
        PreferenceNotificationDTO dto = PreferenceNotificationDTO.builder()
                .emailActif(true)
                .frequence("hebdomadaire")
                .whatsappActif(true)
                .telephone("+212600000000")
                .build();

        PreferenceNotificationDTO maj = service.mettreAJourPreferences(ID_ADMIN, dto);

        verify(preferenceRepository).save(p);
        assertThat(p.getEmailActif()).isTrue();
        assertThat(p.getFrequence()).isEqualTo("hebdomadaire");
        assertThat(p.getWhatsappActif()).isTrue();
        assertThat(p.getTelephone()).isEqualTo("+212600000000");
        assertThat(p.getDateModification()).isNotNull();
        assertThat(maj.getFrequence()).isEqualTo("hebdomadaire");
        assertThat(maj.getIdUtilisateur()).isEqualTo(ID_ADMIN);
    }

    @Test
    @DisplayName("Fréquence hors domaine (« mensuelle ») : 400, rien n'est enregistré")
    void frequenceInvalide() {
        PreferenceNotificationDTO dto = PreferenceNotificationDTO.builder()
                .frequence("mensuelle")
                .build();

        assertThatThrownBy(() -> service.mettreAJourPreferences(ID_ADMIN, dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fréquence absente du corps : 400")
    void frequenceAbsente() {
        assertThatThrownBy(() -> service.mettreAJourPreferences(ID_ADMIN,
                        PreferenceNotificationDTO.builder().build()))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Booléens absents du corps : traités comme « coupé » (false), jamais null")
    void booleensAbsentsDuCorps() {
        PreferenceNotification p = preferences(true, "quotidienne", true, "+212600000000");
        when(preferenceRepository.findByUtilisateurIdUser(ID_ADMIN)).thenReturn(Optional.of(p));

        service.mettreAJourPreferences(ID_ADMIN,
                PreferenceNotificationDTO.builder().frequence("aucune").build());

        assertThat(p.getEmailActif()).isFalse();
        assertThat(p.getWhatsappActif()).isFalse();
    }

    @Test
    @DisplayName("Mise à jour pour un compte introuvable : 404")
    void majCompteIntrouvable() {
        when(utilisateurRepository.findById(99L)).thenReturn(Optional.empty());
        PreferenceNotificationDTO dto = PreferenceNotificationDTO.builder()
                .frequence("aucune")
                .build();

        assertThatThrownBy(() -> service.mettreAJourPreferences(99L, dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    // ---------- Envoi via canaux ----------

    @Test
    @DisplayName("Sans préférence enregistrée : rien n'est envoyé ni journalisé")
    void envoiSansPreferences() {
        when(preferenceRepository.findByUtilisateurIdUser(ID_ADMIN)).thenReturn(Optional.empty());

        assertThat(service.envoyer(ID_ADMIN, "Sujet", "Corps")).isFalse();

        verify(notificationRepository, never()).save(any());
        verify(emailCanal, never()).envoyer(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Email activé, canal OK : true + journal « envoyee »")
    void envoiEmailReussi() {
        when(preferenceRepository.findByUtilisateurIdUser(ID_ADMIN))
                .thenReturn(Optional.of(preferences(true, "quotidienne", false, null)));
        when(emailCanal.envoyer("admin@it6.ma", "Sujet", "Corps")).thenReturn(true);

        assertThat(service.envoyer(ID_ADMIN, "Sujet", "Corps")).isTrue();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getCanal()).isEqualTo("email");
        assertThat(captor.getValue().getStatut()).isEqualTo("envoyee");
        assertThat(captor.getValue().getUtilisateur()).isSameAs(admin);
    }

    @Test
    @DisplayName("Email activé, canal en échec : false + journal « echec »")
    void envoiEmailEchec() {
        when(preferenceRepository.findByUtilisateurIdUser(ID_ADMIN))
                .thenReturn(Optional.of(preferences(true, "quotidienne", false, null)));
        when(emailCanal.envoyer(anyString(), anyString(), anyString())).thenReturn(false);

        assertThat(service.envoyer(ID_ADMIN, "Sujet", "Corps")).isFalse();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo("echec");
    }

    @Test
    @DisplayName("WhatsApp activé mais canal coupé par config : aucune tentative, aucun journal")
    void whatsappCoupePasDeJournal() {
        NotificationService sansWhatsApp = nouveauService(false);
        when(preferenceRepository.findByUtilisateurIdUser(ID_ADMIN))
                .thenReturn(Optional.of(preferences(true, "quotidienne", true, "+212600000000")));
        when(emailCanal.envoyer(anyString(), anyString(), anyString())).thenReturn(true);

        assertThat(sansWhatsApp.envoyer(ID_ADMIN, "Sujet", "Corps")).isTrue();

        // Seul l'email est journalisé : pas de « echec » fantôme pour un
        // canal désactivé par configuration.
        verify(notificationRepository, times(1)).save(any());
        verify(whatsappCanal, never()).envoyer(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("WhatsApp activé et disponible : les deux canaux sont tentés")
    void whatsappDisponibleDeuxJournalisations() {
        when(preferenceRepository.findByUtilisateurIdUser(ID_ADMIN))
                .thenReturn(Optional.of(preferences(true, "quotidienne", true, "+212600000000")));
        when(emailCanal.envoyer(anyString(), anyString(), anyString())).thenReturn(true);
        when(whatsappCanal.envoyer(anyString(), anyString(), anyString())).thenReturn(true);

        assertThat(service.envoyer(ID_ADMIN, "Sujet", "Corps")).isTrue();

        verify(notificationRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Envoi pour un compte introuvable : 404")
    void envoiCompteIntrouvable() {
        when(utilisateurRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.envoyer(99L, "Sujet", "Corps"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    // ---------- Digests planifiés ----------

    @Test
    @DisplayName("Digest : contacte chaque abonné de la fréquence (2 comptes)")
    void digestQuotidien() {
        Utilisateur responsable = Utilisateur.builder()
                .idUser(2L)
                .nom("Responsable")
                .email("responsable@it6.ma")
                .build();
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(responsable));

        PreferenceNotification prefsAdmin = preferences(true, "quotidienne", false, null);
        PreferenceNotification prefsResp = preferencesPour(responsable, true,
                "quotidienne", false, null);
        when(preferenceRepository.findByFrequenceAndEmailActifTrue("quotidienne"))
                .thenReturn(List.of(prefsAdmin, prefsResp));
        when(preferenceRepository.findByUtilisateurIdUser(ID_ADMIN))
                .thenReturn(Optional.of(prefsAdmin));
        when(preferenceRepository.findByUtilisateurIdUser(2L))
                .thenReturn(Optional.of(prefsResp));
        when(emailCanal.envoyer(anyString(), anyString(), anyString())).thenReturn(true);

        int contactes = service.envoyerResume("quotidienne", "Résumé", "Corps");

        assertThat(contactes).isEqualTo(2);
        verify(notificationRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Digest : aucun abonné à la fréquence -> aucun envoi")
    void digestSansAbonne() {
        when(preferenceRepository.findByFrequenceAndEmailActifTrue("hebdomadaire"))
                .thenReturn(List.of());

        assertThat(service.envoyerResume("hebdomadaire", "Résumé", "Corps")).isZero();

        verify(notificationRepository, never()).save(any());
    }
}
