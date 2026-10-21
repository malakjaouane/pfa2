package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.SanteDto;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Jalon;
import ma.it6.portefeuille.repository.JalonRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Score de santé automatique d'une initiative : feu vert, orange ou rouge,
 * avec le motif affichable.
 *
 * <p>Les règles sont <strong>déterministes et explicables</strong> — chacune
 * produit un code de motif que l'interface traduit :</p>
 * <ol>
 *   <li>archivée → sans objet ({@code null}) ;</li>
 *   <li>terminée → {@code VERTE} (« initiative terminée ») ;</li>
 *   <li>date butoir dépassée → {@code ROUGE} (« dépassée de N jours ») ;</li>
 *   <li>2 jalons ou plus en retard → {@code ROUGE} ;</li>
 *   <li>1 jalon en retard → {@code ORANGE} (nom du jalon) ;</li>
 *   <li>écart planning/avancement ≥ 30 points → {@code ROUGE} ;</li>
 *   <li>écart ≥ 15 points → {@code ORANGE} (« N % du temps écoulé pour
 *       M % d'avancement ») ;</li>
 *   <li>sans dates exploitables → {@code VERTE} (« pas encore de planning ») ;
 *       sinon {@code VERTE} (« conforme au planning »).</li>
 * </ol>
 *
 * <p>Le budget consommé n'entre pas encore dans le calcul : il n'existe pas
 * de saisie de dépenses par initiative (voir InitiativeResponse). Le jour où
 * « prévu / engagé / consommé » sera saisi, il suffira d'ajouter une règle
 * ici — l'API et l'affichage sont déjà dimensionnés pour l'absorber.</p>
 *
 * <p>Les jalons de la page entière sont chargés en <strong>une seule
 * requête</strong> ({@link JalonRepository#chercherParInitiatives}) : aucun
 * N+1, quel que soit le nombre d'initiatives affichées.</p>
 */
@Component
public class CalculeurSante {

    /** Écart planning/avancement au-delà duquel l'initiative est à risque. */
    static final int SEUIL_ROUGE = 30;
    /** Écart au-delà duquel l'initiative appelle la vigilance. */
    static final int SEUIL_ORANGE = 15;

    private final JalonRepository jalonRepository;

    public CalculeurSante(JalonRepository jalonRepository) {
        this.jalonRepository = jalonRepository;
    }

    /**
     * Santé de chaque initiative fournie, jalons chargés en lot.
     *
     * @return initiative id → santé ({@code null} = archivée, sans objet)
     */
    public Map<Long, SanteDto> calculerTous(List<Initiative> initiatives) {
        if (initiatives == null || initiatives.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = initiatives.stream()
                .map(Initiative::getIdInitiative)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<Jalon>> jalonsParInitiative = jalonRepository.chercherParInitiatives(ids).stream()
                .collect(Collectors.groupingBy(j -> j.getInitiative().getIdInitiative()));

        LocalDate jour = LocalDate.now();
        Map<Long, SanteDto> santes = new HashMap<>();
        for (Initiative i : initiatives) {
            List<Jalon> jalons = jalonsParInitiative.getOrDefault(i.getIdInitiative(), List.of());
            santes.put(i.getIdInitiative(), evaluer(i, jalons, jour));
        }
        return santes;
    }

    /** Santé d'une seule initiative (fiche détaillée). */
    public SanteDto calculer(Initiative initiative) {
        return calculerTous(List.of(initiative)).get(initiative.getIdInitiative());
    }

    // ------------------------------------------------------------------
    // Règles pures (sans dépôt) — couvertes par CalculeurSanteTest
    // ------------------------------------------------------------------

    /** @return la santé, ou {@code null} si l'initiative est archivée */
    static SanteDto evaluer(Initiative i, List<Jalon> jalons, LocalDate jour) {
        if (Boolean.TRUE.equals(i.getArchive())) {
            return null;
        }
        if (i.getStatut() != null && "Terminée".equals(i.getStatut().getLibelle())) {
            return new SanteDto(SanteDto.VERTE, "terminee", Map.of());
        }

        List<Jalon> enRetard = jalons.stream()
                .filter(j -> enRetard(j, jour))
                .toList();

        if (i.getDateFinPrevue() != null && i.getDateFinPrevue().isBefore(jour)) {
            long jours = ChronoUnit.DAYS.between(i.getDateFinPrevue(), jour);
            return new SanteDto(SanteDto.ROUGE, "finDepassee", Map.of("n", jours));
        }
        if (enRetard.size() >= 2) {
            return new SanteDto(SanteDto.ROUGE, "jalonsRetard", Map.of("n", (long) enRetard.size()));
        }
        if (enRetard.size() == 1) {
            String nom = enRetard.get(0).getNom() == null ? "" : enRetard.get(0).getNom();
            return new SanteDto(SanteDto.ORANGE, "jalonRetard", Map.of("nom", nom));
        }

        int avancement = i.getAvancement() == null ? 0 : i.getAvancement();
        int[] ecart = ecartPlanning(i, jour);
        if (ecart != null) {
            int e = ecart[0] - avancement;
            if (e >= SEUIL_ROUGE) {
                return new SanteDto(SanteDto.ROUGE, "ecartPlanning",
                        Map.of("temps", ecart[0], "avancement", avancement));
            }
            if (e >= SEUIL_ORANGE) {
                return new SanteDto(SanteDto.ORANGE, "ecartPlanning",
                        Map.of("temps", ecart[0], "avancement", avancement));
            }
            return new SanteDto(SanteDto.VERTE, "conforme", Map.of());
        }
        return new SanteDto(SanteDto.VERTE, "sansPlanning", Map.of());
    }

    /** Jalon en retard : date prévue passée et non atteint (règle de la KPI). */
    private static boolean enRetard(Jalon j, LocalDate jour) {
        return j.getDatePrevue() != null
                && j.getDatePrevue().isBefore(jour)
                && (j.getStatutJalon() == null || !"Atteint".equalsIgnoreCase(j.getStatutJalon().getLibelle()));
    }

    /**
     * @return {@code [tempsÉcouléEnPourcentage, …]} ou {@code null} si les
     * dates ne permettent pas de mesurer le planning (absentes ou incohérentes)
     */
    private static int[] ecartPlanning(Initiative i, LocalDate jour) {
        if (i.getDateDebut() == null || i.getDateFinPrevue() == null) {
            return null;
        }
        long duree = ChronoUnit.DAYS.between(i.getDateDebut(), i.getDateFinPrevue());
        if (duree <= 0) {
            return null;
        }
        long ecoule = ChronoUnit.DAYS.between(i.getDateDebut(), jour);
        long borne = Math.max(0, Math.min(ecoule, duree));
        return new int[]{(int) (borne * 100 / duree)};
    }
}
