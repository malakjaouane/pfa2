package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.DashboardResponse;
import ma.it6.portefeuille.dto.DashboardResponse.ChartDatum;
import ma.it6.portefeuille.dto.InitiativeFilterRequest;
import ma.it6.portefeuille.dto.InitiativeResponse;
import ma.it6.portefeuille.dto.RisqueCritiqueDto;
import ma.it6.portefeuille.dto.SanteDto;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.mapper.InitiativeMapper;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.JalonRepository;
import ma.it6.portefeuille.specification.InitiativeSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calcul des KPI du dashboard (RM : suivi du portefeuille et indicateurs
 * d'avancement) :
 *  - Total initiatives, En cours, Terminées, En pause (statuts métier)
 *  - En retard (date de fin dépassée, statut != Terminée, avancement < 100 %)
 *  - Jalons en retard (date prévue passée, jalon non atteint)
 *  - Budget total et avancement moyen
 *  - Répartitions par statut, priorité, entité, responsable et objectif
 *
 * <p><strong>Tous les indicateurs et tous les graphiques suivent les mêmes
 * filtres</strong> que la liste des initiatives (statut, entité, responsable,
 * priorité, objectif, période, recherche). Les calculs sont effectués en Java
 * sur la sélection filtrée afin qu'une modification de filtre se répercute
 * partout de façon homogène.</p>
 */
@Service
public class DashboardService {

    private static final String STATUT_EN_COURS = InitiativeService.STATUT_EN_COURS;
    private static final String STATUT_TERMINEE = InitiativeService.STATUT_TERMINEE;
    private static final String STATUT_EN_PAUSE = InitiativeService.STATUT_EN_PAUSE;

    /** Libellés de repli repris des agrégations SQL précédentes. */
    private static final String NON_AFFECTE = "Non affecté";
    private static final String NON_RATTACHE = "Non rattaché";

    private final InitiativeRepository initiativeRepository;
    private final JalonRepository jalonRepository;
    private final CalculeurSante calculeurSante;
    private final RisqueService risqueService;

    public DashboardService(InitiativeRepository initiativeRepository,
                            JalonRepository jalonRepository,
                            CalculeurSante calculeurSante,
                            RisqueService risqueService) {
        this.initiativeRepository = initiativeRepository;
        this.jalonRepository = jalonRepository;
        this.calculeurSante = calculeurSante;
        this.risqueService = risqueService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse calculer(InitiativeFilterRequest filtre) {
        LocalDate aujourdHui = LocalDate.now();

        List<Initiative> initiatives = initiativeRepository
                .findAll(InitiativeSpecifications.filtrer(filtre));

        long total = initiatives.size();
        long enCours = compterParStatut(initiatives, STATUT_EN_COURS);
        long terminees = compterParStatut(initiatives, STATUT_TERMINEE);
        long enPause = compterParStatut(initiatives, STATUT_EN_PAUSE);

        List<Initiative> enRetardListe = initiatives.stream()
                .filter(i -> enRetard(i, aujourdHui))
                .sorted(Comparator.comparing(Initiative::getDateFinPrevue,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        long enRetard = enRetardListe.size();

        List<Long> ids = initiatives.stream().map(Initiative::getIdInitiative).toList();
        long jalonsEnRetard = ids.isEmpty()
                ? 0 : jalonRepository.countEnRetardParInitiatives(aujourdHui, ids);

        // Message d'accueil : jalons à venir dans la semaine en cours
        // (du jour courant au dimanche inclus), mêmes filtres que les KPI.
        long jalonsSemaine = ids.isEmpty()
                ? 0 : jalonRepository.countEntreDatesParInitiatives(
                        aujourdHui, dimancheCourant(aujourdHui), ids);

        BigDecimal budgetTotal = initiatives.stream()
                .map(Initiative::getBudget)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        double avancementMoyen = arrondir(initiatives.stream()
                .map(Initiative::getAvancement)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0));

        List<ChartDatum> parStatut = regrouper(
                trierParOrdre(initiatives, i -> i.getStatut() == null ? null : i.getStatut().getOrdre()),
                i -> i.getStatut() == null ? "Non défini" : i.getStatut().getLibelle());

        List<ChartDatum> parPriorite = regrouper(
                trierParOrdre(initiatives, i -> i.getPriorite() == null ? null : i.getPriorite().getOrdre()),
                i -> i.getPriorite() == null ? "Non définie" : i.getPriorite().getLibelle());

        List<ChartDatum> parEntite = regrouper(
                trierParCompte(initiatives, i -> i.getEntite() == null ? NON_RATTACHE : i.getEntite().getNom()),
                i -> i.getEntite() == null ? NON_RATTACHE : i.getEntite().getNom());

        List<ChartDatum> parResponsable = regrouper(
                trierParCompte(initiatives, i -> i.getResponsable() == null
                        ? NON_AFFECTE
                        : (i.getResponsable().getPrenom() + " " + i.getResponsable().getNom()).trim()),
                i -> i.getResponsable() == null
                        ? NON_AFFECTE
                        : (i.getResponsable().getPrenom() + " " + i.getResponsable().getNom()).trim());

        List<ChartDatum> parObjectif = regrouper(
                trierParCompte(initiatives, i -> i.getObjectif() == null ? NON_RATTACHE : i.getObjectif().getLibelle()),
                i -> i.getObjectif() == null ? NON_RATTACHE : i.getObjectif().getLibelle());

        // Les initiatives à surveiller portent aussi leur score de santé
        // (calculé en lot) : la page d'accueil affiche le niveau et le motif.
        // Le même lot alimente le KPI « initiatives à risque » (ORANGE/ROUGE)
        // du message d'accueil.
        Map<Long, SanteDto> santes = calculeurSante.calculerTous(initiatives);
        long initiativesARisque = santes.values().stream()
                .filter(s -> s != null && !SanteDto.VERTE.equals(s.niveau()))
                .count();
        List<InitiativeResponse> enRetardReponses = enRetardListe.stream()
                .map(i -> InitiativeMapper.toResponse(i, santes.get(i.getIdInitiative())))
                .toList();

        // Risques critiques (santé pondérée ≥ 6) des mêmes initiatives
        // filtrées : le widget du tableau de bord suit donc les filtres,
        // exactement comme les KPI.
        List<RisqueCritiqueDto> risquesCritiques = risqueService.listerCritiques(ids);

        return new DashboardResponse(total, enCours, terminees, enPause, enRetard, jalonsEnRetard,
                jalonsSemaine, initiativesARisque,
                budgetTotal, avancementMoyen,
                parStatut, parPriorite, parEntite, parResponsable, parObjectif, enRetardReponses,
                risquesCritiques);
    }

    /** Dimanche (inclus) de la semaine civile contenant {@code date}. */
    private static LocalDate dimancheCourant(LocalDate date) {
        return date.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
    }

    // ------------------------------------------------------------------
    // Règles de calcul
    // ------------------------------------------------------------------

    /** RM : une initiative est en retard si son échéance est passée. */
    private static boolean enRetard(Initiative initiative, LocalDate aujourdHui) {
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            return false;
        }
        if (initiative.getDateFinPrevue() == null || initiative.getDateFinPrevue().isAfter(aujourdHui)) {
            return false;
        }
        String statut = initiative.getStatut() == null ? null : initiative.getStatut().getLibelle();
        if (STATUT_TERMINEE.equals(statut)) {
            return false;
        }
        return initiative.getAvancement() != null && initiative.getAvancement() < 100;
    }

    private static long compterParStatut(List<Initiative> initiatives, String libelle) {
        return initiatives.stream()
                .filter(i -> i.getStatut() != null && libelle.equals(i.getStatut().getLibelle()))
                .count();
    }

    private static double arrondir(double valeur) {
        return Math.round(valeur * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    // Regroupements des graphiques
    // ------------------------------------------------------------------

    /** Copie triée par l'ordre du référentiel (null = dernière position). */
    private static List<Initiative> trierParOrdre(List<Initiative> initiatives,
                                                  Function<Initiative, Integer> ordre) {
        List<Initiative> copie = new ArrayList<>(initiatives);
        copie.sort((a, b) -> Integer.compare(ordreDe(ordre.apply(a)), ordreDe(ordre.apply(b))));
        return copie;
    }

    private static int ordreDe(Integer ordre) {
        return ordre == null ? Integer.MAX_VALUE : ordre;
    }

    /** Copie triée par effectif décroissant (libellé ascendant en cas d'égalité). */
    private static List<Initiative> trierParCompte(List<Initiative> initiatives,
                                                   Function<Initiative, String> cle) {
        Map<String, Long> comptes = new LinkedHashMap<>();
        for (Initiative i : initiatives) {
            comptes.merge(cle.apply(i), 1L, Long::sum);
        }
        Map<String, Long> tries = comptes.entrySet().stream()
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.getValue(), a.getValue());
                    return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (x, y) -> x, LinkedHashMap::new));

        // L'ordre d'affichage suit l'effectif ; on reconstruit la liste par
        // groupes afin que regrouper() conserve cet ordre.
        Map<String, List<Initiative>> groupes = new LinkedHashMap<>();
        for (Initiative i : initiatives) {
            groupes.computeIfAbsent(cle.apply(i), k -> new ArrayList<>()).add(i);
        }
        List<Initiative> ordonne = new ArrayList<>();
        tries.keySet().forEach(c -> ordonne.addAll(groupes.get(c)));
        return ordonne;
    }

    /**
     * Regroupe en préservant l'ordre d'entrée (LinkedHashMap) : les éléments
     * sont déjà triés par l'appelant.
     */
    private static List<ChartDatum> regrouper(List<Initiative> initiatives,
                                              Function<Initiative, String> cle) {
        Map<String, Long> compteurs = new LinkedHashMap<>();
        for (Initiative i : initiatives) {
            compteurs.merge(cle.apply(i), 1L, Long::sum);
        }
        return compteurs.entrySet().stream()
                .map(e -> new ChartDatum(e.getKey(), e.getValue()))
                .toList();
    }
}
