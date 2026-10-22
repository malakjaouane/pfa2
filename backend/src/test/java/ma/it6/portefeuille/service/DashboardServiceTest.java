package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.DashboardResponse;
import ma.it6.portefeuille.dto.InitiativeFilterRequest;
import ma.it6.portefeuille.entity.Entite;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.ObjectifStrategique;
import ma.it6.portefeuille.entity.Priorite;
import ma.it6.portefeuille.entity.StatutInitiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.JalonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du calcul des KPI du dashboard.
 *
 * <p>Les indicateurs sont désormais calculés <strong>en Java sur la sélection
 * filtrée</strong> : ces tests vérifient que les compteurs, les graphiques et le
 * KPI « jalons en retard » suivent bien la liste d'initiatives retournée par
 * la spécification.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService — calcul des KPI")
class DashboardServiceTest {

    @Mock
    private InitiativeRepository initiativeRepository;
    @Mock
    private JalonRepository jalonRepository;
    @Mock
    private CalculeurSante calculeurSante;

    @InjectMocks
    private DashboardService service;

    @SuppressWarnings("unchecked")
    private void brancher(List<Initiative> initiatives) {
        when(initiativeRepository.findAll(any(Specification.class))).thenReturn(initiatives);
        when(calculeurSante.calculerTous(any())).thenReturn(Map.of());
    }

    private Initiative initiative(Long id, String statut, Integer ordreStatut, int avancement,
                                  String dateFin, String entite, String responsable, String objectif) {
        Utilisateur u = null;
        if (responsable != null) {
            u = Utilisateur.builder().prenom(responsable.split(" ")[0])
                    .nom(responsable.split(" ")[1]).build();
        }
        ObjectifStrategique o = null;
        if (objectif != null) {
            o = ObjectifStrategique.builder().libelle(objectif).build();
        }
        return Initiative.builder()
                .idInitiative(id)
                .code("I-" + id)
                .nom("Initiative " + id)
                .archive(false)
                .avancement(avancement)
                .budget(new BigDecimal("1000"))
                .dateFinPrevue(dateFin == null ? null : LocalDate.parse(dateFin))
                .entite(entite == null ? null : Entite.builder().nom(entite).build())
                .responsable(u)
                .statut(StatutInitiative.builder().libelle(statut).ordre(ordreStatut).build())
                .priorite(Priorite.builder().libelle("Moyenne").ordre(2).build())
                .objectif(o)
                .build();
    }

