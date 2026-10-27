package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.RisqueCritiqueDto;
import ma.it6.portefeuille.dto.RisqueRequest;
import ma.it6.portefeuille.dto.RisqueResponse;
import ma.it6.portefeuille.dto.SanteDto;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Risque;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.RisqueMapper;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.RisqueRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du registre des risques : santé pondérée (probabilité × impact,
 * 1 à 9, recalculée à chaque lecture), garde-fous d'écriture identiques
 * aux étapes (404 initiative, 400 initiative archivée) et filtrage des
 * risques critiques du tableau de bord (score ≥ 6).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RisqueService — registre, santé pondérée, critiques")
class RisqueServiceTest {

    private static final Long ID_INITIATIVE = 1L;

    @Mock
    private RisqueRepository risqueRepository;
    @Mock
    private InitiativeRepository initiativeRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private RisqueService service;

    private Initiative initiative;
    private Initiative archivée;
    private Utilisateur responsable;

    @BeforeEach
    void preparer() {
        initiative = Initiative.builder()
                .idInitiative(ID_INITIATIVE)
                .code("I-1")
                .nom("Initiative 1")
                .archive(false)
                .build();
        archivée = Initiative.builder()
                .idInitiative(2L)
                .code("I-2")
                .nom("Initiative 2")
                .archive(true)
                .build();
        responsable = Utilisateur.builder()
                .idUser(7L)
                .prenom("Karim")
                .nom("Alaoui")
                .email("responsable@it6.ma")
                .build();

        when(initiativeRepository.findById(ID_INITIATIVE)).thenReturn(Optional.of(initiative));
        when(initiativeRepository.findById(2L)).thenReturn(Optional.of(archivée));
        when(risqueRepository.save(any(Risque.class))).thenAnswer(inv -> inv.getArgument(0));
        when(utilisateurRepository.findById(7L)).thenReturn(Optional.of(responsable));
    }

    // ------------------------------------------------------------------
    // Registre (lecture)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Registre : lecture triée du plus critique au moins critique")
    class Registre {

        @Test
        @DisplayName("les risques sont triés par santé pondérée décroissante")
        void triParCriticite() {
            when(risqueRepository.findByInitiativeIdInitiative(ID_INITIATIVE)).thenReturn(List.of(
                    risque(1L, 1, 1), // score 1
                    risque(2L, 3, 3), // score 9
                    risque(3L, 2, 2))); // score 4

            List<RisqueResponse> reponses = service.lister(ID_INITIATIVE);

            assertThat(reponses).extracting(RisqueResponse::idRisque)
                    .containsExactly(2L, 3L, 1L);
        }

