package ma.it6.portefeuille.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Représentation d'une initiative renvoyée par l'API.
 *
 * <p>Le champ {@code budget} est le montant <em>saisi</em>. Aucun montant
 * dérivé n'est ajouté ici : il n'existe pas de saisie de dépenses réelles,
 * et afficher un « reste » calculé à côté de chaque initiative inviterait à
 * le lire comme un solde comptable. Les agrégats projetés (consommé, écart)
 * sont regroupés sur {@code GET /api/budget}, avec leurs définitions.</p>
 *
 * <p>{@code sante} est le score de santé automatique (feu vert / orange /
 * rouge + motif), calculé pour les lectures (liste, fiche, dashboard) et
 * {@code null} pour les écritures et les archives. Voir {@code CalculeurSante}.</p>
 */
public record InitiativeResponse(
        Long idInitiative,
        String code,
        String nom,
        String description,
        LocalDate dateDebut,
        LocalDate dateFinPrevue,
        BigDecimal budget,
        Integer avancement,
        Boolean archive,
        Boolean enRetard,
        SanteDto sante,
        EntiteDto entite,
        UserSummaryDto responsable,
        StatutInitiativeDto statut,
        PrioriteDto priorite,
        ObjectifDto objectif
) {
}