package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.MonEspaceResponse;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Risque;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.DemandeInitiativeRepository;
import ma.it6.portefeuille.repository.DepenseRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.JalonRepository;
import ma.it6.portefeuille.repository.RisqueRepository;
import ma.it6.portefeuille.security.CustomUserDetailsService;
import ma.it6.portefeuille.security.PrincipalUser;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de l'agrégat « Mon espace » : profil du compte, initiatives
 * pilotées avec santé, jalons proches, risques, alertes personnelles,
 * activité récente et les champs conditionnels (budgetTotal sans
 * BUDGET_CONSULTER → null, depensesTotales sans DEPENSE_SAISIR → null).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MonEspaceService — agrégat personnel du compte")
class MonEspaceServiceTest {

    @Mock
    private CustomUserDetailsService userDetailsService;
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
    @Mock
    private CalculeurSante calculeurSante;
    @Mock
    private HistoriqueModificationService historiqueService;
    @Mock
    private AlerteService alerteService;

    @InjectMocks
    private MonEspaceService service;

    private Utilisateur compte;
    private PrincipalUser principal;

    @BeforeEach
    void preparer() {
        compte = Utilisateur.builder()
                .idUser(7L).prenom("Karim").nom("Alaoui")
                .email("responsable@it6.ma").build();
        principal = new PrincipalUser(compte);

        // Contexte de sécurité : le principal porte l'utilisateur complet.
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, "n/a", principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(initiativeRepository.parResponsable(7L)).thenReturn(List.of());
        when(risqueRepository.mesRisques(7L)).thenReturn(List.of());
        when(jalonRepository.aSurveiller(any(), any())).thenReturn(List.of());
        when(demandeRepository.enAttenteEvaluation()).thenReturn(List.of());
        when(depenseRepository.totalParInitiatives(any())).thenReturn(BigDecimal.ZERO);
        when(alerteService.personnelles(7L)).thenReturn(List.of());
        when(historiqueService.activiteUtilisateur(7L)).thenReturn(List.of());
        when(calculeurSante.calculerTous(any())).thenReturn(java.util.Map.of());
    }

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Agrégat complet
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Vue complète")
    class VueComplete {

        @Test
        @DisplayName("profil du compte et rôle renvoyés")
        void profil() {
            MonEspaceResponse reponse = service.consulter();

            assertThat(reponse.compte().nomComplet()).isEqualTo("Karim Alaoui");
            assertThat(reponse.role()).isNull(); // pas de rôle dans le fixture
        }

        @Test
        @DisplayName("initiatives pilotées avec santé recalculée")
        void initiatives() {
            Initiative i = Initiative.builder()
                    .idInitiative(1L).code("I-1").nom("Projet A").archive(false).build();
            when(initiativeRepository.parResponsable(7L)).thenReturn(List.of(i));
            when(calculeurSante.calculerTous(any()))
                    .thenReturn(java.util.Map.of(1L,
                            new ma.it6.portefeuille.dto.SanteDto("VERTE", "aucunSigne", java.util.Map.of())));

            MonEspaceResponse reponse = service.consulter();

            assertThat(reponse.initiativesPilotees()).hasSize(1);
            assertThat(reponse.initiativesPilotees().get(0).code()).isEqualTo("I-1");
        }

        @Test
        @DisplayName("jalons proches bornés à 10 éléments")
        void jalonsProches() {
            // Le dépôt renvoie potentiellement plus que 10 : le service borne.
            List<ma.it6.portefeuille.entity.Jalon> jalons = new java.util.ArrayList<>();
            for (int k = 1; k <= 15; k++) {
                jalons.add(ma.it6.portefeuille.entity.Jalon.builder()
                        .idJalon((long) k).nom("Jalon " + k)
                        .datePrevue(java.time.LocalDate.now().plusDays(k))
                        .initiative(Initiative.builder().idInitiative(1L).code("I-1").build())
                        .build());
            }
            when(jalonRepository.aSurveiller(any(), any())).thenReturn(jalons);

            MonEspaceResponse reponse = service.consulter();

            assertThat(reponse.jalonsProches()).hasSize(10);
        }

