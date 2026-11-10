package ma.it6.portefeuille.controller;

import org.springframework.security.core.Authentication;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import ma.it6.portefeuille.dto.PieceJointeDTO;
import ma.it6.portefeuille.service.PieceJointeService;

/**
 * Contrôleur des pièces jointes (initiatives et commentaires).
 *
 * <p>Routes :</p>
 * <ul>
 *   <li>{@code POST /api/initiatives/{id}/pieces-jointes} — upload (réservé aux
 *       détenteurs de {@code INITIATIVE_MODIFIER} sur l'initiative) ;</li>
 *   <li>{@code GET /api/pieces-jointes/{id}/telecharger} — téléchargement
 *       (réservé à {@code INITIATIVE_CONSULTER}) ;</li>
 *   <li>{@code DELETE /api/pieces-jointes/{id}} — suppression (uploader ou
 *       {@code UTILISATEUR_GERER}).</li>
 * </ul>
 *
 * <p>Toutes les routes sont isolées par organisation via
 * {@link ma.it6.portefeuille.filter.OrganisationIsolationInterceptor}.</p>
 */
@RestController
@RequestMapping("/api")
public class PieceJointeController {

    private final PieceJointeService pieceJointeService;

    public PieceJointeController(PieceJointeService pieceJointeService) {
        this.pieceJointeService = pieceJointeService;
    }

    /**
     * Upload d'une pièce jointe sur une initiative.
     *
     * @param idInitiative initiative cible
     * @param fichier      fichier envoyé
     * @param idCommentaire commentaire cible optionnel (null = initiative)
     * @param email        e-mail du compte connecté (principal JWT)
     * @return DTO de la pièce jointe créée (201)
     */
    @PostMapping("/initiatives/{idInitiative}/pieces-jointes")
    public ResponseEntity<PieceJointeDTO> uploader(
            @PathVariable Long idInitiative,
            @RequestParam MultipartFile fichier,
            @RequestParam(required = false) Long idCommentaire,
            @AuthenticationPrincipal String email) {
        PieceJointeDTO dto = pieceJointeService.uploader(fichier, idInitiative, idCommentaire, email);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(dto);
    }

    /**
     * Téléchargement d'une pièce jointe.
     */
    @GetMapping("/pieces-jointes/{idPieceJointe}/telecharger")
    public ResponseEntity<ByteArrayResource> telecharger(
            @PathVariable Long idPieceJointe) {
        PieceJointeDTO dto = pieceJointeService.getDTO(idPieceJointe);
        byte[] contenu = pieceJointeService.recuperer(idPieceJointe);

        ByteArrayResource ressource = new ByteArrayResource(contenu);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(dto.getTypeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + dto.getNomFichier() + "\"")
                .contentLength(contenu.length)
                .body(ressource);
    }

    /**
     * Suppression d'une pièce jointe (uploader ou administrateur).
     */
    @DeleteMapping("/pieces-jointes/{idPieceJointe}")
    public ResponseEntity<Void> supprimer(
            @PathVariable Long idPieceJointe,
            Authentication authentication) {
        String email = authentication.getName();
        boolean estAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        pieceJointeService.supprimer(idPieceJointe, email, estAdmin);
        return ResponseEntity.noContent().build();
    }
}
