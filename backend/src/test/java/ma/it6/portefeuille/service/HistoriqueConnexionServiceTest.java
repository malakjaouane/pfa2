package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.ConnexionResponse;
import ma.it6.portefeuille.entity.HistoriqueConnexion;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.repository.HistoriqueConnexionRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Historique des connexions : écriture de chaque tentative (traçabilité
 * loi 09-08) et lecture administrative de la page dédiée.
 *
 * <p>Trois familles : l'<strong>écriture</strong> — normalisation e-mail,
 * rattachement du compte, troncatures —, la <strong>lecture</strong> :
 * filtres, tri et pagination, et la <strong>suppression</strong> (ligne puis
 * purge totale, réservées aux administrateurs par SecurityConfig).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HistoriqueConnexionService — traçabilité des tentatives et lecture administrative")
class HistoriqueConnexionServiceTest {

    @Mock
    private HistoriqueConnexionRepository connexionRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private HistoriqueConnexionService service;

    private Utilisateur compte;

    @BeforeEach
    void preparer() {
        compte = new Utilisateur();
        compte.setIdUser(7L);
        compte.setNom("Dupont");
        compte.setPrenom("Marie");
        compte.setEmail("dupont@it6.ma");
        when(utilisateurRepository.findById(7L)).thenReturn(Optional.of(compte));
    }

    private static HistoriqueConnexion tentative(boolean succes, Utilisateur lien) {
        HistoriqueConnexion c = new HistoriqueConnexion();
        c.setIdConnexion(42L);
        c.setDateConnexion(LocalDateTime.of(2026, 10, 20, 8, 15));
        c.setEmail("dupont@it6.ma");
        c.setAdresseIp("10.0.0.1");
        c.setSucces(succes);
        c.setMotif(succes ? null : "Mot de passe incorrect.");
        c.setUtilisateur(lien);
        return c;
    }

    // ------------------------------------------------------------------
    // Écriture : chaque tentative dépose une ligne
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Écriture — une ligne par tentative, réussie ou non")
    class Ecriture {

        @Test
        @DisplayName("une connexion réussie est rattachée au compte, e-mail normalisé")
        void connexionReussie() {
            service.enregistrer("  Dupont@IT6.ma ", 7L, "10.0.0.1", true, null);

            ArgumentCaptor<HistoriqueConnexion> captor = ArgumentCaptor.forClass(HistoriqueConnexion.class);
            verify(connexionRepository).save(captor.capture());
            HistoriqueConnexion ligne = captor.getValue();
            assertThat(ligne.getEmail()).isEqualTo("dupont@it6.ma");
            assertThat(ligne.isSucces()).isTrue();
            assertThat(ligne.getMotif()).isNull();
            assertThat(ligne.getAdresseIp()).isEqualTo("10.0.0.1");
            assertThat(ligne.getUtilisateur()).isSameAs(compte);
        }

        @Test
        @DisplayName("une tentative échouée reste tracée, avec son motif")
        void tentativeEchouee() {
            service.enregistrer("inconnu@it6.ma", 7L, "10.0.0.2", false, "Mot de passe incorrect.");

            ArgumentCaptor<HistoriqueConnexion> captor = ArgumentCaptor.forClass(HistoriqueConnexion.class);
            verify(connexionRepository).save(captor.capture());
            assertThat(captor.getValue().isSucces()).isFalse();
            assertThat(captor.getValue().getMotif()).isEqualTo("Mot de passe incorrect.");
        }

        @Test
        @DisplayName("un compte inconnu (ou supprimé) laisse la ligne sans lien")
        void compteInconnu() {
            when(utilisateurRepository.findById(99L)).thenReturn(Optional.empty());

            service.enregistrer("fantome@it6.ma", 99L, "10.0.0.3", false, "Compte inconnu.");

            ArgumentCaptor<HistoriqueConnexion> captor = ArgumentCaptor.forClass(HistoriqueConnexion.class);
            verify(connexionRepository).save(captor.capture());
            assertThat(captor.getValue().getUtilisateur()).isNull();
            // L'e-mail saisi reste la trace, même sans compte rattaché.
            assertThat(captor.getValue().getEmail()).isEqualTo("fantome@it6.ma");
        }

        @Test
        @DisplayName("sans identifiant : aucune recherche de compte, motif tronqué à 200")
        void sansIdentifiantEtMotifLong() {
            service.enregistrer(null, null, "", false, "y".repeat(300));

            ArgumentCaptor<HistoriqueConnexion> captor = ArgumentCaptor.forClass(HistoriqueConnexion.class);
            verify(connexionRepository).save(captor.capture());
            HistoriqueConnexion ligne = captor.getValue();
            assertThat(ligne.getEmail()).isEqualTo("inconnu");
            assertThat(ligne.getAdresseIp()).isEqualTo("inconnue");
            assertThat(ligne.getMotif()).hasSize(200);
            verify(utilisateurRepository, never()).findById(any());
        }
    }

