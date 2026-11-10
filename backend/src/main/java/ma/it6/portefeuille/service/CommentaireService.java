package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.CommentaireRequest;
import ma.it6.portefeuille.dto.CommentaireResponse;
import ma.it6.portefeuille.entity.Commentaire;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.UtilisateurMapper;
import ma.it6.portefeuille.repository.CommentaireRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.security.OrganisationContext;
import ma.it6.portefeuille.service.NotificationService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fil de discussion d'une initiative — commentaires.
 *
 * <p>Un commentaire est <strong>immuable</strong> : ni mise à jour ni
 * remplacement, seule la suppression existe (droit
 * {@code COMMENTAIRE_AJOUTER}). L'auteur est anonymisé s'il disparaît, le
 * fil reste consultable.</p>
 *
 * <p>Garde-fous :</p>
 * <ul>
 *   <li>initiative inexistante → 404 ;</li>
 *   <li>initiative archivée → 400 sur ajout (le dossier est clos) ;</li>
 *   <li>commentaire inexistant → 404 ;</li>
 *   <li>corps vide ou trop long → 400 (validation du contrôleur).</li>
 * </ul>
 *
 * <p>Mentions @ (4E) : une mention {@code @email} ou {@code @nom} dans le
 * corps du commentaire notifie l'utilisateur visé s'il appartient à la même
 * organisation. Les mentions sont résolues après création du commentaire et
 * n'empêchent pas sa publication en cas d'erreur.</p>
 */
@Service
public class CommentaireService {

    /** Plafond de longueur rappelé ici pour la coupe propre. */
    static final int LONGUEUR_MAXIMALE = 4000;

