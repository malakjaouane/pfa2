package ma.it6.portefeuille.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     */
    @Transactional
    public PreferenceNotificationDTO mettreAJourPreferences(Long idUtilisateur, PreferenceNotificationDTO dto) {
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
        p.setEmailActif(dto.getEmailActif());
        p.setFrequence(dto.getFrequence());
        p.setWhatsappActif(dto.getWhatsappActif());
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

        if (p.getEmailActif() != null && p.getEmailActif() && p.getUtilisateur().getEmail() != null) {
            CanalNotification canal = canaux.get("email");
            boolean ok = canal != null && canal.envoyer(p.getUtilisateur().getEmail(), sujet, corps);
            enregistréNotification(u, "email", sujet, corps, ok);
            envoyé = envoyé || ok;
        }

        if (p.getWhatsappActif() != null && p.getWhatsappActif() && p.getTelephone() != null) {
            CanalNotification canal = canaux.get("whatsapp");
            boolean ok = canal != null && canal.envoyer(p.getTelephone(), sujet, corps);
            enregistréNotification(u, "whatsapp", sujet, corps, ok);
            envoyé = envoyé || ok;
        }

        return envoyé;
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