    // ------------------------------------------------------------------
    // Lecture : filtres, tri, pagination
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Lecture globale — filtres, tri et pagination")
    class Lecture {

        @Test
        @DisplayName("les filtres sont normalisés (jokers, succès, période « du » inclusive, « au » inclusive)")
        void normalisationDesFiltres() {
            when(connexionRepository.rechercher(any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            service.lister(new HistoriqueConnexionService.FiltreConnexion(
                    "  admin  ", Boolean.TRUE,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), 0, 10);

            verify(connexionRepository).rechercher(
                    eq("%admin%"), eq(Boolean.TRUE),
                    eq(LocalDateTime.of(2026, 1, 1, 0, 0)),
                    eq(LocalDateTime.of(2026, 2, 1, 0, 0)),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("tri par défaut : plus récent d'abord, avec l'id en critère d'appui")
        void triParDefautPlusRecent() {
            when(connexionRepository.rechercher(any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            service.lister(new HistoriqueConnexionService.FiltreConnexion(null, null, null, null),
                    0, 25);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(connexionRepository).rechercher(any(), any(), any(), any(), captor.capture());
            assertThat(captor.getValue().getSort()).isEqualTo(
                    Sort.by(Sort.Direction.DESC, "dateConnexion")
                            .and(Sort.by(Sort.Direction.DESC, "idConnexion")));
        }

        @Test
        @DisplayName("pagination : page négative à 0, taille plafonnée à 100")
        void paginationPlafonnee() {
            when(connexionRepository.rechercher(any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            service.lister(new HistoriqueConnexionService.FiltreConnexion(null, null, null, null),
                    -3, 999);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(connexionRepository).rechercher(any(), any(), any(), any(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isZero();
            assertThat(captor.getValue().getPageSize())
                    .isEqualTo(HistoriqueConnexionService.TAILLE_MAXIMALE);
        }

        @Test
        @DisplayName("une recherche vide ne produit aucun joker, même au compteur")
        void rechercheVideSansJoker() {
            when(connexionRepository.compter(any(), any(), any(), any())).thenReturn(0L);

            long total = service.compter(new HistoriqueConnexionService.FiltreConnexion(
                    "   ", Boolean.FALSE, null, null));

            assertThat(total).isZero();
            verify(connexionRepository).compter(
                    isNull(), eq(Boolean.FALSE), isNull(), isNull());
        }

        @Test
        @DisplayName("la réponse remonte compte rattaché (ou vide) et le résultat")
        void mappageDeLaReponse() {
            when(connexionRepository.rechercher(any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(tentative(true, compte), tentative(false, null)),
                            PageRequest.of(0, 25), 2));

            List<ConnexionResponse> lignes = service.lister(
                    new HistoriqueConnexionService.FiltreConnexion(null, null, null, null), 0, 25);

            assertThat(lignes).hasSize(2);
            assertThat(lignes.get(0).utilisateur().nomComplet()).isEqualTo("Marie Dupont");
            assertThat(lignes.get(0).succes()).isTrue();
            assertThat(lignes.get(1).utilisateur()).isNull();
            assertThat(lignes.get(1).succes()).isFalse();
            assertThat(lignes.get(1).motif()).isEqualTo("Mot de passe incorrect.");
        }
    }

    // ------------------------------------------------------------------
    // Suppression : ligne puis purge (administrateurs)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Suppression — une ligne retirée, 404 si elle n'existe pas, purge totale")
    class Suppression {

        @Test
        @DisplayName("une ligne existante est retirée du journal")
        void ligneExistanteSupprimee() {
            HistoriqueConnexion entree = tentative(true, compte);
            when(connexionRepository.findById(42L)).thenReturn(Optional.of(entree));

            service.supprimer(42L);

            verify(connexionRepository).delete(entree);
        }

        @Test
        @DisplayName("une ligne inexistante lève une 404 et ne supprime rien")
        void ligneInexistante() {
            when(connexionRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.supprimer(404L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(connexionRepository, never()).delete(any(HistoriqueConnexion.class));
        }

        @Test
        @DisplayName("la purge totale vide la table en une seule requête SQL")
        void purgeTotale() {
            service.purgerTout();

            verify(connexionRepository).deleteAllInBatch();
            // Aucun chargement des lignes en mémoire : un seul DELETE en base.
            verify(connexionRepository, never()).deleteAll();
        }
    }
}
