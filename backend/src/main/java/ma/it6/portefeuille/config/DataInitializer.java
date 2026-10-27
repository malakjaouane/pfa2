package ma.it6.portefeuille.config;

import ma.it6.portefeuille.entity.*;
import ma.it6.portefeuille.repository.*;
import ma.it6.portefeuille.service.CalculeurAvancement;
import ma.it6.portefeuille.service.HistoriqueModificationService;
import ma.it6.portefeuille.service.InitiativeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Initialiseur de données de démonstration :
 *  - trois comptes de test (un par profil) avec mots de passe hashés BCrypt ;
 *  - un petit portefeuille d'initiatives (avec étapes et jalons) afin de
 *    visualiser le dashboard et les KPI dès le premier lancement.
 * Seules les tables vides sont alimentées (idempotent).
 *
 * <p>Le compte administrateur est paramétré par environnement
 * ({@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}, cf. {@code app.admin} dans
 * application.yml) : aucun mot de passe n'est codé en dur dans le code
 * source. Les valeurs par défaut ne servent qu'au lancement local.</p>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    public static final String RESPONSABLE_PASSWORD = "Resp@123";
    public static final String VISITEUR_PASSWORD = "Visi@123";

    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final EntiteRepository entiteRepository;
    private final InitiativeRepository initiativeRepository;
    private final StatutInitiativeRepository statutInitiativeRepository;
    private final PrioriteRepository prioriteRepository;
    private final EtapeRepository etapeRepository;
    private final StatutEtapeRepository statutEtapeRepository;
    private final JalonRepository jalonRepository;
    private final StatutJalonRepository statutJalonRepository;
    private final ObjectifStrategiqueRepository objectifRepository;
    private final HistoriqueModificationService historiqueService;
    private final PasswordEncoder passwordEncoder;
    private final String emailAdmin;
    private final String motDePasseAdmin;

    public DataInitializer(UtilisateurRepository utilisateurRepository,
                           RoleRepository roleRepository,
                           EntiteRepository entiteRepository,
                           InitiativeRepository initiativeRepository,
                           StatutInitiativeRepository statutInitiativeRepository,
                           PrioriteRepository prioriteRepository,
                           EtapeRepository etapeRepository,
                           StatutEtapeRepository statutEtapeRepository,
                           JalonRepository jalonRepository,
                           StatutJalonRepository statutJalonRepository,
                           ObjectifStrategiqueRepository objectifRepository,
                           HistoriqueModificationService historiqueService,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.email}") String emailAdmin,
                           @Value("${app.admin.password}") String motDePasseAdmin) {
        this.utilisateurRepository = utilisateurRepository;
        this.roleRepository = roleRepository;
        this.entiteRepository = entiteRepository;
        this.initiativeRepository = initiativeRepository;
        this.statutInitiativeRepository = statutInitiativeRepository;
        this.prioriteRepository = prioriteRepository;
        this.etapeRepository = etapeRepository;
        this.statutEtapeRepository = statutEtapeRepository;
        this.jalonRepository = jalonRepository;
        this.statutJalonRepository = statutJalonRepository;
        this.objectifRepository = objectifRepository;
        this.historiqueService = historiqueService;
        this.passwordEncoder = passwordEncoder;
        this.emailAdmin = emailAdmin;
        this.motDePasseAdmin = motDePasseAdmin;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (utilisateurRepository.count() == 0) {
            creerUtilisateurs();
        }
        if (initiativeRepository.count() == 0) {
            creerInitiatives();
        }
        alignerAvancements();
        alignerHistorique();
    }

    /**
     * Journal d'une initiative créée <em>avant</em> l'ouverture du journal.
     *
     * <p>Le seed décoré de {@link #creerInitiatives()} ne tourne que sur une
     * base vierge. Sur une base déjà alimentée — le cas de toute installation
     * existante — le journal serait donc vide, et un pilote conclurait à tort
     * que la fonctionnalité est cassée. On dépose alors une unique entrée
     * « création » pour chaque initiative encore non tracée.</p>
     *
     * <p>La date portée est celle de l'exécution, et non celle de la création
     * réelle : on refuse d'inventer une date. Le commentaire le dit
     * explicitement, pour que personne ne l'interprète autrement.</p>
     *
     * <p>Idempotent : une initiative déjà tracée n'est jamais retouchée.</p>
     */
    private void alignerHistorique() {
        for (Initiative initiative : initiativeRepository.findAll()) {
            // Une seule ligne suffit pour savoir si l'initiative est tracée
            // (le fil complet n'est pas chargé pour un simple garde-fou).
            if (historiqueService.historiqueInitiative(
                    initiative.getIdInitiative(), null, 0, 1).total() > 0) {
                continue;
            }
            String statut = initiative.getStatut() == null ? null : initiative.getStatut().getLibelle();
            historiqueService.enregistrer(
                    initiative,
                    null,
                    statut == null ? "Non défini" : statut,
                    "Initiative antérieure à l'ouverture du journal : la date portée est celle "
                            + "de la mise en service du journal, pas celle de la création réelle.",
                    null,
                    true);
        }
    }

    /**
     * L'avancement d'une initiative est piloté par ses étapes : à chaque
     * démarrage, on aligne les valeurs afin qu'une base créée avant cette
     * règle ne conserve pas un pourcentage saisi à la main.
     *
     * <p>L'invariant RM07 « 100 % ⇒ Terminée » est également réappliqué ici.
     * Une base héritée — créée avant le passage automatique — peut contenir
     * une initiative bloquée à 100 % sous un statut actif (« À planifier »,
     * « En cours », « En pause ») : le démarrage la replace à « Terminée »
     * et en laisse la trace au journal des statuts.</p>
     *
     * <p>La règle RM18 (l'avancement d'une étape découle de son statut :
     * « À faire » = 0 %, « Terminée » = 100 %) est d'abord réappliquée aux
     * étapes héritées, afin que l'affichage et le calcul partagent toujours
     * les mêmes valeurs.</p>
     */
    private void alignerAvancements() {
        for (Initiative initiative : initiativeRepository.findAll()) {
            if (Boolean.TRUE.equals(initiative.getArchive())) {
                continue;
            }
            String statut = initiative.getStatut() == null ? null : initiative.getStatut().getLibelle();
            if (InitiativeService.STATUT_ANNULEE.equals(statut)) {
                continue;
            }

            // RM18 : une étape « À faire » héritée à 100 % (ou « Terminée » à
            // 0 %) est remise à la valeur imposée par son statut.
            List<Etape> etapes = etapeRepository
                    .findByInitiativeIdInitiativeOrderByOrdreAsc(initiative.getIdInitiative());
            for (Etape etape : etapes) {
                Integer coherente = avancementCoherent(etape);
                if (coherente != null
                        && (etape.getAvancement() == null || !coherente.equals(etape.getAvancement()))) {
                    etape.setAvancement(coherente);
                    etapeRepository.save(etape);
                }
            }

            int calcule = CalculeurAvancement.calculer(etapes);
            int attendu;
            if (calcule >= 0) {
                // RM07 : une initiative « Terminée » affiche toujours 100 %.
                attendu = InitiativeService.STATUT_TERMINEE.equals(statut) ? 100 : calcule;
                if (initiative.getAvancement() == null || initiative.getAvancement() != attendu) {
                    initiative.setAvancement(attendu);
                    initiativeRepository.save(initiative);
                }
            } else {
                // Aucune étape : on conserve la valeur saisie — sauf pour une
                // « Terminée », que la règle RM07 maintient à 100 %.
                attendu = InitiativeService.STATUT_TERMINEE.equals(statut)
                        ? 100
                        : (initiative.getAvancement() == null ? 0 : initiative.getAvancement());
                if (initiative.getAvancement() == null || initiative.getAvancement() != attendu) {
                    initiative.setAvancement(attendu);
                    initiativeRepository.save(initiative);
                }
            }

            // RM07-auto : 100 % atteint et statut encore actif -> « Terminée ».
            if (attendu == 100 && !InitiativeService.STATUT_TERMINEE.equals(statut)) {
                statutInitiativeRepository.findByLibelle(InitiativeService.STATUT_TERMINEE)
                        .ifPresent(terminee -> {
                            initiative.setStatut(terminee);
                            initiativeRepository.save(initiative);
                            historiqueService.enregistrer(
                                    initiative, statut, InitiativeService.STATUT_TERMINEE,
                                    "Passage automatique : avancement atteint 100 % "
                                            + "(RM07, alignement au démarrage)",
                                    null, true);
                        });
            }

            // 0 % piloté par les étapes : rien n'a commencé -> « À planifier ».
            // Un « En cours » resté sans progression (donnée héritée) est
            // ramené à l'état réel du travail, avec trace au journal.
            if (calcule >= 0 && attendu == 0
                    && InitiativeService.STATUT_EN_COURS.equals(statut)) {
                statutInitiativeRepository.findByLibelle(InitiativeService.STATUT_A_PLANIFIER)
                        .ifPresent(aPlanifier -> {
                            initiative.setStatut(aPlanifier);
                            initiativeRepository.save(initiative);
                            historiqueService.enregistrer(
                                    initiative, statut, InitiativeService.STATUT_A_PLANIFIER,
                                    "Retour automatique : avancement à 0 %, "
                                            + "l'initiative n'a pas commencé "
                                            + "(alignement au démarrage)",
                                    null, true);
                        });
            }
        }
    }

    /**
     * RM18 : valeur d'avancement imposée par le statut d'une étape
     * (« Terminée » 100 %, « À faire » 0 %, « En cours » à 0 % → 50 % car
     * un champ non renseigné) ; {@code null} sinon (valeur saisie
     * conservée pour « En cours » et statuts inconnus).
     */
    private static Integer avancementCoherent(Etape etape) {
        String statut = etape.getStatutEtape() == null ? null : etape.getStatutEtape().getLibelle();
        if (CalculeurAvancement.ETAPE_TERMINEE.equals(statut)) {
            return 100;
        }
        if (CalculeurAvancement.ETAPE_A_FAIRE.equals(statut)) {
            return 0;
        }
        if (CalculeurAvancement.ETAPE_EN_COURS.equals(statut)
                && (etape.getAvancement() == null || etape.getAvancement() == 0)) {
            return 50;
        }
        return null;
    }

    private void creerUtilisateurs() {
        Role admin = roleRepository.findByCode("ADMIN").orElseThrow();
        Role responsable = roleRepository.findByCode("RESPONSABLE").orElseThrow();
        Role visiteur = roleRepository.findByCode("VISITEUR").orElseThrow();

        Entite it6 = entiteRepository.findById(1L).orElse(null);
        Entite dv = entiteRepository.findById(2L).orElse(null);
        Entite si = entiteRepository.findById(3L).orElse(null);

        utilisateurRepository.save(Utilisateur.builder()
                .nom("Administrateur").prenom("Système").email(emailAdmin)
                .motDePasse(passwordEncoder.encode(motDePasseAdmin))
                .telephone("0611111111").actif(true).role(admin).entite(it6).build());

        utilisateurRepository.save(Utilisateur.builder()
                .nom("Alaoui").prenom("Karim").email("responsable@it6.ma")
                .motDePasse(passwordEncoder.encode(RESPONSABLE_PASSWORD))
                .telephone("0622222222").actif(true).role(responsable).entite(dv).build());

        utilisateurRepository.save(Utilisateur.builder()
                .nom("Benali").prenom("Salma").email("visiteur@it6.ma")
                .motDePasse(passwordEncoder.encode(VISITEUR_PASSWORD))
                .telephone("0633333333").actif(true).role(visiteur).entite(si).build());
    }

    private void creerInitiatives() {
        StatutInitiative aPlanifier = statutInitiativeRepository.findByLibelle("À planifier").orElseThrow();
        StatutInitiative enCours = statutInitiativeRepository.findByLibelle("En cours").orElseThrow();
        StatutInitiative enPause = statutInitiativeRepository.findByLibelle("En pause").orElseThrow();
        StatutInitiative terminee = statutInitiativeRepository.findByLibelle("Terminée").orElseThrow();

        Priorite basse = prioriteRepository.findById(1L).orElseThrow();
        Priorite moyenne = prioriteRepository.findById(2L).orElseThrow();
        Priorite haute = prioriteRepository.findById(3L).orElseThrow();
        Priorite critique = prioriteRepository.findById(4L).orElseThrow();

        Utilisateur responsable = utilisateurRepository.findByEmail("responsable@it6.ma").orElse(null);
        Utilisateur admin = utilisateurRepository.findByEmail(emailAdmin).orElse(null);
        Entite it6 = entiteRepository.findById(1L).orElse(null);
        Entite dv = entiteRepository.findById(2L).orElse(null);
        Entite si = entiteRepository.findById(3L).orElse(null);

        // L'alignement initiative -> objectif est énoncé ICI, au moment de la
        // création. Les UPDATE équivalents de data.sql s'exécutent avant que
        // cette méthode n'ait inséré la moindre initiative : sur un premier
        // lancement ils ne touchent rien, et il faut un deuxième démarrage
        // pour les voir prendre effet. Une donnée de démonstration doit être
        // cohérente dès la première ouverture, sinon on passe la soutenance à
        // expliquer un écran vide.
        ObjectifStrategique objectifExperience = objectifRepository.findById(1L).orElse(null);
        ObjectifStrategique objectifSystemeInformation = objectifRepository.findById(2L).orElse(null);
        ObjectifStrategique objectifCloud = objectifRepository.findById(3L).orElse(null);

        // 1. Initiative en cours avec étapes et jalons
        Initiative digitalisation = Initiative.builder()
                .code("DIG-CLI-001").nom("Digitalisation des services clients")
                .description("Déploiement d'un portail client en ligne pour IT6 Consulting.")
                .dateDebut(LocalDate.of(2026, 1, 15)).dateFinPrevue(LocalDate.of(2026, 12, 31))
                .budget(new BigDecimal("450000.00")).avancement(60).archive(false)
                .entite(it6).responsable(responsable).statut(enCours).priorite(haute)
                .objectif(objectifExperience)
                .build();
        digitalisation = initiativeRepository.save(digitalisation);

        etapeRepository.save(Etape.builder().nom("Analyse").description("Recueil du besoin.")
                .ordre(1).dateDebut(LocalDate.of(2026, 1, 15)).dateFinPrevue(LocalDate.of(2026, 2, 28))
                .avancement(100).initiative(digitalisation).statutEtape(statutEtapeRepository.findById(3L).orElseThrow())
                .build());
        etapeRepository.save(Etape.builder().nom("Conception").description("Maquettes et architecture.")
                .ordre(2).dateDebut(LocalDate.of(2026, 3, 1)).dateFinPrevue(LocalDate.of(2026, 4, 30))
                .avancement(100).initiative(digitalisation).statutEtape(statutEtapeRepository.findById(3L).orElseThrow())
                .build());
        etapeRepository.save(Etape.builder().nom("Développement").description("Implémentation des modules.")
                .ordre(3).dateDebut(LocalDate.of(2026, 5, 1)).dateFinPrevue(LocalDate.of(2026, 9, 30))
                .avancement(40).initiative(digitalisation).statutEtape(statutEtapeRepository.findById(2L).orElseThrow())
                .build());
        etapeRepository.save(Etape.builder().nom("Tests").description("Tests fonctionnels et recette.")
                .ordre(4).dateDebut(LocalDate.of(2026, 10, 1)).dateFinPrevue(LocalDate.of(2026, 11, 30))
                .avancement(0).initiative(digitalisation).statutEtape(statutEtapeRepository.findById(1L).orElseThrow())
                .build());
        etapeRepository.save(Etape.builder().nom("Déploiement").description("Mise en production.")
                .ordre(5).dateDebut(LocalDate.of(2026, 12, 1)).dateFinPrevue(LocalDate.of(2026, 12, 31))
                .avancement(0).initiative(digitalisation).statutEtape(statutEtapeRepository.findById(1L).orElseThrow())
                .build());

        // L'avancement est piloté par les étapes : on aligne l'initiative de
        // démonstration sur le calcul (sinon elle afficherait 60 % alors que
        // ses étapes donnent autre chose).
        List<Etape> etapesInitiative = etapeRepository
                .findByInitiativeIdInitiativeOrderByOrdreAsc(digitalisation.getIdInitiative());
        int avancementCalcule = CalculeurAvancement.calculer(etapesInitiative);
        if (avancementCalcule >= 0) {
            digitalisation.setAvancement(avancementCalcule);
            initiativeRepository.save(digitalisation);
        }

        jalonRepository.save(Jalon.builder().nom("Validation du cahier des charges")
                .description("Document validé par l'encadrant.")
                .datePrevue(LocalDate.of(2026, 2, 28)).dateRealisation(LocalDate.of(2026, 2, 25))
                .initiative(digitalisation).statutJalon(statutJalonRepository.findById(2L).orElseThrow())
                .build());
        jalonRepository.save(Jalon.builder().nom("Recette finale")
                .description("Fin des tests et acceptation du livrable.")
                .datePrevue(LocalDate.of(2026, 11, 30)).dateRealisation(null)
                .initiative(digitalisation).statutJalon(statutJalonRepository.findById(1L).orElseThrow())
                .build());

        // 2. Initiative terminée
        Initiative portailRh = Initiative.builder()
                .code("RH-PORT-002").nom("Refonte du portail RH")
                .description("Modernisation de l'intranet RH interne.")
                .dateDebut(LocalDate.of(2025, 3, 1)).dateFinPrevue(LocalDate.of(2025, 9, 30))
                .budget(new BigDecimal("250000.00")).avancement(100).archive(false)
                .entite(si).responsable(admin).statut(terminee).priorite(moyenne)
                .objectif(objectifSystemeInformation)
                .build();
        initiativeRepository.save(portailRh);

        // 3. Initiative en pause et potentiellement en retard (échéance dépassée)
        Initiative mobile = Initiative.builder()
                .code("MOB-SUI-003").nom("Application mobile de suivi des missions")
                .description("Application mobile pour les consultants terrain.")
                .dateDebut(LocalDate.of(2026, 3, 1)).dateFinPrevue(LocalDate.of(2026, 6, 15))
                .budget(new BigDecimal("320000.00")).avancement(35).archive(false)
                .entite(dv).responsable(null).statut(enPause).priorite(critique)
                .build();
        initiativeRepository.save(mobile);

        // 4. Initiative à planifier
        Initiative cloud = Initiative.builder()
                .code("CLOUDMIG-004").nom("Migration vers le cloud")
                .description("Étude de la migration des serveurs vers le cloud.")
                .dateDebut(null).dateFinPrevue(null)
                .budget(BigDecimal.ZERO).avancement(0).archive(false)
                .entite(si).responsable(null).statut(aPlanifier).priorite(basse)
                // La 3e initiative (MOB-SUI-003) reste volontairement sans
                // objectif : c'est le cas « Non rattaché » de la répartition.
                .objectif(objectifCloud)
                .build();
        initiativeRepository.save(cloud);

        // Journal des transitions : on donne au démonstrateur un historique
        // crédible. Un journal vide au premier affichage laisserait croire que
        // la fonctionnalité est cassée, alors qu'elle n'a simplement jamais été
        // exercée. Le même gardien d'idempotence que les initiatives protège ce
        // bloc : il ne tourne que lorsque la table initiative était vide.
        String auteur = "admin@it6.ma";

        historiqueService.enregistrer(digitalisation, null, "À planifier",
                "Création de l'initiative", auteur);
        historiqueService.enregistrer(digitalisation, "À planifier", "En cours",
                "Cadrage validé, ouverture du chantier", auteur);

        historiqueService.enregistrer(portailRh, null, "À planifier",
                "Création de l'initiative", auteur);
        historiqueService.enregistrer(portailRh, "À planifier", "En cours",
                "Démarrage des travaux", auteur);
        historiqueService.enregistrer(portailRh, "En cours", "Terminée",
                "Recette signée par les RH", auteur);

        historiqueService.enregistrer(mobile, null, "À planifier",
                "Création de l'initiative", auteur);
        historiqueService.enregistrer(mobile, "À planifier", "En cours",
                "Démarrage des travaux", auteur);
        historiqueService.enregistrer(mobile, "En cours", "En pause",
                "En attente de la mise à disposition des terminaux terrain", auteur);

        historiqueService.enregistrer(cloud, null, "À planifier",
                "Création de l'initiative", auteur);
    }
}