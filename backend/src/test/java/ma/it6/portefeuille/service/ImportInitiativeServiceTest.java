package ma.it6.portefeuille.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.dto.ImportResultatDTO;
import ma.it6.portefeuille.entity.Entite;
import ma.it6.portefeuille.entity.Organisation;
import ma.it6.portefeuille.entity.Plan;
import ma.it6.portefeuille.entity.Priorite;
import ma.it6.portefeuille.entity.StatutInitiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.EntiteRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.ObjectifStrategiqueRepository;
import ma.it6.portefeuille.repository.OrganisationRepository;
import ma.it6.portefeuille.repository.PlanRepository;
import ma.it6.portefeuille.repository.PrioriteRepository;
import ma.it6.portefeuille.repository.StatutInitiativeRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.security.OrganisationContext;

/**
 * Tests du service d'import d'initiatives (4B).
 * Les repositories sont mockes ; OrganisationContext est positionne
 * manuellement pour simuler le contexte de requete.
 */
class ImportInitiativeServiceTest {

    private InitiativeRepository initiativeRepository;
    private OrganisationRepository organisationRepository;
    private PlanRepository planRepository;
    private UtilisateurRepository utilisateurRepository;
    private EntiteRepository entiteRepository;
    private StatutInitiativeRepository statutRepository;
    private PrioriteRepository prioriteRepository;
    private ObjectifStrategiqueRepository objectifRepository;

    private ImportInitiativeService service;

    @BeforeEach
    void setup() {
        OrganisationContext.setId(1L);

        initiativeRepository = mock(InitiativeRepository.class);
        organisationRepository = mock(OrganisationRepository.class);
        planRepository = mock(PlanRepository.class);
        utilisateurRepository = mock(UtilisateurRepository.class);
        entiteRepository = mock(EntiteRepository.class);
        statutRepository = mock(StatutInitiativeRepository.class);
        prioriteRepository = mock(PrioriteRepository.class);
        objectifRepository = mock(ObjectifStrategiqueRepository.class);

        service = new ImportInitiativeService(
                initiativeRepository, organisationRepository, planRepository,
                utilisateurRepository, entiteRepository, statutRepository,
                prioriteRepository, objectifRepository, "10MB");

        Organisation org = new Organisation();
        org.setIdOrganisation(1L);
        org.setSlug("it6");
        org.setPlan("gratuit");
        when(organisationRepository.findById(1L)).thenReturn(Optional.of(org));

        Plan plan = new Plan();
        plan.setCode("gratuit");
        plan.setNom("Gratuit");
        plan.setNbInitiativesMax(3);
        when(planRepository.findByCode("gratuit")).thenReturn(plan);

        when(initiativeRepository.countByIdOrganisationAndArchiveFalse(1L)).thenReturn(0L);
        when(initiativeRepository.existsByCode(anyString())).thenReturn(false);

        Utilisateur admin = new Utilisateur();
        admin.setIdUser(1L);
        admin.setEmail("admin@it6.ma");
        admin.setNom("Admin");
        admin.setPrenom("IT6");
        admin.setActif(true);
        when(utilisateurRepository.findAll()).thenReturn(List.of(admin));

        StatutInitiative aPlanifier = new StatutInitiative();
        aPlanifier.setIdStatut(1L);
        aPlanifier.setLibelle("A planifier");
        when(statutRepository.findAll()).thenReturn(List.of(aPlanifier));

        Priorite normale = new Priorite();
        normale.setIdPriorite(1L);
        normale.setLibelle("Normale");
        when(prioriteRepository.findAll()).thenReturn(List.of(normale));

        Entite direction = new Entite();
        direction.setIdEntite(2L);
        direction.setNom("Direction Digitale");
        when(entiteRepository.findAll()).thenReturn(List.of(direction));
        when(objectifRepository.findAll()).thenReturn(List.of());
    }

    // ------------------------------------------------------------------
    // CSV valide, mode strict
    // ------------------------------------------------------------------

    @Test
    void importer_csv_valide_mode_strict() {
        String csv = """
                code;nom;description;dateDebut;dateFinPrevue;budget;responsable;statut;priorite;entite;objectif
                PROJ-001;Mon premier projet;Description du projet;2026-01-01;2026-12-31;1500.00;admin@it6.ma;A planifier;Normale;Direction Digitale;;
                """;
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "test.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportResultatDTO resultat = service.importer(fichier, false);

        assertThat(resultat.getMode()).isEqualTo("strict");
        assertThat(resultat.getTotalLignes()).isEqualTo(1);
        assertThat(resultat.getLignesImportees()).isEqualTo(1);
        assertThat(resultat.getLignesIgnorees()).isEqualTo(0);
        assertThat(resultat.getErreurs()).isEmpty();

        verify(initiativeRepository).save(org.mockito.ArgumentMatchers.any());
    }

    // ------------------------------------------------------------------
    // Code duplique dans le fichier, mode strict -> rien n'importe
    // ------------------------------------------------------------------

