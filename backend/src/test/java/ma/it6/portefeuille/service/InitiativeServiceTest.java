package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.InitiativeRequest;
import ma.it6.portefeuille.dto.InitiativeResponse;
import ma.it6.portefeuille.entity.Entite;
import ma.it6.portefeuille.entity.Etape;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Priorite;
import ma.it6.portefeuille.entity.StatutEtape;
import ma.it6.portefeuille.entity.StatutInitiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.repository.EntiteRepository;
import ma.it6.portefeuille.repository.EtapeRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.ObjectifStrategiqueRepository;
import ma.it6.portefeuille.repository.PrioriteRepository;
import ma.it6.portefeuille.repository.StatutInitiativeRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires des règles métier et du cycle de vie des initiatives
 * (InitiativeService). Les dépendances de persistance sont simulées, ce qui
 * permet de vérifier le comportement métier sans base de données.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InitiativeService — règles métier et cycle de vie")
class InitiativeServiceTest {

    private static final Long ID_ENTITE = 20L;
    private static final Long ID_PRIORITE = 10L;

    @Mock
    private InitiativeRepository initiativeRepository;
    @Mock
    private StatutInitiativeRepository statutInitiativeRepository;
    @Mock
    private PrioriteRepository prioriteRepository;
    @Mock
    private EntiteRepository entiteRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private ObjectifStrategiqueRepository objectifRepository;
    @Mock
    private EtapeRepository etapeRepository;
    @Mock
    private HistoriqueStatutService historiqueStatutService;
    @Mock
    private CalculeurSante calculeurSante;

    @InjectMocks
    private InitiativeService service;

    private Entite entite;
    private Priorite priorite;
    private StatutInitiative aPlanifier;
    private StatutInitiative enCours;
    private StatutInitiative enPause;
    private StatutInitiative terminee;
    private StatutInitiative annulee;

