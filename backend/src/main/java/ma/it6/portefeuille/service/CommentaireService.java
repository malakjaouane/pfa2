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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
 */
@Service
public class CommentaireService {

    /** Plafond de longueur rappelé ici pour la coupe propre. */
    static final int LONGUEUR_MAXIMALE = 4000;

    private final CommentaireRepository commentaireRepository;
    private final InitiativeRepository initiativeRepository;
    private final UtilisateurRepository utilisateurRepository;

    public CommentaireService(CommentaireRepository commentaireRepository,
                              InitiativeRepository initiativeRepository,
                              UtilisateurRepository utilisateurRepository) {
        this.commentaireRepository = commentaireRepository;
        this.initiativeRepository = initiativeRepository;
        this.utilisateurRepository = utilisateurRepository;
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
        return toResponse(commentaireRepository.save(commentaire));
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
