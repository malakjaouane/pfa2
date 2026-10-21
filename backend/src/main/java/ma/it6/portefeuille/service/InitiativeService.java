package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.ChangerStatutRequest;
import ma.it6.portefeuille.dto.InitiativeFilterRequest;
import ma.it6.portefeuille.dto.InitiativeRequest;
import ma.it6.portefeuille.dto.InitiativeResponse;
import ma.it6.portefeuille.dto.SanteDto;
import ma.it6.portefeuille.entity.*;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.InitiativeMapper;
import ma.it6.portefeuille.repository.*;
import ma.it6.portefeuille.specification.InitiativeSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Services métier des initiatives : création, consultation, modification,
 * archivage, cycle de vie des statuts et règles métier RM01 à RM16.
 */
@Service
public class InitiativeService {

    /** Statuts métier (référentiel) — l'archivage n'est pas un statut (RM14). */
    public static final String STATUT_A_PLANIFIER = "À planifier";
    public static final String STATUT_EN_COURS = "En cours";
    public static final String STATUT_EN_PAUSE = "En pause";
    public static final String STATUT_TERMINEE = "Terminée";
    public static final String STATUT_ANNULEE = "Annulée";

    private final InitiativeRepository initiativeRepository;
    private final StatutInitiativeRepository statutInitiativeRepository;
    private final PrioriteRepository prioriteRepository;
    private final EntiteRepository entiteRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ObjectifStrategiqueRepository objectifRepository;
    private final EtapeRepository etapeRepository;
    private final HistoriqueStatutService historiqueStatutService;
    private final CalculeurSante calculeurSante;

    public InitiativeService(InitiativeRepository initiativeRepository,
                             StatutInitiativeRepository statutInitiativeRepository,
                             PrioriteRepository prioriteRepository,
                             EntiteRepository entiteRepository,
                             UtilisateurRepository utilisateurRepository,
                             ObjectifStrategiqueRepository objectifRepository,
                             EtapeRepository etapeRepository,
                             HistoriqueStatutService historiqueStatutService,
                             CalculeurSante calculeurSante) {
        this.initiativeRepository = initiativeRepository;
        this.statutInitiativeRepository = statutInitiativeRepository;
        this.prioriteRepository = prioriteRepository;
        this.entiteRepository = entiteRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.objectifRepository = objectifRepository;
        this.etapeRepository = etapeRepository;
        this.historiqueStatutService = historiqueStatutService;
        this.calculeurSante = calculeurSante;
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    /**
     * Champs sur lesquels l'API accepte de trier. Liste <em>blanche</em> et
     * non noire : un champ non listé est refusé avec un 400 explicite, plutôt
     * que d'être discrètement ignoré ou — pire — concaténé dans une requête.
     */
    private static final Map<String, String> TRIS_AUTORISES = triAutorises();

    /** Taille d'une page quand le client ne la précise pas. */
    public static final int TAILLE_PAR_DEFAUT = 20;
    /** Plafond de taille : évite qu'un client ne demande un million de lignes. */
    public static final int TAILLE_MAXIMALE = 200;

    private static Map<String, String> triAutorises() {
        Map<String, String> tris = new LinkedHashMap<>();
        tris.put("idInitiative", "idInitiative");
        tris.put("code", "code");
        tris.put("nom", "nom");
        tris.put("dateDebut", "dateDebut");
        tris.put("dateFinPrevue", "dateFinPrevue");
        tris.put("budget", "budget");
        tris.put("avancement", "avancement");
        return Collections.unmodifiableMap(tris);
    }

    @Transactional(readOnly = true)
    public List<InitiativeResponse> lister(InitiativeFilterRequest filtre) {
        return lister(filtre, null, null, null, null);
    }

    /**
     * Liste filtrée, triée et <strong>paginée côté serveur</strong>.
     *
     * <p>Sans {@code page} ni {@code size}, la liste complète est renvoyée :
     * le comportement d'origine est préservé, et les écrans à faible volume
     * (Kanban, dashboard) n'ont pas à se préoccuper de pagination.</p>
     *
     * @return la page courante, dans l'ordre demandé
     */
    @Transactional(readOnly = true)
    public List<InitiativeResponse> lister(InitiativeFilterRequest filtre,
                                           Integer page,
                                           Integer size,
                                           String tri,
                                           String direction) {
        Sort sort = construireTri(tri, direction);

        if (page == null && size == null) {
            return versReponsesAvecSante(initiativeRepository
                    .findAll(InitiativeSpecifications.filtrer(filtre), sort)
                    .stream()
                    .toList());
        }

        int numeroPage = Math.max(0, page == null ? 0 : page);
        int taille = (size == null || size <= 0) ? TAILLE_PAR_DEFAUT : Math.min(size, TAILLE_MAXIMALE);
        Pageable pageable = PageRequest.of(numeroPage, taille, sort);

        return versReponsesAvecSante(initiativeRepository
                .findAll(InitiativeSpecifications.filtrer(filtre), pageable)
                .stream()
                .toList());
    }

    /**
     * DTOs d'une liste d'initiatives, enrichis du score de santé calculé en
     * lot (une seule requête de jalons pour toute la page).
     */
    private List<InitiativeResponse> versReponsesAvecSante(List<Initiative> initiatives) {
        Map<Long, SanteDto> santes = calculeurSante.calculerTous(initiatives);
        return initiatives.stream()
                .map(i -> InitiativeMapper.toResponse(i, santes.get(i.getIdInitiative())))
                .toList();
    }

    /** Nombre total d'initiatives pour les filtres donnés (hors pagination). */
    @Transactional(readOnly = true)
    public long compter(InitiativeFilterRequest filtre) {
        return initiativeRepository.count(InitiativeSpecifications.filtrer(filtre));
    }

    private static Sort construireTri(String tri, String direction) {
        if (tri == null || tri.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "idInitiative");
        }
        String propriete = TRIS_AUTORISES.get(tri.trim());
        if (propriete == null) {
            throw new BadRequestException("Champ de tri non autorisé : « " + tri.trim() + " ». "
                    + "Champs acceptés : " + String.join(", ", TRIS_AUTORISES.keySet()) + ".");
        }
        Sort.Direction sens = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(sens, propriete);
    }

