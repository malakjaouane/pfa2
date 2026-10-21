package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.HistoriqueStatutResponse;
import ma.it6.portefeuille.dto.InitiativeSummaryDto;
import ma.it6.portefeuille.dto.UserSummaryDto;
import ma.it6.portefeuille.entity.HistoriqueStatut;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.mapper.UtilisateurMapper;
import ma.it6.portefeuille.repository.HistoriqueStatutRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Journal des transitions de statut des initiatives.
 *
 * <p>Chaque changement de cycle de vie (création, modification, glisser-déposer
 * sur le Kanban, archivage) dépose une ligne ici. Le journal est écrit dans la
 * <strong>même transaction</strong> que la modification de l'initiative : il ne
 * peut donc jamais exister une transition sans trace.</p>
 *
 * <p>Chaque ligne porte aussi son <strong>origine</strong> : manuelle
 * (action explicite d'un utilisateur) ou automatique (moteur de règles
 * RM07/RM17, alignement au démarrage). La lecture globale alimente la page
 * « Historique des statuts » ; il n'existe aucune écriture publique autre que
 * {@link #enregistrer}, réservée au serveur — le journal est en lecture seule
 * pour l'interface.</p>
 */
@Service
public class HistoriqueStatutService {

    /** Longueur maximale du motif en base ; au-delà, on tronque proprement. */
    private static final int LONGUEUR_MAX_COMMENTAIRE = 500;

    /** Taille de page par défaut de la page « Historique des statuts ». */
    public static final int TAILLE_PAR_DEFAUT = 25;
    /** Plafond de taille de page (lecture seule : au-delà, le tableau s'essouffle). */
    public static final int TAILLE_MAXIMALE = 100;

    private final HistoriqueStatutRepository historiqueRepository;
    private final UtilisateurRepository utilisateurRepository;

    public HistoriqueStatutService(HistoriqueStatutRepository historiqueRepository,
                                   UtilisateurRepository utilisateurRepository) {
        this.historiqueRepository = historiqueRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * Filtres combinés de la lecture globale (tous facultatifs).
     *
     * @param recherche  fragment du code ou du nom d'initiative
     * @param statut     libellé du statut visé (nouveau statut de l'événement)
     * @param idUtilisateur auteur de la transition
     * @param idInitiative  initiative filtrée (lien « Voir tout l'historique »)
     * @param du         début de période (jour inclus)
     * @param au         fin de période (jour inclus)
     */
    public record FiltreHistorique(String recherche,
                                   String statut,
                                   Long idUtilisateur,
                                   Long idInitiative,
                                   LocalDate du,
                                   LocalDate au) {
    }

    /**
     * Dépose une ligne dans le journal — variante <strong>manuelle</strong>
     * (origine : une action explicite d'un utilisateur).
     *
     * @param ancienStatut   statut avant la transition, {@code null} à la création
     * @param nouveauStatut  statut après la transition
     * @param emailUtilisateur e-mail de l'auteur ; peut être {@code null}
     */
    @Transactional
    public void enregistrer(Initiative initiative,
                            String ancienStatut,
                            String nouveauStatut,
                            String commentaire,
                            String emailUtilisateur) {
        enregistrer(initiative, ancienStatut, nouveauStatut, commentaire, emailUtilisateur, false);
    }

    /**
     * Dépose une ligne dans le journal en précisant l'<strong>origine</strong>
     * du changement.
     *
     * @param automatique {@code true} pour un passage déclenché par le moteur
     *                    de règles (RM07-auto, réouverture, RM17, alignement
     *                    de démarrage), {@code false} pour une action manuelle
     */
    @Transactional
    public void enregistrer(Initiative initiative,
                            String ancienStatut,
                            String nouveauStatut,
                            String commentaire,
                            String emailUtilisateur,
                            boolean automatique) {
        HistoriqueStatut entree = new HistoriqueStatut();
        entree.setInitiative(initiative);
        entree.setAncienStatut(ancienStatut);
        entree.setNouveauStatut(nouveauStatut);
        entree.setCommentaire(tronquer(commentaire));
        entree.setDateChangement(LocalDateTime.now());
        entree.setUtilisateur(resoudreUtilisateur(emailUtilisateur));
        entree.setAutomatique(automatique);
        historiqueRepository.save(entree);
    }

    /** Journal complet d'une initiative, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<HistoriqueStatutResponse> historique(Long idInitiative) {
        return historiqueRepository
                .findByInitiativeIdInitiativeOrderByDateChangementDesc(idInitiative)
                .stream()
                .map(HistoriqueStatutService::toResponse)
                .toList();
    }

    /**
     * Une page du journal global (lecture seule), filtrée et triée.
     *
     * @param triAsc {@code false} = plus récent d'abord (défaut),
     *               {@code true} = plus ancien d'abord
     */
    @Transactional(readOnly = true)
    public List<HistoriqueStatutResponse> lister(FiltreHistorique filtre,
                                                 int numeroPage,
                                                 int taille,
                                                 boolean triAsc) {
        Sort tri = Sort.by(triAsc ? Sort.Direction.ASC : Sort.Direction.DESC, "dateChangement")
                .and(Sort.by(triAsc ? Sort.Direction.ASC : Sort.Direction.DESC, "idHistorique"));
        int tailleEffective = Math.min(Math.max(taille, 1), TAILLE_MAXIMALE);
        Page<HistoriqueStatut> page = historiqueRepository.rechercher(
                jokersRecherche(filtre.recherche()), filtre.statut(), filtre.idUtilisateur(),
                filtre.idInitiative(), borneDebut(filtre.du()), borneFin(filtre.au()),
                PageRequest.of(Math.max(0, numeroPage), tailleEffective, tri));
        return page.map(HistoriqueStatutService::toResponse).getContent();
    }

    /** Nombre total de lignes pour les filtres donnés (hors pagination). */
    @Transactional(readOnly = true)
    public long compter(FiltreHistorique filtre) {
        return historiqueRepository.compter(
                jokersRecherche(filtre.recherche()), filtre.statut(), filtre.idUtilisateur(),
                filtre.idInitiative(), borneDebut(filtre.du()), borneFin(filtre.au()));
    }

    /**
     * Auteurs présents dans le journal, pour la liste déroulante de filtres.
     * Réutilise la synthèse utilisateur existante — pas de nouveau droit
     * d'accès, la lecture reste soumise à INITIATIVE_CONSULTER.
     */
    @Transactional(readOnly = true)
    public List<UserSummaryDto> auteurs() {
        return historiqueRepository.trouverAuteurs().stream()
                .map(UtilisateurMapper::toSummary)
                .toList();
    }

    // ------------------------------------------------------------------
    // Internes
    // ------------------------------------------------------------------

    /** Le joker n'est construit que si la recherche est renseignée. */
    private static String jokersRecherche(String recherche) {
        if (recherche == null || recherche.isBlank()) {
            return null;
        }
        return "%" + recherche.trim() + "%";
    }

    /** Période « du » inclus : premier instant du jour. */
    private static LocalDateTime borneDebut(LocalDate du) {
        return du == null ? null : du.atStartOfDay();
    }

    /** Période « au » inclusive : exclusive au lendemain (jour entier inclus). */
    private static LocalDateTime borneFin(LocalDate au) {
        return au == null ? null : au.plusDays(1).atStartOfDay();
    }

    /**
     * L'auteur peut avoir été supprimé entre-temps (la clé étrangère est en
     * ON DELETE SET NULL) : on laisse alors le champ vide plutôt que de faire
     * échouer la lecture du journal.
     */
    private Utilisateur resoudreUtilisateur(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return utilisateurRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
    }

    private static String tronquer(String commentaire) {
        if (commentaire == null || commentaire.isBlank()) {
            return null;
        }
        String nettoye = commentaire.trim();
        return nettoye.length() > LONGUEUR_MAX_COMMENTAIRE
                ? nettoye.substring(0, LONGUEUR_MAX_COMMENTAIRE)
                : nettoye;
    }

    private static HistoriqueStatutResponse toResponse(HistoriqueStatut h) {
        Initiative initiative = h.getInitiative();
        return new HistoriqueStatutResponse(
                h.getIdHistorique(),
                h.getAncienStatut(),
                h.getNouveauStatut(),
                h.getCommentaire(),
                h.getDateChangement(),
                h.isAutomatique(),
                h.getUtilisateur() == null ? null : UtilisateurMapper.toSummary(h.getUtilisateur()),
                initiative == null ? null
                        : new InitiativeSummaryDto(
                                initiative.getIdInitiative(),
                                initiative.getCode(),
                                initiative.getNom()));
    }
}
