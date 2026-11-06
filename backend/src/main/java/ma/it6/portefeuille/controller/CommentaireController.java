package ma.it6.portefeuille.controller;

import jakarta.validation.Valid;
import ma.it6.portefeuille.dto.CommentaireRequest;
import ma.it6.portefeuille.dto.CommentaireResponse;
import ma.it6.portefeuille.security.CustomUserDetailsService;
import ma.it6.portefeuille.security.PrincipalUser;
import ma.it6.portefeuille.service.CommentaireService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST des commentaires d'une initiative — fil de discussion.
 *
 * <p>Lecture ({@code GET}) : ouverte par la règle générale des initiatives
 * en {@code INITIATIVE_CONSULTER} (le visiteur lit le fil). Ajout
 * ({@code POST}) : {@code COMMENTAIRE_AJOUTER}. Suppression
 * ({@code DELETE}) : même droit, restreint dans le service à l'auteur ou à
 * un administrateur.</p>
 *
 * <p>Un commentaire est immuable : aucune mise à jour n'est exposée (le
 * {@code PUT} renvoie 405).</p>
 */
@RestController
@RequestMapping("/api")
public class CommentaireController {

    private final CommentaireService commentaireService;
    private final CustomUserDetailsService userDetailsService;

    public CommentaireController(CommentaireService commentaireService,
                                 CustomUserDetailsService userDetailsService) {
        this.commentaireService = commentaireService;
        this.userDetailsService = userDetailsService;
    }

    @GetMapping("/initiatives/{idInitiative}/commentaires")
    public ResponseEntity<List<CommentaireResponse>> lister(@PathVariable Long idInitiative) {
        return ResponseEntity.ok(commentaireService.lister(idInitiative));
    }

    @PostMapping("/initiatives/{idInitiative}/commentaires")
    public ResponseEntity<CommentaireResponse> creer(@PathVariable Long idInitiative,
                                                     @Valid @RequestBody CommentaireRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentaireService.creer(idInitiative, request, idAuteurCourant()));
    }

    @DeleteMapping("/commentaires/{idCommentaire}")
    public ResponseEntity<Void> supprimer(@PathVariable Long idCommentaire) {
        commentaireService.supprimer(idCommentaire, idAuteurCourant());
        return ResponseEntity.noContent().build();
    }

    /**
     * Identifiant du compte connecté — la route est déjà
     * {@code authenticated()}. Le filtre JWT pose l'e-mail (String) comme
     * principal : on recharge le compte, comme MonEspaceService.
     */
    private Long idAuteurCourant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof PrincipalUser principal) {
            return principal.getUtilisateur().getIdUser();
        }
        if (authentication.getPrincipal() instanceof String courant
                && !"anonymousUser".equals(courant)) {
            // Rechargement par e-mail (le JWT ne porte que l'e-mail). Si le
            // compte a disparu entre-temps : auteur anonyme, comme au fil.
            PrincipalUser recharge = userDetailsService.chargerParEmail(courant);
            return recharge == null ? null : recharge.getUtilisateur().getIdUser();
        }
        return null;
    }
}
