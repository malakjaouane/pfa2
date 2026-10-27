package ma.it6.portefeuille.dto;

import java.util.List;

/**
 * Données du dashboard et des KPI.
 *
 * @param jalonsSemaine      jalons non atteints dont la date prévue tombe dans
 *                           la semaine en cours (message d'accueil)
 * @param initiativesARisque initiatives dont le score de santé est ORANGE ou
 *                           ROUGE (message d'accueil « N initiatives à risque »)
 * @param risquesCritiques   risques du registre dont la santé pondérée
 *                           (probabilité × impact) atteint 6 sur 9 — widget
 *                           « Risques critiques » de la page d'accueil
 * @param activiteRecente    dernières lignes du journal des modifications
 *                           (tous objets confondus, vue active) — widget
 *                           « Activité récente » de la page d'accueil
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
        List<InitiativeResponse> initiativesEnRetard,
        List<RisqueCritiqueDto> risquesCritiques,
        List<HistoriqueModificationResponse> activiteRecente
) {

    public record ChartDatum(String libelle, long count) {
    }
}
