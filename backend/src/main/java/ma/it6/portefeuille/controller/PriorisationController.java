package ma.it6.portefeuille.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ma.it6.portefeuille.dto.InitiativeResponse;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.mapper.InitiativeMapper;
import ma.it6.portefeuille.service.PriorisationService;

/**
 * Contrôleur de priorisation des initiatives (4C).
 *
 * <p>Route :</p>
 * <ul>
 *   <li>{@code PATCH /api/initiatives/{id}/priorisation} — met à jour la
 *       valeur et l'effort d'une initiative, et recalcule son score
 *       de priorisation (réservé à {@code INITIATIVE_MODIFIER}).</li>
 * </ul>
 *
 * <p>Isolée par organisation via
 * {@link ma.it6.portefeuille.filter.OrganisationIsolationInterceptor}.</p>
 */
@RestController
@RequestMapping("/api")
public class PriorisationController {

    private final PriorisationService priorisationService;

    public PriorisationController(PriorisationService priorisationService) {
        this.priorisationService = priorisationService;
    }

    /**
     * Met à jour la valeur et l'effort d'une initiative.
     *
     * @param idInitiative identifiant de l'initiative
     * @param valeur       valeur métier (1-5)
     * @param effort       effort estimé (1-5)
     * @param email        e-mail du compte connecté (principal JWT)
     * @return l'initiative mise à jour (avec son score recalculé)
     */
    @PatchMapping("/initiatives/{idInitiative}/priorisation")
    public ResponseEntity<InitiativeResponse> mettreAJourPriorisation(
            @PathVariable Long idInitiative,
            @RequestParam Integer valeur,
            @RequestParam Integer effort,
            @AuthenticationPrincipal String email) {
        Initiative initiative = priorisationService.mettreAJourPriorisation(idInitiative, valeur, effort);
        // DTO comme partout ailleurs : l'entite brute exposerait des proxies
        // Hibernate que Jackson ne sait pas serialiser (500).
        return ResponseEntity.ok(InitiativeMapper.toResponse(initiative));
    }
}
