package ma.it6.portefeuille.service;

import ma.it6.portefeuille.entity.Etape;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.StatutEtape;
import ma.it6.portefeuille.entity.StatutInitiative;
import ma.it6.portefeuille.repository.EtapeRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.StatutEtapeRepository;
import ma.it6.portefeuille.repository.StatutInitiativeRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du journal des transitions déclenchées par les étapes : le passage
 * automatique à « Terminée » (RM07, 100 % atteint) doit laisser une trace
 * dans l'historique des statuts, au même titre que les transitions menées à
 * la main depuis l'interface.
 *
 * <p>La suppression d'une étape sert ici de simple déclencheur du recalcule
 * d'avancement : c'est le seul point d'entrée du service qui dépend du
 * contenu de la liste des étapes.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EtapeService — RM07-auto et journal des statuts")
class EtapeServiceTest {

    private static final Long ID_INITIATIVE = 1L;
    private static final Long ID_ETAPE = 9L;

    @Mock
    private EtapeRepository etapeRepository;
    @Mock
    private InitiativeRepository initiativeRepository;
    @Mock
    private StatutEtapeRepository statutEtapeRepository;
    @Mock
    private StatutInitiativeRepository statutInitiativeRepository;
    @Mock
    private HistoriqueStatutService historiqueStatutService;

    @InjectMocks
    private EtapeService service;

    private Initiative initiative;
    private StatutInitiative enCours;
    private StatutInitiative terminee;
    private StatutEtape etapeTerminee;

    @BeforeEach
    void preparer() {
        enCours = statut("En cours");
        terminee = statut("Terminée");

        etapeTerminee = new StatutEtape();
        etapeTerminee.setLibelle("Terminée");

        initiative = new Initiative();
        initiative.setIdInitiative(ID_INITIATIVE);
        initiative.setArchive(false);
        initiative.setStatut(enCours);
        initiative.setAvancement(75);

        Etape supprimee = nouvelleEtape(1);
        supprimee.setIdEtape(ID_ETAPE);

        // Après suppression : deux étapes restantes, toutes terminées (recalcule = 100).
        when(etapeRepository.findById(ID_ETAPE)).thenReturn(Optional.of(supprimee));
        when(etapeRepository.findByInitiativeIdInitiativeOrderByOrdreAsc(ID_INITIATIVE))
                .thenReturn(List.of(nouvelleEtape(1), nouvelleEtape(2)));
        when(statutInitiativeRepository.findByLibelle("Terminée"))
                .thenReturn(Optional.of(terminee));
    }

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("RM07-auto : toutes les étapes sont terminées")
    class PassageAutomatique {

        @Test
        @DisplayName("le passage à « Terminée » est enregistré dans l'historique")
        void journaliseLePassage() {
            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(terminee);
            assertThat(initiative.getAvancement()).isEqualTo(100);
            verify(initiativeRepository).save(initiative);
            verify(historiqueStatutService).enregistrer(
                    eq(initiative), eq("En cours"), eq("Terminée"),
                    contains("toutes les étapes sont terminées"), isNull());
        }

        @Test
        @DisplayName("l'auteur de la trace est l'utilisateur connecté")
        void auteurConnecte() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "responsable@it6.ma", "n/a", List.of()));

            service.supprimer(ID_ETAPE);

            verify(historiqueStatutService).enregistrer(
                    eq(initiative), eq("En cours"), eq("Terminée"),
                    contains("toutes les étapes"), eq("responsable@it6.ma"));
        }

        @Test
        @DisplayName("initiative « En pause » : avancement recalculé, mais ni transition ni journal")
        void sansTransition() {
            StatutInitiative enPause = statut("En pause");
            initiative.setStatut(enPause);

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(enPause);
            assertThat(initiative.getAvancement()).isEqualTo(100);
            verify(historiqueStatutService, never())
                    .enregistrer(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("référentiel sans « Terminée » : aucun changement de statut, aucun journal")
        void referentielAbsent() {
            when(statutInitiativeRepository.findByLibelle("Terminée"))
                    .thenReturn(Optional.empty());

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(enCours);
            verify(historiqueStatutService, never())
                    .enregistrer(any(), any(), any(), any(), any());
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private StatutInitiative statut(String libelle) {
        StatutInitiative statut = new StatutInitiative();
        statut.setLibelle(libelle);
        return statut;
    }

    private Etape nouvelleEtape(int ordre) {
        Etape etape = new Etape();
        etape.setIdEtape((long) ordre);
        etape.setOrdre(ordre);
        etape.setAvancement(100);
        etape.setStatutEtape(etapeTerminee);
        etape.setInitiative(initiative);
        return etape;
    }
}
