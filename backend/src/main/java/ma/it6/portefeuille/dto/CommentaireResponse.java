package ma.it6.portefeuille.dto;

import java.time.LocalDateTime;

/**
 * Commentaire renvoyé par l'API (fil d'une initiative).
 *
 * @param auteur {@code null} si le compte a été supprimé (anonymisation)
 */
public record CommentaireResponse(
        Long idCommentaire,
        Long idInitiative,
        String contenu,
        LocalDateTime dateCreation,
        UserSummaryDto auteur
) {
}
