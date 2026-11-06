package ma.it6.portefeuille.mapper;

import ma.it6.portefeuille.dto.RisqueCritiqueDto;
import ma.it6.portefeuille.dto.RisqueResponse;
import ma.it6.portefeuille.dto.SanteDto;
import ma.it6.portefeuille.entity.Risque;

/**
 * Conversions entre l'entité Risque et ses DTO, avec calcul de la
 * <b>santé pondérée</b> (probabilité × impact, soit 1 à 25) :
 *
 * <ul>
 *   <li>1-4 → {@link SanteDto#VERTE} ;</li>
 *   <li>5-9 → {@link SanteDto#AMBRE} ;</li>
 *   <li>10-14 → {@link SanteDto#ORANGE} ;</li>
 *   <li>15-25 → {@link SanteDto#ROUGE} — et donc <b>critique</b>
 *       (score ≥ {@link #SEUIL_CRITIQUE}), bande rouge alignée sur
 *       {@code CalculeurSante}.</li>
 * </ul>
 *
 * <p>Le score n'est jamais stocké : recalculé à chaque lecture.</p>
 */
public final class RisqueMapper {

    /** Seuil de criticité du dashboard : score ≥ 15 sur 25. */
    public static final int SEUIL_CRITIQUE = 15;

    /** En dessous de ce score, le risque est dans la bande orange. */
    public static final int SEUIL_ORANGE = 10;

    /** En dessous de ce score, le risque est dans la bande ambre. */
    public static final int SEUIL_AMBRE = 5;

    private RisqueMapper() {
    }

    /** Santé pondérée d'un risque : probabilité × impact (1 à 25). */
    public static int score(Risque r) {
        if (r.getProbabilite() == null || r.getImpact() == null) {
            return 0;
        }
        return r.getProbabilite() * r.getImpact();
    }

    /** Niveau de couleur correspondant à un score (codes de SanteDto). */
    public static String niveau(int score) {
        if (score >= SEUIL_CRITIQUE) {
            return SanteDto.ROUGE;
        }
        if (score >= SEUIL_ORANGE) {
            return SanteDto.ORANGE;
        }
        if (score >= SEUIL_AMBRE) {
            return SanteDto.AMBRE;
        }
        return SanteDto.VERTE;
    }

    public static RisqueResponse toResponse(Risque r) {
        if (r == null) {
            return null;
        }
        int s = score(r);
        return new RisqueResponse(
                r.getIdRisque(),
                r.getTitre(),
                r.getDescription(),
                r.getStatut(),
                r.getEcheance(),
                r.getProbabilite(),
                r.getImpact(),
                r.getPlanAction(),
                UtilisateurMapper.toSummary(r.getResponsable()),
                r.getInitiative() != null ? r.getInitiative().getIdInitiative() : null,
                r.getInitiative() != null ? r.getInitiative().getCode() : null,
                s,
                niveau(s)
        );
    }

    /** Variante dashboard : le contexte d'initiative est embarqué. */
    public static RisqueCritiqueDto toCritique(Risque r) {
        if (r == null) {
            return null;
        }
        int s = score(r);
        return new RisqueCritiqueDto(
                r.getIdRisque(),
                r.getTitre(),
                r.getDescription(),
                r.getStatut(),
                r.getEcheance(),
                r.getProbabilite(),
                r.getImpact(),
                s,
                niveau(s),
                r.getInitiative() != null ? r.getInitiative().getIdInitiative() : null,
                r.getInitiative() != null ? r.getInitiative().getCode() : null,
                r.getInitiative() != null ? r.getInitiative().getNom() : null,
                UtilisateurMapper.toSummary(r.getResponsable())
        );
    }
}
