package ma.it6.portefeuille.mapper;

import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.StatutInitiative;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la règle « en retard » du mapper : date de fin dépassée, statut
 * autre que « Terminée », avancement < 100 %, initiative non archivée.
 */
@DisplayName("InitiativeMapper — règle « en retard »")
class InitiativeMapperTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 1);

    private Initiative initiative(LocalDate dateFin, String statut, Integer avancement, Boolean archive) {
        return Initiative.builder()
                .idInitiative(1L)
                .code("INIT-1")
                .nom("Nom")
                .dateFinPrevue(dateFin)
                .avancement(avancement)
                .archive(archive)
                .statut(StatutInitiative.builder().idStatut(2L).libelle(statut).ordre(2).build())
                .build();
    }

    @Test
    @DisplayName("vrai : échéance dépassée, non terminée, avancement < 100 %")
    void enRetard_vrai() {
        assertThat(InitiativeMapper.enRetard(
                initiative(AUJOURDHUI.minusDays(1), "En cours", 50, false), AUJOURDHUI)).isTrue();
    }

    @Test
    @DisplayName("faux : échéance future")
    void enRetard_faux_echeanceFuture() {
        assertThat(InitiativeMapper.enRetard(
                initiative(AUJOURDHUI.plusDays(1), "En cours", 50, false), AUJOURDHUI)).isFalse();
    }

    @Test
    @DisplayName("faux : initiative terminée")
    void enRetard_faux_terminee() {
        assertThat(InitiativeMapper.enRetard(
                initiative(AUJOURDHUI.minusDays(1), "Terminée", 50, false), AUJOURDHUI)).isFalse();
    }

    @Test
    @DisplayName("faux : avancement à 100 %")
    void enRetard_faux_avancement100() {
        assertThat(InitiativeMapper.enRetard(
                initiative(AUJOURDHUI.minusDays(1), "En cours", 100, false), AUJOURDHUI)).isFalse();
    }

    @Test
    @DisplayName("faux : initiative archivée")
    void enRetard_faux_archivee() {
        assertThat(InitiativeMapper.enRetard(
                initiative(AUJOURDHUI.minusDays(1), "En cours", 50, true), AUJOURDHUI)).isFalse();
    }

    @Test
    @DisplayName("faux : aucune date de fin prévue")
    void enRetard_faux_sansDate() {
        assertThat(InitiativeMapper.enRetard(
                initiative(null, "En cours", 50, false), AUJOURDHUI)).isFalse();
    }

    @Test
    @DisplayName("toResponse propage le drapeau « en retard »")
    void toResponse_exposeEnRetard() {
        var response = InitiativeMapper.toResponse(
                initiative(LocalDate.now().minusDays(1), "En cours", 50, false));

        assertThat(response.enRetard()).isTrue();
        assertThat(response.statut().libelle()).isEqualTo("En cours");
    }

    @Test
    @DisplayName("toResponse propage valeur, effort et score de priorisation")
    void toResponse_exposePriorisation() {
        var initiative = Initiative.builder()
                .idInitiative(1L)
                .code("INIT-1")
                .nom("Nom")
                .valeur(4)
                .effort(2)
                .scorePriorisation(20)
                .build();

        var response = InitiativeMapper.toResponse(initiative);

        assertThat(response.valeur()).isEqualTo(4);
        assertThat(response.effort()).isEqualTo(2);
        assertThat(response.scorePriorisation()).isEqualTo(20);
    }

    @Test
    @DisplayName("toResponse laisse la priorisation à null tant que non notée")
    void toResponse_priorisationNonNotee() {
        var response = InitiativeMapper.toResponse(
                initiative(AUJOURDHUI.plusDays(10), "À planifier", 0, false));

        assertThat(response.valeur()).isNull();
        assertThat(response.effort()).isNull();
        assertThat(response.scorePriorisation()).isNull();
    }
}
