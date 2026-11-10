package ma.it6.portefeuille.controller;

import jakarta.validation.Valid;
import ma.it6.portefeuille.dto.ChangerStatutRequest;
import ma.it6.portefeuille.dto.HistoriqueModificationResponse;
import ma.it6.portefeuille.dto.InitiativeFilterRequest;
import ma.it6.portefeuille.dto.InitiativeRequest;
import ma.it6.portefeuille.dto.InitiativeResponse;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.entity.Plan;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.service.HistoriqueModificationService;
import ma.it6.portefeuille.service.InitiativeService;
import ma.it6.portefeuille.service.MembreInitiativeService;
import ma.it6.portefeuille.service.PlanService;
import ma.it6.portefeuille.service.UtilisateurService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API REST des initiatives (recherche, filtres combinés, CRUD, archivage,
 * transitions de statut et journal).
 *
 * <p>La liste accepte une pagination et un tri <strong>côté serveur</strong>
 * ({@code page}, {@code size}, {@code tri}, {@code direction}) : sans ces
 * paramètres, la sélection complète est renvoyée comme précédemment, donc les
 * écrans existants continuent de fonctionner sans modification.</p>
 */
@RestController
@RequestMapping("/api/initiatives")
public class InitiativeController {

    private final InitiativeService initiativeService;
    private final HistoriqueModificationService historiqueService;
    private final UtilisateurRepository utilisateurRepository;
    private final MembreInitiativeService membreInitiativeService;
    private final PlanService planService;

    public InitiativeController(InitiativeService initiativeService,
                                HistoriqueModificationService historiqueService,
                                UtilisateurRepository utilisateurRepository,
                                MembreInitiativeService membreInitiativeService,
                                PlanService planService) {
        this.initiativeService = initiativeService;
        this.historiqueService = historiqueService;
        this.utilisateurRepository = utilisateurRepository;
        this.membreInitiativeService = membreInitiativeService;
        this.planService = planService;
    }

    /**
     * Liste des initiatives selon les filtres combinés (statut, entité,
     * responsable, période, priorité) et la recherche textuelle.
     *
     * @param tri        champ de tri autorisé (code, nom, budget, avancement…)
     * @param direction  {@code asc} ou {@code desc} ; {@code desc} par défaut
     * @param page       numéro de page (0-indexé) ; si absent et sans
     *                   {@code size}, la liste complète est renvoyée
     * @param size       taille de page, plafonnée à {@value
     *                   InitiativeService#TAILLE_MAXIMALE}
     */
    @GetMapping
    public ResponseEntity<List<InitiativeResponse>> lister(
            @ModelAttribute InitiativeFilterRequest filtre,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String tri,
            @RequestParam(required = false) String direction) {

        List<InitiativeResponse> reponse =
                initiativeService.lister(filtre, page, size, tri, direction);

        // Compte total HORS pagination : c'est ce dont un composant de
        // pagination a besoin pour savoir s'il reste des pages, sans
        // charger la suite pour le deviner.
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(initiativeService.compter(filtre)))
                .body(reponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InitiativeResponse> trouver(@PathVariable Long id) {
        return ResponseEntity.ok(initiativeService.trouver(id));
    }

    /**
     * Fil d'activité complet de la fiche : lignes de l'initiative elle-même
     * et de ses étapes / jalons, du plus récent au plus ancien, avec filtre
     * par type d'objet et chargement progressif (« Charger plus »).
     *
     * @param type filtre facultatif : {@code initiative}, {@code etape} ou
     *             {@code jalon} (400 sinon)
     * @param page numéro de page (0-indexé), 0 par défaut
     * @param size lignes par page, plafonnée à
     *             {@value HistoriqueModificationService#TAILLE_MAXIMALE}
     * @return la page courante + le total des lignes du fil dans
     *         {@code X-Total-Count}
     */
    @GetMapping("/{id}/historique")
    public ResponseEntity<List<HistoriqueModificationResponse>> historique(
            @PathVariable Long id,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // Fait échouer l'appel si l'initiative n'existe pas, plutôt que de
        // renvoyer un journal vide ambigu.
        initiativeService.trouver(id);

        int numeroPage = page == null ? 0 : Math.max(0, page);
        int taille = size == null || size <= 0
                ? HistoriqueModificationService.TAILLE_PAR_DEFAUT
                : size;
        HistoriqueModificationService.PageJournal resultat =
                historiqueService.historiqueInitiative(id, type, numeroPage, taille);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(resultat.total()))
                .body(resultat.lignes());
    }

