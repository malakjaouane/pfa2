package ma.it6.portefeuille.dto;

/**
 * Risque renvoyé par l'API (registre d'une initiative).
 *
 * @param score        santé pondérée recalculée : probabilité × impact (1 à 9)
 * @param niveau       {@code VERTE} (1-2) | {@code ORANGE} (3-4) | {@code ROUGE}
 *                     (6-9) — mêmes codes que {@link SanteDto} pour réutiliser
 *                     les badges existants
 */
public record RisqueResponse(
        Long idRisque,
        String description,
        Integer probabilite,
        Integer impact,
        String planAction,
        UserSummaryDto responsable,
        Long idInitiative,
        Integer score,
        String niveau
) {
}
