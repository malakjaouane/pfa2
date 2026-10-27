package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.HistoriqueModificationResponse;
import ma.it6.portefeuille.entity.HistoriqueModification;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.repository.HistoriqueModificationRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Journal des modifications : écriture (transitions, diff champ à champ,
 * lignes génériques), lecture globale (filtres, fenêtre d'archivage, tri,
 * pagination) et <strong>immuabilité</strong> — aucune suppression n'existe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HistoriqueModificationService — journal immuable, diff et vues")
class HistoriqueModificationServiceTest {

    @Mock
    private HistoriqueModificationRepository historiqueRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private HistoriqueModificationService service;

    private Initiative initiative;
    private Utilisateur auteur;

    @BeforeEach
    void preparer() {
        initiative = new Initiative();
        initiative.setIdInitiative(1L);
        initiative.setCode("DIG-CLI-001");
        initiative.setNom("Digitalisation du parcours client");

        auteur = new Utilisateur();
        auteur.setIdUser(7L);
        auteur.setNom("Dupont");
        auteur.setPrenom("Marie");
        auteur.setEmail("dupont@it6.ma");
        when(utilisateurRepository.findByEmail("dupont@it6.ma")).thenReturn(Optional.of(auteur));
    }

    private static HistoriqueModification ligne(boolean automatique) {
        HistoriqueModification h = new HistoriqueModification();
        h.setIdHistorique(42L);
        h.setTypeObjet("initiative");
        h.setIdObjet(1L);
        h.setInitiative(new Initiative());
        h.getInitiative().setIdInitiative(1L);
        h.getInitiative().setCode("DIG-CLI-001");
        h.getInitiative().setNom("Digitalisation du parcours client");
        h.setChamp("statut");
        h.setAncienneValeur("En cours");
        h.setNouvelleValeur("Terminée");
        h.setCommentaire("Recette signée");
        h.setDateChangement(LocalDateTime.of(2026, 10, 15, 9, 30));
        h.setUtilisateur(null);
        h.setAutomatique(automatique);
        return h;
    }

    // ------------------------------------------------------------------
    // Écriture : transitions, événements, diff
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Écriture — transitions, lignes génériques, diff")
    class Ecriture {

        @Test
        @DisplayName("la variante à 5 paramètres enregistre une transition manuelle (champ « statut »)")
        void varianteClassiqueEstManuelle() {
            service.enregistrer(initiative, "À planifier", "En cours",
                    "Cadrage validé", "dupont@it6.ma");

            ArgumentCaptor<HistoriqueModification> captor =
                    ArgumentCaptor.forClass(HistoriqueModification.class);
            verify(historiqueRepository).save(captor.capture());
            HistoriqueModification enregistree = captor.getValue();
            assertThat(enregistree.isAutomatique()).isFalse();
            assertThat(enregistree.getUtilisateur()).isSameAs(auteur);
            assertThat(enregistree.getTypeObjet()).isEqualTo("initiative");
            assertThat(enregistree.getChamp()).isEqualTo("statut");
            assertThat(enregistree.getNouvelleValeur()).isEqualTo("En cours");
            assertThat(enregistree.getInitiative()).isSameAs(initiative);
        }

        @Test
        @DisplayName("le moteur de règles marque la ligne « automatique »")
        void moteurDeReglesMarqueAutomatique() {
            service.enregistrer(initiative, "En cours", "Terminée",
                    "Passage automatique : avancement atteint 100 %", null, true);

            ArgumentCaptor<HistoriqueModification> captor =
                    ArgumentCaptor.forClass(HistoriqueModification.class);
            verify(historiqueRepository).save(captor.capture());
            assertThat(captor.getValue().isAutomatique()).isTrue();
            assertThat(captor.getValue().getUtilisateur()).isNull();
        }

        @Test
        @DisplayName("un auteur absent (compte supprimé, alignement de démarrage) ne casse pas l'insertion")
        void auteurAbsent() {
            service.enregistrer(initiative, null, "À planifier", "Création de l'initiative", null);

            ArgumentCaptor<HistoriqueModification> captor =
                    ArgumentCaptor.forClass(HistoriqueModification.class);
            verify(historiqueRepository).save(captor.capture());
            assertThat(captor.getValue().getUtilisateur()).isNull();
            assertThat(captor.getValue().getAncienneValeur()).isNull();
        }

