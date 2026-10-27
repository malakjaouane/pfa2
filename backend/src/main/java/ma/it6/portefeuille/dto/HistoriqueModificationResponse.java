package ma.it6.portefeuille.dto;

import java.time.LocalDateTime;

/**
 * Une ligne du journal des modifications, telle que la rend l'API.
 *
 * @param id              identifiant de la ligne
 * @param typeObjet       type d'objet journalisé (initiative, etape, jalon,
 *                        demande, utilisateur, role)
 * @param idObjet         identifiant de l'objet dans son référentiel
 * @param champ           code du champ modifié (libellé traduit côté UI)
 * @param ancienneValeur  valeur avant changement ({@code null} à la création)
 * @param nouvelleValeur  valeur après changement ({@code null} à la suppression)
 * @param commentaire     motif facultatif posé par le domaine
 * @param dateChangement  horodatage du changement
 * @param automatique     {@code true} = moteur de règles, {@code false} = manuel
 * @param utilisateur     auteur ( {@code null} hors contexte ou compte parti)
 * @param initiative      initiative de rattachement ({@code null} pour
 *                        demande / utilisateur / rôle)
 */
public record HistoriqueModificationResponse(
        Long id,
        String typeObjet,
        Long idObjet,
        String champ,
        String ancienneValeur,
        String nouvelleValeur,
        String commentaire,
        LocalDateTime dateChangement,
        boolean automatique,
        UserSummaryDto utilisateur,
        InitiativeSummaryDto initiative
) {
}
