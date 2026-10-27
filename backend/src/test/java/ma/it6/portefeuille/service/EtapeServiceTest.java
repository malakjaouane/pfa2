package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.EtapeRequest;
import ma.it6.portefeuille.dto.EtapeResponse;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    private HistoriqueModificationService historiqueService;

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
            verify(historiqueService).enregistrer(
                    eq(initiative), eq("En cours"), eq("Terminée"),
                    contains("toutes les étapes sont terminées"), isNull(), eq(true));
        }

        @Test
        @DisplayName("l'auteur de la trace est l'utilisateur connecté")
        void auteurConnecte() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "responsable@it6.ma", "n/a", List.of()));

            service.supprimer(ID_ETAPE);

            verify(historiqueService).enregistrer(
                    eq(initiative), eq("En cours"), eq("Terminée"),
                    contains("toutes les étapes"), eq("responsable@it6.ma"), eq(true));
        }

        @Test
        @DisplayName("initiative « En pause » : passage automatique à « Terminée » avec journal")
        void enPausePasseTerminee() {
            StatutInitiative enPause = statut("En pause");
            initiative.setStatut(enPause);

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(terminee);
            assertThat(initiative.getAvancement()).isEqualTo(100);
            verify(historiqueService).enregistrer(
                    eq(initiative), eq("En pause"), eq("Terminée"),
                    contains("toutes les étapes sont terminées"), isNull(), eq(true));
        }

        @Test
        @DisplayName("initiative « À planifier » : passage automatique à « Terminée » avec journal")
        void aPlanifierPasseTerminee() {
            StatutInitiative aPlanifier = statut("À planifier");
            initiative.setStatut(aPlanifier);

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(terminee);
            assertThat(initiative.getAvancement()).isEqualTo(100);
            verify(historiqueService).enregistrer(
                    eq(initiative), eq("À planifier"), eq("Terminée"),
                    contains("toutes les étapes sont terminées"), isNull(), eq(true));
        }

        @Test
        @DisplayName("référentiel sans « Terminée » : aucun changement de statut, aucun journal")
        void referentielAbsent() {
            when(statutInitiativeRepository.findByLibelle("Terminée"))
                    .thenReturn(Optional.empty());

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(enCours);
            verify(historiqueService, never())
                    .enregistrer(any(), any(), any(), any(), any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Réouverture : une étape redevient active sur une initiative « Terminée »")
    class Reouverture {

        @Test
        @DisplayName("l'initiative repasse « En cours » avec l'avancement recalculé, tracé au journal")
        void etapeRouverte() {
            initiative.setStatut(terminee);
            initiative.setAvancement(100);
            // Après la suppression, il reste une étape « Terminée » (100) et
            // une « À faire » (0) : le recalcul retombe à 50 %.
            Etape afaire = nouvelleEtape(2);
            StatutEtape statutAFaire = new StatutEtape();
            statutAFaire.setLibelle("À faire");
            afaire.setStatutEtape(statutAFaire);
            afaire.setAvancement(0);
            when(etapeRepository.findByInitiativeIdInitiativeOrderByOrdreAsc(ID_INITIATIVE))
                    .thenReturn(List.of(nouvelleEtape(1), afaire));
            StatutInitiative enCoursStatut = statut("En cours");
            when(statutInitiativeRepository.findByLibelle("En cours"))
                    .thenReturn(Optional.of(enCoursStatut));

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(enCoursStatut);
            assertThat(initiative.getAvancement()).isEqualTo(50);
            verify(historiqueService).enregistrer(
                    eq(initiative), eq("Terminée"), eq("En cours"),
                    contains("Réouverture automatique"), isNull(), eq(true));
        }

        @Test
        @DisplayName("réouverture à 0 % : l'initiative repasse « À planifier », rien n'a commencé")
        void reouvertureAZero() {
            initiative.setStatut(terminee);
            initiative.setAvancement(100);
            // Une seule étape, rouverte : le recalcul retombe à 0 %.
            when(etapeRepository.findByInitiativeIdInitiativeOrderByOrdreAsc(ID_INITIATIVE))
                    .thenReturn(List.of(etapeAFaire()));
            StatutInitiative aPlanifier = statut("À planifier");
            when(statutInitiativeRepository.findByLibelle("À planifier"))
                    .thenReturn(Optional.of(aPlanifier));

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(aPlanifier);
            assertThat(initiative.getAvancement()).isEqualTo(0);
            verify(historiqueService).enregistrer(
                    eq(initiative), eq("Terminée"), eq("À planifier"),
                    contains("Réouverture automatique"), isNull(), eq(true));
        }

        @Test
        @DisplayName("toutes les étapes restent terminées : « Terminée » et 100 % sont conservés")
        void toutesTerminees() {
            initiative.setStatut(terminee);
            initiative.setAvancement(100);

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(terminee);
            assertThat(initiative.getAvancement()).isEqualTo(100);
            verify(historiqueService, never())
                    .enregistrer(any(), any(), any(), any(), any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Retour à 0 % : « En cours » redevient « À planifier »")
    class RetourAZero {

        @Test
        @DisplayName("étapes toutes inactive à 0 % : retour à « À planifier » avec journal")
        void enCoursRetourneAPlanifier() {
            when(etapeRepository.findByInitiativeIdInitiativeOrderByOrdreAsc(ID_INITIATIVE))
                    .thenReturn(List.of(etapeAFaire()));
            StatutInitiative aPlanifier = statut("À planifier");
            when(statutInitiativeRepository.findByLibelle("À planifier"))
                    .thenReturn(Optional.of(aPlanifier));

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(aPlanifier);
            assertThat(initiative.getAvancement()).isEqualTo(0);
            verify(historiqueService).enregistrer(
                    eq(initiative), eq("En cours"), eq("À planifier"),
                    contains("n'a pas commencé"), isNull(), eq(true));
        }

        @Test
        @DisplayName("« En pause » à 0 % : l'arrêt délibéré est conservé, sans journal")
        void enPauseConservee() {
            StatutInitiative enPause = statut("En pause");
            initiative.setStatut(enPause);
            when(etapeRepository.findByInitiativeIdInitiativeOrderByOrdreAsc(ID_INITIATIVE))
                    .thenReturn(List.of(etapeAFaire()));

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(enPause);
            assertThat(initiative.getAvancement()).isEqualTo(0);
            verify(historiqueService, never())
                    .enregistrer(any(), any(), any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("référentiel sans « À planifier » : statut inchangé, avancement recalculé")
        void referentielAbsent() {
            when(statutInitiativeRepository.findByLibelle("À planifier"))
                    .thenReturn(Optional.empty());
            when(etapeRepository.findByInitiativeIdInitiativeOrderByOrdreAsc(ID_INITIATIVE))
                    .thenReturn(List.of(etapeAFaire()));

            service.supprimer(ID_ETAPE);

            assertThat(initiative.getStatut()).isSameAs(enCours);
            assertThat(initiative.getAvancement()).isEqualTo(0);
            verify(historiqueService, never())
                    .enregistrer(any(), any(), any(), any(), any(), anyBoolean());
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

    @Nested
    @DisplayName("RM18 : statut et avancement d'une étape se déduisent mutuellement")
    class CoherenceStatutAvancement {

        private StatutEtape statut(long id, String libelle) {
            StatutEtape statut = new StatutEtape();
            statut.setIdStatutEtape(id);
            statut.setLibelle(libelle);
            return statut;
        }

        @Test
        @DisplayName("« À faire » sans valeur : 0 % par défaut")
        void creerAFaireSansValeurImposeZero() {
            when(initiativeRepository.findById(ID_INITIATIVE)).thenReturn(Optional.of(initiative));
            when(statutEtapeRepository.findById(1L)).thenReturn(Optional.of(statut(1, "À faire")));
            when(etapeRepository.save(any(Etape.class))).thenAnswer(inv -> inv.getArgument(0));

            EtapeResponse reponse = service.creer(ID_INITIATIVE,
                    new EtapeRequest("E-AF", null, 3, null, null, null, 1L));

            assertThat(reponse.avancement()).isZero();
            assertThat(reponse.statutEtape().libelle()).isEqualTo("À faire");
        }

        @Test
        @DisplayName("« Terminée » sans valeur : 100 % par défaut")
        void creerTermineeSansValeurImposeCent() {
            when(initiativeRepository.findById(ID_INITIATIVE)).thenReturn(Optional.of(initiative));
            when(statutEtapeRepository.findById(3L)).thenReturn(Optional.of(statut(3, "Terminée")));
            when(etapeRepository.save(any(Etape.class))).thenAnswer(inv -> inv.getArgument(0));

            EtapeResponse reponse = service.creer(ID_INITIATIVE,
                    new EtapeRequest("E-T", null, 3, null, null, null, 3L));

            assertThat(reponse.avancement()).isEqualTo(100);
            assertThat(reponse.statutEtape().libelle()).isEqualTo("Terminée");
        }

        @Test
        @DisplayName("nombre saisi (87 %) : statut « En cours » automatique")
        void creerValeurPartiellePasseEnCours() {
            when(initiativeRepository.findById(ID_INITIATIVE)).thenReturn(Optional.of(initiative));
            when(statutEtapeRepository.findById(1L)).thenReturn(Optional.of(statut(1, "À faire")));
            when(statutEtapeRepository.findAllByOrderByOrdreAsc()).thenReturn(List.of(
                    statut(1, "À faire"), statut(2, "En cours"), statut(3, "Terminée")));
            when(etapeRepository.save(any(Etape.class))).thenAnswer(inv -> inv.getArgument(0));

            EtapeResponse reponse = service.creer(ID_INITIATIVE,
                    new EtapeRequest("E-87", null, 3, null, null, 87, 1L));

            assertThat(reponse.avancement()).isEqualTo(87);
            assertThat(reponse.statutEtape().libelle()).isEqualTo("En cours");
        }

        @Test
        @DisplayName("100 % saisi : statut « Terminée » automatique")
        void creerCentPasseTerminee() {
            when(initiativeRepository.findById(ID_INITIATIVE)).thenReturn(Optional.of(initiative));
            when(statutEtapeRepository.findById(2L)).thenReturn(Optional.of(statut(2, "En cours")));
            when(statutEtapeRepository.findAllByOrderByOrdreAsc()).thenReturn(List.of(
                    statut(1, "À faire"), statut(2, "En cours"), statut(3, "Terminée")));
            when(etapeRepository.save(any(Etape.class))).thenAnswer(inv -> inv.getArgument(0));

            EtapeResponse reponse = service.creer(ID_INITIATIVE,
                    new EtapeRequest("E-100", null, 3, null, null, 100, 2L));

            assertThat(reponse.avancement()).isEqualTo(100);
            assertThat(reponse.statutEtape().libelle()).isEqualTo("Terminée");
        }

        @Test
        @DisplayName("« En cours » conserve la valeur saisie (0-100)")
        void modifierEnCoursConserveLaValeur() {
            when(statutEtapeRepository.findById(2L)).thenReturn(Optional.of(statut(2, "En cours")));
            when(etapeRepository.save(any(Etape.class))).thenAnswer(inv -> inv.getArgument(0));

            EtapeResponse reponse = service.modifier(ID_ETAPE,
                    new EtapeRequest("E1", null, null, null, null, 78, 2L));

            assertThat(reponse.avancement()).isEqualTo(78);
            assertThat(reponse.statutEtape().libelle()).isEqualTo("En cours");
        }

        @Test
        @DisplayName("« En cours » sans valeur : 50 % par défaut")
        void creerEnCoursSansValeurDonne50() {
            when(initiativeRepository.findById(ID_INITIATIVE)).thenReturn(Optional.of(initiative));
            when(statutEtapeRepository.findById(2L)).thenReturn(Optional.of(statut(2, "En cours")));
            when(etapeRepository.save(any(Etape.class))).thenAnswer(inv -> inv.getArgument(0));

            EtapeResponse reponse = service.creer(ID_INITIATIVE,
                    new EtapeRequest("E-EC", null, 3, null, null, null, 2L));

            assertThat(reponse.avancement()).isEqualTo(50);
        }

        @Test
        @DisplayName("« En cours » avec 0 % (champ non touché) : 50 % par défaut")
        void modifierEnCoursZeroDonne50() {
            when(statutEtapeRepository.findById(2L)).thenReturn(Optional.of(statut(2, "En cours")));
            when(etapeRepository.save(any(Etape.class))).thenAnswer(inv -> inv.getArgument(0));

            EtapeResponse reponse = service.modifier(ID_ETAPE,
                    new EtapeRequest("E1", null, null, null, null, 0, 2L));

            assertThat(reponse.avancement()).isEqualTo(50);
        }
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

    /** Étape « À faire » : progression 0 dans le calcul d'avancement. */
    private Etape etapeAFaire() {
        Etape etape = nouvelleEtape(1);
        StatutEtape statut = new StatutEtape();
        statut.setLibelle("À faire");
        etape.setStatutEtape(statut);
        etape.setAvancement(0);
        return etape;
    }
}
