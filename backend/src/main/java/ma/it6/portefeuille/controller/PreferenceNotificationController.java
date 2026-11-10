package ma.it6.portefeuille.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ma.it6.portefeuille.dto.PreferenceNotificationDTO;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.service.NotificationService;

/**
 * Contrôleur des préférences de notification (4D).
 *
 * <p>Routes :</p>
 * <ul>
 *   <li>{@code GET /api/notifications/preferences} — lire ou créer les
 *       préférences de l’utilisateur connecté.</li>
 *   <li>{@code PATCH /api/notifications/preferences} — mettre à jour les
 *       préférences de l’utilisateur connecté.</li>
 * </ul>
 *
 * <p>Isolée par organisation via
 * {@link ma.it6.portefeuille.filter.OrganisationIsolationInterceptor}.</p>
 */
@RestController
@RequestMapping("/api/notifications")
public class PreferenceNotificationController {

    private final NotificationService notificationService;
    private final UtilisateurRepository utilisateurRepository;

    public PreferenceNotificationController(NotificationService notificationService,
                                            UtilisateurRepository utilisateurRepository) {
        this.notificationService = notificationService;
        this.utilisateurRepository = utilisateurRepository;
    }

    private Long idUtilisateurConnecte(String email) {
        Utilisateur u = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        return u.getIdUser();
    }

    /**
     * Lit (ou crée avec les valeurs par défaut) les préférences de
     * notification de l’utilisateur connecté.
     */
    @GetMapping("/preferences")
    public ResponseEntity<PreferenceNotificationDTO> getPreferences(
            @AuthenticationPrincipal String email) {
        PreferenceNotificationDTO dto = notificationService.getOuCreerPreferences(idUtilisateurConnecte(email));
        return ResponseEntity.ok(dto);
    }

    /**
     * Met à jour les préférences de notification de l’utilisateur connecté.
     */
    @PatchMapping("/preferences")
    public ResponseEntity<PreferenceNotificationDTO> mettreAJourPreferences(
            @AuthenticationPrincipal String email,
            PreferenceNotificationDTO dto) {
        PreferenceNotificationDTO misAJour = notificationService.mettreAJourPreferences(
                idUtilisateurConnecte(email), dto);
        return ResponseEntity.ok(misAJour);
    }
}
