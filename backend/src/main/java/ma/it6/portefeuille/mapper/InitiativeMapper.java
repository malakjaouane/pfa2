package ma.it6.portefeuille.mapper;

import ma.it6.portefeuille.dto.InitiativeResponse;
import ma.it6.portefeuille.dto.ObjectifDto;
import ma.it6.portefeuille.dto.SanteDto;
import ma.it6.portefeuille.dto.StatutInitiativeDto;
import ma.it6.portefeuille.dto.PrioriteDto;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.ObjectifStrategique;
import ma.it6.portefeuille.entity.Priorite;
import ma.it6.portefeuille.entity.StatutInitiative;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Conversions entre l'entité Initiative et son DTO.
 */
public final class InitiativeMapper {

    private InitiativeMapper() {
    }

    public static StatutInitiativeDto toStatutDto(StatutInitiative s) {
        if (s == null) {
            return null;
        }
        return new StatutInitiativeDto(s.getIdStatut(), s.getLibelle(), s.getOrdre());
    }

    public static PrioriteDto toPrioriteDto(Priorite p) {
        if (p == null) {
            return null;
        }
        return new PrioriteDto(p.getIdPriorite(), p.getLibelle(), p.getOrdre());
    }

    public static ObjectifDto toObjectifDto(ObjectifStrategique o) {
        if (o == null) {
            return null;
        }
        return new ObjectifDto(o.getIdObjectif(), o.getCode(), o.getLibelle(), o.getDescription());
    }

    /**
     * Une initiative non archivée est potentiellement en retard lorsque
     * sa date de fin prévue est dépassée, que son statut n'est pas
     * « Terminée » et que son avancement est inférieur à 100 %.
     */
    public static boolean enRetard(Initiative i, LocalDate aujourdHui) {
        return Boolean.FALSE.equals(i.getArchive())
                && i.getDateFinPrevue() != null
                && i.getDateFinPrevue().isBefore(aujourdHui)
                && i.getStatut() != null
                && !"Terminée".equals(i.getStatut().getLibelle())
                && i.getAvancement() != null
                && i.getAvancement() < 100;
    }

    public static InitiativeResponse toResponse(Initiative i) {
        return toResponse(i, null);
    }

    /**
     * @param sante score de santé calculé (voir {@code CalculeurSante}) ;
     *              {@code null} quand il n'a pas été demandé (écritures,
     *              archives)
     */
    public static InitiativeResponse toResponse(Initiative i, SanteDto sante) {
        if (i == null) {
            return null;
        }
        return new InitiativeResponse(
                i.getIdInitiative(),
                i.getCode(),
                i.getNom(),
                i.getDescription(),
                i.getDateDebut(),
                i.getDateFinPrevue(),
                i.getBudget(),
                i.getAvancement(),
                i.getArchive(),
                enRetard(i, LocalDate.now()),
                sante,
                UtilisateurMapper.toEntiteDto(i.getEntite()),
                UtilisateurMapper.toSummary(i.getResponsable()),
                toStatutDto(i.getStatut()),
                toPrioriteDto(i.getPriorite()),
                toObjectifDto(i.getObjectif())
        );
    }
}