    @Transactional(readOnly = true)
    public InitiativeResponse trouver(Long idInitiative) {
        Initiative initiative = getInitiative(idInitiative);
        return InitiativeMapper.toResponse(initiative, calculeurSante.calculer(initiative));
    }

    @Transactional(readOnly = true)
    public List<InitiativeResponse> listerEnRetard() {
        return initiativeRepository.findEnRetard(LocalDate.now()).stream()
                .map(InitiativeMapper::toResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Création
    // ------------------------------------------------------------------

    /**
     * Création d'une initiative.
     * Cycle de vie : « Créer → À planifier ». L'initiative est donc créée
     * avec le statut « À planifier ».
     */
    @Transactional
    public InitiativeResponse creer(InitiativeRequest request) {
        return InitiativeMapper.toResponse(creerEnEntite(request));
    }

    /**
     * Création d'une initiative (retourne l'entité persistée). Utilisée par
     * le workflow des demandes pour transformer une demande acceptée en
     * initiative du portefeuille.
     */
    @Transactional
    public Initiative creerEnEntite(InitiativeRequest request) {
        validerCodeUnique(request.code(), null);
        validerReglesMetier(request);

        // Cycle de vie : l'initiative naît « À planifier » ; créée directement
        // à 100 % d'avancement, elle est d'emblée « Terminée » (RM07). Le
        // statut est donc dérivé par le domaine : la valeur transmise par le
        // client est ignorée (le formulaire de création l'affiche en lecture
        // seule), ce qui évite un rejet incohérent avec le front.
        int avancement = request.avancement() == null ? 0 : request.avancement();
        String statutCreation = avancement == 100 ? STATUT_TERMINEE : STATUT_A_PLANIFIER;

        StatutInitiative statut = statutInitiativeRepository.findByLibelle(statutCreation)
                .orElseThrow(() -> new ResourceNotFoundException("Statut « " + statutCreation + " » introuvable."));
        Priorite priorite = prioriteRepository.findById(request.idPriorite())
                .orElseThrow(() -> new ResourceNotFoundException("Priorité introuvable : " + request.idPriorite()));
        Entite entite = entiteRepository.findById(request.idEntite())
                .orElseThrow(() -> new ResourceNotFoundException("Entité introuvable : " + request.idEntite()));
        Utilisateur responsable = resoudreResponsable(request.idResponsable());

        Initiative initiative = Initiative.builder()
                .code(request.code().trim())
                .nom(request.nom().trim())
                .description(request.description())
                .dateDebut(request.dateDebut())
                .dateFinPrevue(request.dateFinPrevue())
                .budget(request.budget() == null ? BigDecimal.ZERO : request.budget())
                .avancement(avancement)
                .archive(false)
                .entite(entite)
                .responsable(responsable)
                .statut(statut)
                .priorite(priorite)
                .objectif(resoudreObjectif(request.idObjectif()))
                .build();

        Initiative enregistree = initiativeRepository.save(initiative);

        // Journal : première entrée du cycle de vie (ancienStatut = null).
        historiqueStatutService.enregistrer(enregistree, null, statutCreation,
                "Création de l'initiative", utilisateurCourant());

        return enregistree;
    }

    // ------------------------------------------------------------------
    // Modification + cycle de vie
    // ------------------------------------------------------------------

    /**
     * Modification d'une initiative avec contrôle du cycle de vie :
     *   À planifier → En cours
     *   En cours → En pause
     *   En pause → En cours
     *   En cours → Terminée (lorsque l'avancement atteint 100 %)
     *   À planifier / En cours / En pause → Annulée
     *   Terminée / Annulée → archivage (PATCH dédié)
     */
    @Transactional
    public InitiativeResponse modifier(Long idInitiative, InitiativeRequest request) {
        Initiative initiative = getInitiative(idInitiative);
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            throw new BadRequestException("L'initiative est archivée : toute modification est refusée.");
        }

        validerCodeUnique(request.code(), idInitiative);
        validerReglesMetier(request);

        String statutActuel = initiative.getStatut().getLibelle();
        int nouvelAvancement = request.avancement() == null ? initiative.getAvancement() : request.avancement();

        // L'avancement est piloté par les étapes : s'il en existe, la valeur
        // saisie dans le formulaire est remplacée par le calcul
        // (moyenne pondérée par la durée des étapes).
        int avancementCalcule = CalculeurAvancement.calculer(
                etapeRepository.findByInitiativeIdInitiativeOrderByOrdreAsc(idInitiative));
        if (avancementCalcule >= 0) {
            nouvelAvancement = avancementCalcule;
        }

        // Statut absent de la requête : on conserve le statut actuel.
        StatutInitiative statutCible = request.idStatut() == null
                ? initiative.getStatut()
                : statutInitiativeRepository.findById(request.idStatut())
                        .orElseThrow(() -> new ResourceNotFoundException("Statut introuvable : " + request.idStatut()));
        String statutDemande = statutCible.getLibelle();

        // RM07-auto : l'avancement atteint 100 % -> l'initiative passe à « Terminée »,
        // quel que soit l'état de départ (« À planifier », « En cours » ou « En pause »).
        boolean passageAutoTerminee = nouvelAvancement == 100
                && !statutActuel.equals(STATUT_TERMINEE)
                && !statutActuel.equals(STATUT_ANNULEE);
        if (passageAutoTerminee) {
            statutDemande = STATUT_TERMINEE;
        }

        // RM17 (0 % ⇒ « À planifier ») : ici le statut vient d'un CHOIX
        // manuel dans le formulaire — il est respecté tel quel. La règle
        // ne s'applique qu'aux recalculs automatiques pilotés par les
        // étapes (EtapeService) et à l'alignement de démarrage.

        // RM07 : une initiative « Terminée » doit avoir un avancement de 100 %
        if (statutDemande.equals(STATUT_TERMINEE) && nouvelAvancement < 100) {
            throw new BadRequestException("Une initiative « Terminée » doit avoir un avancement de 100 %.");
        }

        // Contrôle de la transition demandée (diagramme d'état). Le passage
        // automatique RM07 est une exception au diagramme : il est accepté
        // depuis n'importe quel état actif.
        verifierTransition(statutActuel, statutDemande, passageAutoTerminee);

        initiative.setCode(request.code().trim());
        initiative.setNom(request.nom().trim());
        initiative.setDescription(request.description());
        initiative.setDateDebut(request.dateDebut());
        initiative.setDateFinPrevue(request.dateFinPrevue());
        initiative.setBudget(request.budget() == null ? initiative.getBudget() : request.budget());
        initiative.setAvancement(nouvelAvancement);
        initiative.setEntite(entiteRepository.findById(request.idEntite())
                .orElseThrow(() -> new ResourceNotFoundException("Entité introuvable : " + request.idEntite())));
        initiative.setResponsable(resoudreResponsable(request.idResponsable()));
        initiative.setPriorite(prioriteRepository.findById(request.idPriorite())
                .orElseThrow(() -> new ResourceNotFoundException("Priorité introuvable : " + request.idPriorite())));
        initiative.setStatut(statutInitiativeRepository.findByLibelle(statutDemande)
                .orElse(statutCible));
        initiative.setObjectif(resoudreObjectif(request.idObjectif()));

        Initiative enregistree = initiativeRepository.save(initiative);

        // Journal : on ne dépose une ligne que si le statut a réellement
        // changé — sinon la fiche se remplirait d'événements « En cours →
        // En cours » à chaque simple retouche d'un budget.
        if (!statutActuel.equals(statutDemande)) {
            // Origine portée par la ligne : un passage forcé par RM07-auto
            // (100 % atteint) est « automatique », une transition choisie au
            // formulaire reste « manuelle ».
            historiqueStatutService.enregistrer(enregistree, statutActuel, statutDemande,
                    passageAutoTerminee
                            ? "Passage automatique : avancement atteint 100 %"
                            : "Modification de l'initiative",
                    utilisateurCourant(), passageAutoTerminee);
        }

        return InitiativeMapper.toResponse(enregistree);
    }

    // ------------------------------------------------------------------
    // Changement de statut (tableau Kanban, glisser-déposer)
    // ------------------------------------------------------------------

    /**
     * Change le statut d'une initiative en appliquant <strong>les mêmes
     * règles</strong> que la modification via formulaire (RM07, diagramme
     * d'état) et en déposant une ligne dans le journal.
     *
     * <p>Le Kanban permet de glisser une carte d'une colonne à une autre :
     * refuser silencieusement une transition invalide laisserait croire à
     * l'utilisateur que son glisser a bien été pris en compte. On renvoie
     * donc un 400 avec le motif, que le front affiche.</p>
     *
     * @param request statut cible et motif facultatif
     */
    @Transactional
    public InitiativeResponse changerStatut(Long idInitiative, ChangerStatutRequest request) {
        Initiative initiative = getInitiative(idInitiative);
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            throw new BadRequestException("L'initiative est archivée : toute modification est refusée.");
        }

        StatutInitiative statutCible = statutInitiativeRepository.findById(request.idStatut())
                .orElseThrow(() -> new ResourceNotFoundException("Statut introuvable : " + request.idStatut()));

        String statutActuel = initiative.getStatut().getLibelle();
        String statutDemande = statutCible.getLibelle();

        // Glisser sur la colonne courante : no-op explicite (200), pas une
        // erreur — l'utilisateur n'a rien fait de mal.
        if (statutActuel.equals(statutDemande)) {
            return InitiativeMapper.toResponse(initiative);
        }

        // RM07 : une initiative « Terminée » doit avoir un avancement de 100 %.
        if (statutDemande.equals(STATUT_TERMINEE) && initiative.getAvancement() < 100) {
            throw new BadRequestException("Impossible de passer à « Terminée » : l'avancement est de "
                    + initiative.getAvancement() + " %. Passez d'abord les étapes à « Terminée ».");
        }

        verifierTransition(statutActuel, statutDemande);

        initiative.setStatut(statutCible);
        Initiative enregistree = initiativeRepository.save(initiative);
        historiqueStatutService.enregistrer(enregistree, statutActuel, statutDemande,
                request.commentaire(), utilisateurCourant());

        return InitiativeMapper.toResponse(enregistree);
    }