    /**
     * Changement de statut explicite (tableau Kanban : glisser une carte
     * d'une colonne à une autre). Applique les mêmes règles de cycle de vie
     * que la modification par formulaire.
     */
    @PutMapping("/{id}/statut")
    public ResponseEntity<InitiativeResponse> changerStatut(
            @PathVariable Long id,
            @Valid @RequestBody ChangerStatutRequest request) {
        verifierEcriture(id);
        return ResponseEntity.ok(initiativeService.changerStatut(id, request));
    }

    @PostMapping
    public ResponseEntity<?> creer(@Valid @RequestBody InitiativeRequest request) {
        // Vérification des limites de plan avant toute création.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            Utilisateur u = utilisateurRepository.findByEmail(auth.getName())
                    .orElse(null);
            if (u != null && !planService.peutCreerInitiative(u.getIdOrganisation())) {
                Plan plan = planService.planDe(u.getIdOrganisation());
                return ResponseEntity.badRequest().body(Map.of(
                        "code", "LIMITE_PLAN",
                        "message", "Limite d'initiatives atteinte pour le plan "
                                + (plan == null ? "inconnu" : plan.getNom())
                                + " (" + plan.getNbInitiativesMax() + " initiatives maximum)."));
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(initiativeService.creer(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InitiativeResponse> modifier(@PathVariable Long id,
                                                       @Valid @RequestBody InitiativeRequest request) {
        verifierEcriture(id);
        return ResponseEntity.ok(initiativeService.modifier(id, request));
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<InitiativeResponse> archiver(@PathVariable Long id) {
        verifierEcriture(id);
        return ResponseEntity.ok(initiativeService.archiver(id));
    }

    /**
     * Purge définitive d'une initiative archivée — INITIATIVE_ARCHIVER
     * (imposé par SecurityConfig). 400 si l'initiative n'est pas archivée.
     *
     * @return {@code 204} si la purge a eu lieu, {@code 404} si elle
     *         n'existe pas
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> purger(@PathVariable Long id) {
        verifierEcriture(id);
        initiativeService.purger(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Vérifie que l'utilisateur connecté peut modifier l'initiative
     * (responsable ou membre contributeur/responsable). Sinon 403.
     *
     * <p>L'administrateur est exempté : la permission
     * {@code INITIATIVE_MODIFIER} (exigée par {@code SecurityConfig}) vaut
     * pour toutes les initiatives de son organisation — le contrôle
     * d'appartenance est un raffinement « moindre privilège » qui ne
     * restreint que les autres rôles (un responsable ne modifie que ses
     * initiatives ou celles dont il est membre).</p>
     */
    private void verifierEcriture(Long idInitiative) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return;
        }
        boolean administrateur = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (administrateur) {
            return;
        }
        Optional<Utilisateur> opt = utilisateurRepository.findByEmail(auth.getName());
        if (opt == null || opt.isEmpty()) {
            return;
        }
        Utilisateur utilisateur = opt.get();
        if (!membreInitiativeService.peutModifier(utilisateur.getIdUser(), idInitiative)) {
            throw new AccessDeniedException(
                    "Vous n'avez pas le droit de modifier cette initiative (résponsable ou membre contributeur requis).");
        }
    }
}
