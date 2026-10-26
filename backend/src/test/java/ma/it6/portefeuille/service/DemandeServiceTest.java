package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.DemandeRequest;
import ma.it6.portefeuille.dto.DemandeResponse;
import ma.it6.portefeuille.entity.*;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.repository.DemandeInitiativeRepository;
import ma.it6.portefeuille.repository.EntiteRepository;
import ma.it6.portefeuille.repository.PrioriteRepository;
import ma.it6.portefeuille.repository.StatutDemandeRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du workflow des demandes d'initiatives :
 * Brouillon -> Soumise -> En évaluation -> Acceptée / Refusée,
 * puis transformation en initiative sans duplication.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DemandeService — workflow des demandes")
class DemandeServiceTest {

    @Mock
    private DemandeInitiativeRepository demandeRepository;
    @Mock
    private StatutDemandeRepository statutDemandeRepository;
    @Mock
    private EntiteRepository entiteRepository;
    @Mock
    private PrioriteRepository prioriteRepository;
    @Mock
    private InitiativeService initiativeService;

    @InjectMocks
    private DemandeService service;

    private Entite entite;
    private Priorite priorite;
    private Utilisateur demandeur;
    private StatutDemande brouillon;
    private StatutDemande soumise;
    private StatutDemande enEvaluation;
    private StatutDemande acceptee;
    private StatutDemande refusee;