        @Test
        @DisplayName("initiative inexistante : 404, la base n'est jamais interrogée")
        void initiativeInconnue() {
            when(initiativeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.lister(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Initiative introuvable : 99");
            verify(risqueRepository, never()).findByInitiativeIdInitiative(any());
        }
    }

    // ------------------------------------------------------------------
    // Création
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Création")
    class Creation {

        @Test
        @DisplayName("santé pondérée calculée : 3 × 2 = 6 → critique (ROUGE)")
        void creationCalculeLeScore() {
            RisqueResponse reponse = service.creer(ID_INITIATIVE,
                    new RisqueRequest("Fournisseur unique en retard", 3, 2,
                            "  Contracter un second fournisseur  ", 7L));

            assertThat(reponse.score()).isEqualTo(6);
            assertThat(reponse.niveau()).isEqualTo(SanteDto.ROUGE);
            assertThat(reponse.planAction()).isEqualTo("Contracter un second fournisseur");
            assertThat(reponse.responsable().nomComplet()).isEqualTo("Karim Alaoui");
            assertThat(reponse.idInitiative()).isEqualTo(ID_INITIATIVE);
            verify(risqueRepository).save(any(Risque.class));
        }

        @Test
        @DisplayName("notes basses : 1 × 1 = 1 → saine (VERTE)")
        void creationScoreFaible() {
            RisqueResponse reponse = service.creer(ID_INITIATIVE,
                    new RisqueRequest("Retard administratif mineur", 1, 1, null, null));

            assertThat(reponse.score()).isEqualTo(1);
            assertThat(reponse.niveau()).isEqualTo(SanteDto.VERTE);
            assertThat(reponse.responsable()).isNull();
            assertThat(reponse.planAction()).isNull();
        }

        @Test
        @DisplayName("initiative archivée : 400, rien n'est enregistré")
        void initiativeArchivee() {
            assertThatThrownBy(() -> service.creer(2L,
                    new RisqueRequest("Risque tardif", 3, 3, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("archivée")
                    .hasMessageContaining("ajout de risque refusé");
            verify(risqueRepository, never()).save(any(Risque.class));
        }

        @Test
        @DisplayName("initiative inexistante : 404")
        void initiativeInconnue() {
            when(initiativeRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creer(42L,
                    new RisqueRequest("Risque", 2, 2, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("note hors bornes (4) : 400 explicite")
        void noteHorsBornes() {
            assertThatThrownBy(() -> service.creer(ID_INITIATIVE,
                    new RisqueRequest("Risque", 4, 2, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("de 1 à 3");
            verify(risqueRepository, never()).save(any(Risque.class));
        }

        @Test
        @DisplayName("responsable inexistant : 404")
        void responsableInconnu() {
            when(utilisateurRepository.findById(88L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creer(ID_INITIATIVE,
                    new RisqueRequest("Risque", 2, 2, null, 88L)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Responsable introuvable : 88");
        }
    }

    // ------------------------------------------------------------------
    // Modification
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Modification")
    class Modification {

        @Test
        @DisplayName("les nouvelles notes recalculent la santé : 6 → 4 (ORANGE)")
        void modificationRecalcule() {
            Risque existant = risque(9L, 3, 3);
            existant.setPlanAction("Plan initial");
            when(risqueRepository.findById(9L)).thenReturn(Optional.of(existant));

            RisqueResponse reponse = service.modifier(9L,
                    new RisqueRequest("Risque réévalué", 2, 2, "Plan révisé", null));

            assertThat(reponse.score()).isEqualTo(4);
            assertThat(reponse.niveau()).isEqualTo(SanteDto.ORANGE);
            assertThat(reponse.planAction()).isEqualTo("Plan révisé");
        }

        @Test
        @DisplayName("risque introuvable : 404")
        void risqueInconnu() {
            when(risqueRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.modifier(404L,
                    new RisqueRequest("Risque", 2, 2, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Risque introuvable : 404");
        }

        @Test
        @DisplayName("initiative archivée : 400, rien n'est modifié")
        void initiativeArchivee() {
            Risque existant = risque(9L, 3, 3);
            existant.setInitiative(archivée);
            when(risqueRepository.findById(9L)).thenReturn(Optional.of(existant));

            assertThatThrownBy(() -> service.modifier(9L,
                    new RisqueRequest("Risque", 3, 3, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("modification de risque refusée");
            verify(risqueRepository, never()).save(any(Risque.class));
        }

        @Test
        @DisplayName("note hors bornes (0) : 400")
        void noteHorsBornes() {
            when(risqueRepository.findById(9L)).thenReturn(Optional.of(risque(9L, 2, 2)));

            assertThatThrownBy(() -> service.modifier(9L,
                    new RisqueRequest("Risque", 0, 2, null, null)))
                    .isInstanceOf(BadRequestException.class);
            verify(risqueRepository, never()).save(any(Risque.class));
        }
    }

    // ------------------------------------------------------------------
    // Suppression
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Suppression")
    class Suppression {

        @Test
        @DisplayName("suppression d'un risque actif")
        void suppression() {
            Risque existant = risque(9L, 3, 3);
            when(risqueRepository.findById(9L)).thenReturn(Optional.of(existant));

            service.supprimer(9L);

            verify(risqueRepository).delete(existant);
        }

        @Test
        @DisplayName("risque introuvable : 404")
        void risqueInconnu() {
            when(risqueRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.supprimer(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(risqueRepository, never()).delete(any(Risque.class));
        }

        @Test
        @DisplayName("initiative archivée : 400, rien n'est supprimé")
        void initiativeArchivee() {
            Risque existant = risque(9L, 3, 3);
            existant.setInitiative(archivée);
            when(risqueRepository.findById(9L)).thenReturn(Optional.of(existant));

            assertThatThrownBy(() -> service.supprimer(9L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("suppression de risque refusée");
            verify(risqueRepository, never()).delete(any(Risque.class));
        }
    }

    // ------------------------------------------------------------------
    // Santé pondérée (bandes du mapper)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Santé pondérée : bandes 1-2 verte, 3-4 orange, 6-9 rouge")
    class SantePonderee {

        @Test
        @DisplayName("toutes les combinaisons de la matrice 3 × 3")
        void matriceComplete() {
            assertThat(RisqueMapper.niveau(RisqueMapper.score(risque(1L, 1, 1)))).isEqualTo(SanteDto.VERTE);
            assertThat(RisqueMapper.niveau(RisqueMapper.score(risque(1L, 1, 2)))).isEqualTo(SanteDto.VERTE);
            assertThat(RisqueMapper.niveau(RisqueMapper.score(risque(1L, 1, 3)))).isEqualTo(SanteDto.ORANGE);
            assertThat(RisqueMapper.niveau(RisqueMapper.score(risque(1L, 2, 2)))).isEqualTo(SanteDto.ORANGE);
            assertThat(RisqueMapper.niveau(RisqueMapper.score(risque(1L, 2, 3)))).isEqualTo(SanteDto.ROUGE);
            assertThat(RisqueMapper.niveau(RisqueMapper.score(risque(1L, 3, 3)))).isEqualTo(SanteDto.ROUGE);
        }

        @Test
        @DisplayName("le score n'est jamais stocké : il vaut probabilité × impact")
        void scoreJamaisStocke() {
            assertThat(RisqueMapper.score(risque(1L, 3, 2))).isEqualTo(6);
            assertThat(RisqueMapper.SEUIL_CRITIQUE).isEqualTo(6);
        }
    }

    // ------------------------------------------------------------------
    // Risques critiques (dashboard)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Widget dashboard : risques critiques (score ≥ 6)")
    class Critiques {

        @Test
        @DisplayName("seuls les scores ≥ 6 sont retenus, du plus critique au moins critique")
        void filtreLeCritique() {
            when(risqueRepository.findByInitiatives(List.of(1L))).thenReturn(List.of(
                    risque(1L, 2, 3), // 6 → critique
                    risque(2L, 2, 2), // 4 → ignoré
                    risque(3L, 3, 3), // 9 → critique
                    risque(4L, 1, 1))); // 1 → ignoré

            List<RisqueCritiqueDto> critiques = service.listerCritiques(List.of(1L));

            assertThat(critiques).extracting(RisqueCritiqueDto::idRisque)
                    .containsExactly(3L, 1L);
            assertThat(critiques.get(0).score()).isEqualTo(9);
            assertThat(critiques.get(0).niveau()).isEqualTo(SanteDto.ROUGE);
        }

        @Test
        @DisplayName("aucun risque critique : liste vide, sans exception")
        void aucunCritique() {
            when(risqueRepository.findByInitiatives(List.of(1L)))
                    .thenReturn(List.of(risque(1L, 1, 2), risque(2L, 2, 2)));

            assertThat(service.listerCritiques(List.of(1L))).isEmpty();
        }

        @Test
        @DisplayName("aucune initiative sélectionnée : liste vide, la base n'est pas appelée")
        void listeVide() {
            assertThat(service.listerCritiques(List.of())).isEmpty();
            assertThat(service.listerCritiques(null)).isEmpty();
            verify(risqueRepository, never()).findByInitiatives(any());
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private Risque risque(long id, int probabilite, int impact) {
        return Risque.builder()
                .idRisque(id)
                .description("Risque " + id)
                .probabilite(probabilite)
                .impact(impact)
                .initiative(initiative)
                .build();
    }
}
