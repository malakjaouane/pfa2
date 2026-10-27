package ma.it6.portefeuille.controller;

import jakarta.validation.Valid;
import ma.it6.portefeuille.dto.DemandeRequest;
import ma.it6.portefeuille.dto.DemandeResponse;
import ma.it6.portefeuille.dto.RefusRequest;
import ma.it6.portefeuille.security.CustomUserDetailsService;
import ma.it6.portefeuille.security.PrincipalUser;
import ma.it6.portefeuille.service.DemandeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST des demandes d'initiatives (workflow SPM) :
 * soumission (Brouillon -> Soumise), évaluation (Soumise ->
 * En évaluation -> Acceptée / Refusée) et transformation d'une
 * demande acceptée en initiative.
 */
@RestController
@RequestMapping("/api/demandes")
public class DemandeController {

    private final DemandeService demandeService;
    private final CustomUserDetailsService userDetailsService;

    public DemandeController(DemandeService demandeService, CustomUserDetailsService userDetailsService) {
        this.demandeService = demandeService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Liste des demandes — {@code recherche} filtre sur titre, description
     * et justification (insensible à la casse, collation de la base),
     * {@code archive=true} bascule sur la vue « Archives ». Les deux vues
     * sont exclusives : sans paramètre, seules les demandes actives
     * reviennent.
     */
    @GetMapping
    public ResponseEntity<List<DemandeResponse>> lister(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Boolean archive) {
        return ResponseEntity.ok(demandeService.lister(recherche, archive));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DemandeResponse> trouver(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.trouver(id));
    }

    /** Suppression définitive (DEMANDE_EVALUER) — 400 si déjà transformée. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        demandeService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /** Bascule vers la vue « Archives » — seules les demandes terminées. */
    @PatchMapping("/{id}/archive")
    public ResponseEntity<DemandeResponse> archiver(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.archiver(id));
    }

    /** Retour dans la vue « Actives » — annule l'archivage (réversible). */
    @PatchMapping("/{id}/restaurer")
    public ResponseEntity<DemandeResponse> restaurer(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.restaurer(id));
    }

    @PostMapping
    public ResponseEntity<DemandeResponse> creer(@Valid @RequestBody DemandeRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        PrincipalUser principal = userDetailsService.chargerParEmail(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(demandeService.creer(request, principal.getUtilisateur()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DemandeResponse> modifier(@PathVariable Long id,
                                                    @Valid @RequestBody DemandeRequest request) {
        return ResponseEntity.ok(demandeService.modifier(id, request));
    }

    /** Brouillon -> Soumise. */
    @PostMapping("/{id}/soumettre")
    public ResponseEntity<DemandeResponse> soumettre(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.soumettre(id));
    }

    /** Soumise -> En évaluation. */
    @PostMapping("/{id}/evaluer")
    public ResponseEntity<DemandeResponse> evaluer(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.evaluer(id));
    }

    /** En évaluation -> Acceptée. */
    @PostMapping("/{id}/accepter")
    public ResponseEntity<DemandeResponse> accepter(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.accepter(id));
    }

    /** En évaluation -> Refusée (motif facultatif mais recommandé). */
    @PostMapping("/{id}/refuser")
    public ResponseEntity<DemandeResponse> refuser(@PathVariable Long id,
                                                   @RequestBody(required = false) RefusRequest request) {
        String motif = request == null ? null : request.motifRefus();
        return ResponseEntity.ok(demandeService.refuser(id, motif));
    }

    /** Acceptée -> initiative du portefeuille (aucun doublon). */
    @PostMapping("/{id}/transformer")
    public ResponseEntity<DemandeResponse> transformer(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.transformer(id));
    }
}