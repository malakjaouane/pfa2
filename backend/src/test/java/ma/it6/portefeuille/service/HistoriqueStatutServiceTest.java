package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.HistoriqueStatutResponse;
import ma.it6.portefeuille.entity.HistoriqueStatut;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.HistoriqueStatutRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Journal des statuts : origine des lignes (manuel / automatique) et lecture
 * globale paginée de la page « Historique des statuts ».
 *
 * <p>Deux familles : l'<strong>écriture</strong> — seule zone sensible, puisque
 * l'interface n'expose aucune écriture sur le journal — et la
 * <strong>lecture</strong> : construction des filtres, tri et pagination.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HistoriqueStatutService — origine des lignes et lecture globale")
class HistoriqueStatutServiceTest {

    @Mock
    private HistoriqueStatutRepository historiqueRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private HistoriqueStatutService service;

    private Initiative initiative;
    private Utilisateur auteur;

    @BeforeEach
    void preparer() {
        initiative = new Initiative();
        initiative.setIdInitiative(1L);
        initiative.setCode("DIG-CLI-001");
        initiative.setNom("Digitalisation du parcours client");

        auteur = new Utilisateur();
        auteur.setIdUser(7L);
        auteur.setNom("Dupont");
        auteur.setPrenom("Marie");
        auteur.setEmail("dupont@it6.ma");
        when(utilisateurRepository.findByEmail("dupont@it6.ma")).thenReturn(Optional.of(auteur));
    }

    private static HistoriqueStatut ligne(boolean automatique) {
        HistoriqueStatut h = new HistoriqueStatut();
        h.setIdHistorique(42L);
        h.setInitiative(new Initiative());
        h.getInitiative().setIdInitiative(1L);
        h.getInitiative().setCode("DIG-CLI-001");
        h.getInitiative().setNom("Digitalisation du parcours client");
        h.setAncienStatut("En cours");
        h.setNouveauStatut("Terminée");
        h.setCommentaire("Recette signée");
        h.setDateChangement(LocalDateTime.of(2026, 10, 15, 9, 30));
        h.setUtilisateur(null);
        h.setAutomatique(automatique);
        return h;
    }

    // ------------------------------------------------------------------
    // Écriture : l'origine de la ligne
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Écriture — origine « manuel » ou « automatique »")
    class Ecriture {

        @Test
        @DisplayName("la variante à 5 paramètres enregistre une ligne manuelle")
        void varianteClassiqueEstManuelle() {
            service.enregistrer(initiative, "À planifier", "En cours",
                    "Cadrage validé", "dupont@it6.ma");

            ArgumentCaptor<HistoriqueStatut> captor = ArgumentCaptor.forClass(HistoriqueStatut.class);
            verify(historiqueRepository).save(captor.capture());
            HistoriqueStatut enregistree = captor.getValue();
            assertThat(enregistree.isAutomatique()).isFalse();
            assertThat(enregistree.getUtilisateur()).isSameAs(auteur);
            assertThat(enregistree.getNouveauStatut()).isEqualTo("En cours");
        }

        @Test
        @DisplayName("le moteur de règles marque la ligne « automatique »")
        void moteurDeReglesMarqueAutomatique() {
            service.enregistrer(initiative, "En cours", "Terminée",
                    "Passage automatique : avancement atteint 100 %", null, true);

            ArgumentCaptor<HistoriqueStatut> captor = ArgumentCaptor.forClass(HistoriqueStatut.class);
            verify(historiqueRepository).save(captor.capture());
            assertThat(captor.getValue().isAutomatique()).isTrue();
            assertThat(captor.getValue().getUtilisateur()).isNull();
        }

        @Test
        @DisplayName("un auteur absent (compte supprimé, alignement de démarrage) ne casse pas l'insertion")
        void auteurAbsent() {
            service.enregistrer(initiative, null, "À planifier", "Création de l'initiative", null);

            ArgumentCaptor<HistoriqueStatut> captor = ArgumentCaptor.forClass(HistoriqueStatut.class);
            verify(historiqueRepository).save(captor.capture());
            assertThat(captor.getValue().getUtilisateur()).isNull();
            assertThat(captor.getValue().getAncienStatut()).isNull();
        }

        @Test
        @DisplayName("le motif est tronqué à la longueur de la colonne (500)")
        void motifTronque() {
            service.enregistrer(initiative, "En cours", "En pause",
                    "x".repeat(600), "dupont@it6.ma");

            ArgumentCaptor<HistoriqueStatut> captor = ArgumentCaptor.forClass(HistoriqueStatut.class);
            verify(historiqueRepository).save(captor.capture());
            assertThat(captor.getValue().getCommentaire()).hasSize(500);
        }
    }

