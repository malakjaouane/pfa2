package ma.it6.portefeuille.dto;

/**
 * Risque critique (santé pondérée ≥ 6 sur 9) destiné au widget
 * « Risques critiques » du tableau de bord : le contexte d'initiative
 * est embarqué pour que la page n'ait pas à recharger chaque fiche.
 *
 * @param idInitiative  identifiant de l'initiative porteuse (lien direct)
 * @param codeInitiative code de l'initiative porteuse (affiché en lien)
 * @param nomInitiative  nom de l'initiative porteuse
 */
public record RisqueCritiqueDto(
        Long idRisque,
        String description,
        Integer probabilite,
        Integer impact,
        Integer score,
        String niveau,
        Long idInitiative,
        String codeInitiative,
        String nomInitiative,
        UserSummaryDto responsable
) {
}
