package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.HistoriqueModificationResponse;
import ma.it6.portefeuille.dto.InitiativeSummaryDto;
import ma.it6.portefeuille.dto.UserSummaryDto;
import ma.it6.portefeuille.entity.HistoriqueModification;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.mapper.UtilisateurMapper;
import ma.it6.portefeuille.repository.HistoriqueModificationRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Journal générique des modifications — l'audit du portefeuille.
 *
 * <p>Chaque changement significatif dépose une ligne ici, dans la
 * <strong>même transaction</strong> que la modification : il ne peut donc
 * jamais exister un changement sans trace. Le journal absorbe :</p>
 * <ul>
 *   <li>les <strong>initiatives</strong> — les transitions de statut et le
 *       diff champ à champ de chaque modification (budget, responsable,
 *       dates, priorité, entité, objectif, avancement…) ;</li>
 *   <li>les <strong>étapes et jalons</strong> — création, modification,
 *       suppression, réouverture ;</li>
 *   <li>les <strong>demandes</strong> — soumission, évaluation, acceptation,
 *       refus avec motif, transformation ;</li>
 *   <li>l'<strong>administration</strong> — comptes (création, modification,
 *       suppression) et permissions de rôles.</li>
 * </ul>
 *
 * <p><strong>Journal immuable</strong> : aucune écriture n'est exposée à
 * l'interface (aucun POST/PUT/PATCH n'existe) et aucune suppression n'est
 * possible — ni ligne par ligne, ni totale. Les lignes de plus de
 * {@value #MOIS_D_ARCHIVAGE} mois sortent de la vue active par un simple
 * calcul sur la date (bascule « Actives | Archives ») : la preuve n'est
 * jamais détruite.</p>
 *
 * <p>Chaque ligne porte son <strong>origine</strong> : manuelle (action
 * explicite d'un utilisateur) ou automatique (moteur de règles RM07/RM17,
 * réouverture, alignement de démarrage).</p>
 */
@Service
public class HistoriqueModificationService {

    /** Longueur maximale d'une valeur en base ; au-delà, on tronque proprement. */
    private static final int LONGUEUR_MAX_VALEUR = 500;
    /** Longueur maximale du motif en base ; au-delà, on tronque proprement. */
    private static final int LONGUEUR_MAX_COMMENTAIRE = 500;

    /** Taille de page par défaut de la page « Historique ». */
    public static final int TAILLE_PAR_DEFAUT = 25;
    /** Plafond de taille de page (lecture seule : au-delà, le tableau s'essouffle). */
    public static final int TAILLE_MAXIMALE = 100;
    /** Délai au bout duquel une ligne quitte la vue active (jamais détruite). */
    public static final int MOIS_D_ARCHIVAGE = 12;
    /** Taille du fil « Activité récente » du tableau de bord. */
    public static final int ACTIVITE_RECENTE = 10;

    // ------------------------------------------------------------------
    // Types d'objet et champs (codes i18n côté interface)
    // ------------------------------------------------------------------

    public static final String TYPE_INITIATIVE = "initiative";
    public static final String TYPE_ETAPE = "etape";
    public static final String TYPE_JALON = "jalon";
    public static final String TYPE_DEMANDE = "demande";
    public static final String TYPE_UTILISATEUR = "utilisateur";
    public static final String TYPE_ROLE = "role";

    /** Types acceptés par la page « Historique » (filtre « type d'objet »). */
    public static final Set<String> TYPES = Set.of(
            TYPE_INITIATIVE, TYPE_ETAPE, TYPE_JALON,
            TYPE_DEMANDE, TYPE_UTILISATEUR, TYPE_ROLE);

    /** Types rattachés à une initiative : seuls eux alimentent le fil de la fiche. */
    public static final Set<String> TYPES_DE_FICHE = Set.of(
            TYPE_INITIATIVE, TYPE_ETAPE, TYPE_JALON);

    public static final String CHAMP_STATUT = "statut";
    public static final String CHAMP_CREATION = "creation";
    public static final String CHAMP_SUPPRESSION = "suppression";
    public static final String CHAMP_ARCHIVE = "archive";
    public static final String CHAMP_TRANSFORMATION = "transformation";
    public static final String CHAMP_PERMISSIONS = "permissions";
    public static final String CHAMP_MOT_DE_PASSE = "motDePasse";

    private final HistoriqueModificationRepository historiqueRepository;
    private final UtilisateurRepository utilisateurRepository;

    public HistoriqueModificationService(HistoriqueModificationRepository historiqueRepository,
                                         UtilisateurRepository utilisateurRepository) {
        this.historiqueRepository = historiqueRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    // ------------------------------------------------------------------
    // Filtres et résultats de lecture
    // ------------------------------------------------------------------

    /**
     * Filtres combinés de la lecture globale (tous facultatifs).
     *
     * @param recherche     fragment du code ou du nom d'initiative
     * @param type          type d'objet ({@code null} = tous)
     * @param champ         code de champ ({@code null} = tous)
     * @param idUtilisateur auteur
     * @param idInitiative  initiative filtrée (lien « Voir tout l'historique »)
     * @param du            début de période (jour inclus)
     * @param au            fin de période (jour inclus)
     * @param archives      {@code true} = vue « Archives » (lignes de plus de
     *                      {@value #MOIS_D_ARCHIVAGE} mois) ; {@code false} =
     *                      vue « Actives » (le reste)
     */
    public record FiltreHistorique(String recherche,
                                   String type,
                                   String champ,
                                   Long idUtilisateur,
                                   Long idInitiative,
                                   LocalDate du,
                                   LocalDate au,
                                   boolean archives) {
    }

    /** Une page du journal : lignes + total hors pagination. */
    public record PageJournal(List<HistoriqueModificationResponse> lignes, long total) {
    }

    // ------------------------------------------------------------------
    // Écriture — zone serveur uniquement
    // ------------------------------------------------------------------

    /**
     * Transition de statut d'une initiative — variante <strong>manuelle</strong>.
     *
     * @param ancienStatut statut avant la transition, {@code null} à la création
     * @param nouveauStatut statut après la transition
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
     * Transition de statut d'une initiative en précisant l'<strong>origine</strong>
     * du changement ({@code true} = moteur de règles).
     */
    @Transactional
    public void enregistrer(Initiative initiative,
                            String ancienStatut,
                            String nouveauStatut,
                            String commentaire,
                            String emailUtilisateur,
                            boolean automatique) {
        ecrire(TYPE_INITIATIVE,
                initiative.getIdInitiative(),
                initiative,
                CHAMP_STATUT,
                ancienStatut,
                nouveauStatut,
                commentaire,
                emailUtilisateur,
                automatique);
    }

    /**
     * Dépose une ligne quelconque du journal (zone serveur).
     *
     * @param typeObjet     type d'objet journalisé
     * @param idObjet       identifiant de l'objet
     * @param initiative    initiative de rattachement (entité gérée), ou
     *                      {@code null} pour demande / compte / rôle
     * @param champ         code du champ (ou événement : création, suppression…)
     * @param ancienne      valeur avant, {@code null} s'il n'y en a pas
     * @param nouvelle      valeur après, {@code null} s'il n'y en a pas
     * @param commentaire   motif facultatif
     * @param email         e-mail de l'auteur, {@code null} hors contexte
     * @param automatique   {@code true} = moteur de règles
     */
    @Transactional
    public void ecrire(String typeObjet,
                       Long idObjet,
                       Initiative initiative,
                       String champ,
                       String ancienne,
                       String nouvelle,
                       String commentaire,
                       String email,
                       boolean automatique) {
        HistoriqueModification entree = new HistoriqueModification();
        entree.setTypeObjet(typeObjet);
        entree.setIdObjet(idObjet);
        entree.setInitiative(initiative);
        entree.setChamp(champ);
        entree.setAncienneValeur(tronquer(ancienne, LONGUEUR_MAX_VALEUR));
        entree.setNouvelleValeur(tronquer(nouvelle, LONGUEUR_MAX_VALEUR));
        entree.setCommentaire(tronquer(commentaire, LONGUEUR_MAX_COMMENTAIRE));
        entree.setDateChangement(LocalDateTime.now());
        entree.setUtilisateur(resoudreUtilisateur(email));
        entree.setAutomatique(automatique);
        historiqueRepository.save(entree);
    }

    /**
     * Diff champ à champ : une ligne par champ réellement modifié (les
     * valeurs identiques ne produisent rien — la fiche ne se remplit pas
     * d'événements « En cours → En cours » à chaque retouche).
     *
     * <p>Les deux cartes doivent couvrir les mêmes clés : l'appelant les
     * construit avec le même helper avant / après application de la
     * modification.</p>
     *
     * @param avant  valeurs avant modification (code de champ → affichage)
     * @param apres  valeurs après modification
     */
    @Transactional
    public void diff(String typeObjet,
                     Long idObjet,
                     Initiative initiative,
                     Map<String, String> avant,
                     Map<String, String> apres,
                     String email,
                     boolean automatique) {
        for (Map.Entry<String, String> entree : avant.entrySet()) {
            String champ = entree.getKey();
            String valeurAvant = normaliserDiff(entree.getValue());
            String valeurApres = normaliserDiff(apres.get(champ));
            if (Objects.equals(valeurAvant, valeurApres)) {
                continue;
            }
            ecrire(typeObjet, idObjet, initiative, champ,
                    valeurAvant, valeurApres, null, email, automatique);
        }
    }

    /**
     * Normalisation d'une valeur de diff : l'absence et le vide sont la
     * même chose (« description » nulle à la création face à « » envoyée
     * par le formulaire) et on ignore les espaces de bord. Sans cela, le
     * journal se remplit de lignes parasites qui ne racontent aucun vrai
     * changement — la preuve d'audit ne doit montrer que ce qui a bougé.
     */
    private static String normaliserDiff(String valeur) {
        if (valeur == null) {
            return null;
        }
        String nettoyee = valeur.trim();
        return nettoyee.isEmpty() ? null : nettoyee;
    }

    /**
     * E-mail de l'utilisateur derrière l'appel courant, ou {@code null}
     * (appel interne, exécution de démarrage, test unitaire sans contexte).
     */
    public static String auteurCourant() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || authentification.getName() == null) {
            return null;
        }
        String nom = authentification.getName();
        return nom.isBlank() ? null : nom;
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    /**
     * Une page du journal global (lecture seule), filtrée et triée.
     *
     * <p>La fenêtre « Actives | Archives » est appliquée ici : la vue active
     * ne rend que les lignes de moins de {@value #MOIS_D_ARCHIVAGE} mois, la
     * vue archives le reste — combinée à la période « du / au » saisie par
     * l'utilisateur.</p>
     *
     * @param triAsc {@code false} = plus récent d'abord (défaut),
     *               {@code true} = plus ancien d'abord
     */
    @Transactional(readOnly = true)
    public PageJournal lister(FiltreHistorique filtre,
                              int numeroPage,
                              int taille,
                              boolean triAsc) {
        Sort tri = Sort.by(triAsc ? Sort.Direction.ASC : Sort.Direction.DESC, "dateChangement")
                .and(Sort.by(triAsc ? Sort.Direction.ASC : Sort.Direction.DESC, "idHistorique"));
        int tailleEffective = Math.min(Math.max(taille, 1), TAILLE_MAXIMALE);
        Page<HistoriqueModification> page = historiqueRepository.rechercher(
                jokersRecherche(filtre.recherche()), filtre.type(), filtre.champ(),
                filtre.idUtilisateur(), filtre.idInitiative(),
                fenetreVue(filtre), finVue(filtre),
                PageRequest.of(Math.max(0, numeroPage), tailleEffective, tri));
        return new PageJournal(
                page.map(HistoriqueModificationService::toResponse).getContent(),
                page.getTotalElements());
    }

    /**
     * Export CSV complet : mêmes filtres, aucune pagination — l'export porte
     * sur toutes les lignes de la vue, pas sur l'affichage courant.
     */
    @Transactional(readOnly = true)
    public List<HistoriqueModificationResponse> exporter(FiltreHistorique filtre) {
        return historiqueRepository.exporter(
                        jokersRecherche(filtre.recherche()), filtre.type(), filtre.champ(),
                        filtre.idUtilisateur(), filtre.idInitiative(),
                        fenetreVue(filtre), finVue(filtre))
                .stream()
                .map(HistoriqueModificationService::toResponse)
                .toList();
    }

    /**
     * Fil d'activité de la fiche d'initiative (initiative + étapes + jalons),
     * paginé : la fiche charge d'abord une page, puis « Charger plus ».
     *
     * @param type type d'objet à retenir, ou {@code null} = tous
     * @return la page courante + le total pour le bouton « Charger plus »
     */
    @Transactional(readOnly = true)
    public PageJournal historiqueInitiative(Long idInitiative, String type, int numeroPage, int taille) {
        if (type != null && !TYPES_DE_FICHE.contains(type)) {
            throw new BadRequestException("Type de filtre inconnu pour la fiche : « " + type
                    + " » (valeurs admises : " + String.join(", ", TYPES_DE_FICHE) + ").");
        }
        int tailleEffective = Math.min(Math.max(taille, 1), TAILLE_MAXIMALE);
        Page<HistoriqueModification> page = historiqueRepository.filInitiative(
                idInitiative, type,
                PageRequest.of(Math.max(0, numeroPage), tailleEffective,
                        Sort.by(Sort.Direction.DESC, "dateChangement")
                                .and(Sort.by(Sort.Direction.DESC, "idHistorique"))));
        return new PageJournal(
                page.map(HistoriqueModificationService::toResponse).getContent(),
                page.getTotalElements());
    }

    /**
     * « Activité récente » du tableau de bord : les {@value #ACTIVITE_RECENTE}
     * dernières lignes de la vue active, tous objets confondus — même filtres
     * d'archivage que la page « Historique », aucun autre critère.
     */
    @Transactional(readOnly = true)
    public List<HistoriqueModificationResponse> activiteRecente() {
        return lister(new FiltreHistorique(null, null, null, null, null, null, null, false),
                0, ACTIVITE_RECENTE, false).lignes();
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

    /**
     * Borne basse de la fenêtre de vue : la vue active commence à minuit il y
     * a {@value #MOIS_D_ARCHIVAGE} mois ; la vue archives n'a pas de borne
     * basse (combinaison avec la période « du » saisie).
     */
    private LocalDateTime fenetreVue(FiltreHistorique filtre) {
        if (filtre.archives()) {
            return borneDebut(filtre.du());
        }
        LocalDateTime debutActives = limiteArchivage();
        LocalDateTime debutUtilisateur = borneDebut(filtre.du());
        if (debutUtilisateur == null) {
            return debutActives;
        }
        return debutUtilisateur.isAfter(debutActives) ? debutUtilisateur : debutActives;
    }

    /**
     * Borne haute de la fenêtre de vue : la vue archives s'arrête à minuit il
     * a {@value #MOIS_D_ARCHIVAGE} mois (combinaison avec la période « au »).
     */
    private LocalDateTime finVue(FiltreHistorique filtre) {
        LocalDateTime limite = limiteArchivage();
        if (filtre.archives()) {
            // La vue archives S'ARRÊTE à la borne des 12 mois : sans cette
            // borne, elle ne serait qu'une répétition de la vue active. La
            // période « au » saisie peut resserrer encore (min des deux).
            LocalDateTime finUtilisateur = borneFin(filtre.au());
            if (finUtilisateur == null) {
                return limite;
            }
            return finUtilisateur.isBefore(limite) ? finUtilisateur : limite;
        }
        return borneFin(filtre.au());
    }

    /** Jour exact de bascule « actives | archives » (minuit, mois entiers). */
    private static LocalDateTime limiteArchivage() {
        return LocalDateTime.now().minusMonths(MOIS_D_ARCHIVAGE).toLocalDate().atStartOfDay();
    }

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

    /** Tronque une valeur ou un motif à la longueur de sa colonne. */
    private static String tronquer(String valeur, int longueurMax) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String nettoye = valeur.trim();
        return nettoye.length() > longueurMax
                ? nettoye.substring(0, longueurMax)
                : nettoye;
    }

    private static HistoriqueModificationResponse toResponse(HistoriqueModification h) {
        Initiative initiative = h.getInitiative();
        return new HistoriqueModificationResponse(
                h.getIdHistorique(),
                h.getTypeObjet(),
                h.getIdObjet(),
                h.getChamp(),
                h.getAncienneValeur(),
                h.getNouvelleValeur(),
                h.getCommentaire(),
                h.getDateChangement(),
                h.isAutomatique(),
                h.getUtilisateur() == null ? null : UtilisateurMapper.toSummary(h.getUtilisateur()),
                initiative == null || initiative.getIdInitiative() == null ? null
                        : new InitiativeSummaryDto(
                                initiative.getIdInitiative(),
                                initiative.getCode(),
                                initiative.getNom()));
    }
}
