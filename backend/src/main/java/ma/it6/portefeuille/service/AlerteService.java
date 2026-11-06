package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.AlerteDto;
import ma.it6.portefeuille.entity.DemandeInitiative;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Jalon;
import ma.it6.portefeuille.entity.Risque;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.RisqueMapper;
import ma.it6.portefeuille.repository.DemandeInitiativeRepository;
import ma.it6.portefeuille.repository.DepenseRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.JalonRepository;
import ma.it6.portefeuille.repository.RisqueRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Alertes calculées <strong>à la volée</strong> : aucune table, aucune
 * synchronisation — chaque lecture recalcule l'état réel du portefeuille.
 *
 * <p>Sept types, tous traduits côté front par le code {@code type}
 * (clés {@code suivi.alerte.<type>}), jamais par un texte formé ici :</p>
 * <ul>
 *   <li>{@code initiativeEnRetard} — ROUGE, échéance passée (règle du
 *       dashboard, une seule définition) ;</li>
 *   <li>{@code jalonEnRetard} — ROUGE, date prévue passée, non atteint ;</li>
 *   <li>{@code jalonBientot} — ORANGE, jalon dans les 7 jours ;</li>
 *   <li>{@code risqueCritique} — ROUGE, score ≥ 15, risque non clos ;</li>
 *   <li>{@code risqueEcheance} — ORANGE, échéance de risque ≤ 7 jours ;</li>
 *   <li>{@code demandeEnAttente} — ORANGE, demande « En évaluation »
 *       (uniquement pour qui peut évaluer : {@code DEMANDE_EVALUER}) ;</li>
 *   <li>{@code budgetDepasse} — ROUGE, dépenses &gt; budget (uniquement pour
 *       qui voit le budget : {@code BUDGET_CONSULTER} ou
 *       {@code DEPENSE_SAISIR}).</li>
 * </ul>
 *
 * <p>Les alertes « mon espace » restreignent le portefeuille aux
 <strong>mes</strong> initiatives et à mes demandes à évaluer.</p>
 */
@Service
public class AlerteService {

    /** Codes de type, alignés sur les clés i18n {@code suivi.alerte.*}. */
    public static final String TYPE_INITIATIVE_RETARD = "initiativeEnRetard";
    public static final String TYPE_JALON_RETARD = "jalonEnRetard";
    public static final String TYPE_JALON_BIENTOT = "jalonBientot";
    public static final String TYPE_RISQUE_CRITIQUE = "risqueCritique";
    public static final String TYPE_RISQUE_ECHEANCE = "risqueEcheance";
    public static final String TYPE_DEMANDE_EN_ATTENTE = "demandeEnAttente";
    public static final String TYPE_BUDGET_DEPASSE = "budgetDepasse";

    /** Horizon des alertes « bientôt » / « échéance proche », en jours. */
    public static final int HORIZON_JOURS = 7;

    /** Horizon des jalons proches de « Mon espace », en jours. */
    public static final int HORIZON_MON_ESPACE_JOURS = 14;

    /** Permissions conditionnant certaines alertes (jamais ouvertes par défaut). */
    public static final String PERMISSION_EVALUER = "DEMANDE_EVALUER";
    public static final String PERMISSION_BUDGET = "BUDGET_CONSULTER";
    public static final String PERMISSION_DEPENSE = "DEPENSE_SAISIR";

    private final InitiativeRepository initiativeRepository;
    private final JalonRepository jalonRepository;
    private final RisqueRepository risqueRepository;
    private final DemandeInitiativeRepository demandeRepository;
    private final DepenseRepository depenseRepository;

    public AlerteService(InitiativeRepository initiativeRepository,
                         JalonRepository jalonRepository,
                         RisqueRepository risqueRepository,
                         DemandeInitiativeRepository demandeRepository,
                         DepenseRepository depenseRepository) {
        this.initiativeRepository = initiativeRepository;
        this.jalonRepository = jalonRepository;
        this.risqueRepository = risqueRepository;
        this.demandeRepository = demandeRepository;
        this.depenseRepository = depenseRepository;
    }