    // ------------------------------------------------------------------
    // Archivage
    // ------------------------------------------------------------------

    /**
     * Archivage d'une initiative (RM14). Seules les initiatives
     * « Terminée » ou « Annulée » peuvent être archivées (cycle de vie :
     * Terminée / Annulée → Archivage).
     */
    @Transactional
    public InitiativeResponse archiver(Long idInitiative) {
        Initiative initiative = getInitiative(idInitiative);
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            return InitiativeMapper.toResponse(initiative);
        }
        String statut = initiative.getStatut().getLibelle();
        if (!statut.equals(STATUT_TERMINEE) && !statut.equals(STATUT_ANNULEE)) {
            throw new BadRequestException("Seules les initiatives « Terminée » ou « Annulée » "
                    + "peuvent être archivées.");
        }
        initiative.setArchive(true);
        Initiative enregistree = initiativeRepository.save(initiative);

        // L'archivage n'est PAS un statut (RM14) : le statut lui-même ne
        // change pas. On journalise quand même l'événement, car « qui a
        // archivé et quand » fait partie de la traçabilité attendue.
        historiqueStatutService.enregistrer(enregistree, statut, statut,
                "Archivage de l'initiative", utilisateurCourant());

        return InitiativeMapper.toResponse(enregistree);
    }

    /**
     * E-mail de l'utilisateur derrière l'appel courant, ou {@code null}
     * (appel interne, exécution de démarrage, ou test unitaire sans
     * contexte de sécurité).
     *
     * <p>Plutôt que de faire remonter le paramètre « auteur » dans toute la
     * chaîne de signatures, on lit le contexte de sécurité : c'est la seule
     * source fiable, et elle est déjà en place sur toutes les routes
     * protégées.</p>
     */
    private static String utilisateurCourant() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || authentification.getName() == null) {
            return null;
        }
        String nom = authentification.getName();
        return nom.isBlank() ? null : nom;
    }

    // ------------------------------------------------------------------
    // Règles métier
    // ------------------------------------------------------------------

    /**
     * Contrôle une transition manuelle (diagramme d'état strict).
     */
    private void verifierTransition(String actuel, String demande) {
        verifierTransition(actuel, demande, false);
    }

    /**
     * Contrôle une transition de statut.
     *
     * @param passageAuto {@code true} lorsque le passage est déclenché
     *                    automatiquement par la règle RM07 (avancement atteint
     *                    100 %) : il est alors accepté depuis n'importe quel
     *                    état actif, contrairement à une transition manuelle.
     */
    private void verifierTransition(String actuel, String demande, boolean passageAuto) {
        if (actuel.equals(demande)) {
            return;
        }
        boolean autorisee =
                // À planifier → En cours
                (actuel.equals(STATUT_A_PLANIFIER) && demande.equals(STATUT_EN_COURS))
                // En cours → En pause
                || (actuel.equals(STATUT_EN_COURS) && demande.equals(STATUT_EN_PAUSE))
                // En pause → En cours
                || (actuel.equals(STATUT_EN_PAUSE) && demande.equals(STATUT_EN_COURS))
                // En cours → Terminée (avancement = 100 vérifié séparément)
                || (actuel.equals(STATUT_EN_COURS) && demande.equals(STATUT_TERMINEE))
                // RM07-auto : avancement 100 % -> Terminée, quel que soit l'état de départ
                || (passageAuto && demande.equals(STATUT_TERMINEE))
                // À planifier / En cours / En pause → Annulée
                || (actuel.equals(STATUT_A_PLANIFIER) && demande.equals(STATUT_ANNULEE))
                || (actuel.equals(STATUT_EN_COURS) && demande.equals(STATUT_ANNULEE))
                || (actuel.equals(STATUT_EN_PAUSE) && demande.equals(STATUT_ANNULEE));

        if (!autorisee) {
            throw new BadRequestException("Transition de statut non autorisée : « " + actuel
                    + " » → « " + demande + " ».");
        }
    }

    private void validerCodeUnique(String code, Long idIgnore) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Le code est obligatoire.");
        }
        if (idIgnore == null) {
            if (initiativeRepository.existsByCode(code.trim())) {
                throw new BadRequestException("Le code « " + code.trim() + " » est déjà utilisé.");
            }
        } else if (initiativeRepository.existsByCodeAndIdInitiativeNot(code.trim(), idIgnore)) {
            throw new BadRequestException("Le code « " + code.trim() + " » est déjà utilisé.");
        }
    }

    private void validerReglesMetier(InitiativeRequest request) {
        // RM01 : nom obligatoire (en plus de la contrainte @NotBlank)
        // RM04 : date de fin prévue >= date de début
        if (request.dateDebut() != null && request.dateFinPrevue() != null
                && request.dateFinPrevue().isBefore(request.dateDebut())) {
            throw new BadRequestException("La date de fin prévue ne peut pas être antérieure "
                    + "à la date de début.");
        }
        // RM05 : budget >= 0
        if (request.budget() != null && request.budget().signum() < 0) {
            throw new BadRequestException("Le budget doit être supérieur ou égal à zéro.");
        }
        // RM06 : avancement entre 0 et 100
        if (request.avancement() != null && (request.avancement() < 0 || request.avancement() > 100)) {
            throw new BadRequestException("L'avancement doit être compris entre 0 et 100.");
        }
        // RM03 : le responsable est facultatif, mais doit exister (validé dans resoudreResponsable)
    }

    private Utilisateur resoudreResponsable(Long idResponsable) {
        if (idResponsable == null) {
            return null;
        }
        Utilisateur u = utilisateurRepository.findById(idResponsable)
                .orElseThrow(() -> new ResourceNotFoundException("Responsable introuvable : " + idResponsable));
        if (!Boolean.TRUE.equals(u.getActif())) {
            throw new BadRequestException("Le responsable doit être un utilisateur actif.");
        }
        return u;
    }

    private ObjectifStrategique resoudreObjectif(Long idObjectif) {
        if (idObjectif == null) {
            return null;
        }
        return objectifRepository.findById(idObjectif)
                .orElseThrow(() -> new ResourceNotFoundException("Objectif stratégique introuvable : " + idObjectif));
    }

    private Initiative getInitiative(Long idInitiative) {
        return initiativeRepository.findById(idInitiative)
                .orElseThrow(() -> new ResourceNotFoundException("Initiative introuvable : " + idInitiative));
    }
}