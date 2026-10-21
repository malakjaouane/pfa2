package ma.it6.portefeuille.service;

import ma.it6.portefeuille.entity.Etape;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Calcule l'avancement d'une initiative à partir de ses étapes.
 *
 * <p>Règle de progression d'une étape (RM18, valeur automatique selon le
 * statut) :</p>
 * <ul>
 *   <li>« Terminée » = 100 % ;</li>
 *   <li>« En cours » = la valeur saisie (1-100), 50 % par défaut si elle
 *   est absente ou à 0 (champ non renseigné) ;</li>
 *   <li>« À faire » = 0 % ;</li>
 *   <li>statut inconnu : l'avancement saisi (0-100), par sécurité.</li>
 * </ul>
 *
 * <p>L'avancement de l'initiative est la <strong>moyenne pondérée par la
 * durée</strong> de chaque étape (lorsque début et fin sont renseignés),
 * sinon la moyenne simple. {@link #calculer(List)} renvoie {@code -1} quand
 * le calcul n'est pas possible (aucune étape) : le système conserve alors la
 * valeur saisie manuellement.</p>
 */
public final class CalculeurAvancement {

    /** Statuts de l'étape (référentiel {@code statut_etape}). */
    public static final String ETAPE_TERMINEE = "Terminée";
    public static final String ETAPE_A_FAIRE = "À faire";
    public static final String ETAPE_EN_COURS = "En cours";

    private CalculeurAvancement() {
    }

    /**
     * @return l'avancement calculé (0-100) ou {@code -1} s'il n'y a aucune étape.
     */
    public static int calculer(List<Etape> etapes) {
        if (etapes == null || etapes.isEmpty()) {
            return -1;
        }

        // Toutes les étapes datées et cohérentes -> pondération par durée.
        boolean ponderationPossible = true;
        for (Etape etape : etapes) {
            if (dureeEnJours(etape) <= 0) {
                ponderationPossible = false;
                break;
            }
        }

        double totalPondere = 0;
        long poidsTotal = 0;
        for (Etape etape : etapes) {
            int progression = progression(etape);
            long poids = ponderationPossible ? Math.max(dureeEnJours(etape), 1) : 1;
            totalPondere += (double) progression * poids;
            poidsTotal += poids;
        }

        if (poidsTotal == 0) {
            return 0;
        }
        return (int) Math.round(totalPondere / poidsTotal);
    }

    /** Progression d'une étape déduite de son statut (RM18). */
    public static int progression(Etape etape) {
        String statut = etape.getStatutEtape() == null ? null : etape.getStatutEtape().getLibelle();
        if (ETAPE_TERMINEE.equals(statut)) {
            return 100;
        }
        if (ETAPE_A_FAIRE.equals(statut)) {
            return 0;
        }
        Integer avancement = etape.getAvancement();
        if (avancement == null || (ETAPE_EN_COURS.equals(statut) && avancement == 0)) {
            // « En cours » sans valeur (ou à 0) : 50 % par défaut.
            return ETAPE_EN_COURS.equals(statut) ? 50 : 0;
        }
        return Math.max(0, Math.min(100, avancement));
    }

    /** Durée en jours d'une étape, ou {@code 0} si les dates sont absentes/incohérentes. */
    public static long dureeEnJours(Etape etape) {
        if (etape.getDateDebut() == null || etape.getDateFinPrevue() == null) {
            return 0;
        }
        long jours = ChronoUnit.DAYS.between(etape.getDateDebut(), etape.getDateFinPrevue());
        return jours > 0 ? jours : 0;
    }
}