    @BeforeEach
    void setUp() {
        entite = Entite.builder().idEntite(1L).code("IT6").nom("IT6 Consulting").actif(true).build();
        priorite = Priorite.builder().idPriorite(1L).libelle("Haute").ordre(3).build();
        demandeur = Utilisateur.builder().idUser(5L).prenom("Karim").nom("Alaoui").actif(true).build();

        brouillon = statut(1L, DemandeService.STATUT_BROUILLON, 1);
        soumise = statut(2L, DemandeService.STATUT_SOUMISE, 2);
        enEvaluation = statut(3L, DemandeService.STATUT_EN_EVALUATION, 3);
        acceptee = statut(4L, DemandeService.STATUT_ACCEPTEE, 4);
        refusee = statut(5L, DemandeService.STATUT_REFUSEE, 5);

        when(statutDemandeRepository.findByLibelle(DemandeService.STATUT_BROUILLON))
                .thenReturn(Optional.of(brouillon));
        when(statutDemandeRepository.findByLibelle(DemandeService.STATUT_SOUMISE))
                .thenReturn(Optional.of(soumise));
        when(statutDemandeRepository.findByLibelle(DemandeService.STATUT_EN_EVALUATION))
                .thenReturn(Optional.of(enEvaluation));
        when(statutDemandeRepository.findByLibelle(DemandeService.STATUT_ACCEPTEE))
                .thenReturn(Optional.of(acceptee));
        when(statutDemandeRepository.findByLibelle(DemandeService.STATUT_REFUSEE))
                .thenReturn(Optional.of(refusee));
        when(entiteRepository.findById(1L)).thenReturn(Optional.of(entite));
        when(prioriteRepository.findById(1L)).thenReturn(Optional.of(priorite));
        when(demandeRepository.save(any(DemandeInitiative.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static StatutDemande statut(Long id, String libelle, int ordre) {
        return StatutDemande.builder().idStatutDemande(id).libelle(libelle).ordre(ordre).build();
    }

    private DemandeInitiative demande(StatutDemande statut) {
        return DemandeInitiative.builder()
                .idDemande(1L)
                .titre("Demande de test")
                .dateSoumission(LocalDate.of(2026, 10, 1))
                .entite(entite)
                .demandeur(demandeur)
                .priorite(priorite)
                .statut(statut)
                .build();
    }

    private DemandeRequest request() {
        return new DemandeRequest("Demande de test", "Besoin", "Justification", 1L, 1L);
    }

    private void base(Long id) {
        when(demandeRepository.findById(id)).thenReturn(Optional.of(demande(brouillon)));
    }

    // ------------------------------------------------------------------
    // Création / modification
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Création et modification")
    class Creation {

        @Test
        @DisplayName("crée la demande en « Brouillon » avec le demandeur connecté")
        void creer_brouillon() {
            DemandeResponse response = service.creer(request(), demandeur);

            assertThat(response.statut().libelle()).isEqualTo(DemandeService.STATUT_BROUILLON);
            assertThat(response.demandeur().nomComplet()).isEqualTo("Karim Alaoui");
            assertThat(response.dateSoumission()).isNotNull();
            assertThat(response.initiativeLiee()).isNull();
        }

        @Test
        @DisplayName("modifie une demande tant qu'elle est en « Brouillon »")
        void modifier_brouillon() {
            base(1L);
            DemandeResponse response = service.modifier(1L, request());

            assertThat(response.titre()).isEqualTo("Demande de test");
        }

        @Test
        @DisplayName("refuse de modifier une demande déjà soumise")
        void modifier_soumiseRefusee() {
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(demande(soumise)));

            assertThatThrownBy(() -> service.modifier(1L, request()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("modifiée");
        }
    }

    // ------------------------------------------------------------------
    // Transitions du workflow
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Workflow")
    class Workflow {

        @Test
        @DisplayName("Brouillon -> Soumise")
        void soumettre_ok() {
            base(1L);
            DemandeResponse response = service.soumettre(1L);

            assertThat(response.statut().libelle()).isEqualTo(DemandeService.STATUT_SOUMISE);
        }

        @Test
        @DisplayName("ne pas re-soumettre une demande déjà soumise")
        void soumettre_depuisSoumiseRefuse() {
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(demande(soumise)));

            assertThatThrownBy(() -> service.soumettre(1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("soumise");
        }

        @Test
        @DisplayName("Soumise -> En évaluation")
        void evaluer_ok() {
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(demande(soumise)));

            DemandeResponse response = service.evaluer(1L);

            assertThat(response.statut().libelle()).isEqualTo(DemandeService.STATUT_EN_EVALUATION);
        }

        @Test
        @DisplayName("refuse d'évaluer une demande en « Brouillon »")
        void evaluer_brouillonRefuse() {
            base(1L);

            assertThatThrownBy(() -> service.evaluer(1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("évaluée");
        }

        @Test
        @DisplayName("En évaluation -> Acceptée")
        void accepter_ok() {
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(demande(enEvaluation)));

            DemandeResponse response = service.accepter(1L);

            assertThat(response.statut().libelle()).isEqualTo(DemandeService.STATUT_ACCEPTEE);
        }

        @Test
        @DisplayName("refuse d'accepter une demande non évaluée")
        void accepter_soumiseRefuse() {
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(demande(soumise)));

            assertThatThrownBy(() -> service.accepter(1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("acceptée");
        }

        @Test
        @DisplayName("En évaluation -> Refusée avec motif")
        void refuser_avecMotif() {
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(demande(enEvaluation)));

            DemandeResponse response = service.refuser(1L, "Budget non disponible");

            assertThat(response.statut().libelle()).isEqualTo(DemandeService.STATUT_REFUSEE);
            assertThat(response.motifRefus()).isEqualTo("Budget non disponible");
        }

        @Test
        @DisplayName("refus sans motif accepté (motif facultatif)")
        void refuser_sansMotif() {
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(demande(enEvaluation)));

            DemandeResponse response = service.refuser(1L, null);

            assertThat(response.statut().libelle()).isEqualTo(DemandeService.STATUT_REFUSEE);
            assertThat(response.motifRefus()).isNull();
        }
    }

    // ------------------------------------------------------------------
    // Transformation en initiative
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Transformation en initiative")
    class Transformation {

        @Test
        @DisplayName("transforme une demande « Acceptée » en initiative (aucun doublon)")
        void transformer_ok() {
            DemandeInitiative accepteeEntity = demande(acceptee);
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(accepteeEntity));
            Initiative initiative = Initiative.builder()
                    .idInitiative(99L).code("DEM-000001").nom("Demande de test").build();
            when(initiativeService.creerEnEntite(any())).thenReturn(initiative);

            DemandeResponse response = service.transformer(1L);

            assertThat(response.initiativeLiee()).isNotNull();
            assertThat(response.initiativeLiee().idInitiative()).isEqualTo(99L);
            assertThat(response.initiativeLiee().code()).isEqualTo("DEM-000001");
            verify(initiativeService).creerEnEntite(any());
        }

        @Test
        @DisplayName("refuse une seconde transformation (doublon)")
        void transformer_dejaTransformee() {
            Initiative initiative = Initiative.builder().idInitiative(99L).build();
            DemandeInitiative dejaLiee = demande(acceptee);
            dejaLiee.setInitiativeLiee(initiative);
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(dejaLiee));

            assertThatThrownBy(() -> service.transformer(1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("déjà été transformée");
        }

        @Test
        @DisplayName("refuse de transformer une demande non acceptée")
        void transformer_nonAcceptee() {
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(demande(enEvaluation)));

            assertThatThrownBy(() -> service.transformer(1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("transformée");
        }
    }

    // ------------------------------------------------------------------
    // Recherche et suppression
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Recherche - filtre titre / description / justification")
    class Recherche {

        @Test
        @DisplayName("construit le joker %...% autour du mot-clé saisi")
        void recherche_joker() {
            when(demandeRepository.findAllFetch("%cloud%")).thenReturn(List.of(demande(soumise)));

            assertThat(service.lister(" cloud ")).hasSize(1);

            verify(demandeRepository).findAllFetch("%cloud%");
        }

        @Test
        @DisplayName("sans mot-clé : aucun joker, liste complète")
        void recherche_sansMotCle() {
            when(demandeRepository.findAllFetch(null)).thenReturn(List.of());

            assertThat(service.lister(null)).isEmpty();

            verify(demandeRepository).findAllFetch(null);
        }
    }

    @Nested
    @DisplayName("Suppression - droits gérés par la couche sécurité")
    class Suppression {

        @Test
        @DisplayName("supprime une demande non transformée")
        void supprimer_ok() {
            DemandeInitiative demande = demande(brouillon);
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(demande));

            service.supprimer(1L);

            verify(demandeRepository).delete(demande);
        }

        @Test
        @DisplayName("refuse de supprimer une demande déjà transformée en initiative")
        void supprimer_transformee() {
            DemandeInitiative transformee = demande(acceptee);
            transformee.setInitiativeLiee(Initiative.builder()
                    .idInitiative(99L).code("DEM-000001").build());
            when(demandeRepository.findById(1L)).thenReturn(Optional.of(transformee));

            assertThatThrownBy(() -> service.supprimer(1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("suppression impossible");

            verify(demandeRepository, never()).delete(any(DemandeInitiative.class));
        }

        @Test
        @DisplayName("demande inexistante : 404, rien n'est supprimé")
        void supprimer_introuvable() {
            when(demandeRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.supprimer(404L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(demandeRepository, never()).delete(any(DemandeInitiative.class));
        }
    }
}