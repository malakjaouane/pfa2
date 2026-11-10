package ma.it6.portefeuille.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.security.OrganisationContext;

/**
 * Tests unitaires du service de priorisation (4C).
 */
class PriorisationServiceTest {

    private InitiativeRepository initiativeRepository;
    private PriorisationService service;

    @BeforeEach
    void setup() {
        OrganisationContext.setId(1L);

        initiativeRepository = mock(InitiativeRepository.class);
        service = new PriorisationService(initiativeRepository);
    }

    // ------------------------------------------------------------------
    // Calcul du score
    // ------------------------------------------------------------------

    @Test
    void calculerScore_valeur5_effort1_retourne25() {
        assertThat(service.calculerScore(5, 1)).isEqualTo(25);
    }

    @Test
    void calculerScore_valeur1_effort5_retourne5() {
        assertThat(service.calculerScore(1, 5)).isEqualTo(5);
    }

    @Test
    void calculerScore_valeur3_effort3_retourne25() {
        assertThat(service.calculerScore(3, 3)).isEqualTo(25);
    }

    @Test
    void calculerScore_valeur2_effort4_retourne12() {
        // 2 * 25 / 4 = 12.5 -> arrondi inferieur a 12
        assertThat(service.calculerScore(2, 4)).isEqualTo(12);
    }

    @Test
    void calculerScore_valeur1_effort1_retourne25() {
        assertThat(service.calculerScore(1, 1)).isEqualTo(25);
    }

    @Test
    void calculerScore_valeur_hors_limite_refuse_400() {
        assertThatThrownBy(() -> service.calculerScore(0, 3))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.calculerScore(6, 3))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void calculerScore_effort_hors_limite_refuse_400() {
        assertThatThrownBy(() -> service.calculerScore(3, 0))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.calculerScore(3, 6))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    // ------------------------------------------------------------------
    // Mise a jour priorisation
    // ------------------------------------------------------------------

    @Test
    void mettreAJourPriorisation_initiative_existante_score_recalcule() {
        Initiative initiative = new Initiative();
        initiative.setIdInitiative(10L);
        initiative.setCode("PROJ-001");

        when(initiativeRepository.findByIdInitiative(10L)).thenReturn(Optional.of(initiative));
        when(initiativeRepository.save(initiative)).thenReturn(initiative);

        Initiative resultat = service.mettreAJourPriorisation(10L, 4, 2);

        assertThat(resultat.getValeur()).isEqualTo(4);
        assertThat(resultat.getEffort()).isEqualTo(2);
        // 4 * 25 / 2 = 50 -> cappe a 25
        assertThat(resultat.getScorePriorisation()).isEqualTo(25);

        verify(initiativeRepository).save(initiative);
    }

    @Test
    void mettreAJourPriorisation_initiative_inexistante_refuse_404() {
        when(initiativeRepository.findByIdInitiative(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mettreAJourPriorisation(99L, 3, 3))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(initiativeRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void mettreAJourPriorisation_valeur_null_refuse_400() {
        assertThatThrownBy(() -> service.mettreAJourPriorisation(10L, null, 3))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void mettreAJourPriorisation_effort_null_refuse_400() {
        assertThatThrownBy(() -> service.mettreAJourPriorisation(10L, 3, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void recalculerTousLesScores_organisation_vide_ne_fait_rien() {
        // Aucune initiative dans l'organisation -> ne lance pas d'exception
        service.recalculerTousLesScores();
    }

    @AfterEach
    void teardown() {
        OrganisationContext.clear();
    }
}
