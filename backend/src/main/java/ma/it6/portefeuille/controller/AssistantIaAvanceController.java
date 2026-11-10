package ma.it6.portefeuille.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ma.it6.portefeuille.dto.InitiativeFormeDto;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.service.AssistantIaAvanceService;

/**
 * Assistant IA avance (4G).
 *
 * <p>Routes :</p>
 * <ul>
 *   <li>{@code POST /api/ia/creer-initiative} — propose une initiative a
 *       partir d'un texte libre (reserve a INITIATIVE_CREER). L'initiative
 *       n'est pas enregistre : l'utilisateur doit confirmer via
 *       {@code /api/ia/creer-initiative/confirmer}.</li>
 *   <li>{@code POST /api/ia/creer-initiative/confirmer} — enregistre
 *       l'initiative proposee (reserve a INITIATIVE_CREER).</li>
 * </ul>
 *
 * <p>Isolée par organisation via
 * {@link ma.it6.portefeuille.filter.OrganisationIsolationInterceptor}.</p>
 */
@RestController
@RequestMapping("/api/ia")
public class AssistantIaAvanceController {

    private final AssistantIaAvanceService service;
    private final UtilisateurRepository utilisateurRepository;

    public AssistantIaAvanceController(AssistantIaAvanceService service,
                                       UtilisateurRepository utilisateurRepository) {
        this.service = service;
        this.utilisateurRepository = utilisateurRepository;
    }

    private Long idUtilisateurConnecte(String email) {
        Utilisateur u = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        return u.getIdUser();
    }

    /**
     * Propose une initiative a partir d'un texte libre. L'initiative n'est
     * pas enregistre : l'utilisateur doit confirmer via
     * {@code /api/ia/creer-initiative/confirmer}.
     */
    @PostMapping("/creer-initiative")
    public ResponseEntity<InitiativeFormeDto> proposerDepuisTexte(
            @RequestParam String texte,
            @AuthenticationPrincipal String email) {
        InitiativeFormeDto dto = service.proposerDepuisTexte(texte, idUtilisateurConnecte(email));
        return ResponseEntity.ok(dto);
    }

    /**
     * Confirme et enregistre une initiative proposee par l'IA.
     */
    @PostMapping("/creer-initiative/confirmer")
    public ResponseEntity<Initiative> confirmerCreation(
            @AuthenticationPrincipal String email,
            InitiativeFormeDto dto) {
        Initiative i = service.confirmerCreation(dto, idUtilisateurConnecte(email));
        return ResponseEntity.ok(i);
    }
}