        @Test
        @DisplayName("mes risques triés par criticité décroissante, bornés à 10")
        void mesRisques() {
            List<Risque> risques = new java.util.ArrayList<>();
            for (int k = 1; k <= 15; k++) {
                risques.add(Risque.builder()
                        .idRisque((long) k).titre("Risque " + k)
                        .probabilite(1).impact(k % 5 + 1)
                        .statut(Risque.STATUT_OUVERTE)
                        .initiative(Initiative.builder().idInitiative(1L).code("I-1").build())
                        .build());
            }
            when(risqueRepository.mesRisques(7L)).thenReturn(risques);

            MonEspaceResponse reponse = service.consulter();

            assertThat(reponse.mesRisques()).hasSize(10);
        }

        @Test
        @DisplayName("alertes personnelles et activité récente transmises telles quelles")
        void alertesEtActivite() {
            ma.it6.portefeuille.dto.AlerteDto alerte = new ma.it6.portefeuille.dto.AlerteDto(
                    "initiativeEnRetard", "ROUGE", 1L, null, "I-1", "Projet A",
                    java.time.LocalDate.now().minusDays(5), java.util.Map.of("jours", 5));
            when(alerteService.personnelles(7L)).thenReturn(List.of(alerte));

            MonEspaceResponse reponse = service.consulter();

            assertThat(reponse.alertes()).hasSize(1);
            assertThat(reponse.alertes().get(0).type()).isEqualTo("initiativeEnRetard");
        }
    }

    // ------------------------------------------------------------------
    // Champs conditionnels (permissions)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Champs conditionnels aux permissions")
    class ChampsConditionnels {

        @Test
        @DisplayName("sans BUDGET_CONSULTER : budgetTotal est null")
        void budgetSansDroit() {
            MonEspaceResponse reponse = service.consulter();

            assertThat(reponse.budgetTotal()).isNull();
        }

        @Test
        @DisplayName("avec BUDGET_CONSULTER : budgetTotal additionne les budgets")
        void budgetAvecDroit() {
            Initiative i1 = Initiative.builder().idInitiative(1L).code("I-1")
                    .budget(new BigDecimal("100")).archive(false).build();
            Initiative i2 = Initiative.builder().idInitiative(2L).code("I-2")
                    .budget(new BigDecimal("250")).archive(false).build();
            when(initiativeRepository.parResponsable(7L)).thenReturn(List.of(i1, i2));
            ajouterPermission(AlerteService.PERMISSION_BUDGET);

            MonEspaceResponse reponse = service.consulter();

            assertThat(reponse.budgetTotal()).isEqualByComparingTo("350");
        }

        @Test
        @DisplayName("sans DEPENSE_SAISIR : depensesTotales est null")
        void depensesSansDroit() {
            MonEspaceResponse reponse = service.consulter();

            assertThat(reponse.depensesTotales()).isNull();
        }

        @Test
        @DisplayName("avec DEPENSE_SAISIR : depensesTotales vient du dépôt")
        void depensesAvecDroit() {
            Initiative i1 = Initiative.builder().idInitiative(1L).code("I-1")
                    .archive(false).build();
            when(initiativeRepository.parResponsable(7L)).thenReturn(List.of(i1));
            when(depenseRepository.totalParInitiatives(List.of(1L)))
                    .thenReturn(new BigDecimal("42.50"));
            ajouterPermission(AlerteService.PERMISSION_DEPENSE);

            MonEspaceResponse reponse = service.consulter();

            assertThat(reponse.depensesTotales()).isEqualByComparingTo("42.50");
        }

        @Test
        @DisplayName("sans DEMANDE_EVALUER : demandesEnAttente est vide")
        void demandesSansDroit() {
            MonEspaceResponse reponse = service.consulter();

            assertThat(reponse.demandesEnAttente()).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Ajoute une permission au compte courant dans le contexte de sécurité. */
    private void ajouterPermission(String permission) {
        var authorities = new java.util.HashSet<org.springframework.security.core.GrantedAuthority>(
                principal.getAuthorities());
        authorities.add(new org.springframework.security.core.authority
                .SimpleGrantedAuthority(permission));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", authorities));
    }
}