    /** Motif des mentions : @ suivi d'un identifiant (lettre, chiffre, point, tiret, bas de soulage). */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.-]+)");

    private final CommentaireRepository commentaireRepository;
    private final InitiativeRepository initiativeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    public CommentaireService(CommentaireRepository commentaireRepository,
                              InitiativeRepository initiativeRepository,
                              UtilisateurRepository utilisateurRepository,
                              NotificationService notificationService) {
        this.commentaireRepository = commentaireRepository;
        this.initiativeRepository = initiativeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.notificationService = notificationService;
    }

    /** Fil complet d'une initiative, plus récent d'abord. */
    @Transactional(readOnly = true)
    public List<CommentaireResponse> lister(Long idInitiative) {
        Initiative initiative = verifierInitiative(idInitiative);
        return commentaireRepository.filInitiative(initiative.getIdInitiative()).stream()
                .map(CommentaireService::toResponse)
                .toList();
    }

    @Transactional
    public CommentaireResponse creer(Long idInitiative, CommentaireRequest request, Long idAuteur) {
        Initiative initiative = verifierInitiative(idInitiative);
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            throw new BadRequestException("L'initiative est archivée : ajout de commentaire refusé.");
        }
        String contenu = couper(request.contenu().trim());

        Commentaire commentaire = Commentaire.builder()
                .initiative(initiative)
                .auteur(utilisateur(idAuteur))
                .contenu(contenu)
                .build();
        commentaire = commentaireRepository.save(commentaire);

        // Détection des mentions @ et notification des utilisateurs visés
        // (même organisation). Les mentions n'empêchent pas la publication
        // du commentaire en cas d'erreur.
        notifierMentions(commentaire, contenu, idAuteur);

        return toResponse(commentaire);
    }

    /**
     * Suppression d'un commentaire — l'auteur lui-même, ou une personne
     * munie du droit {@code UTILISATEUR_GERER} (administration du fil).
     */
    @Transactional
    public void supprimer(Long idCommentaire, Long idAuteurCourant) {
        Commentaire commentaire = commentaireRepository.findById(idCommentaire)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Commentaire introuvable : " + idCommentaire));
        boolean estAuteur = commentaire.getAuteur() != null
                && idAuteurCourant != null
                && idAuteurCourant.equals(commentaire.getAuteur().getIdUser());
        if (!estAuteur && !aDroitAdministrateur()) {
            throw new BadRequestException(
                    "Seul l'auteur du commentaire ou un administrateur peut le supprimer.");
        }
        commentaireRepository.delete(commentaire);
    }

    // ------------------------------------------------------------------
    // Garde-fous et internes
    // ------------------------------------------------------------------

    private Initiative verifierInitiative(Long idInitiative) {
        return initiativeRepository.findById(idInitiative)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Initiative introuvable : " + idInitiative));
    }

    private Utilisateur utilisateur(Long idUtilisateur) {
        if (idUtilisateur == null) {
            return null;
        }
        return utilisateurRepository.findById(idUtilisateur)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable : " + idUtilisateur));
    }

    /** Coupe proprement au-delà du plafond (jamais tronqué à cheval). */
    private static String couper(String contenu) {
        if (contenu.length() <= LONGUEUR_MAXIMALE) {
            return contenu;
        }
        return contenu.substring(0, LONGUEUR_MAXIMALE);
    }

    private static boolean aDroitAdministrateur() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "UTILISATEUR_GERER".equals(a.getAuthority()));
    }

    // ------------------------------------------------------------------
    // Mentions @ (4E)
    // ------------------------------------------------------------------

    /** Détecte les mentions @ dans le contenu et notifie les utilisateurs
     *  visés s'ils appartiennent à la même organisation. Les erreurs
     *  d'envoi n'empêchent pas la publication du commentaire. */
    private void notifierMentions(Commentaire commentaire, String contenu, Long idAuteur) {
        Matcher m = MENTION_PATTERN.matcher(contenu);
        Long idOrganisation = OrganisationContext.getId();
        if (idOrganisation == null) {
            return;
        }
        while (m.find()) {
            String mention = m.group(1).trim();
            if (mention.isEmpty()) {
                continue;
            }
            Utilisateur cible = resoudreMention(mention, idOrganisation);
            if (cible != null && !cible.getIdUser().equals(idAuteur)) {
                try {
                    notificationService.envoyer(
                            cible.getIdUser(),
                            "Vous avez été mentionné dans un commentaire",
                            "Vous avez été mentionné dans un commentaire sur l'initiative « "
                                    + commentaire.getInitiative().getNom()
                                    + " » par " + commentaire.getAuteur().getPrenom()
                                    + " " + commentaire.getAuteur().getNom()
                                    + ".\n\nContenu : " + couper(commentaire.getContenu()));
                } catch (Exception e) {
                    // Échec d'envoi : le commentaire reste publié.
                }
            }
        }
    }

    /** Résout une mention @email ou @nom vers un utilisateur de la même
     *  organisation. Retourne null si aucun utilisateur ne correspond. */
    private Utilisateur resoudreMention(String mention, Long idOrganisation) {
        // 1. Essai par e-mail exact
        if (mention.contains("@")) {
            return utilisateurRepository.findByEmail(mention)
                    .filter(u -> Boolean.TRUE.equals(u.getActif())
                            && idOrganisation.equals(u.getIdOrganisation()))
                    .orElse(null);
        }
        // 2. Essai par nom complet (prénom nom ou nom)
        String[] parties = mention.split("\\s+", 2);
        String nom = parties[parties.length - 1];
        String prenom = parties.length > 1 ? parties[0] : null;
        return utilisateurRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getActif()))
                .filter(u -> idOrganisation.equals(u.getIdOrganisation()))
                .filter(u -> nom.equalsIgnoreCase(u.getNom())
                        || (prenom != null && prenom.equalsIgnoreCase(u.getPrenom())
                                && nom.equalsIgnoreCase(u.getNom())))
                .findFirst()
                .orElse(null);
    }

    static CommentaireResponse toResponse(Commentaire c) {
        if (c == null) {
            return null;
        }
        return new CommentaireResponse(
                c.getIdCommentaire(),
                c.getInitiative() != null ? c.getInitiative().getIdInitiative() : null,
                c.getContenu(),
                c.getCreatedAt(),
                UtilisateurMapper.toSummary(c.getAuteur())
        );
    }
}
