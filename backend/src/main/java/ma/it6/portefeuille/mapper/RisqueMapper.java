package ma.it6.portefeuille.mapper;

import ma.it6.portefeuille.dto.RisqueCritiqueDto;
import ma.it6.portefeuille.dto.RisqueResponse;
import ma.it6.portefeuille.dto.SanteDto;
import ma.it6.portefeuille.entity.Risque;

/**
 * Conversions entre l'entité Risque et ses DTO, avec calcul de la
 * <b>santé pondérée</b> (probabilité × impact, soit 1 à 9) :
 *
 * <ul>
 *   <li>1-2 → {@link SanteDto#VERTE} ;</li>
 *   <li>3-4 → {@link SanteDto#ORANGE} ;</li>
 *   <li>6-9 → {@link SanteDto#ROUGE} — et donc <b>critique</b>
 *       (score ≥ {@link #SEUIL_CRITIQUE}), bande rouge alignée sur
 *       {@code CalculeurSante}.</li>
 * </ul>
 *
 * <p>Le score n'est jamais stocké : recalculé à chaque lecture.</p>
 */
public final class RisqueMapper {

    /** Seuil de criticité du dashboard : score ≥ 6 sur 9. */
    public static final int SEUIL_CRITIQUE = 6;

    /** En dessous de ce score, le risque est dans la bande orange. */
    public static final int SEUIL_ORANGE = 3;

    private RisqueMapper() {
    }

    /** Santé pondérée d'un risque : probabilité × impact (1 à 9). */
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
        return SanteDto.VERTE;
    }

    public static RisqueResponse toResponse(Risque r) {
        if (r == null) {
            return null;
        }
        int s = score(r);
        return new RisqueResponse(
                r.getIdRisque(),
                r.getDescription(),
                r.getProbabilite(),
                r.getImpact(),
                r.getPlanAction(),
                UtilisateurMapper.toSummary(r.getResponsable()),
                r.getInitiative() != null ? r.getInitiative().getIdInitiative() : null,
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
                r.getDescription(),
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