    @Test
    void importer_code_duplique_mode_strict_rien_n_importe() {
        String csv = """
                code;nom;statut;priorite;responsable;entite
                PROJ-001;Projet A;A planifier;Normale;admin@it6.ma;Direction Digitale
                PROJ-001;Projet B;A planifier;Normale;admin@it6.ma;Direction Digitale
                """;
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "test.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportResultatDTO resultat = service.importer(fichier, false);

        assertThat(resultat.getMode()).isEqualTo("strict");
        assertThat(resultat.getTotalLignes()).isEqualTo(2);
        assertThat(resultat.getLignesImportees()).isEqualTo(0); // rien n'importe en strict avec erreur
        assertThat(resultat.getLignesIgnorees()).isEqualTo(1);
        assertThat(resultat.getErreurs()).hasSize(1);
        assertThat(resultat.getErreurs().get(0).getLigne()).isEqualTo(3); // en-tete=1, 2e donnee=3
        assertThat(resultat.getErreurs().get(0).getMessage())
                .contains("duplique");

        // Rien n'a ete persiste
        verify(initiativeRepository, org.mockito.Mockito.times(0))
                .save(org.mockito.ArgumentMatchers.any());
    }

    // ------------------------------------------------------------------
    // Ligne en erreur, mode ignorer -> ligne valide importee, erreur reportee
    // ------------------------------------------------------------------

    @Test
    void importer_ligne_erreur_mode_ignorer() {
        String csv = """
                code;nom;statut;priorite;responsable;entite
                PROJ-001;Projet A;A planifier;Normale;admin@it6.ma;Direction Digitale
                ;Projet sans code;A planifier;Normale;admin@it6.ma;Direction Digitale
                PROJ-002;Projet B;A planifier;Normale;admin@it6.ma;Direction Digitale
                """;
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "test.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportResultatDTO resultat = service.importer(fichier, true);

        assertThat(resultat.getMode()).isEqualTo("ignorer_erreurs");
        assertThat(resultat.getTotalLignes()).isEqualTo(3);
        assertThat(resultat.getLignesImportees()).isEqualTo(2);
        assertThat(resultat.getLignesIgnorees()).isEqualTo(1);
        assertThat(resultat.getErreurs()).hasSize(1);
        assertThat(resultat.getErreurs().get(0).getLigne()).isEqualTo(3);
        assertThat(resultat.getErreurs().get(0).getMessage()).contains("obligatoire");

        // Les deux lignes valides ont ete persistees
        verify(initiativeRepository, org.mockito.Mockito.times(2))
                .save(org.mockito.ArgumentMatchers.any());
    }

    // ------------------------------------------------------------------
    // Limite du plan depassee
    // ------------------------------------------------------------------

    @Test
    void importer_limite_plan_depassee() {
        // Organisation deja a 3 initiatives sur un plan max 3
        when(initiativeRepository.countByIdOrganisationAndArchiveFalse(1L)).thenReturn(3L);

        String csv = """
                code;nom;statut;priorite;responsable;entite
                PROJ-004;Projet en trop;A planifier;Normale;admin@it6.ma;Direction Digitale
                """;
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "test.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.importer(fichier, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Limite du plan");

        // Rien n'a ete persiste
        verify(initiativeRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any());
    }

    // ------------------------------------------------------------------
    // Colonne requise manquante
    // ------------------------------------------------------------------

    @Test
    void importer_colonne_requise_manquante() {
        String csv = """
                code;nom;statut
                PROJ-001;Mon projet;A planifier
                """;
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "test.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.importer(fichier, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("priorite");
    }

    // ------------------------------------------------------------------
    // Modèle CSV téléchargeable
    // ------------------------------------------------------------------

    @Test
    void modele_csv_entetes_et_ligne_exemple() {
        byte[] modele = service.modele();

        // BOM UTF-8 en tête (convention des exports : Excel ouvre sans « ANSI »)
        assertThat(modele[0]).isEqualTo((byte) 0xEF);
        assertThat(modele[1]).isEqualTo((byte) 0xBB);
        assertThat(modele[2]).isEqualTo((byte) 0xBF);

        String texte = new String(modele, StandardCharsets.UTF_8).replace("\uFEFF", "");
        String[] lignes = texte.split("\r\n|\n");

        // Ligne 1 : colonnes requises (entite comprise, NOT NULL en base)
        // puis optionnelles, séparateur « ; »
        assertThat(lignes[0]).startsWith("code;nom;statut;priorite;responsable;entite");
        assertThat(lignes[0])
                .contains("description;dateDebut;dateFinPrevue;budget;objectif");

        // Une seule ligne d'exemple, sur les 11 colonnes déclarées
        assertThat(lignes).hasSize(2);
        assertThat(lignes[1]).startsWith("EXEMPLE-001;");
        assertThat(lignes[1].split(";", -1)).hasSize(11);
    }

    @AfterEach
    void teardown() {
        OrganisationContext.clear();
    }
}
