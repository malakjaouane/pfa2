package ma.it6.portefeuille.security;

import ma.it6.portefeuille.entity.TentativeConnexion;
import ma.it6.portefeuille.exception.TooManyRequestsException;
import ma.it6.portefeuille.repository.TentativeConnexionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProtectionConnexions avec un repository simulé : vérifie les compteurs
 * glissants par compte et par adresse IP, et la réinitialisation en cas de
 * succès.
 */
class ProtectionConnexionsTest {

    private TentativeConnexionRepository repository;
    private ProtectionConnexions protection;

    /** Compteur simulé des échecs (incrémenté à chaque enregistrement). */
    private AtomicInteger compteurEchecs;

    @BeforeEach
    void preparer() {
        repository = mock(TentativeConnexionRepository.class);
        compteurEchecs = new AtomicInteger(0);
        // compterEchecsDansFenetre renvoie la valeur courante du compteur simulé.
        when(repository.compterEchecsDansFenetre(anyString(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> (long) compteurEchecs.get());
        protection = new ProtectionConnexions(repository, 3, 3, 900_000);
    }

    @Nested
    @DisplayName("Compte (e-mail)")
    class Compte {

        @Test
        @DisplayName("zéro échec : accès autorisé")
        void accesAutoriseSansEchec() {
            protection.verifierAutorise("192.168.1.1", "a@b.c"); // ne lève pas
        }

        @Test
        @DisplayName("3 échecs : le 4e accès est refusé (429)")
        void quatreiemeAccesRefuseApresTroisEchecs() {
            for (int i = 0; i < 3; i++) {
                protection.enregistrerEchec("192.168.1.1", "a@b.c");
                compteurEchecs.incrementAndGet();
            }
            assertThatThrownBy(() -> protection.verifierAutorise("192.168.1.1", "a@b.c"))
                    .isInstanceOf(TooManyRequestsException.class);
        }

        @Test
        @DisplayName("connexion réussie : le compteur du compte est réinitialisé")
        void reussiteReinitialiseCompteur() {
            for (int i = 0; i < 3; i++) {
                protection.enregistrerEchec("192.168.1.1", "a@b.c");
                compteurEchecs.incrementAndGet();
            }
            protection.reussite("192.168.1.1", "a@b.c");
            compteurEchecs.set(0);
            protection.verifierAutorise("192.168.1.1", "a@b.c"); // ne lève pas
            verify(repository).reinitialiserCompte("a@b.c");
        }
    }

    @Nested
    @DisplayName("Adresse IP")
    class AdresseIp {

        @Test
        @DisplayName("3 échecs depuis une IP : le 4e accès est refusé (429)")
        void quatriemeAccesRefuseDepuisIp() {
            for (int i = 0; i < 3; i++) {
                protection.enregistrerEchec("10.0.0.1", "diff@f.fr");
                compteurEchecs.incrementAndGet();
            }
            assertThatThrownBy(() -> protection.verifierAutorise("10.0.0.1", "n@m.c"))
                    .isInstanceOf(TooManyRequestsException.class);
        }
    }

    @Nested
    @DisplayName("Effets de bord")
    class EffetsBord {

        @Test
        @DisplayName("enregistrerEchec crée une ligne par compte et par IP")
        void deuxLignesParEchec() {
            protection.enregistrerEchec("1.2.3.4", "u@d.e");
            verify(repository, Mockito.times(2)).save(any());
        }
    }
}
