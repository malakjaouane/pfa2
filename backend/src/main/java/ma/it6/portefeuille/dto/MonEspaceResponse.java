package ma.it6.portefeuille.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agrégat « Mon espace » : ce qui concerne personnellement le compte
 * connecté — son profil, ses initiatives pilotées, ses jalons proches,
 * ses risques ouverts, ses alertes, son activité récente et, selon ses
 * droits, les demandes à évaluer et les chiffres budgétaires.
 *
 * <p>Les listes sont bornées côté service ; un champ {@code null} signale
 * une information que le compte n'a pas le droit de voir (budget sans
 * {@code BUDGET_CONSULTER}, dépenses sans {@code DEPENSE_SAISIR}).</p>
 */
public record MonEspaceResponse(
        UserSummaryDto compte,
        String role,
        List<InitiativeResponse> initiativesPilotees,
        List<JalonResponse> jalonsProches,
        List<RisqueResponse> mesRisques,
        List<AlerteDto> alertes,
        List<HistoriqueModificationResponse> activiteRecente,
        List<DemandeResponse> demandesEnAttente,
        BigDecimal budgetTotal,
        BigDecimal depensesTotales
) {
}
