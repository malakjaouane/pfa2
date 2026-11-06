package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.JournalAuditResponse;
import ma.it6.portefeuille.entity.JournalAudit;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.repository.JournalAuditRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Tests de lecture du journal d'audit : filtre méthodologique (POST / PUT /
 * PATCH / DELETE), période (du/au inclus), pagination et immuabilité
 * (aucune écriture n'est exposée — pas de méthode de modification dans le
 * service).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JournalAuditService — lecture seule, filtres et période")
class JournalAuditServiceTest {

    @Mock
    private JournalAuditRepository journalRepository;

    @InjectMocks
    private JournalAuditService service;

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Lecture paginée")
    class Lecture {

        @Test
        @DisplayName("lignes renvoyées avec le total hors pagination")
        void page() {
            JournalAudit a = journalAudit(1L, "POST", "/api/initiatives");
            when(journalRepository.rechercher(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(a)));

            JournalAuditService.PageJournal page = service.lister(null, null, null,
                    null, null, 0, 25);

            assertThat(page.total()).isEqualTo(1);
            assertThat(page.lignes()).hasSize(1);
            assertThat(page.lignes().get(0).methode()).isEqualTo("POST");
            assertThat(page.lignes().get(0).route()).isEqualTo("/api/initiatives");
        }

        @Test
        @DisplayName("journal vide : page vide, total à zéro, sans exception")
        void journalVide() {
            when(journalRepository.rechercher(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            JournalAuditService.PageJournal page = service.lister(null, null, null,
                    null, null, 0, 25);

            assertThat(page.total()).isZero();
            assertThat(page.lignes()).isEmpty();
        }

        @Test
        @DisplayName("méthode inconnue (GET) : 400 explicite")
        void methodeInconnue() {
            assertThatThrownBy(() -> service.lister(null, "GET", null, null, null, 0, 25))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Méthode d'audit inconnue");
        }

        @Test
        @DisplayName("période inversée (du > au) : 400 explicite")
        void periodeInversee() {
            LocalDate du = LocalDate.of(2026, 6, 1);
            LocalDate au = LocalDate.of(2026, 1, 1);

            assertThatThrownBy(() -> service.lister(null, null, null, du, au, 0, 25))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("La période est inversée");
        }

        @Test
        @DisplayName("période valide : du et au convertis en bornes de fenêtre")
        void periodeValide() {
            LocalDate du = LocalDate.of(2026, 1, 1);
            LocalDate au = LocalDate.of(2026, 12, 31);
            when(journalRepository.rechercher(isNull(), isNull(), isNull(),
                    any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            JournalAuditService.PageJournal page = service.lister(null, null, null,
                    du, au, 0, 25);

            assertThat(page.total()).isZero();
        }
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Export")
    class Export {

        @Test
        @DisplayName("export complet : toutes les lignes filtrées")
        void export() {
            JournalAudit a1 = journalAudit(1L, "POST", "/api/initiatives");
            JournalAudit a2 = journalAudit(2L, "DELETE", "/api/depenses/1");
            when(journalRepository.rechercher(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(a1, a2)));

            List<JournalAuditResponse> lignes = service.exporter(null, null, null, null, null);

            assertThat(lignes).hasSize(2);
            assertThat(lignes.get(0).methode()).isEqualTo("POST");
            assertThat(lignes.get(1).methode()).isEqualTo("DELETE");
        }
    }

    // ------------------------------------------------------------------
    // Immuabilité (propriété structurelle)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Immuabilité")
    class Immuabilite {

        @Test
        @DisplayName("le service ne expose aucune méthode d'écriture")
        void aucuneEcriture() {
            // Vérification structurelle : aucune méthode publique nommée
            // « creer », « modifier », « supprimer » ou « enregistrer ».
            var methodes = java.util.Arrays.stream(JournalAuditService.class.getDeclaredMethods())
                    .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                    .map(java.lang.reflect.Method::getName)
                    .toList();

            assertThat(methodes).doesNotContain(
                    "creer", "modifier", "supprimer", "enregistrer", "ajouter");
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static JournalAudit journalAudit(long id, String methode, String route) {
        return JournalAudit.builder()
                .idAudit(id)
                .dateAction(LocalDateTime.of(2026, 3, 15, 10, 30))
                .email("admin@it6.ma")
                .methode(methode)
                .route(route)
                .statutHttp(201)
                .adresseIp("127.0.0.1")
                .corps("{\"test\":true}")
                .build();
    }
}
