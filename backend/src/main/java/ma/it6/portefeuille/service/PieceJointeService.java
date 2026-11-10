package ma.it6.portefeuille.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.dto.PieceJointeDTO;
import ma.it6.portefeuille.entity.Commentaire;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.PieceJointe;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.CommentaireRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.PieceJointeRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.security.OrganisationContext;
import ma.it6.portefeuille.storage.StockageFichier;

import static org.springframework.http.HttpStatus.*;

/**
 * Service de gestion des pièces jointes (initiatives et commentaires).
 *
 * <p>Le binaire est délégué à {@link StockageFichier} (système de fichiers ou
 * S3 selon la configuration). Seuls les métadonnées sont persistées en base
 * ({@code piece_jointe}), isolées par organisation.</p>
 *
 * <p>Règles de sécurité :</p>
 * <ul>
 *   <li>taille maximale configurable ({@code stockage.taille-max-octets},
 *       défaut 10 Mo) ;</li>
 *   <li>types MIME autorisés configurables ({@code stockage.mime-autorises}) ;</li>
 *   <li>une pièce jointe se rattache soit à une initiative, soit à un
 *       commentaire (pas les deux) de la même organisation ;</li>
 *   <li>seul l'uploader ou un administrateur de l'organisation peut supprimer.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PieceJointeService {

    /** Types MIME autorisés par défaut (PDF, images, CSV, Excel, Word). */
    private static final Set<String> MIME_PAR_DEFAUT = new HashSet<>(Arrays.asList(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/gif",
            "text/csv",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

    private final PieceJointeRepository pieceJointeRepository;
    private final InitiativeRepository initiativeRepository;
    private final CommentaireRepository commentaireRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final StockageFichier stockage;
    private final long tailleMaxOctets;
    private final Set<String> mimeAutorises;

    public PieceJointeService(PieceJointeRepository pieceJointeRepository,
                              InitiativeRepository initiativeRepository,
                              CommentaireRepository commentaireRepository,
                              UtilisateurRepository utilisateurRepository,
                              StockageFichier stockage,
                              @Value("${stockage.taille-max-octets:10485760}") long tailleMaxOctets,
                              @Value("${stockage.mime-autorises:}") String mimeAutorises) {
        this.pieceJointeRepository = pieceJointeRepository;
        this.initiativeRepository = initiativeRepository;
        this.commentaireRepository = commentaireRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.stockage = stockage;
        this.tailleMaxOctets = tailleMaxOctets;
        this.mimeAutorises = mimeAutorises == null || mimeAutorises.isBlank()
                ? MIME_PAR_DEFAUT
                : new HashSet<>(Arrays.stream(mimeAutorises.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()));
    }

    /**
     * Upload d'une pièce jointe sur une initiative ou un commentaire.
     *
     * @param fichier        fichier envoyé par le client
     * @param idInitiative   initiative cible (ou {@code null})
     * @param idCommentaire  commentaire cible (ou {@code null})
     * @param email          e-mail du compte uploader (doit appartenir à l'org courante)
     * @return DTO de la pièce jointe créée
     */
    @Transactional
    public PieceJointeDTO uploader(MultipartFile fichier,
                                   Long idInitiative,
                                   Long idCommentaire,
                                   String email) {
        validerFichier(fichier);

        // Vérifie que la cible existe et appartient à l'organisation courante
        // (le filtre Hibernate filtreParOrganisation est actif).
        if (idInitiative != null) {
            if (initiativeRepository.findByIdInitiative(idInitiative) == null) {
                throw new ResponseStatusException(NOT_FOUND, "Initiative introuvable");
            }
        } else if (idCommentaire != null) {
            if (commentaireRepository.findById(idCommentaire).isEmpty()) {
                throw new ResponseStatusException(NOT_FOUND, "Commentaire introuvable");
            }
        }

        Utilisateur uploader = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Utilisateur introuvable"));

        // Organisation courante (positionnée par OrganisationIsolationInterceptor)
        Long idOrganisation = OrganisationContext.getId();
        if (idOrganisation == null) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR,
                    "Aucune organisation dans le contexte de requête");
        }

        String cle = stockage.stocker(fichier.getOriginalFilename(), lireOctets(fichier), fichier.getContentType());

        PieceJointe pj = PieceJointe.builder()
                .nomFichier(fichier.getOriginalFilename())
                .typeMime(fichier.getContentType())
                .tailleOctets(fichier.getSize())
                .cleStockage(cle)
                .initiative(idInitiative != null ? Initiative.builder().idInitiative(idInitiative).build() : null)
                .commentaire(idCommentaire != null ? Commentaire.builder().idCommentaire(idCommentaire).build() : null)
                .utilisateur(uploader)
                .dateCreation(LocalDateTime.now())
                .build();
        // id_organisation initialisé par BaseOrganisation.initialiserOrganisation (@PrePersist)
        pieceJointeRepository.save(pj);

        return toDTO(pj);
    }

    /**
     * Récupère le contenu d'une pièce jointe.
     */
    public byte[] recuperer(Long idPieceJointe) {
        PieceJointe pj = pieceJointeRepository.findByIdPieceJointe(idPieceJointe)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pièce jointe introuvable"));
        return stockage.recuperer(pj.getCleStockage());
    }

    public PieceJointeDTO getDTO(Long idPieceJointe) {
        PieceJointe pj = pieceJointeRepository.findByIdPieceJointe(idPieceJointe)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pièce jointe introuvable"));
        return toDTO(pj);
    }

    /**
     * Supprime une pièce jointe et son binaire. Seul l'uploader ou un
     * administrateur de l'organisation peut supprimer.
     */
    @Transactional
    public void supprimer(Long idPieceJointe, String email, boolean estAdmin) {
        PieceJointe pj = pieceJointeRepository.findByIdPieceJointe(idPieceJointe)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pièce jointe introuvable"));

        Utilisateur utilisateurCourant = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Utilisateur introuvable"));

        if (!estAdmin && !pj.getUtilisateur().getIdUser().equals(utilisateurCourant.getIdUser())) {
            throw new ResponseStatusException(FORBIDDEN, "Suppression non autorisée");
        }

        stockage.supprimer(pj.getCleStockage());
        pieceJointeRepository.delete(pj);
    }

    /** Valide la présence, la taille et le type MIME du fichier. */
    private void validerFichier(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Fichier absent");
        }
        if (fichier.getSize() > tailleMaxOctets) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Fichier trop lourd (" + fichier.getSize() + " octets, max " + tailleMaxOctets + ")");
        }
        String mime = fichier.getContentType();
        if (mime == null || !mimeAutorises.contains(mime)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Type MIME non autorisé : " + mime);
        }
    }

    private byte[] lireOctets(MultipartFile fichier) {
        try {
            return fichier.getBytes();
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Impossible de lire le fichier", e);
        }
    }

    private PieceJointeDTO toDTO(PieceJointe pj) {
        return PieceJointeDTO.builder()
                .idPieceJointe(pj.getIdPieceJointe())
                .nomFichier(pj.getNomFichier())
                .typeMime(pj.getTypeMime())
                .tailleOctets(pj.getTailleOctets())
                .dateCreation(pj.getDateCreation())
                .utilisateurEmail(pj.getUtilisateur().getEmail())
                .utilisateurNomComplet(pj.getUtilisateur().getPrenom() + " " + pj.getUtilisateur().getNom())
                .idInitiative(pj.getInitiative() != null ? pj.getInitiative().getIdInitiative() : null)
                .idCommentaire(pj.getCommentaire() != null ? pj.getCommentaire().getIdCommentaire() : null)
                .build();
    }
}
