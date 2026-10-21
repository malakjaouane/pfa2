package ma.it6.portefeuille.dto;

import java.util.Map;

/**
 * Score de santé automatique d'une initiative.
 *
 * <p>Trois niveaux, déterministes et explicables, calculés par
 * {@code CalculeurSante} à partir des données déjà suivies :</p>
 * <ul>
 *   <li>{@link #ROUGE} — date butoir dépassée, ≥ 2 jalons en retard, ou
 *       écart planning/avancement ≥ 30 points ;</li>
 *   <li>{@link #ORANGE} — 1 jalon en retard, ou écart ≥ 15 points ;</li>
 *   <li>{@link #VERTE} — aucun signe négatif (ou initiative terminée).</li>
 * </ul>
 *
 * <p>La raison est renvoyée sous forme de <strong>code + paramètres</strong>
 * (jamais un texte formé côté serveur) : l'interface la traduit dans la
 * langue de l'utilisateur, ex. « 60 % du temps écoulé pour 30 %
 * d'avancement ».</p>
 *
 * @param niveau     {@code VERTE} | {@code ORANGE} | {@code ROUGE} ; {@code null}
 *                   pour une initiative archivée (sans objet)
 * @param raison     code de motif (ex. {@code ecartPlanning}), traduit côté front
 * @param parametres paramètres d'interpolation du motif ({@code n}, {@code nom},
 *                   {@code temps}, {@code avancement}…)
 */
public record SanteDto(
        String niveau,
        String raison,
        Map<String, Object> parametres
) {
    public static final String VERTE = "VERTE";
    public static final String ORANGE = "ORANGE";
    public static final String ROUGE = "ROUGE";
}
