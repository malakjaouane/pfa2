package ma.it6.portefeuille.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps d'ajout d'un commentaire sur une initiative.
 */
public record CommentaireRequest(
        @NotBlank(message = "Le commentaire ne peut pas être vide")
        @Size(max = 4000, message = "Le commentaire ne peut pas dépasser 4000 caractères")
        String contenu
) {
}
