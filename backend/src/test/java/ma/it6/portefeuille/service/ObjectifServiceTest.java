package ma.it6.portefeuille.service;

import ma.it6.portefeuille.entity.ObjectifStrategique;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.ObjectifStrategiqueRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suppression d'un objectif stratégique : parcours nominal puis les deux
 * refus — objectif référencé par des initiatives (la clé étrangère est en
 * {@code ON DELETE SET NULL} : le détachement silencieux perdrait
 * l'alignement stratégique) et objectif introuvable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ObjectifService — suppression définitive et garde-fou")
class ObjectifServiceTest {

    @Mock
    private ObjectifStrategiqueRepository objectifRepository;
    @Mock
    private InitiativeRepository initiativeRepository;

    @InjectMocks
    private ObjectifService service;

    private ObjectifStrategique objectif;

    @BeforeEach
    void preparer() {
        objectif = ObjectifStrategique.builder()
                .idObjectif(5L)
                .code("DIG-CLI")
                .libelle("Digitalisation client")
                .build();
        when(objectifRepository.findById(5L)).thenReturn(Optional.of(objectif));
        when(initiativeRepository.countByObjectif_IdObjectif(5L)).thenReturn(0L);
    }

    @Nested
    @DisplayName("Suppression — un objectif retiré, deux refus explicites")
    class Suppression {

        @Test
        @DisplayName("un objectif sans initiative rattachée est supprimé")
        void objectifSansInitiative() {
            service.supprimer(5L);

            verify(objectifRepository).delete(objectif);
        }

        @Test
        @DisplayName("refus : un objectif référencé par des initiatives reste (400 avec le compte)")
        void objectifReferencedRefuse() {
            when(initiativeRepository.countByObjectif_IdObjectif(5L)).thenReturn(2L);

            assertThatThrownBy(() -> service.supprimer(5L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("2 initiative(s)")
                    .hasMessageContaining("Détachez-les d'abord");

            verify(objectifRepository, never()).delete(any(ObjectifStrategique.class));
        }

        @Test
        @DisplayName("un objectif introuvable lève une 404 et ne supprime rien")
        void objectifIntrouvable() {
            when(objectifRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.supprimer(404L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(objectifRepository, never()).delete(any(ObjectifStrategique.class));
        }
    }
}
