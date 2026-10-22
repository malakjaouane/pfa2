package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.AssistantIaResponse;
import ma.it6.portefeuille.dto.SanteDto;
import ma.it6.portefeuille.dto.StatutIaResponse;
import ma.it6.portefeuille.entity.Entite;
import ma.it6.portefeuille.entity.Etape;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Jalon;
import ma.it6.portefeuille.entity.JournalIa;
import ma.it6.portefeuille.entity.StatutEtape;
import ma.it6.portefeuille.entity.StatutInitiative;
import ma.it6.portefeuille.entity.StatutJalon;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.IaIndisponibleException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.JalonRepository;
import ma.it6.portefeuille.repository.JournalIaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssistantIaService — assistant IA du portefeuille")
class AssistantIaServiceTest {

    private static final String CLE = "sk-ant-test";
    private static final String MODELE = "claude-haiku-4-5";

    @Mock
    private InitiativeRepository initiativeRepository;
    @Mock
    private JalonRepository jalonRepository;
    @Mock
    private CalculeurSante calculeurSante;
    @Mock
    private JournalIaRepository journalIaRepository;
    @Mock
    private ClientIa clientIa;

    @Captor
    private ArgumentCaptor<JournalIa> journalCaptor;

    private AssistantIaService service(boolean actif, String cle) {
        return new AssistantIaService(initiativeRepository, jalonRepository, calculeurSante,
                journalIaRepository, clientIa, actif, cle);
    }

    private static Initiative initiativeTest() {
        Entite entite = Entite.builder().nom("SI").build();
        StatutInitiative statut = StatutInitiative.builder().libelle("En cours").build();
        Utilisateur resp = Utilisateur.builder().prenom("Rachid").nom("Alaoui").build();
        return Initiative.builder()
                .idInitiative(1L)
                .code("TESTIA-001")
                .nom("Migration cloud")
                .avancement(40)
                .budget(new BigDecimal("100000"))
                .archive(false)
                .dateFinPrevue(LocalDate.of(2026, 12, 31))
                .entite(entite)
                .statut(statut)
                .responsable(resp)
                .build();
    }

    // =================================================================
    // Disponibilité
    // =================================================================

    @Test
    @DisplayName("statut : disponible seulement si interrupteur actif ET clé présente")
    void statut() {
        when(clientIa.modele()).thenReturn(MODELE);

        assertThat(service(true, CLE).statut())
                .isEqualTo(new StatutIaResponse(true, true, true, MODELE));
        assertThat(service(true, "").statut().disponible()).isFalse();
        assertThat(service(false, CLE).statut().disponible()).isFalse();
        assertThat(service(false, CLE).statut().actif()).isFalse();
    }

    @Test
    @DisplayName("kill switch (IA_ACTIF=false) : 503 explicite + requête journalisée")
    void killSwitch() {
        when(clientIa.modele()).thenReturn(MODELE);

        assertThatThrownBy(() -> service(false, CLE)
                .poserQuestion("Quelles initiatives sont à risque ?", "fr", null))
                .isInstanceOf(IaIndisponibleException.class)
                .hasMessageContaining("IA_ACTIF");

        verify(journalIaRepository).save(journalCaptor.capture());
        assertThat(journalCaptor.getValue().getStatut()).isEqualTo(JournalIa.STATUT_NON_DISPONIBLE);
        assertThat(journalCaptor.getValue().getMessageErreur()).contains("IA_ACTIF");
    }

    @Test
    @DisplayName("clé absente : 503 avec le nom de la variable à renseigner")
    void cleAbsente() {
        when(clientIa.modele()).thenReturn(MODELE);

        assertThatThrownBy(() -> service(true, "  ")
                .poserQuestion("Bonjour ?", "fr", null))
                .isInstanceOf(IaIndisponibleException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");

        verify(journalIaRepository).save(journalCaptor.capture());
        assertThat(journalCaptor.getValue().getStatut()).isEqualTo(JournalIa.STATUT_NON_DISPONIBLE);
    }

    // =================================================================
    // Question conversationnelle
    // =================================================================

    @Test
    @DisplayName("question : contexte réel injecté, réponse tronquée, journal OK")
    void question() {
        when(clientIa.modele()).thenReturn(MODELE);
        when(initiativeRepository.findAllFetch()).thenReturn(List.of(initiativeTest()));
        when(calculeurSante.calculerTous(any()))
                .thenReturn(Map.of(1L, new SanteDto(SanteDto.VERTE, "conforme", Map.of())));
        when(clientIa.generer(anyString(), eq("Quelles initiatives sont à risque ?")))
                .thenReturn("  Aucune initiative n'est actuellement à risque.  ");

        AssistantIaResponse reponse = service(true, CLE)
                .poserQuestion("Quelles initiatives sont à risque ?", "fr", null);

        assertThat(reponse.reponse()).isEqualTo("Aucune initiative n'est actuellement à risque.");
        assertThat(reponse.modele()).isEqualTo(MODELE);
        assertThat(reponse.dureeMs()).isNotNull().isGreaterThanOrEqualTo(0);

        // Le prompt système doit contenir le portefeuille réel et la langue.
        ArgumentCaptor<String> systemeCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientIa).generer(systemeCaptor.capture(), anyString());
        assertThat(systemeCaptor.getValue())
                .contains("TESTIA-001")
                .contains("VERTE")
                .contains("Rachid Alaoui")
                .contains("français");

        verify(journalIaRepository).save(journalCaptor.capture());
        assertThat(journalCaptor.getValue().getStatut()).isEqualTo(JournalIa.STATUT_OK);
        assertThat(journalCaptor.getValue().getTypeRequete()).isEqualTo(JournalIa.TYPE_QUESTION);
        assertThat(journalCaptor.getValue().getReponse()).isNotBlank();
    }

