package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.HistoriqueStatut;
import ma.it6.portefeuille.entity.Utilisateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HistoriqueStatutRepository extends JpaRepository<HistoriqueStatut, Long> {

    /**
     * Journal d'une initiative, du plus récent au plus ancien : c'est l'ordre
     * dans lequel la fiche détaillée l'affiche.
     */
    List<HistoriqueStatut> findByInitiativeIdInitiativeOrderByDateChangementDesc(Long idInitiative);

    /**
     * Journal global paginé de la page « Historique des statuts ».
     *
     * <p>Tous les filtres sont facultatifs ({@code null} = filtre absent).
     * L'initiative est récupérée en même temps (JOIN FETCH) car chaque ligne
     * du tableau partagé doit afficher code et nom sans second appel. La
     * collation {@code utf8mb4_unicode_ci} des tables rend LIKE insensible à la
     * casse et aux accents côté MySQL (« terminee » trouve « Terminée »).</p>
     *
     * @param recherche jokers {@code %...%} préparés par le service, ou {@code null}
     * @param finExclusive borne haute exclusive (lendemain 00 h de la date « au »)
     */
    @Query("""
            SELECT h FROM HistoriqueStatut h
              JOIN FETCH h.initiative i
              LEFT JOIN FETCH h.utilisateur u
            WHERE (:recherche IS NULL OR i.code LIKE :recherche OR i.nom LIKE :recherche)
              AND (:statut IS NULL OR h.nouveauStatut = :statut)
              AND (:idUtilisateur IS NULL OR u.idUser = :idUtilisateur)
              AND (:idInitiative IS NULL OR i.idInitiative = :idInitiative)
              AND (:debut IS NULL OR h.dateChangement >= :debut)
              AND (:finExclusive IS NULL OR h.dateChangement < :finExclusive)
            """)
    Page<HistoriqueStatut> rechercher(@Param("recherche") String recherche,
                                      @Param("statut") String statut,
                                      @Param("idUtilisateur") Long idUtilisateur,
                                      @Param("idInitiative") Long idInitiative,
                                      @Param("debut") LocalDateTime debut,
                                      @Param("finExclusive") LocalDateTime finExclusive,
                                      Pageable pageable);

    /**
     * Compteur associé à {@link #rechercher} : même WHERE, renvoyé en une
     * seule ligne. Attention : sur une méthode qui n'est PAS paginée, Spring
     * Data exécute uniquement la requête principale du {@code @Query} — il
     * faut donc écrire COUNT directement (le {@code countQuery} ne sert qu'aux
     * requêtes paginées).
     *
     * <p>Le LEFT JOIN sur l'auteur reproduit la requête de données : un événement
     * dont le compte a été supprimé (clé étrangère SET NULL) reste compté.</p>
     */
    @Query("""
            SELECT COUNT(h) FROM HistoriqueStatut h
              LEFT JOIN h.utilisateur u
            WHERE (:recherche IS NULL OR h.initiative.code LIKE :recherche OR h.initiative.nom LIKE :recherche)
              AND (:statut IS NULL OR h.nouveauStatut = :statut)
              AND (:idUtilisateur IS NULL OR u.idUser = :idUtilisateur)
              AND (:idInitiative IS NULL OR h.initiative.idInitiative = :idInitiative)
              AND (:debut IS NULL OR h.dateChangement >= :debut)
              AND (:finExclusive IS NULL OR h.dateChangement < :finExclusive)
            """)
    long compter(@Param("recherche") String recherche,
                 @Param("statut") String statut,
                 @Param("idUtilisateur") Long idUtilisateur,
                 @Param("idInitiative") Long idInitiative,
                 @Param("debut") LocalDateTime debut,
                 @Param("finExclusive") LocalDateTime finExclusive);

    /**
     * Utilisateurs réellement présents dans le journal, pour la liste
     * déroulante « utilisateur » : on ne propose que des choix qui mènent à un
     * résultat (et on évite d'exposer /api/users, réservé à l'administrateur).
     */
    @Query("""
            SELECT DISTINCT h.utilisateur FROM HistoriqueStatut h
            WHERE h.utilisateur IS NOT NULL
            ORDER BY h.utilisateur.nom, h.utilisateur.prenom
            """)
    List<Utilisateur> trouverAuteurs();
}
