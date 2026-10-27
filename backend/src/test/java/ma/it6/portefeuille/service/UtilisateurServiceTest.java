package ma.it6.portefeuille.service;

import ma.it6.portefeuille.entity.Role;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.repository.DemandeInitiativeRepository;
import ma.it6.portefeuille.repository.EntiteRepository;
import ma.it6.portefeuille.repository.RoleRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.security.SessionsService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suppression définitive d'un compte : les trois garde-fous de
 * {@link UtilisateurService#supprimer} (400 explicite chacun) puis le cas
 * nominal.
 *
 * <p>Le garde-fou « propre compte » lit le {@code SecurityContextHolder}
 * (l'e-mail derrière l'appel) : le contexte est donc posé puis nettoyé
 * autour de chaque test.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UtilisateurService — suppression définitive et garde-fous")
class UtilisateurServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private EntiteRepository entiteRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SessionsService sessionsService;
    @Mock
    private DemandeInitiativeRepository demandeRepository;
    @Mock
    private HistoriqueModificationService historiqueService;

    @InjectMocks
    private UtilisateurService service;

    private Utilisateur compte;

    @BeforeEach
    void preparer() {
        compte = Utilisateur.builder()
                .idUser(9L)
                .nom("Dupont")
                .prenom("Marie")
                .email("dupont@it6.ma")
                .motDePasse("hash")
                .actif(true)
                .role(Role.builder().code("UTILISATEUR").libelle("Utilisateur").build())
                .build();
        when(utilisateurRepository.findById(9L)).thenReturn(Optional.of(compte));
        when(demandeRepository.countByDemandeur_IdUser(anyLong())).thenReturn(0L);
    }

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    private static void connecterEnTantQue(String email) {
        // Token 3 arguments = authentifié, comme celui que pose le filtre JWT
        // (le principal EST l'e-mail en production).
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    @Nested
    @DisplayName("Suppression — un compte retiré, trois refus explicites")
    class Suppression {

        @Test
        @DisplayName("un compte ordinaire sans demande est supprimé, sa session invalidée")
        void compteOrdinaireSupprime() {
            service.supprimer(9L);

            verify(utilisateurRepository).delete(compte);
            // Le jeton du compte supprimé est révoqué à la requête suivante.
            verify(sessionsService).invaliderApresCommit("dupont@it6.ma");
        }

        @Test
        @DisplayName("refus : impossible de supprimer son propre compte")
        void propreCompteRefuse() {
            connecterEnTantQue("dupont@it6.ma");

            assertThatThrownBy(() -> service.supprimer(9L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("propre compte");

            verify(utilisateurRepository, never()).delete(any(Utilisateur.class));
        }

        @Test
        @DisplayName("refus : le dernier administrateur actif est conservé")
        void dernierAdministrateurRefuse() {
            compte.setRole(Role.builder().code("ADMIN").libelle("Administrateur").build());
            when(utilisateurRepository.existsByIdUserNotAndActifTrueAndRoleCode(9L, "ADMIN"))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.supprimer(9L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("dernier administrateur actif");

            verify(utilisateurRepository, never()).delete(any(Utilisateur.class));
        }

        @Test
        @DisplayName("un autre administrateur actif existe : la suppression passe")
        void unAutreAdministrateurExiste() {
            compte.setRole(Role.builder().code("ADMIN").libelle("Administrateur").build());
            when(utilisateurRepository.existsByIdUserNotAndActifTrueAndRoleCode(9L, "ADMIN"))
                    .thenReturn(true);

            service.supprimer(9L);

            verify(utilisateurRepository).delete(compte);
        }

        @Test
        @DisplayName("refus : un compte qui porte encore des demandes se désactive, ne se supprime pas")
        void compteAvecDemandesRefuse() {
            when(demandeRepository.countByDemandeur_IdUser(9L)).thenReturn(3L);

            assertThatThrownBy(() -> service.supprimer(9L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("3 demande(s)")
                    .hasMessageContaining("Désactivez");

            verify(utilisateurRepository, never()).delete(any(Utilisateur.class));
        }

        @Test
        @DisplayName("un compte introuvable lève une 404")
        void compteIntrouvable() {
            when(utilisateurRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.supprimer(404L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(utilisateurRepository, never()).delete(any(Utilisateur.class));
        }
    }
}