    /**
     * Alertes du <strong>portefeuille entier</strong> (cloche de
     * notifications) : initiatives en retard, jalons et risques à
     * surveiller, puis demandes et budget selon les permissions du compte.
     */
    @Transactional(readOnly = true)
    public List<AlerteDto> toutes() {
        LocalDate jour = LocalDate.now();
        List<AlerteDto> alertes = new ArrayList<>();

        initiativeRepository.findAll().stream()
                .filter(i -> !Boolean.TRUE.equals(i.getArchive()))
                .filter(i -> DashboardService.enRetard(i, jour))
                .forEach(i -> alertes.add(alerteInitiativeEnRetard(i, jour)));

        alertes.addAll(alertesJalons(jalonRepository.aSurveiller(
                jour.plusDays(HORIZON_JOURS), null), jour));
        alertes.addAll(alertesRisques(risqueRepository.actifs(), jour));

        if (aDroit(PERMISSION_EVALUER)) {
            demandeRepository.enAttenteEvaluation().forEach(
                    d -> alertes.add(alerteDemande(d, jour)));
        }
        if (aDroit(PERMISSION_BUDGET) || aDroit(PERMISSION_DEPENSE)) {
            initiativeRepository.budgetDepasse().forEach(
                    i -> alertes.add(alerteBudgetDepasse(i)));
        }
        return trier(alertes);
    }

    /**
     * Alertes <strong>personnelles</strong> (« Mon espace ») : mes
     * initiatives, mes jalons, mes risques ouverts, mes demandes à évaluer
     * et les dépassements de budget de mes initiatives.
     */
    @Transactional(readOnly = true)
    public List<AlerteDto> personnelles(Long idResponsable) {
        LocalDate jour = LocalDate.now();
        List<AlerteDto> alertes = new ArrayList<>();

        initiativeRepository.parResponsable(idResponsable).stream()
                .filter(i -> DashboardService.enRetard(i, jour))
                .forEach(i -> alertes.add(alerteInitiativeEnRetard(i, jour)));

        alertes.addAll(alertesJalons(jalonRepository.aSurveiller(
                jour.plusDays(HORIZON_JOURS), idResponsable), jour));
        alertes.addAll(alertesRisques(risqueRepository.mesRisques(idResponsable), jour));

        if (aDroit(PERMISSION_EVALUER)) {
            demandeRepository.enAttenteEvaluation().forEach(
                    d -> alertes.add(alerteDemande(d, jour)));
        }
        if (aDroit(PERMISSION_BUDGET) || aDroit(PERMISSION_DEPENSE)) {
            initiativeRepository.budgetDepasse().stream()
                    .filter(i -> i.getResponsable() != null
                            && idResponsable.equals(i.getResponsable().getIdUser()))
                    .forEach(i -> alertes.add(alerteBudgetDepasse(i)));
        }
        return trier(alertes);
    }

    /**
     * Alertes d'<strong>une</strong> initiative (bloc de la fiche de suivi) :
     * jalons en retard ou proches, risques critiques ou échéance proche,
     * dépassement de budget selon les permissions.
     */
    @Transactional(readOnly = true)
    public List<AlerteDto> pourInitiative(Long idInitiative) {
        Initiative initiative = initiativeRepository.findById(idInitiative)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Initiative introuvable : " + idInitiative));
        LocalDate jour = LocalDate.now();
        List<AlerteDto> alertes = new ArrayList<>();

        if (DashboardService.enRetard(initiative, jour)) {
            alertes.add(alerteInitiativeEnRetard(initiative, jour));
        }
        alertes.addAll(alertesJalons(jalonRepository.aSurveiller(
                        jour.plusDays(HORIZON_JOURS), null).stream()
                .filter(j -> initiative.getIdInitiative()
                        .equals(j.getInitiative().getIdInitiative()))
                .toList(), jour));
        alertes.addAll(alertesRisques(risqueRepository.findByInitiativeIdInitiative(
                initiative.getIdInitiative()).stream()
                .filter(r -> !Risque.STATUT_CLOSE.equals(r.getStatut()))
                .toList(), jour));

        if ((aDroit(PERMISSION_BUDGET) || aDroit(PERMISSION_DEPENSE))
                && initiative.getBudget() != null
                && !Boolean.TRUE.equals(initiative.getArchive())) {
            BigDecimal consomme = depenseRepository.totalParInitiative(initiative.getIdInitiative());
            if (consomme != null && consomme.compareTo(initiative.getBudget()) > 0) {
                alertes.add(budgetDepasse(initiative, consomme));
            }
        }
        return trier(alertes);
    }

    // ------------------------------------------------------------------
    // Fabriques d'alertes
    // ------------------------------------------------------------------

    private static AlerteDto alerteInitiativeEnRetard(Initiative i, LocalDate jour) {
        long jours = i.getDateFinPrevue() == null ? 0
                : ChronoUnit.DAYS.between(i.getDateFinPrevue(), jour);
        return new AlerteDto(TYPE_INITIATIVE_RETARD, "ROUGE",
                i.getIdInitiative(), null, i.getCode(), i.getNom(),
                i.getDateFinPrevue(), Map.of("jours", Math.max(jours, 0)));
    }

