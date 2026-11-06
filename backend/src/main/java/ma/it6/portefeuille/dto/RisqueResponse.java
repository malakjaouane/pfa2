package ma.it6.portefeuille.dto;

import java.time.LocalDate;

/**
 * Risque renvoyé par l'API (registre d'une initiative ou registre global).
 *
 * @param score    santé pondérée recalculée : probabilité × impact (1 à 25)
 * @param niveau   {@code VERTE} (1-4) | {@code AMBRE} (5-9) | {@code ORANGE}
 *                 (10-14) | {@code ROUGE} (15-25) — mêmes codes que
 *                 {@link SanteDto} pour réutiliser les badges existants
 * @param statut   {@code ouverte} | {@code attenuation} | {@code close}
 */
public record RisqueResponse(
        Long idRisque,
        String titre,
        String description,
        String statut,
        LocalDate echeance,
        Integer probabilite,
        Integer impact,
        String planAction,
        UserSummaryDto responsable,
        Long idInitiative,
        String codeInitiative,
        Integer score,
        String niveau
) {
}
