package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.AlerteDto;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Jalon;
import ma.it6.portefeuille.entity.Risque;
import ma.it6.portefeuille.entity.StatutJalon;
import ma.it6.portefeuille.repository.DemandeInitiativeRepository;
import ma.it6.portefeuille.repository.DepenseRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.JalonRepository;
import ma.it6.portefeuille.repository.RisqueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests des alertes calculées à la volée : sept types, aucun tableau, les
 * sept règles de niveau (ROUGE / ORANGE) et le tri (ROUGE d'abord).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AlerteService — sept types, calcul à la volée, tri par niveau")
class AlerteServiceTest {

    @Mock
    private InitiativeRepository initiativeRepository;
    @Mock
    private JalonRepository jalonRepository;
    @Mock
    private RisqueRepository risqueRepository;
    @Mock
    private DemandeInitiativeRepository demandeRepository;
    @Mock
    private DepenseRepository depenseRepository;

    @InjectMocks
    private AlerteService service;

    private Initiative initiative;
    private Initiative initiativeEnRetard;

    @BeforeEach
    void preparer() {
        initiative = Initiative.builder()
                .idInitiative(1L).code("I-1").nom("Initiative 1").archive(false)
                .dateFinPrevue(LocalDate.now().plusDays(60)).build();
        initiativeEnRetard = Initiative.builder()
                .idInitiative(2L).code("I-2").nom("Initiative 2").archive(false)
                .dateFinPrevue(LocalDate.now().minusDays(10))
                .avancement(50).build();

        when(initiativeRepository.findAll()).thenReturn(List.of(initiative, initiativeEnRetard));
        when(initiativeRepository.findById(1L)).thenReturn(java.util.Optional.of(initiative));
        when(initiativeRepository.findById(2L)).thenReturn(java.util.Optional.of(initiativeEnRetard));
        when(initiativeRepository.parResponsable(any())).thenReturn(List.of(initiativeEnRetard));
        when(initiativeRepository.budgetDepasse()).thenReturn(List.of());
        when(jalonRepository.aSurveiller(any(), any())).thenReturn(List.of());
        when(risqueRepository.actifs()).thenReturn(List.of());
        when(risqueRepository.mesRisques(any())).thenReturn(List.of());
        when(demandeRepository.enAttenteEvaluation()).thenReturn(List.of());
        when(depenseRepository.totalParInitiative(any())).thenReturn(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // types d'alertes
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Types d'alertes")
    class Types {

        @Test
        @DisplayName("initiative en retard : ROUGE")
        void initiativeRetard() {
            List<AlerteDto> alertes = service.toutes();

            assertThat(alertes).filteredOn(a -> AlerteService.TYPE_INITIATIVE_RETARD.equals(a.type()))
                    .hasSize(1)
                    .first()
                    .extracting(AlerteDto::niveau).isEqualTo("ROUGE");
        }

        @Test
        @DisplayName("jalon en retard (date passée, non atteint) : ROUGE")
        void jalonEnRetard() {
            Jalon jalon = Jalon.builder()
                    .idJalon(1L).nom("Jalon critique")
                    .datePrevue(LocalDate.now().minusDays(3))
                    .initiative(initiative)
                    .statutJalon(statutJalon("En cours"))
                    .build();
            when(jalonRepository.aSurveiller(any(), any())).thenReturn(List.of(jalon));

            List<AlerteDto> alertes = service.toutes();

            assertThat(alertes).filteredOn(a -> AlerteService.TYPE_JALON_RETARD.equals(a.type()))
                    .hasSize(1)
                    .first()
                    .extracting(AlerteDto::niveau).isEqualTo("ROUGE");
        }

        @Test
        @DisplayName("jalon bientôt (≤ 7 jours) : ORANGE")
        void jalonBientot() {
            Jalon jalon = Jalon.builder()
                    .idJalon(1L).nom("Jalon proche")
                    .datePrevue(LocalDate.now().plusDays(5))
                    .initiative(initiative)
                    .statutJalon(statutJalon("En cours"))
                    .build();
            when(jalonRepository.aSurveiller(any(), any())).thenReturn(List.of(jalon));

            List<AlerteDto> alertes = service.toutes();

            assertThat(alertes).filteredOn(a -> AlerteService.TYPE_JALON_BIENTOT.equals(a.type()))
                    .hasSize(1)
                    .first()
                    .extracting(AlerteDto::niveau).isEqualTo("ORANGE");
        }

        @Test
        @DisplayName("risque critique (score ≥ 15, non clos) : ROUGE")
        void risqueCritique() {
            Risque risque = Risque.builder()
                    .idRisque(1L).titre("Risque critique")
                    .probabilite(5).impact(5).statut(Risque.STATUT_OUVERTE)
                    .initiative(initiative).build();
            when(risqueRepository.actifs()).thenReturn(List.of(risque));

            List<AlerteDto> alertes = service.toutes();

            assertThat(alertes).filteredOn(a -> AlerteService.TYPE_RISQUE_CRITIQUE.equals(a.type()))
                    .hasSize(1)
                    .first()
                    .extracting(AlerteDto::niveau).isEqualTo("ROUGE");
        }

        @Test
        @DisplayName("risque avec échéance ≤ 7 jours (non critique) : ORANGE")
        void risqueEcheance() {
            Risque risque = Risque.builder()
                    .idRisque(1L).titre("Risque à échéance proche")
                    .probabilite(2).impact(2).statut(Risque.STATUT_OUVERTE)
                    .echeance(LocalDate.now().plusDays(3))
                    .initiative(initiative).build();
            when(risqueRepository.actifs()).thenReturn(List.of(risque));

            List<AlerteDto> alertes = service.toutes();

            assertThat(alertes).filteredOn(a -> AlerteService.TYPE_RISQUE_ECHEANCE.equals(a.type()))
                    .hasSize(1)
                    .first()
                    .extracting(AlerteDto::niveau).isEqualTo("ORANGE");
        }

        @Test
        @DisplayName("demande « En évaluation » : ORANGE, mais seulement avec DEMANDE_EVALUER")
        void demandeEnAttente() {
            ma.it6.portefeuille.entity.DemandeInitiative demande =
                    ma.it6.portefeuille.entity.DemandeInitiative.builder()
                            .idDemande(1L).titre("Demande TEST").build();
            when(demandeRepository.enAttenteEvaluation()).thenReturn(List.of(demande));

            // Hors contexte de sécurité : pas de permission DEMANDE_EVALUER.
            List<AlerteDto> sansDroit = service.toutes();
            assertThat(sansDroit).filteredOn(a -> AlerteService.TYPE_DEMANDE_EN_ATTENTE.equals(a.type()))
                    .isEmpty();

            // Avec la permission.
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(new org.springframework.security.authentication
                            .UsernamePasswordAuthenticationToken(
                                    "admin@it6.ma", "n/a",
                                    List.of(new org.springframework.security.core.authority
                                            .SimpleGrantedAuthority(AlerteService.PERMISSION_EVALUER))));
            try {
                List<AlerteDto> avecDroit = service.toutes();
                assertThat(avecDroit)
                        .filteredOn(a -> AlerteService.TYPE_DEMANDE_EN_ATTENTE.equals(a.type()))
                        .hasSize(1)
                        .first()
                        .extracting(AlerteDto::niveau).isEqualTo("ORANGE");
            } finally {
                org.springframework.security.core.context.SecurityContextHolder
                        .clearContext();
            }
        }

        @Test
        @DisplayName("budget dépassé : ROUGE, mais seulement avec BUDGET_CONSULTER ou DEPENSE_SAISIR")
        void budgetDepasse() {
            when(initiativeRepository.budgetDepasse()).thenReturn(List.of(
                    Initiative.builder().idInitiative(1L).code("I-1").nom("I-1")
                            .budget(new BigDecimal("100")).archive(false).build()));

            // Hors contexte : pas de permission budget.
            List<AlerteDto> sansDroit = service.toutes();
            assertThat(sansDroit).filteredOn(a -> AlerteService.TYPE_BUDGET_DEPASSE.equals(a.type()))
                    .isEmpty();

            // Avec BUDGET_CONSULTER.
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(new org.springframework.security.authentication
                            .UsernamePasswordAuthenticationToken(
                                    "admin@it6.ma", "n/a",
                                    List.of(new org.springframework.security.core.authority
                                            .SimpleGrantedAuthority(AlerteService.PERMISSION_BUDGET))));
            try {
                List<AlerteDto> avecDroit = service.toutes();
                assertThat(avecDroit)
                        .filteredOn(a -> AlerteService.TYPE_BUDGET_DEPASSE.equals(a.type()))
                        .hasSize(1)
                        .first()
                        .extracting(AlerteDto::niveau).isEqualTo("ROUGE");
            } finally {
                org.springframework.security.core.context.SecurityContextHolder
                        .clearContext();
            }
        }
    }

    // ------------------------------------------------------------------
    // tri et portée
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Tri et portée")
    class TriEtPortee {

        @Test
        @DisplayName("ROUGE d'abord, ORANGE ensuite, tri par échéance croissante")
        void triParNiveauEtEcheance() {
            // Un jalon ORANGE (bientôt) et une initiative ROUGE (retard).
            Jalon jalon = Jalon.builder()
                    .idJalon(1L).nom("Jalon bientôt")
                    .datePrevue(LocalDate.now().plusDays(2))
                    .initiative(initiative)
                    .statutJalon(statutJalon("En cours"))
                    .build();
            when(jalonRepository.aSurveiller(any(), any())).thenReturn(List.of(jalon));

            List<AlerteDto> alertes = service.toutes();

            // La première alerte est ROUGE (initiative en retard), puis ORANGE (jalon).
            assertThat(alertes).isNotEmpty();
            assertThat(alertes.get(0).niveau()).isEqualTo("ROUGE");
            assertThat(alertes.get(alertes.size() - 1).niveau()).isEqualTo("ORANGE");
        }

        @Test
        @DisplayName("alertes personnelles : seules mes initiatives sont concernées")
        void personnelles() {
            List<AlerteDto> alertes = service.personnelles(1L);

            assertThat(alertes).isNotEmpty();
            assertThat(alertes).allMatch(a -> a.idInitiative() == null
                    || a.idInitiative().equals(1L) || a.idInitiative().equals(2L));
        }

        @Test
        @DisplayName("alertes d'une initiative : uniquement les siennes")
        void pourInitiative() {
            when(risqueRepository.findByInitiativeIdInitiative(1L)).thenReturn(List.of());

            List<AlerteDto> alertes = service.pourInitiative(1L);

            assertThat(alertes).isEmpty(); // pas de retard (fin prévue future), pas de jalons ni risques
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static StatutJalon statutJalon(String libelle) {
        StatutJalon s = new StatutJalon();
        s.setLibelle(libelle);
        return s;
    }
}