    // ------------------------------------------------------------------
    // Lecture globale : filtres, tri, pagination
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Lecture globale — filtres, tri et pagination")
    class LectureGlobale {

        @Test
        @DisplayName("les filtres sont normalisés (jokers, période « du » inclusive, « au » inclusive)")
        void normalisationDesFiltres() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            service.lister(new HistoriqueStatutService.FiltreHistorique(
                    "  dig  ", null, null, null,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), 0, 10, false);

            verify(historiqueRepository).rechercher(
                    eq("%dig%"), isNull(), isNull(), isNull(),
                    eq(LocalDateTime.of(2026, 1, 1, 0, 0)),
                    eq(LocalDateTime.of(2026, 2, 1, 0, 0)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("tri par défaut : plus récent d'abord, avec l'id en critère d'appui")
        void triParDefautPlusRecent() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            service.lister(new HistoriqueStatutService.FiltreHistorique(null, null, null, null, null, null),
                    0, 25, false);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(historiqueRepository).rechercher(any(), any(), any(), any(), any(), any(), captor.capture());
            assertThat(captor.getValue().getSort()).isEqualTo(
                    Sort.by(Sort.Direction.DESC, "dateChangement")
                            .and(Sort.by(Sort.Direction.DESC, "idHistorique")));
        }

        @Test
        @DisplayName("tri inversé : plus ancien d'abord")
        void triInverse() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            service.lister(new HistoriqueStatutService.FiltreHistorique(null, null, null, null, null, null),
                    3, 25, true);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(historiqueRepository).rechercher(any(), any(), any(), any(), any(), any(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
            assertThat(captor.getValue().getSort()).isEqualTo(
                    Sort.by(Sort.Direction.ASC, "dateChangement")
                            .and(Sort.by(Sort.Direction.ASC, "idHistorique")));
        }

        @Test
        @DisplayName("la taille de page est plafonnée (lecture seule : 100 lignes maximum)")
        void taillePlafonnee() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            service.lister(new HistoriqueStatutService.FiltreHistorique(null, null, null, null, null, null),
                    -5, 999, false);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(historiqueRepository).rechercher(any(), any(), any(), any(), any(), any(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(HistoriqueStatutService.TAILLE_MAXIMALE);
            assertThat(captor.getValue().getPageNumber()).isZero();
        }

        @Test
        @DisplayName("une recherche vide ne produit aucun joker")
        void rechercheVideSansJoker() {
            when(historiqueRepository.compter(any(), any(), any(), any(), any(), any())).thenReturn(0L);

            long total = service.compter(new HistoriqueStatutService.FiltreHistorique(
                    "   ", "Terminée", 7L, 1L, null, null));

            assertThat(total).isZero();
            verify(historiqueRepository).compter(
                    isNull(), eq("Terminée"), eq(7L), eq(1L), isNull(), isNull());
        }

        @Test
        @DisplayName("la réponse conjoint code/nom/initiative est bien remontée")
        void reponseAvecInitiativeEtOrigine() {
            when(historiqueRepository.findByInitiativeIdInitiativeOrderByDateChangementDesc(1L))
                    .thenReturn(List.of(ligne(true)));

            List<HistoriqueStatutResponse> reponse = service.historique(1L);

            assertThat(reponse).hasSize(1);
            HistoriqueStatutResponse ev = reponse.get(0);
            assertThat(ev.automatique()).isTrue();
            assertThat(ev.initiative().code()).isEqualTo("DIG-CLI-001");
            assertThat(ev.initiative().idInitiative()).isEqualTo(1L);
            assertThat(ev.dateChangement()).isEqualTo(LocalDateTime.of(2026, 10, 15, 9, 30));
            assertThat(ev.utilisateur()).isNull();
        }

        @Test
        @DisplayName("la liste des auteurs propose les seuls comptes présents dans le journal")
        void listeAuteurs() {
            when(historiqueRepository.trouverAuteurs()).thenReturn(List.of(auteur));

            var auteurs = service.auteurs();

            assertThat(auteurs).hasSize(1);
            assertThat(auteurs.get(0).idUser()).isEqualTo(7L);
            assertThat(auteurs.get(0).nomComplet()).isEqualTo("Marie Dupont");
        }
    }
}
