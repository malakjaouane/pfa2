package ma.it6.portefeuille.dto;

/**
 * Résumé d'une initiative joint aux lignes d'un journal partagé.
 *
 * <p>La page « Historique des statuts » mélange les événements de toutes les
 * initiatives : chaque ligne doit pouvoir s'identifier seule, sans second
 * appel au serveur.</p>
 *
 * @param idInitiative clé primaire (utilisée par le filtre « voir tout »)
 * @param code         code lisible (ex. {@code DIG-CLI-001})
 * @param nom          intitulé de l'initiative
 */
public record InitiativeSummaryDto(
        Long idInitiative,
        String code,
        String nom
) {
}
