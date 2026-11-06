package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.DepenseRequest;
import ma.it6.portefeuille.dto.DepenseResponse;
import ma.it6.portefeuille.entity.Depense;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.repository.DepenseRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests des dépenses : registre paginé avec total, garde-fous d'écriture
 * (initiative archivée, montant positif, date obligatoire) et lifecycle
 * complet (création, modification, suppression).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DepenseService — registre, garde-fous financiers, lifecycle")
class DepenseServiceTest {

    @Mock
    private DepenseRepository depenseRepository;
    @Mock
    private InitiativeRepository initiativeRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private DepenseService service;

    private Initiative initiative;
    private Initiative archivée;
    private Utilisateur saisisseur;

    @BeforeEach
    void preparer() {
        initiative = Initiative.builder()
                .idInitiative(1L).code("I-1").nom("Initiative 1").archive(false).build();
        archivée = Initiative.builder()
                .idInitiative(2L).code("I-2").nom("Initiative 2").archive(true).build();
        saisisseur = Utilisateur.builder()
                .idUser(7L).prenom("Karim").nom("Alaoui").email("k@it6.ma").build();

        when(initiativeRepository.findById(1L)).thenReturn(Optional.of(initiative));
        when(initiativeRepository.findById(2L)).thenReturn(Optional.of(archivée));
        when(utilisateurRepository.findById(7L)).thenReturn(Optional.of(saisisseur));
        when(depenseRepository.save(any(Depense.class))).thenAnswer(inv -> {
            Depense d = inv.getArgument(0);
            d.setIdDepense(10L);
            return d;
        });
        when(depenseRepository.totalFiltre(any(), any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // Création
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Création")
    class Creation {

        @Test
        @DisplayName("dépense enregistrée avec libellé trimé, montant et date")
        void creation() {
            DepenseResponse reponse = service.creer(1L,
                    new DepenseRequest("  Achat de licences  ", new BigDecimal("1500.50"),
                            LocalDate.of(2026, 3, 15), "materiel"), 7L);

            assertThat(reponse.idInitiative()).isEqualTo(1L);
            assertThat(reponse.codeInitiative()).isEqualTo("I-1");
            assertThat(reponse.libelle()).isEqualTo("Achat de licences");
            assertThat(reponse.montant()).isEqualByComparingTo("1500.50");
            assertThat(reponse.categorie()).isEqualTo("materiel");
            assertThat(reponse.saisisseur().nomComplet()).isEqualTo("Karim Alaoui");
            verify(depenseRepository).save(any(Depense.class));
        }

        @Test
        @DisplayName("initiative archivée : 400 sur ajout, rien n'est enregistré")
        void initiativeArchivee() {
            assertThatThrownBy(() -> service.creer(2L,
                    new DepenseRequest("Dépense tardive", BigDecimal.TEN,
                            LocalDate.now(), null), 7L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("archivée")
                    .hasMessageContaining("ajout de dépense refusé");
            verify(depenseRepository, never()).save(any(Depense.class));
        }

        @Test
        @DisplayName("initiative inexistante : 404")
        void initiativeInconnue() {
            when(initiativeRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creer(42L,
                    new DepenseRequest("Dépense", BigDecimal.TEN, LocalDate.now(), null), null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("montant négatif : 400 explicite")
        void montantNegatif() {
            assertThatThrownBy(() -> service.creer(1L,
                    new DepenseRequest("Dépense", new BigDecimal("-5"),
                            LocalDate.now(), null), null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("strictement positif");
        }

        @Test
        @DisplayName("montant nul : 400 explicite")
        void montantNul() {
            assertThatThrownBy(() -> service.creer(1L,
                    new DepenseRequest("Dépense", BigDecimal.ZERO,
                            LocalDate.now(), null), null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("strictement positif");
        }

        @Test
        @DisplayName("date absente : 400 explicite")
        void dateAbsente() {
            assertThatThrownBy(() -> service.creer(1L,
                    new DepenseRequest("Dépense", BigDecimal.TEN, null, null), null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("date de la dépense est obligatoire");
        }
    }

    // ------------------------------------------------------------------
    // Registre global
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Registre global paginé")
    class Registre {

        @Test
        @DisplayName("page et total des montants renvoyés ensemble")
        void pageEtTotal() {
            Depense d1 = depense(1L, "Licences", new BigDecimal("100.00"), LocalDate.of(2026, 1, 10));
            Depense d2 = depense(2L, "Matériel", new BigDecimal("250.00"), LocalDate.of(2026, 2, 5));
            when(depenseRepository.rechercher(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(d2, d1)));
            when(depenseRepository.totalFiltre(any(), any(), any(), any(), any()))
                    .thenReturn(new BigDecimal("350.00"));

            DepenseService.PageDepenses page = service.lister(null, null, null, null,
                    null, 0, 25);

            assertThat(page.total()).isEqualTo(2);
            assertThat(page.lignes()).extracting(DepenseResponse::libelle)
                    .containsExactly("Matériel", "Licences");
            assertThat(page.montantTotal()).isEqualByComparingTo("350.00");
        }

        @Test
        @DisplayName("aucune ligne : montant total à zéro, jamais null")
        void listeVide() {
            when(depenseRepository.rechercher(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(depenseRepository.totalFiltre(any(), any(), any(), any(), any()))
                    .thenReturn(null);

            DepenseService.PageDepenses page = service.lister(null, null, null, null,
                    null, 0, 25);

            assertThat(page.total()).isZero();
            assertThat(page.lignes()).isEmpty();
            assertThat(page.montantTotal()).isEqualByComparingTo("0.00");
        }
    }

    // ------------------------------------------------------------------
    // Registre d'une initiative + total
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Registre par initiative et total")
    class ParInitiative {

        @Test
        @DisplayName("les 50 dépenses les plus récentes sont renvoyées")
        void top50() {
            Depense d = depense(1L, "Licences", new BigDecimal("100"), LocalDate.now());
            when(depenseRepository
                    .findTop50ByInitiativeIdInitiativeOrderByDateDepenseDesc(1L))
                    .thenReturn(List.of(d));

            List<DepenseResponse> lignes = service.listerParInitiative(1L);

            assertThat(lignes).hasSize(1);
            assertThat(lignes.get(0).libelle()).isEqualTo("Licences");
        }

        @Test
        @DisplayName("total des dépenses d'une initiative")
        void total() {
            when(depenseRepository.totalParInitiative(1L)).thenReturn(new BigDecimal("1234.56"));

            assertThat(service.totalInitiative(1L)).isEqualByComparingTo("1234.56");
        }

        @Test
        @DisplayName("initiative inexistante : 404")
        void initiativeInconnue() {
            when(initiativeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listerParInitiative(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // Modification / suppression
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Modification et suppression")
    class ModificationSuppression {

        @Test
        @DisplayName("modification : nouveaux libellé, montant, date et catégorie")
        void modification() {
            Depense existante = depense(10L, "Ancien libellé", new BigDecimal("100"),
                    LocalDate.of(2026, 1, 1));
            existante.setInitiative(initiative);
            when(depenseRepository.findById(10L)).thenReturn(Optional.of(existante));

            DepenseResponse reponse = service.modifier(10L,
                    new DepenseRequest("Nouveau libellé", new BigDecimal("999.99"),
                            LocalDate.of(2026, 6, 1), "salaires"));

            assertThat(reponse.libelle()).isEqualTo("Nouveau libellé");
            assertThat(reponse.montant()).isEqualByComparingTo("999.99");
            assertThat(reponse.dateDepense()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(reponse.categorie()).isEqualTo("salaires");
        }

        @Test
        @DisplayName("dépense inexistante : 404")
        void depenseInconnue() {
            when(depenseRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.modifier(404L,
                    new DepenseRequest("Libellé", BigDecimal.TEN, LocalDate.now(), null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Dépense introuvable : 404");
        }

        @Test
        @DisplayName("initiative archivée : 400 sur modification, rien n'est enregistré")
        void modificationArchivee() {
            Depense existante = depense(10L, "Libellé", BigDecimal.TEN, LocalDate.now());
            existante.setInitiative(archivée);
            when(depenseRepository.findById(10L)).thenReturn(Optional.of(existante));

            assertThatThrownBy(() -> service.modifier(10L,
                    new DepenseRequest("Libellé", BigDecimal.TEN, LocalDate.now(), null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("modification de dépense refusée");
            verify(depenseRepository, never()).save(any(Depense.class));
        }

        @Test
        @DisplayName("suppression d'une dépense active")
        void suppression() {
            Depense existante = depense(10L, "Libellé", BigDecimal.TEN, LocalDate.now());
            existante.setInitiative(initiative);
            when(depenseRepository.findById(10L)).thenReturn(Optional.of(existante));

            service.supprimer(10L);

            verify(depenseRepository).delete(existante);
        }

        @Test
        @DisplayName("initiative archivée : 400 sur suppression, rien n'est supprimé")
        void suppressionArchivee() {
            Depense existante = depense(10L, "Libellé", BigDecimal.TEN, LocalDate.now());
            existante.setInitiative(archivée);
            when(depenseRepository.findById(10L)).thenReturn(Optional.of(existante));

            assertThatThrownBy(() -> service.supprimer(10L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("suppression de dépense refusée");
            verify(depenseRepository, never()).delete(any(Depense.class));
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Depense depense(long id, String libelle, BigDecimal montant, LocalDate date) {
        return Depense.builder()
                .idDepense(id).libelle(libelle).montant(montant).dateDepense(date).build();
    }
}
