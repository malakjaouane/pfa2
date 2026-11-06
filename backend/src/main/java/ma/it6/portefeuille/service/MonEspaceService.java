package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.AlerteDto;
import ma.it6.portefeuille.dto.DemandeResponse;
import ma.it6.portefeuille.dto.HistoriqueModificationResponse;
import ma.it6.portefeuille.dto.InitiativeResponse;
import ma.it6.portefeuille.dto.JalonResponse;
import ma.it6.portefeuille.dto.MonEspaceResponse;
import ma.it6.portefeuille.dto.RisqueResponse;
import ma.it6.portefeuille.dto.SanteDto;
import ma.it6.portefeuille.dto.UserSummaryDto;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Jalon;
import ma.it6.portefeuille.entity.Risque;
import ma.it6.portefeuille.mapper.InitiativeMapper;
import ma.it6.portefeuille.mapper.JalonMapper;
import ma.it6.portefeuille.mapper.RisqueMapper;
import ma.it6.portefeuille.mapper.UtilisateurMapper;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.JalonRepository;
import ma.it6.portefeuille.repository.RisqueRepository;
import ma.it6.portefeuille.repository.DemandeInitiativeRepository;
import ma.it6.portefeuille.repository.DepenseRepository;
import ma.it6.portefeuille.security.CustomUserDetailsService;
import ma.it6.portefeuille.security.PrincipalUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Agrégat « Mon espace » : ce qui concerne <strong>personnellement</strong>
 * le compte connecté.
 *
 * <p>Les listes sont bornées (10 éléments au maximum) ; un champ
 * {@code null} signale une information que le compte n'a pas le droit de
 * voir — {@code budgetTotal} exige {@code BUDGET_CONSULTER},
 * {@code depensesTotales} exige {@code DEPENSE_SAISIR}. Les alertes sont
 * recalculées à la volée (aucune table) et restreintes à mes initiatives et
 * à mes demandes à évaluer.</p>
 */
@Service
public class MonEspaceService {

    /** Nombre maximum de jalons et de risques listés dans l'agrégat. */
    public static final int LIMITE_LISTES = 10;

    private final CustomUserDetailsService userDetailsService;
    private final InitiativeRepository initiativeRepository;
    private final JalonRepository jalonRepository;
    private final RisqueRepository risqueRepository;
    private final DemandeInitiativeRepository demandeRepository;
    private final DepenseRepository depenseRepository;
    private final CalculeurSante calculeurSante;
    private final HistoriqueModificationService historiqueService;
    private final AlerteService alerteService;

    public MonEspaceService(CustomUserDetailsService userDetailsService,
                            InitiativeRepository initiativeRepository,
                            JalonRepository jalonRepository,
                            RisqueRepository risqueRepository,
                            DemandeInitiativeRepository demandeRepository,
                            DepenseRepository depenseRepository,
                            CalculeurSante calculeurSante,
                            HistoriqueModificationService historiqueService,
                            AlerteService alerteService) {
        this.userDetailsService = userDetailsService;
        this.initiativeRepository = initiativeRepository;
        this.jalonRepository = jalonRepository;
        this.risqueRepository = risqueRepository;
        this.demandeRepository = demandeRepository;
        this.depenseRepository = depenseRepository;
        this.calculeurSante = calculeurSante;
        this.historiqueService = historiqueService;
        this.alerteService = alerteService;
    }

    /**
     * Vue complète du compte courant : profil, initiatives pilotées (avec
     * santé), jalons proches, risques ouverts, alertes personnelles,
     * activité récente et, selon les droits, demandes à évaluer et chiffres
     * budgétaires.
     */
    @Transactional(readOnly = true)
    public MonEspaceResponse consulter() {
        PrincipalUser principal = utilisateurCourant();
        Long id = principal.getUtilisateur().getIdUser();

        List<Initiative> initiatives = initiativeRepository.parResponsable(id);
        List<Risque> risques = risqueRepository.mesRisques(id);

        return new MonEspaceResponse(
                UtilisateurMapper.toSummary(principal.getUtilisateur()),
                role(principal),
                initiativesAvecSante(initiatives),
                jalonsProches(id),
                risquesEnBref(risques),
                alerteService.personnelles(id),
                historiqueService.activiteUtilisateur(id),
                demandesEnAttente(),
                budgetTotal(initiatives),
                depensesTotales(initiatives));
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    /** Initiatives pilotées, santé recalculée en une passe (pas de N+1). */
    private List<InitiativeResponse> initiativesAvecSante(List<Initiative> initiatives) {
        if (initiatives.isEmpty()) {
            return List.of();
        }
        java.util.Map<Long, SanteDto> santes = calculeurSante.calculerTous(initiatives);
        return initiatives.stream()
                .map(i -> InitiativeMapper.toResponse(i, santes.get(i.getIdInitiative())))
                .toList();
    }

    /** Jalons non atteints des 14 prochains jours (retards inclus), bornés. */
    private List<JalonResponse> jalonsProches(Long idResponsable) {
        return jalonRepository.aSurveiller(
                        LocalDate.now().plusDays(AlerteService.HORIZON_MON_ESPACE_JOURS),
                        idResponsable).stream()
                .limit(LIMITE_LISTES)
                .map(JalonMapper::toResponse)
                .toList();
    }

    /** Mes risques non clos, du plus critique au moins critique, bornés. */
    private List<RisqueResponse> risquesEnBref(List<Risque> risques) {
        return risques.stream()
                .sorted(RisqueService.PAR_CRITICITE)
                .limit(LIMITE_LISTES)
                .map(RisqueMapper::toResponse)
                .toList();
    }

    /** Demandes « En évaluation » — uniquement pour qui peut évaluer. */
    private List<DemandeResponse> demandesEnAttente() {
        if (!AlerteService.aDroit(AlerteService.PERMISSION_EVALUER)) {
            return List.of();
        }
        return demandeRepository.enAttenteEvaluation().stream()
                .limit(LIMITE_LISTES)
                .map(DemandeService::toResponse)
                .toList();
    }

    /** Budget total de mes initiatives — {@code null} sans BUDGET_CONSULTER. */
    private BigDecimal budgetTotal(List<Initiative> initiatives) {
        if (!AlerteService.aDroit(AlerteService.PERMISSION_BUDGET)) {
            return null;
        }
        return initiatives.stream()
                .map(Initiative::getBudget)
                .filter(b -> b != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Dépenses de mes initiatives — {@code null} sans DEPENSE_SAISIR. */
    private BigDecimal depensesTotales(List<Initiative> initiatives) {
        if (!AlerteService.aDroit(AlerteService.PERMISSION_DEPENSE)) {
            return null;
        }
        if (initiatives.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> ids = initiatives.stream().map(Initiative::getIdInitiative).toList();
        BigDecimal total = depenseRepository.totalParInitiatives(ids);
        return total == null ? BigDecimal.ZERO : total;
    }

    private static String role(PrincipalUser principal) {
        return principal.getUtilisateur().getRole() == null ? null
                : principal.getUtilisateur().getRole().getLibelle();
    }

    /**
     * Compte courant : le principal posé par le filtre JWT, ou rechargé par
     * e-mail (les jetons portent l'e-mail, pas l'objet complet). La route
     * est déjà {@code authenticated()} — cette garde ne sert qu'aux appels
     * internes sans contexte de sécurité.
     */
    private PrincipalUser utilisateurCourant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "Authentification requise.");
        }
        if (authentication.getPrincipal() instanceof PrincipalUser principal) {
            return principal;
        }
        return userDetailsService.chargerParEmail(authentication.getName());
    }
}