    @Test
    @DisplayName("initiatives archivées exclues du contexte")
    void archiveesExclues() {
        Initiative archivee = initiativeTest();
        archivee.setArchive(true);
        when(clientIa.modele()).thenReturn(MODELE);
        when(initiativeRepository.findAllFetch()).thenReturn(List.of(archivee));
        when(calculeurSante.calculerTous(any())).thenReturn(Map.of());
        when(clientIa.generer(anyString(), anyString())).thenReturn("Réponse.");

        service(true, CLE).poserQuestion("Et le budget ?", "fr", null);

        ArgumentCaptor<String> systemeCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientIa).generer(systemeCaptor.capture(), anyString());
        assertThat(systemeCaptor.getValue()).contains("0 initiatives non archivées");
    }

    @Test
    @DisplayName("échec de l'appel modèle : 503 + journal ERREUR avec le motif")
    void echecDuModele() {
        when(clientIa.modele()).thenReturn(MODELE);
        when(initiativeRepository.findAllFetch()).thenReturn(List.of(initiativeTest()));
        when(calculeurSante.calculerTous(any())).thenReturn(Map.of());
        when(clientIa.generer(anyString(), anyString()))
                .thenThrow(new IaIndisponibleException("Quota du service d'IA atteint."));

        assertThatThrownBy(() -> service(true, CLE)
                .poserQuestion("Résume le portefeuille", "fr", null))
                .isInstanceOf(IaIndisponibleException.class)
                .hasMessageContaining("Quota");

        verify(journalIaRepository).save(journalCaptor.capture());
        assertThat(journalCaptor.getValue().getStatut()).isEqualTo(JournalIa.STATUT_ERREUR);
        assertThat(journalCaptor.getValue().getMessageErreur()).contains("Quota");
        assertThat(journalCaptor.getValue().getReponse()).isNull();
    }

    // =================================================================
    // Résumé d'une initiative
    // =================================================================

    @Test
    @DisplayName("résumé : fiche complète (étapes + jalons) envoyée, journal RESUME")
    void resume() {
        Initiative initiative = initiativeTest();
        StatutEtape statutEtape = StatutEtape.builder().libelle("En cours").build();
        Etape etape = Etape.builder().nom("Cadrage").avancement(50).statutEtape(statutEtape).build();
        initiative.setEtapes(List.of(etape));
        StatutJalon statutJalon = StatutJalon.builder().libelle("À venir").build();
        Jalon jalon = Jalon.builder().nom("Go-live").datePrevue(LocalDate.of(2026, 6, 1)).statutJalon(statutJalon).build();

        when(clientIa.modele()).thenReturn(MODELE);
        when(initiativeRepository.findByIdInitiativeAvecEtapes(1L)).thenReturn(Optional.of(initiative));
        when(jalonRepository.chercherParInitiatives(List.of(1L))).thenReturn(List.of(jalon));
        when(calculeurSante.calculer(any(Initiative.class)))
                .thenReturn(new SanteDto(SanteDto.ORANGE, "ecartPlanning", Map.of()));
        when(clientIa.generer(anyString(), anyString())).thenReturn("Résumé : migration à 40 %…");

        AssistantIaResponse reponse = service(true, CLE).resumerInitiative(1L, "en", null);

        assertThat(reponse.reponse()).startsWith("Résumé");

        ArgumentCaptor<String> systemeCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientIa).generer(systemeCaptor.capture(), anyString());
        assertThat(systemeCaptor.getValue())
                .contains("Cadrage")
                .contains("Go-live")
                .contains("anglais");

        verify(journalIaRepository).save(journalCaptor.capture());
        assertThat(journalCaptor.getValue().getTypeRequete()).isEqualTo(JournalIa.TYPE_RESUME);
        assertThat(journalCaptor.getValue().getInitiative()).isSameAs(initiative);
        assertThat(journalCaptor.getValue().getStatut()).isEqualTo(JournalIa.STATUT_OK);
    }

    @Test
    @DisplayName("résumé d'une initiative inconnue : 404 avant tout appel")
    void resumeInconnu() {
        when(initiativeRepository.findByIdInitiativeAvecEtapes(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(true, CLE).resumerInitiative(999L, "fr", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}