        @Test
        @DisplayName("les valeurs et le motif sont tronqués à la longueur des colonnes (500)")
        void valeursEtMotifTronques() {
            service.ecrire("demande", 5L, null, "creation",
                    "x".repeat(600), "y".repeat(600), "z".repeat(600),
                    "dupont@it6.ma", false);

            ArgumentCaptor<HistoriqueModification> captor =
                    ArgumentCaptor.forClass(HistoriqueModification.class);
            verify(historiqueRepository).save(captor.capture());
            HistoriqueModification enregistree = captor.getValue();
            assertThat(enregistree.getAncienneValeur()).hasSize(500);
            assertThat(enregistree.getNouvelleValeur()).hasSize(500);
            assertThat(enregistree.getCommentaire()).hasSize(500);
            assertThat(enregistree.getInitiative()).isNull();
            assertThat(enregistree.getIdObjet()).isEqualTo(5L);
        }

        @Test
        @DisplayName("le diff ne dépose une ligne que par champ réellement modifié")
        void diffUneLigneParChampModifie() {
            // Map mutable : « responsable » part à null (retrait du
            // responsable), ce que Map.of refuse.
            Map<String, String> avant = new java.util.LinkedHashMap<>();
            avant.put("budget", "1000");
            avant.put("nom", "Portail RH");
            avant.put("responsable", "Marie Dupont");
            Map<String, String> apres = new java.util.LinkedHashMap<>();
            apres.put("budget", "2500");        // changé
            apres.put("nom", "Portail RH");     // identique
            apres.put("responsable", null);     // changé (retrait)

            service.diff("initiative", 1L, initiative, avant, apres,
                    "dupont@it6.ma", false);

            ArgumentCaptor<HistoriqueModification> captor =
                    ArgumentCaptor.forClass(HistoriqueModification.class);
            verify(historiqueRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            List<HistoriqueModification> lignes = captor.getAllValues();
            assertThat(lignes)
                    .extracting(HistoriqueModification::getChamp)
                    .containsExactlyInAnyOrder("budget", "responsable");
            assertThat(lignes)
                    .filteredOn(l -> "budget".equals(l.getChamp()))
                    .singleElement()
                    .satisfies(l -> {
                        assertThat(l.getAncienneValeur()).isEqualTo("1000");
                        assertThat(l.getNouvelleValeur()).isEqualTo("2500");
                    });
        }

        @Test
        @DisplayName("un diff sans aucun changement ne dépose rien")
        void diffSansAucunChangement() {
            Map<String, String> valeurs = Map.of("budget", "1000", "nom", "Portail RH");

            service.diff("initiative", 1L, initiative, valeurs, Map.copyOf(valeurs),
                    "dupont@it6.ma", false);

            verify(historiqueRepository, never()).save(any(HistoriqueModification.class));
        }
    }

    // ------------------------------------------------------------------
    // Lecture globale : filtres, fenêtre d'archivage, tri, pagination
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Lecture globale — filtres, archivage, tri, pagination")
    class LectureGlobale {

        @Test
        @DisplayName("les filtres sont normalisés (jokers, période « du » inclusive, « au » inclusive)")
        void normalisationDesFiltres() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(),
                    any(Pageable.class))).thenReturn(Page.empty());

