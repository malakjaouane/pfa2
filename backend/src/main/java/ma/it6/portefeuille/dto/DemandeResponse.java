package ma.it6.portefeuille.dto;

import java.time.LocalDate;

/**
 * Représentation d'une demande d'initiative renvoyée par l'API.
 */
public record DemandeResponse(
        Long idDemande,
        String titre,
        String descriptionBesoin,
        String justificationMetier,
        String motifRefus,
        LocalDate dateSoumission,
        EntiteDto entite,
        UserSummaryDto demandeur,
        PrioriteDto priorite,
        StatutDemandeDto statut,
        InitiativeLieeDto initiativeLiee,
        /** Vue « Archives » : {@code true} = masquée de la liste active. */
        boolean archive
) {
}