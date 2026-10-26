package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.ConnexionResponse;
import ma.it6.portefeuille.entity.HistoriqueConnexion;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.UtilisateurMapper;
import ma.it6.portefeuille.repository.HistoriqueConnexionRepository;
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
 * Historique des connexions (traçabilité loi 09-08).
 *
 * <p>Chaque tentative de connexion — réussie ou échouée — dépose une ligne
 * ici depuis {@code POST /api/auth/login}. Le journal est écrit en
 * <strong>transaction propre</strong> : une panne d'écriture ne doit jamais
 * faire échouer une connexion pourtant valide (l'appelant absorbe l'erreur).</p>
 *
 * <p>La lecture et les suppressions sont réservées aux administrateurs
 * ({@code UTILISATEUR_GERER} imposé par SecurityConfig) — aucun nouveau
 * droit n'est créé.</p>
 */
@Service
public class HistoriqueConnexionService {

    /** Taille de page par défaut de la page « Historique des connexions ». */
    public static final int TAILLE_PAR_DEFAUT = 25;
    /** Plafond de taille de page (lecture seule : au-delà, le tableau s'essouffle). */
    public static final int TAILLE_MAXIMALE = 100;

    /** Longueur maximale du motif en base ; au-delà, on tronque proprement. */
    private static final int LONGUEUR_MAX_MOTIF = 200;

    private final HistoriqueConnexionRepository connexionRepository;
    private final UtilisateurRepository utilisateurRepository;

    public HistoriqueConnexionService(HistoriqueConnexionRepository connexionRepository,
                                      UtilisateurRepository utilisateurRepository) {
        this.connexionRepository = connexionRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * Filtres combinés de la lecture globale (tous facultatifs).
     *
     * @param recherche fragment d'e-mail, d'IP ou de nom
     * @param succes    {@code true} / {@code false}, {@code null} = les deux
     * @param du        début de période (jour inclus)
     * @param au        fin de période (jour inclus)
     */
    public record FiltreConnexion(String recherche,
                                  Boolean succes,
                                  LocalDate du,
                                  LocalDate au) {
    }

    /**
     * Dépose une tentative de connexion dans le journal.
     *
     * <p>Appelé par le contrôleur d'authentification, y compris pour les
     * échecs et les verrouillages 429. Le compte rattaché est résolu par
     * identifiant ; s'il n'existe plus, la ligne reste enregistrée sans
     * lien (clé étrangère SET NULL).</p>
     *
     * @param email e-mail saisi (trace conservée telle quelle)
     * @param idUtilisateur identifiant du compte, {@code null} si non identifié
     * @param adresseIp adresse IP du client
     * @param succes {@code true} si la connexion a abouti
     * @param motif  motif d'échec, {@code null} en cas de succès
     */
    @Transactional
    public void enregistrer(String email,
                            Long idUtilisateur,
                            String adresseIp,
                            boolean succes,
                            String motif) {
        HistoriqueConnexion entree = new HistoriqueConnexion();
        entree.setEmail(normaliserEmail(email));
        entree.setAdresseIp(adresseIp == null || adresseIp.isBlank() ? "inconnue" : adresseIp.trim());
        entree.setSucces(succes);
        entree.setMotif(tronquer(motif));
        entree.setUtilisateur(idUtilisateur == null
                ? null
                : utilisateurRepository.findById(idUtilisateur).orElse(null));
        connexionRepository.save(entree);
    }

    /**
     * Une page du journal des connexions (lecture seule), filtrée, triée
     * du plus récent au plus ancien.
     */
    @Transactional(readOnly = true)
    public List<ConnexionResponse> lister(FiltreConnexion filtre,
                                          int numeroPage,
                                          int taille) {
        int tailleEffective = Math.min(Math.max(taille, 1), TAILLE_MAXIMALE);
        Sort tri = Sort.by(Sort.Direction.DESC, "dateConnexion")
                .and(Sort.by(Sort.Direction.DESC, "idConnexion"));
        Page<HistoriqueConnexion> page = connexionRepository.rechercher(
                jokersRecherche(filtre.recherche()), filtre.succes(),
                borneDebut(filtre.du()), borneFin(filtre.au()),
                PageRequest.of(Math.max(0, numeroPage), tailleEffective, tri));
        return page.map(HistoriqueConnexionService::toResponse).getContent();
    }

    /** Nombre total de lignes pour les filtres donnés (hors pagination). */
    @Transactional(readOnly = true)
    public long compter(FiltreConnexion filtre) {
        return connexionRepository.compter(
                jokersRecherche(filtre.recherche()), filtre.succes(),
                borneDebut(filtre.du()), borneFin(filtre.au()));
    }

    /**
     * Suppression d'une ligne du journal — opération administrative réservée
     * aux comptes titulaires de {@code UTILISATEUR_GERER} (droit existant,
     * aucun nouveau droit créé). Une ligne inexistante lève
     * {@link ResourceNotFoundException} → 404.
     */
    @Transactional
    public void supprimer(Long idConnexion) {
        HistoriqueConnexion entree = connexionRepository.findById(idConnexion)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tentative de connexion introuvable : " + idConnexion));
        connexionRepository.delete(entree);
    }

    /**
     * Purge totale du journal des connexions — demandée par l'administrateur
     * depuis l'écran dédié (réservée à {@code UTILISATEUR_GERER}). Utilise un
     * {@code DELETE} en base en une seule requête (pas de chargement des
     * lignes), efficace même après des années de connexions.
     */
    @Transactional
    public void purgerTout() {
        connexionRepository.deleteAllInBatch();
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

    /** L'e-mail est stocké en minuscules, comme dans la table utilisateur. */
    private static String normaliserEmail(String email) {
        if (email == null || email.isBlank()) {
            return "inconnu";
        }
        String nettoye = email.trim().toLowerCase();
        return nettoye.length() > 150 ? nettoye.substring(0, 150) : nettoye;
    }

    private static String tronquer(String motif) {
        if (motif == null || motif.isBlank()) {
            return null;
        }
        String nettoye = motif.trim();
        return nettoye.length() > LONGUEUR_MAX_MOTIF
                ? nettoye.substring(0, LONGUEUR_MAX_MOTIF)
                : nettoye;
    }

    private static ConnexionResponse toResponse(HistoriqueConnexion c) {
        Utilisateur u = c.getUtilisateur();
        return new ConnexionResponse(
                c.getIdConnexion(),
                c.getDateConnexion(),
                c.getEmail(),
                u == null ? null : UtilisateurMapper.toSummary(u),
                c.getAdresseIp(),
                c.isSucces(),
                c.getMotif());
    }
}