    /** Scinde la liste « à surveiller » en retards (ROUGE) et bientôt (ORANGE). */
    private static List<AlerteDto> alertesJalons(List<Jalon> jalons, LocalDate jour) {
        List<AlerteDto> alertes = new ArrayList<>();
        for (Jalon j : jalons) {
            boolean enRetard = j.getDatePrevue().isBefore(jour);
            long jours = enRetard
                    ? ChronoUnit.DAYS.between(j.getDatePrevue(), jour)
                    : ChronoUnit.DAYS.between(jour, j.getDatePrevue());
            alertes.add(new AlerteDto(
                    enRetard ? TYPE_JALON_RETARD : TYPE_JALON_BIENTOT,
                    enRetard ? "ROUGE" : "ORANGE",
                    j.getInitiative().getIdInitiative(), null,
                    j.getNom(), j.getInitiative().getCode(),
                    j.getDatePrevue(), Map.of("jours", Math.max(jours, 0))));
        }
        return alertes;
    }

    /** Risques critiques (score ≥ 15) et échéances proches (≤ 7 jours). */
    private static List<AlerteDto> alertesRisques(List<Risque> risques, LocalDate jour) {
        List<AlerteDto> alertes = new ArrayList<>();
        LocalDate borne = jour.plusDays(HORIZON_JOURS);
        for (Risque r : risques) {
            if (Risque.STATUT_CLOSE.equals(r.getStatut())) {
                continue;
            }
            String reference = r.getTitre() == null ? "#" + r.getIdRisque() : r.getTitre();
            Long idInitiative = r.getInitiative() == null ? null
                    : r.getInitiative().getIdInitiative();
            String code = r.getInitiative() == null ? null : r.getInitiative().getCode();
            int score = RisqueMapper.score(r);
            if (score >= RisqueMapper.SEUIL_CRITIQUE) {
                alertes.add(new AlerteDto(TYPE_RISQUE_CRITIQUE, "ROUGE",
                        idInitiative, null, reference, code, r.getEcheance(),
                        Map.of("score", score)));
            } else if (r.getEcheance() != null && !r.getEcheance().isAfter(borne)) {
                long jours = r.getEcheance().isBefore(jour)
                        ? ChronoUnit.DAYS.between(r.getEcheance(), jour)
                        : ChronoUnit.DAYS.between(jour, r.getEcheance());
                alertes.add(new AlerteDto(TYPE_RISQUE_ECHEANCE, "ORANGE",
                        idInitiative, null, reference, code, r.getEcheance(),
                        Map.of("jours", Math.max(jours, 0))));
            }
        }
        return alertes;
    }

    private static AlerteDto alerteDemande(DemandeInitiative d, LocalDate jour) {
        long jours = d.getDateSoumission() == null ? 0
                : ChronoUnit.DAYS.between(d.getDateSoumission(), jour);
        String entite = d.getEntite() == null ? null : d.getEntite().getNom();
        return new AlerteDto(TYPE_DEMANDE_EN_ATTENTE, "ORANGE",
                d.getInitiativeLiee() == null ? null : d.getInitiativeLiee().getIdInitiative(),
                d.getIdDemande(), d.getTitre(), entite, null,
                Map.of("jours", Math.max(jours, 0)));
    }

    /**
     * Dépassement de budget : le budget et le consommé (quand il est déjà
     * calculé) partent en paramètres d'affichage — le texte reste traduit
     * côté front.
     */
    private AlerteDto alerteBudgetDepasse(Initiative i) {
        BigDecimal consomme = depenseRepository.totalParInitiative(i.getIdInitiative());
        return budgetDepasse(i, consomme);
    }

    private static AlerteDto budgetDepasse(Initiative i, BigDecimal consomme) {
        java.util.Map<String, Object> parametres = new java.util.LinkedHashMap<>();
        parametres.put("budget", i.getBudget());
        if (consomme != null) {
            parametres.put("consomme", consomme);
        }
        return new AlerteDto(TYPE_BUDGET_DEPASSE, "ROUGE",
                i.getIdInitiative(), null, i.getCode(), i.getNom(), null, parametres);
    }

    /** ROUGE d'abord, puis date d'échéance croissante (sans échéance en dernier). */
    private static List<AlerteDto> trier(List<AlerteDto> alertes) {
        Comparator<AlerteDto> parEcheance = Comparator.comparing(
                AlerteDto::echeance, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<AlerteDto> parNiveau = Comparator.comparing(
                a -> "ROUGE".equals(a.niveau()) ? 0 : 1);
        alertes.sort(parNiveau.thenComparing(parEcheance));
        return alertes;
    }

    /**
     * Permission du compte courant (la sécurité Spring pose les autorités ;
     * hors contexte — test unitaire — aucune permission n'est ouverte).
     */
    static boolean aDroit(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> permission.equals(a.getAuthority()));
    }
}