    @Test
    @DisplayName("KPI, graphiques et jalons en retard suivent la sélection")
    void kpiCalculesSurLaSelection() {
        List<Initiative> selection = List.of(
                initiative(1L, "En cours", 2, 40, "2020-01-01", "IT6", "Karim Alaoui", "Digitalisation"),
                initiative(2L, "Terminée", 3, 100, null, "IT6", "Karim Alaoui", "Digitalisation"),
                initiative(3L, "En pause", 4, 0, null, "Dvision Valeur", "Salma Benali", null),
                initiative(4L, "En cours", 2, 20, null, "Dvision Valeur", null, null));
        brancher(selection);
        when(jalonRepository.countEnRetardParInitiatives(any(LocalDate.class), anyCollection()))
                .thenReturn(2L);

        DashboardResponse reponse = service.calculer(new InitiativeFilterRequest(
                null, null, null, null, null, null, null, null, null));

        assertThat(reponse.totalInitiatives()).isEqualTo(4);
        assertThat(reponse.enCours()).isEqualTo(2);
        assertThat(reponse.terminees()).isEqualTo(1);
        assertThat(reponse.enPause()).isEqualTo(1);

        // Une seule initiative est réellement en retard (échéance dépassée,
        // non terminée, avancement < 100 %).
        assertThat(reponse.enRetard()).isEqualTo(1);
        assertThat(reponse.initiativesEnRetard()).hasSize(1);
        assertThat(reponse.initiativesEnRetard().get(0).idInitiative()).isEqualTo(1L);

        // Le KPI des jalons utilise la même sélection.
        assertThat(reponse.jalonsEnRetard()).isEqualTo(2);

        // Budget : 4 × 1000, arrondi à 2 décimales.
        assertThat(reponse.budgetTotal()).isEqualByComparingTo("4000.00");

        // Avancement moyen : (40 + 100 + 0 + 20) / 4 = 40.
        assertThat(reponse.avancementMoyen()).isEqualTo(40.0);

        // Répartition par statut, triée par l'ordre du référentiel.
        assertThat(reponse.parStatut())
                .extracting(d -> d.libelle())
                .containsExactly("En cours", "Terminée", "En pause");
        assertThat(reponse.parStatut().get(0).count()).isEqualTo(2);

        // Non rattaché / Non affecté sont bien regroupés.
        assertThat(reponse.parObjectif())
                .filteredOn(d -> d.libelle().equals("Non rattaché"))
                .first().extracting(d -> d.count()).isEqualTo(2L);
        assertThat(reponse.parResponsable())
                .filteredOn(d -> d.libelle().equals("Non affecté"))
                .first().extracting(d -> d.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Portefeuille vide : tous les KPI sont à zéro")
    void portefeuilleVide() {
        brancher(List.of());

        DashboardResponse reponse = service.calculer(new InitiativeFilterRequest(
                null, null, null, null, null, null, null, null, null));

        assertThat(reponse.totalInitiatives()).isZero();
        assertThat(reponse.enRetard()).isZero();
        assertThat(reponse.jalonsEnRetard()).isZero();
        assertThat(reponse.jalonsSemaine()).isZero();
        assertThat(reponse.initiativesARisque()).isZero();
        assertThat(reponse.budgetTotal()).isEqualByComparingTo("0.00");
        assertThat(reponse.avancementMoyen()).isZero();
        assertThat(reponse.parStatut()).isEmpty();
        assertThat(reponse.parPriorite()).isEmpty();
        assertThat(reponse.parEntite()).isEmpty();
        assertThat(reponse.parResponsable()).isEmpty();
        assertThat(reponse.parObjectif()).isEmpty();
    }

    @Test
    @DisplayName("Message d'accueil : jalons de la semaine et initiatives à risque")
    void messageAccueil() {
        List<Initiative> selection = List.of(
                initiative(1L, "En cours", 2, 40, null, "IT6", "Karim Alaoui", null),
                initiative(2L, "En cours", 2, 60, null, "IT6", "Karim Alaoui", null),
                initiative(3L, "Terminée", 3, 100, null, "IT6", null, null));
        brancher(selection);
        // Seul le lot renseigné compte : ORANGE et ROUGE = à risque, VERTE non.
        when(calculeurSante.calculerTous(any())).thenReturn(Map.of(
                1L, new ma.it6.portefeuille.dto.SanteDto(
                        ma.it6.portefeuille.dto.SanteDto.ORANGE, "jalonRetard", Map.of("n", 1)),
                2L, new ma.it6.portefeuille.dto.SanteDto(
                        ma.it6.portefeuille.dto.SanteDto.ROUGE, "finDepassee", Map.of("n", 12)),
                3L, new ma.it6.portefeuille.dto.SanteDto(
                        ma.it6.portefeuille.dto.SanteDto.VERTE, "conforme", Map.of())));
        when(jalonRepository.countEntreDatesParInitiatives(
                any(LocalDate.class), any(LocalDate.class), anyCollection())).thenReturn(3L);

        DashboardResponse reponse = service.calculer(new InitiativeFilterRequest(
                null, null, null, null, null, null, null, null, null));

        assertThat(reponse.jalonsSemaine()).isEqualTo(3);
        assertThat(reponse.initiativesARisque()).isEqualTo(2);
    }
}
