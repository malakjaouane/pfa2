package ma.it6.portefeuille.dto;

import java.util.List;

/**
 * Données du dashboard et des KPI.
 *
 * @param jalonsSemaine      jalons non atteints dont la date prévue tombe dans
 *                           la semaine en cours (message d'accueil)
 * @param initiativesARisque initiatives dont le score de santé est ORANGE ou
 *                           ROUGE (message d'accueil « N initiatives à risque »)
 */
public record DashboardResponse(
        long totalInitiatives,
        long enCours,
        long terminees,
        long enPause,
        long enRetard,
        long jalonsEnRetard,
        long jalonsSemaine,
        long initiativesARisque,
        java.math.BigDecimal budgetTotal,
        double avancementMoyen,
        List<ChartDatum> parStatut,
        List<ChartDatum> parPriorite,
        List<ChartDatum> parEntite,
        List<ChartDatum> parResponsable,
        List<ChartDatum> parObjectif,
        List<InitiativeResponse> initiativesEnRetard
) {

    public record ChartDatum(String libelle, long count) {
    }
}
