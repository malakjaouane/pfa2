package ma.it6.portefeuille.controller;

import jakarta.validation.Valid;
import ma.it6.portefeuille.dto.AssistantIaRequest;
import ma.it6.portefeuille.dto.AssistantIaResponse;
import ma.it6.portefeuille.dto.JournalIaResponse;
import ma.it6.portefeuille.dto.ResumeIaRequest;
import ma.it6.portefeuille.dto.StatutIaResponse;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.security.CustomUserDetailsService;
import ma.it6.portefeuille.security.PrincipalUser;
import ma.it6.portefeuille.service.AssistantIaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST de l'assistant IA (« Demande à ton portefeuille »).
 *
 * <p>Protections (voir SecurityConfig) : l'usage exige la permission
 * dédiée {@code IA_UTILISER} (17), le journal est réservé aux
 * administrateurs ({@code UTILISATEUR_GERER}). Le statut reste ouvert à
 * tout utilisateur connecté pour que l'interface affiche un état
 * d'indisponibilité explicite au lieu d'une page d'erreur.</p>
 */
@RestController
@RequestMapping("/api/ia")
public class AssistantIaController {

    private final AssistantIaService assistantIaService;
    private final CustomUserDetailsService userDetailsService;

    public AssistantIaController(AssistantIaService assistantIaService,
                                 CustomUserDetailsService userDetailsService) {
        this.assistantIaService = assistantIaService;
        this.userDetailsService = userDetailsService;
    }

    /** Disponibilité de l'assistant (kill switch, clé, modèle). */
    @GetMapping("/statut")
    public ResponseEntity<StatutIaResponse> statut() {
        return ResponseEntity.ok(assistantIaService.statut());
    }

    /** Question conversationnelle, sourcée sur le portefeuille réel. */
    @PostMapping("/assistant")
    public ResponseEntity<AssistantIaResponse> poser(@Valid @RequestBody AssistantIaRequest request) {
        return ResponseEntity.ok(assistantIaService
                .poserQuestion(request.question(), request.langue(), utilisateurCourant()));
    }

    /** Résumé d'une initiative générée par le modèle. */
    @PostMapping("/resume/{idInitiative}")
    public ResponseEntity<AssistantIaResponse> resumer(@PathVariable Long idInitiative,
                                                       @Valid @RequestBody(required = false) ResumeIaRequest request) {
        String langue = request == null ? null : request.langue();
        return ResponseEntity.ok(assistantIaService
                .resumerInitiative(idInitiative, langue, utilisateurCourant()));
    }

    /** Journal des requêtes IA — administrateurs uniquement. */
    @GetMapping("/journal")
    public ResponseEntity<List<JournalIaResponse>> journal() {
        return ResponseEntity.ok(assistantIaService.journal());
    }

    private Utilisateur utilisateurCourant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        PrincipalUser principal = userDetailsService.chargerParEmail(authentication.getName());
        return principal.getUtilisateur();
    }
}
