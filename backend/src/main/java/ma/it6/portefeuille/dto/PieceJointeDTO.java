package ma.it6.portefeuille.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Représentation légère d'une pièce jointe pour l'API (sans le binaire).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PieceJointeDTO {

    private Long idPieceJointe;
    private String nomFichier;
    private String typeMime;
    private Long tailleOctets;
    private LocalDateTime dateCreation;
    private String utilisateurEmail;
    private String utilisateurNomComplet;
    private Long idInitiative;
    private Long idCommentaire;
}