    @BeforeEach
    void setUp() {
        entite = Entite.builder().idEntite(ID_ENTITE).code("IT6").nom("IT6").actif(true).build();
        priorite = Priorite.builder().idPriorite(ID_PRIORITE).libelle("Haute").ordre(1).build();

        aPlanifier = statut(1L, InitiativeService.STATUT_A_PLANIFIER, 1);
        enCours = statut(2L, InitiativeService.STATUT_EN_COURS, 2);
        enPause = statut(3L, InitiativeService.STATUT_EN_PAUSE, 3);
        terminee = statut(4L, InitiativeService.STATUT_TERMINEE, 4);
        annulee = statut(5L, InitiativeService.STATUT_ANNULEE, 5);

        // Stubs communs aux scénarios de création/modification.
        when(entiteRepository.findById(ID_ENTITE)).thenReturn(Optional.of(entite));
        when(prioriteRepository.findById(ID_PRIORITE)).thenReturn(Optional.of(priorite));
        when(statutInitiativeRepository.findByLibelle(InitiativeService.STATUT_A_PLANIFIER))
                .thenReturn(Optional.of(aPlanifier));
        when(statutInitiativeRepository.findByLibelle(InitiativeService.STATUT_EN_COURS))
                .thenReturn(Optional.of(enCours));
        when(statutInitiativeRepository.findByLibelle(InitiativeService.STATUT_EN_PAUSE))
                .thenReturn(Optional.of(enPause));
        when(statutInitiativeRepository.findByLibelle(InitiativeService.STATUT_TERMINEE))
                .thenReturn(Optional.of(terminee));
        when(statutInitiativeRepository.findByLibelle(InitiativeService.STATUT_ANNULEE))
                .thenReturn(Optional.of(annulee));
        when(initiativeRepository.save(any(Initiative.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static StatutInitiative statut(Long id, String libelle, int ordre) {
        return StatutInitiative.builder().idStatut(id).libelle(libelle).ordre(ordre).build();
    }

    private InitiativeRequest request(String code, Integer avancement, Long idStatut,
                                      BigDecimal budget, LocalDate debut, LocalDate fin,
                                      Long idResponsable) {
        return new InitiativeRequest(code, "Nom", "Description", debut, fin, budget,
                avancement, ID_ENTITE, idResponsable, idStatut, ID_PRIORITE, null);
    }

    private Initiative initiativeExistante(StatutInitiative statut, int avancement, boolean archive) {
        return Initiative.builder()
                .idInitiative(1L)
                .code("INIT-1")
                .nom("Nom")
                .budget(BigDecimal.TEN)
                .avancement(avancement)
                .archive(archive)
                .entite(entite)
                .statut(statut)
                .priorite(priorite)
                .build();
    }

    /** Étape « À faire » : progression 0 dans le calcul d'avancement. */
    private static Etape etapeAFaire() {
        StatutEtape statutEtape = new StatutEtape();
        statutEtape.setLibelle("À faire");
        Etape etape = new Etape();
        etape.setStatutEtape(statutEtape);
        etape.setAvancement(0);
        return etape;
    }

    // ------------------------------------------------------------------
    // Création
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Création")
    class Creation {

        @Test
        @DisplayName("dérive le statut « À planifier » et ignore le statut envoyé par le client")
        void creer_forceStatutAPlanifier() {
            when(initiativeRepository.existsByCode("INIT-1")).thenReturn(false);

            // Le client envoie « En cours » mais le domaine doit imposer « À planifier ».
            InitiativeResponse response = service.creer(
                    request("INIT-1", 0, enCours.getIdStatut(), BigDecimal.TEN, null, null, null));

            assertThat(response.statut().libelle()).isEqualTo(InitiativeService.STATUT_A_PLANIFIER);
            assertThat(response.avancement()).isZero();
            assertThat(response.archive()).isFalse();
        }

        @Test
        @DisplayName("crée directement « Terminée » une initiative à 100 % d'avancement (RM07)")
        void creer_avancement100_terminee() {
            when(initiativeRepository.existsByCode("INIT-1")).thenReturn(false);

            InitiativeResponse response = service.creer(
                    request("INIT-1", 100, aPlanifier.getIdStatut(), BigDecimal.TEN, null, null, null));

            assertThat(response.statut().libelle()).isEqualTo(InitiativeService.STATUT_TERMINEE);
            assertThat(response.avancement()).isEqualTo(100);
        }

        @Test
        @DisplayName("refuse un code déjà utilisé")
        void creer_codeDuplique() {
            when(initiativeRepository.existsByCode("INIT-1")).thenReturn(true);

            assertThatThrownBy(() -> service.creer(
                    request("INIT-1", 0, aPlanifier.getIdStatut(), BigDecimal.TEN, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("déjà utilisé");
        }

        @Test
        @DisplayName("refuse une date de fin antérieure à la date de début (RM04)")
        void creer_dateFinAvantDebut() {
            when(initiativeRepository.existsByCode("INIT-1")).thenReturn(false);

            assertThatThrownBy(() -> service.creer(request("INIT-1", 0, aPlanifier.getIdStatut(),
                    BigDecimal.TEN, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("date de fin");
        }

        @Test
        @DisplayName("refuse un budget négatif (RM05)")
        void creer_budgetNegatif() {
            when(initiativeRepository.existsByCode("INIT-1")).thenReturn(false);

            assertThatThrownBy(() -> service.creer(request("INIT-1", 0, aPlanifier.getIdStatut(),
                    new BigDecimal("-1"), null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("budget");
        }

        @Test
        @DisplayName("refuse un avancement hors bornes (RM06)")
        void creer_avancementHorsBornes() {
            when(initiativeRepository.existsByCode("INIT-1")).thenReturn(false);

            assertThatThrownBy(() -> service.creer(
                    request("INIT-1", 150, aPlanifier.getIdStatut(), BigDecimal.TEN, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("avancement");
        }

        @Test
        @DisplayName("refuse un responsable inactif")
        void creer_responsableInactif() {
            when(initiativeRepository.existsByCode("INIT-1")).thenReturn(false);
            Utilisateur inactif = Utilisateur.builder().idUser(7L).actif(false).build();
            when(utilisateurRepository.findById(7L)).thenReturn(Optional.of(inactif));

            assertThatThrownBy(() -> service.creer(
                    request("INIT-1", 0, aPlanifier.getIdStatut(), BigDecimal.TEN, null, null, 7L)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("actif");
        }

        @Test
        @DisplayName("signale un responsable inexistant (404)")
        void creer_responsableIntrouvable() {
            when(initiativeRepository.existsByCode("INIT-1")).thenReturn(false);
            when(utilisateurRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creer(
                    request("INIT-1", 0, aPlanifier.getIdStatut(), BigDecimal.TEN, null, null, 99L)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // Modification / cycle de vie
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Modification")
    class Modification {

        @Test
        @DisplayName("autorise « En cours » → « En pause »")
        void modifier_transitionAutorisee() {
            Initiative existante = initiativeExistante(enCours, 40, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(initiativeRepository.existsByCodeAndIdInitiativeNot("INIT-1", 1L)).thenReturn(false);
            when(statutInitiativeRepository.findById(enPause.getIdStatut())).thenReturn(Optional.of(enPause));

            InitiativeResponse response = service.modifier(1L,
                    request("INIT-1", 40, enPause.getIdStatut(), BigDecimal.TEN, null, null, null));

            assertThat(response.statut().libelle()).isEqualTo(InitiativeService.STATUT_EN_PAUSE);
        }

        @Test
        @DisplayName("force « Terminée » dès que l'avancement atteint 100 % (RM07-auto)")
        void modifier_avancement100_forceTerminee() {
            Initiative existante = initiativeExistante(enCours, 50, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(initiativeRepository.existsByCodeAndIdInitiativeNot("INIT-1", 1L)).thenReturn(false);
            when(statutInitiativeRepository.findById(enCours.getIdStatut())).thenReturn(Optional.of(enCours));

            InitiativeResponse response = service.modifier(1L,
                    request("INIT-1", 100, enCours.getIdStatut(), BigDecimal.TEN, null, null, null));

            assertThat(response.statut().libelle()).isEqualTo(InitiativeService.STATUT_TERMINEE);
            assertThat(response.avancement()).isEqualTo(100);
        }

        @Test
        @DisplayName("refuse « Terminée » avec un avancement < 100 % (RM07)")
        void modifier_termineeSans100() {
            Initiative existante = initiativeExistante(enCours, 50, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(initiativeRepository.existsByCodeAndIdInitiativeNot("INIT-1", 1L)).thenReturn(false);
            when(statutInitiativeRepository.findById(terminee.getIdStatut())).thenReturn(Optional.of(terminee));

            assertThatThrownBy(() -> service.modifier(1L,
                    request("INIT-1", 50, terminee.getIdStatut(), BigDecimal.TEN, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("refuse une transition non autorisée (Terminée → En cours)")
        void modifier_transitionNonAutorisee() {
            Initiative existante = initiativeExistante(terminee, 100, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(initiativeRepository.existsByCodeAndIdInitiativeNot("INIT-1", 1L)).thenReturn(false);
            when(statutInitiativeRepository.findById(enCours.getIdStatut())).thenReturn(Optional.of(enCours));

            assertThatThrownBy(() -> service.modifier(1L,
                    request("INIT-1", 100, enCours.getIdStatut(), BigDecimal.TEN, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Transition");
        }

        @Test
        @DisplayName("refuse la modification d'une initiative archivée")
        void modifier_archivee() {
            Initiative existante = initiativeExistante(terminee, 100, true);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));

            assertThatThrownBy(() -> service.modifier(1L,
                    request("INIT-1", 100, terminee.getIdStatut(), BigDecimal.TEN, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("archivée");
        }
        @Test
        @DisplayName("conserve le statut actuel quand idStatut est absent")
        void modifier_sansIdStatut_conserveLeStatut() {
            Initiative existante = initiativeExistante(enCours, 40, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(initiativeRepository.existsByCodeAndIdInitiativeNot("INIT-1", 1L)).thenReturn(false);

            InitiativeResponse response = service.modifier(1L,
                    request("INIT-1", 40, null, BigDecimal.TEN, null, null, null));

            assertThat(response.statut().libelle()).isEqualTo(InitiativeService.STATUT_EN_COURS);
            verify(statutInitiativeRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("applique « Terminée » à 100 % même sans idStatut (RM07-auto)")
        void modifier_sansIdStatut_avancement100_forceTerminee() {
            Initiative existante = initiativeExistante(enCours, 50, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(initiativeRepository.existsByCodeAndIdInitiativeNot("INIT-1", 1L)).thenReturn(false);

            InitiativeResponse response = service.modifier(1L,
                    request("INIT-1", 100, null, BigDecimal.TEN, null, null, null));

            assertThat(response.statut().libelle()).isEqualTo(InitiativeService.STATUT_TERMINEE);
            assertThat(response.avancement()).isEqualTo(100);
        }

        @Test
        @DisplayName("force « Terminée » depuis « À planifier » à 100 % (RM07-auto, sans étapes)")
        void modifier_aPlanifier_avancement100_forceTerminee() {
            Initiative existante = initiativeExistante(aPlanifier, 0, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(initiativeRepository.existsByCodeAndIdInitiativeNot("INIT-1", 1L)).thenReturn(false);
            when(statutInitiativeRepository.findById(aPlanifier.getIdStatut()))
                    .thenReturn(Optional.of(aPlanifier));

            InitiativeResponse response = service.modifier(1L,
                    request("INIT-1", 100, aPlanifier.getIdStatut(), BigDecimal.TEN, null, null, null));

            assertThat(response.statut().libelle()).isEqualTo(InitiativeService.STATUT_TERMINEE);
            assertThat(response.avancement()).isEqualTo(100);
        }

        @Test
        @DisplayName("force « Terminée » depuis « En pause » à 100 % (RM07-auto)")
        void modifier_enPause_avancement100_forceTerminee() {
            Initiative existante = initiativeExistante(enPause, 40, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(initiativeRepository.existsByCodeAndIdInitiativeNot("INIT-1", 1L)).thenReturn(false);
            when(statutInitiativeRepository.findById(enPause.getIdStatut()))
                    .thenReturn(Optional.of(enPause));

            InitiativeResponse response = service.modifier(1L,
                    request("INIT-1", 100, enPause.getIdStatut(), BigDecimal.TEN, null, null, null));

            assertThat(response.statut().libelle()).isEqualTo(InitiativeService.STATUT_TERMINEE);
            assertThat(response.avancement()).isEqualTo(100);
        }

        @Test
        @DisplayName("conserve « Annulée » même à 100 % d'avancement")
        void modifier_annulee_resteAnnulee() {
            Initiative existante = initiativeExistante(annulee, 50, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(initiativeRepository.existsByCodeAndIdInitiativeNot("INIT-1", 1L)).thenReturn(false);
            when(statutInitiativeRepository.findById(annulee.getIdStatut()))
                    .thenReturn(Optional.of(annulee));

            InitiativeResponse response = service.modifier(1L,
                    request("INIT-1", 100, annulee.getIdStatut(), BigDecimal.TEN, null, null, null));

            assertThat(response.statut().libelle()).isEqualTo(InitiativeService.STATUT_ANNULEE);
        }

        @Test
        @DisplayName("étapes à 0 % : le choix manuel « En cours » du formulaire est respecté (RM17 : recalcul auto seulement)")
        void modifier_enCours0Etapes_promotionManuelleRespectee() {
            Initiative existante = initiativeExistante(aPlanifier, 0, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(initiativeRepository.existsByCodeAndIdInitiativeNot("INIT-1", 1L)).thenReturn(false);
            when(statutInitiativeRepository.findById(enCours.getIdStatut()))
                    .thenReturn(Optional.of(enCours));
            when(etapeRepository.findByInitiativeIdInitiativeOrderByOrdreAsc(1L))
                    .thenReturn(List.of(etapeAFaire()));

            InitiativeResponse response = service.modifier(1L,
                    request("INIT-1", 50, enCours.getIdStatut(), BigDecimal.TEN, null, null, null));

            assertThat(response.statut().libelle()).isEqualTo(InitiativeService.STATUT_EN_COURS);
            assertThat(response.avancement()).isEqualTo(0);
            verify(historiqueStatutService).enregistrer(
                    any(Initiative.class), anyString(), anyString(),
                    contains("Modification de l'initiative"), any(), eq(false));
        }
    }

    // ------------------------------------------------------------------
    // Archivage
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Archivage")
    class Archivage {

        @Test
        @DisplayName("archive une initiative « Terminée » (RM14)")
        void archiver_terminee() {
            Initiative existante = initiativeExistante(terminee, 100, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));

            InitiativeResponse response = service.archiver(1L);

            assertThat(response.archive()).isTrue();
        }

        @Test
        @DisplayName("refuse d'archiver une initiative « En cours »")
        void archiver_nonTerminee() {
            Initiative existante = initiativeExistante(enCours, 50, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));

            assertThatThrownBy(() -> service.archiver(1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Terminée");
        }

        @Test
        @DisplayName("signale une initiative inexistante (404)")
        void archiver_introuvable() {
            when(initiativeRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.archiver(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        // ----------------------------------------------------------------
        // Purge définitive : uniquement des initiatives archivées
        // ----------------------------------------------------------------

        @Test
        @DisplayName("purge une initiative archivée (suppression définitive)")
        void purger_archivee() {
            Initiative existante = initiativeExistante(terminee, 100, true);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));

            service.purger(1L);

            verify(initiativeRepository).delete(existante);
        }

        @Test
        @DisplayName("refuse de purger une initiative non archivée")
        void purger_nonArchivee() {
            Initiative existante = initiativeExistante(terminee, 50, false);
            when(initiativeRepository.findById(1L)).thenReturn(Optional.of(existante));

            assertThatThrownBy(() -> service.purger(1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("archivées");

            verify(initiativeRepository, never()).delete(any(Initiative.class));
        }

        @Test
        @DisplayName("purge d'une initiative inexistante (404)")
        void purger_introuvable() {
            when(initiativeRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.purger(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