            service.lister(new HistoriqueModificationService.FiltreHistorique(
                    "  dig  ", "initiative", "statut", 7L, 1L,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), false), 0, 10, false);

            ArgumentCaptor<LocalDateTime> debut = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> fin = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(historiqueRepository).rechercher(
                    eq("%dig%"), eq("initiative"), eq("statut"), eq(7L), eq(1L),
                    debut.capture(), fin.capture(), any(Pageable.class));
            // La période saisie (janvier 2026) est PLUS SERRE que la fenêtre
            // d'archivage (12 mois) : c'est elle qui prime aux deux bornes.
            assertThat(debut.getValue()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
            assertThat(fin.getValue()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
        }

        @Test
        @DisplayName("vue active : les lignes de plus de 12 mois sont hors fenêtre")
        void fenetreVueActive() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(),
                    any(Pageable.class))).thenReturn(Page.empty());

            service.lister(new HistoriqueModificationService.FiltreHistorique(
                    null, null, null, null, null, null, null, false), 0, 10, false);

            ArgumentCaptor<LocalDateTime> debut = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(historiqueRepository).rechercher(
                    isNull(), isNull(), isNull(), isNull(), isNull(),
                    debut.capture(), isNull(), any(Pageable.class));
            // Borne de bascule : minuit il y a 12 mois (±1 jour selon l'heure).
            LocalDate attendu = LocalDateTime.now().minusMonths(12).toLocalDate();
            assertThat(debut.getValue().toLocalDate()).isBetween(
                    attendu.minusDays(1), attendu.plusDays(1));
        }

        @Test
        @DisplayName("vue archives : la fenêtre s'arrête à la borne des 12 mois")
        void fenetreVueArchives() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(),
                    any(Pageable.class))).thenReturn(Page.empty());

            service.lister(new HistoriqueModificationService.FiltreHistorique(
                    null, null, null, null, null, null, null, true), 0, 10, false);

            ArgumentCaptor<LocalDateTime> fin = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(historiqueRepository).rechercher(
                    isNull(), isNull(), isNull(), isNull(), isNull(),
                    isNull(), fin.capture(), any(Pageable.class));
            LocalDate attendu = LocalDateTime.now().minusMonths(12).toLocalDate();
            assertThat(fin.getValue().toLocalDate()).isBetween(
                    attendu.minusDays(1), attendu.plusDays(1));
        }

        @Test
        @DisplayName("période « au » antérieure à la borne des archives : c'est la période qui prime (vue archives)")
        void periodeSerrantLaFenetreArchives() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(),
                    any(Pageable.class))).thenReturn(Page.empty());

            service.lister(new HistoriqueModificationService.FiltreHistorique(
                    null, null, null, null, null, null,
                    LocalDate.of(2020, 6, 30), true), 0, 10, false);

            verify(historiqueRepository).rechercher(
                    isNull(), isNull(), isNull(), isNull(), isNull(),
                    isNull(), eq(LocalDateTime.of(2020, 7, 1, 0, 0)), any(Pageable.class));
        }

        @Test
        @DisplayName("tri par défaut : plus récent d'abord, avec l'id en critère d'appui")
        void triParDefautPlusRecent() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(),
                    any(Pageable.class))).thenReturn(Page.empty());

            service.lister(new HistoriqueModificationService.FiltreHistorique(
                    null, null, null, null, null, null, null, false), 0, 25, false);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(historiqueRepository).rechercher(any(), any(), any(), any(), any(), any(), any(),
                    captor.capture());
            assertThat(captor.getValue().getSort()).isEqualTo(
                    Sort.by(Sort.Direction.DESC, "dateChangement")
                            .and(Sort.by(Sort.Direction.DESC, "idHistorique")));
        }

        @Test
        @DisplayName("tri inversé : plus ancien d'abord")
        void triInverse() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(),
                    any(Pageable.class))).thenReturn(Page.empty());

            service.lister(new HistoriqueModificationService.FiltreHistorique(
                    null, null, null, null, null, null, null, false), 3, 25, true);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(historiqueRepository).rechercher(any(), any(), any(), any(), any(), any(), any(),
                    captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
            assertThat(captor.getValue().getSort()).isEqualTo(
                    Sort.by(Sort.Direction.ASC, "dateChangement")
                            .and(Sort.by(Sort.Direction.ASC, "idHistorique")));
        }

        @Test
        @DisplayName("la taille de page est plafonnée (lecture seule : 100 lignes maximum)")
        void taillePlafonnee() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(),
                    any(Pageable.class))).thenReturn(Page.empty());

            service.lister(new HistoriqueModificationService.FiltreHistorique(
                    null, null, null, null, null, null, null, false), -5, 999, false);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(historiqueRepository).rechercher(any(), any(), any(), any(), any(), any(), any(),
                    captor.capture());
            assertThat(captor.getValue().getPageSize())
                    .isEqualTo(HistoriqueModificationService.TAILLE_MAXIMALE);
            assertThat(captor.getValue().getPageNumber()).isZero();
        }

        @Test
        @DisplayName("la page renvoie le total hors pagination (bouton « Charger plus »)")
        void totalHorsPagination() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(),
                    any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(ligne(false)),
                            PageRequest.of(0, 25), 42));

            var resultat = service.lister(new HistoriqueModificationService.FiltreHistorique(
                    null, null, null, null, null, null, null, false), 0, 25, false);

            assertThat(resultat.total()).isEqualTo(42);
            assertThat(resultat.lignes()).hasSize(1);
            assertThat(resultat.lignes().get(0).initiative().code()).isEqualTo("DIG-CLI-001");
            assertThat(resultat.lignes().get(0).automatique()).isFalse();
        }

        @Test
        @DisplayName("l'export part sur la MÊME fenêtre que la lecture, sans pagination")
        void exportSansPagination() {
            when(historiqueRepository.exporter(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(ligne(true), ligne(false)));

            List<HistoriqueModificationResponse> lignes =
                    service.exporter(new HistoriqueModificationService.FiltreHistorique(
                            null, "initiative", null, null, null, null, null, false));

            assertThat(lignes).hasSize(2);
            ArgumentCaptor<LocalDateTime> debut = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(historiqueRepository).exporter(
                    isNull(), eq("initiative"), isNull(), isNull(), isNull(),
                    debut.capture(), isNull());
            LocalDate attendu = LocalDateTime.now().minusMonths(12).toLocalDate();
            assertThat(debut.getValue().toLocalDate()).isBetween(
                    attendu.minusDays(1), attendu.plusDays(1));
        }

        @Test
        @DisplayName("la liste des auteurs propose les seuls comptes présents dans le journal")
        void listeAuteurs() {
            when(historiqueRepository.trouverAuteurs()).thenReturn(List.of(auteur));

            var auteurs = service.auteurs();

            assertThat(auteurs).hasSize(1);
            assertThat(auteurs.get(0).idUser()).isEqualTo(7L);
            assertThat(auteurs.get(0).nomComplet()).isEqualTo("Marie Dupont");
        }
    }

    // ------------------------------------------------------------------
    // Fil de la fiche + activité récente du dashboard
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Fil de la fiche et activité récente")
    class FilEtDashboard {

        @Test
        @DisplayName("le fil d'une initiative est paginé et trié récent → ancien")
        void filPagine() {
            when(historiqueRepository.filInitiative(eq(1L), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(ligne(false)), PageRequest.of(0, 20), 42));

            var resultat = service.historiqueInitiative(1L, null, 0, 20);

            assertThat(resultat.total()).isEqualTo(42);
            assertThat(resultat.lignes()).hasSize(1);
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(historiqueRepository).filInitiative(eq(1L), isNull(), captor.capture());
            assertThat(captor.getValue().getSort().getOrderFor("dateChangement").isDescending())
                    .isTrue();
        }

        @Test
        @DisplayName("un type de filtre hors fiche (demande, utilisateur…) est refusé en 400")
        void typeFiltreInvalide() {
            assertThatThrownBy(() -> service.historiqueInitiative(1L, "utilisateur", 0, 20))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("utilisateur");
        }

        @Test
        @DisplayName("l'activité récente plafonne aux dernières lignes de la vue active")
        void activiteRecentePlafonnee() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(),
                    any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(ligne(false)), PageRequest.of(0, 10), 500));

            var lignes = service.activiteRecente();

            assertThat(lignes).hasSize(1);
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(historiqueRepository).rechercher(any(), any(), any(), any(), any(), any(), any(),
                    captor.capture());
            assertThat(captor.getValue().getPageSize())
                    .isEqualTo(HistoriqueModificationService.ACTIVITE_RECENTE);
            assertThat(captor.getValue().getPageNumber()).isZero();
        }
    }

    // ------------------------------------------------------------------
    // Immuabilité : aucune suppression n'existe
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Immuabilité — ni suppression de ligne, ni purge")
    class Immuabilite {

        @Test
        @DisplayName("le service n'expose AUCUNE méthode de suppression ou de purge")
        void aucuneMethodeDeDestruction() {
            List<String> noms = Arrays.stream(
                            HistoriqueModificationService.class.getDeclaredMethods())
                    .map(Method::getName)
                    .toList();

            assertThat(noms)
                    .noneMatch(nom -> nom.equals("supprimer")
                            || nom.equals("purgerTout")
                            || nom.startsWith("delete")
                            || nom.startsWith("purge"));
        }

        @Test
        @DisplayName("une lecture n'entraîne jamais d'appel destructeur sur la base")
        void lectureJamaisDestructrice() {
            when(historiqueRepository.rechercher(any(), any(), any(), any(), any(), any(), any(),
                    any(Pageable.class))).thenReturn(Page.empty());

            service.lister(new HistoriqueModificationService.FiltreHistorique(
                    null, null, null, null, null, null, null, false), 0, 25, false);
            service.exporter(new HistoriqueModificationService.FiltreHistorique(
                    null, null, null, null, null, null, null, false));

            verify(historiqueRepository, never()).delete(any(HistoriqueModification.class));
            verify(historiqueRepository, never()).deleteAllInBatch();
            verify(historiqueRepository, never()).deleteAll();
        }
    }
}
