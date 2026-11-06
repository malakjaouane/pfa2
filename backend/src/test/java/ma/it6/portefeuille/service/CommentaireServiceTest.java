package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.CommentaireRequest;
import ma.it6.portefeuille.dto.CommentaireResponse;
import ma.it6.portefeuille.entity.Commentaire;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.repository.CommentaireRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du fil de discussion : ajout (gare à l'initiative archivée),
 * suppression (auteur ou administrateur) et immuabilité du commentaire.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CommentaireService — fil de discussion immuable")
class CommentaireServiceTest {

    @Mock
    private CommentaireRepository commentaireRepository;
    @Mock
    private InitiativeRepository initiativeRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private CommentaireService service;

    private Initiative initiative;
    private Initiative archivée;
    private Utilisateur auteur;

    @BeforeEach
    void preparer() {
        initiative = Initiative.builder()
                .idInitiative(1L).code("I-1").nom("Initiative 1").archive(false).build();
        archivée = Initiative.builder()
                .idInitiative(2L).code("I-2").nom("Initiative 2").archive(true).build();
        auteur = Utilisateur.builder()
                .idUser(7L).prenom("Karim").nom("Alaoui").email("k@it6.ma").build();

        when(initiativeRepository.findById(1L)).thenReturn(Optional.of(initiative));
        when(initiativeRepository.findById(2L)).thenReturn(Optional.of(archivée));
        when(utilisateurRepository.findById(7L)).thenReturn(Optional.of(auteur));
        when(commentaireRepository.save(any(Commentaire.class)))
                .thenAnswer(inv -> {
                    Commentaire c = inv.getArgument(0);
                    c.setIdCommentaire(10L);
                    return c;
                });
    }

    // ------------------------------------------------------------------
    // Ajout
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Ajout")
    class Creation {

        @Test
        @DisplayName("commentaire enregistré avec l'auteur et le contenu trimé")
        void creation() {
            CommentaireResponse reponse = service.creer(1L,
                    new CommentaireRequest("  Bonjour, ce projet avance bien.  "), 7L);

            assertThat(reponse.idInitiative()).isEqualTo(1L);
            assertThat(reponse.contenu()).isEqualTo("Bonjour, ce projet avance bien.");
            assertThat(reponse.auteur().nomComplet()).isEqualTo("Karim Alaoui");
            verify(commentaireRepository).save(any(Commentaire.class));
        }

        @Test
        @DisplayName("initiative archivée : 400, rien n'est enregistré")
        void initiativeArchivee() {
            assertThatThrownBy(() -> service.creer(2L,
                    new CommentaireRequest("Trop tard."), 7L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("archivée")
                    .hasMessageContaining("ajout de commentaire refusé");
            verify(commentaireRepository, never()).save(any(Commentaire.class));
        }

        @Test
        @DisplayName("initiative inexistante : 404")
        void initiativeInconnue() {
            when(initiativeRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creer(42L,
                    new CommentaireRequest("Bonjour."), null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Initiative introuvable : 42");
        }

        @Test
        @DisplayName("contenu au-delà du plafond : coupé proprement, sans troncature à cheval")
        void contenuCoupe() {
            String tropLong = "x".repeat(CommentaireService.LONGUEUR_MAXIMALE + 500);

            CommentaireResponse reponse = service.creer(1L,
                    new CommentaireRequest(tropLong), null);

            assertThat(reponse.contenu()).hasSize(CommentaireService.LONGUEUR_MAXIMALE);
        }
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Lecture du fil")
    class Lecture {

        @Test
        @DisplayName("le fil est renvoyé tel que renvoyé par le dépôt (plus récent d'abord)")
        void fil() {
            Commentaire c1 = commentaire(1L, "Plus ancien");
            Commentaire c2 = commentaire(2L, "Plus récent");
            when(commentaireRepository.filInitiative(1L)).thenReturn(List.of(c2, c1));

            List<CommentaireResponse> reponses = service.lister(1L);

            assertThat(reponses).extracting(CommentaireResponse::contenu)
                    .containsExactly("Plus récent", "Plus ancien");
        }

        @Test
        @DisplayName("initiative inexistante : 404")
        void initiativeInconnue() {
            when(initiativeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.lister(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // Suppression
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Suppression")
    class Suppression {

        @Test
        @DisplayName("l'auteur supprime son commentaire")
        void auteurSupprime() {
            Commentaire c = commentaire(10L, "Mon commentaire");
            c.setAuteur(auteur);
            when(commentaireRepository.findById(10L)).thenReturn(Optional.of(c));

            service.supprimer(10L, 7L);

            verify(commentaireRepository).delete(c);
        }

        @Test
        @DisplayName("un autre auteur sans UTILISATEUR_GERER : 400, rien n'est supprimé")
        void autreAuteurRefuse() {
            Commentaire c = commentaire(10L, "Commentaire d'un autre");
            c.setAuteur(auteur);
            when(commentaireRepository.findById(10L)).thenReturn(Optional.of(c));

            // Hors contexte de sécurité : pas d'autorité UTILISATEUR_GERER.
            assertThatThrownBy(() -> service.supprimer(10L, 99L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Seul l'auteur");
            verify(commentaireRepository, never()).delete(any(Commentaire.class));
        }

        @Test
        @DisplayName("commentaire anonymisé (auteur supprimé) : suppression par admin")
        void anonymiseAdmin() {
            Commentaire c = commentaire(10L, "Ancien commentaire");
            c.setAuteur(null);
            when(commentaireRepository.findById(10L)).thenReturn(Optional.of(c));

            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(new org.springframework.security.authentication
                            .UsernamePasswordAuthenticationToken(
                                    "admin@it6.ma", "n/a",
                                    List.of(new org.springframework.security.core.authority
                                            .SimpleGrantedAuthority("UTILISATEUR_GERER"))));
            try {
                service.supprimer(10L, null);
            } finally {
                org.springframework.security.core.context.SecurityContextHolder
                        .clearContext();
            }

            verify(commentaireRepository).delete(c);
        }

        @Test
        @DisplayName("commentaire inexistant : 404")
        void commentaireInconnu() {
            when(commentaireRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.supprimer(404L, 7L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Commentaire introuvable : 404");
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Commentaire commentaire(long id, String contenu) {
        return Commentaire.builder().idCommentaire(id).contenu(contenu).build();
    }
}
