package ma.it6.portefeuille.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.dto.PreferenceNotificationDTO;
import ma.it6.portefeuille.entity.Notification;
import ma.it6.portefeuille.entity.PreferenceNotification;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.notification.CanalNotification;
import ma.it6.portefeuille.repository.NotificationRepository;
import ma.it6.portefeuille.repository.PreferenceNotificationRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;

import static org.springframework.http.HttpStatus.*;

/**
 * Service de notifications (4D).
 * Gère les préférences par utilisateur et l’envoi via les canaux
 * configurés (email par défaut, WhatsApp optionnel et désactivé).
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    /** Fréquences de digest acceptées (aligné sur le CHECK en base). */
    private static final Set<String> FREQUENCES = Set.of("aucune", "quotidienne", "hebdomadaire");

    private final PreferenceNotificationRepository preferenceRepository;
    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final Map<String, CanalNotification> canaux;

    public NotificationService(PreferenceNotificationRepository preferenceRepository,
                               NotificationRepository notificationRepository,
                               UtilisateurRepository utilisateurRepository,
                               List<CanalNotification> canauxDisponibles) {
        this.preferenceRepository = preferenceRepository;
        this.notificationRepository = notificationRepository;
        this.utilisateurRepository = utilisateurRepository;
        // Injection de TOUS les canaux déclarés : CanalWhatsApp porte
        // @ConditionalOnProperty (notification.whatsapp.actif) — un
        // paramètre de type concret ferait échouer le démarrage quand le
        // connecteur est désactivé (cas par défaut, sans configuration).
        this.canaux = new HashMap<>();
        for (CanalNotification canal : canauxDisponibles) {
            this.canaux.put(canal.getCode(), canal);
        }
    }

    /**
     * Récupère (ou crée avec les valeurs par défaut) les préférences de
     * l’utilisateur donné.
     */
    @Transactional
    public PreferenceNotificationDTO getOuCreerPreferences(Long idUtilisateur) {
        Utilisateur u = utilisateurRepository.findById(idUtilisateur)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Utilisateur introuvable"));

        PreferenceNotification p = preferenceRepository.findByUtilisateurIdUser(idUtilisateur)
                .orElse(null);
        if (p == null) {
            p = PreferenceNotification.builder()
                    .utilisateur(u)
                    .emailActif(false)
                    .frequence("aucune")
                    .whatsappActif(false)
                    .telephone(null)
                    .dateCreation(LocalDateTime.now())
                    .build();
            p = preferenceRepository.save(p);
        }
        return toDTO(p);
    }

    /**
     * Met à jour les préférences de notification de l’utilisateur donné.
     * La fréquence est obligatoire et limitée aux valeurs du domaine ;
     * un booléen absent du corps vaut « coupé » (les colonnes sont NOT NULL).
     */
    @Transactional
    public PreferenceNotificationDTO mettreAJourPreferences(Long idUtilisateur, PreferenceNotificationDTO dto) {
        if (dto.getFrequence() == null || !FREQUENCES.contains(dto.getFrequence())) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Fréquence de notification invalide : « " + dto.getFrequence()
                            + " » (valeurs acceptées : aucune, quotidienne, hebdomadaire).");
        }
        Utilisateur u = utilisateurRepository.findById(idUtilisateur)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Utilisateur introuvable"));

        PreferenceNotification p = preferenceRepository.findByUtilisateurIdUser(idUtilisateur)
                .orElse(null);
        if (p == null) {
            p = PreferenceNotification.builder()
                    .utilisateur(u)
                    .dateCreation(LocalDateTime.now())
                    .build();
        }
        p.setEmailActif(Boolean.TRUE.equals(dto.getEmailActif()));
        p.setFrequence(dto.getFrequence());
        p.setWhatsappActif(Boolean.TRUE.equals(dto.getWhatsappActif()));
        p.setTelephone(dto.getTelephone());
        p.setDateModification(LocalDateTime.now());
        p = preferenceRepository.save(p);
        return toDTO(p);
    }

    /**
     * Envoie une notification à un utilisateur via ses canaux activés.
     * Retourne true si au moins un canal a accepté l’envoi.
     */
    @Transactional
    public boolean envoyer(Long idUtilisateur, String sujet, String corps) {
        Utilisateur u = utilisateurRepository.findById(idUtilisateur)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Utilisateur introuvable"));

        PreferenceNotification p = preferenceRepository.findByUtilisateurIdUser(idUtilisateur)
                .orElse(null);
        if (p == null) {
            return false;
        }

        boolean envoyé = false;

        // Un canal absent (WhatsApp désactivé par config) n'est pas tenté :
        // aucune ligne de journal « echec » pour un envoi jamais effectué.
        if (Boolean.TRUE.equals(p.getEmailActif()) && p.getUtilisateur().getEmail() != null) {
            CanalNotification canal = canaux.get("email");
            if (canal != null) {
                boolean ok = canal.envoyer(p.getUtilisateur().getEmail(), sujet, corps);
                enregistréNotification(u, "email", sujet, corps, ok);
                envoyé = envoyé || ok;
            }
        }

        if (Boolean.TRUE.equals(p.getWhatsappActif()) && p.getTelephone() != null) {
            CanalNotification canal = canaux.get("whatsapp");
            if (canal != null) {
                boolean ok = canal.envoyer(p.getTelephone(), sujet, corps);
                enregistréNotification(u, "whatsapp", sujet, corps, ok);
                envoyé = envoyé || ok;
            }
        }

        return envoyé;
    }

    /**
     * Envoie un résumé (digest) à tous les comptes abonnés à la fréquence
     * donnée et ayant activé l’email. Les canaux de chaque compte restent
     * respectés (WhatsApp inclus s’il est actif et disponible).
     *
     * @return le nombre de comptes contactés avec succès
     */
    @Transactional
    public int envoyerResume(String frequence, String sujet, String corps) {
        int comptesContactes = 0;
        for (PreferenceNotification p : preferenceRepository.findByFrequenceAndEmailActifTrue(frequence)) {
            if (envoyer(p.getUtilisateur().getIdUser(), sujet, corps)) {
                comptesContactes++;
            }
        }
        return comptesContactes;
    }

    private void enregistréNotification(Utilisateur u, String canal, String sujet, String corps, boolean envoyé) {
        Notification n = Notification.builder()
                .utilisateur(u)
                .canal(canal)
                .sujet(sujet)
                .corps(corps)
                .dateEnvoi(LocalDateTime.now())
                .statut(envoyé ? "envoyee" : "echec")
                .build();
        notificationRepository.save(n);
    }

    private PreferenceNotificationDTO toDTO(PreferenceNotification p) {
        return PreferenceNotificationDTO.builder()
                .idPreferenceNotification(p.getIdPreferenceNotification())
                .idUtilisateur(p.getUtilisateur().getIdUser())
                .emailActif(p.getEmailActif())
                .frequence(p.getFrequence())
                .whatsappActif(p.getWhatsappActif())
                .telephone(p.getTelephone())
                .dateCreation(p.getDateCreation())
                .dateModification(p.getDateModification())
                .build();
    }
}
