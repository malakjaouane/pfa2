package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.HistoriqueModification;
import ma.it6.portefeuille.entity.Utilisateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lecture du journal des modifications.
 *
 * <p>Toutes les recherches filtrent par <strong>fenêtre de vue</strong>
 * ({@code debut} / {@code finExclusive}) : la service y plonge soit la période
 * « actives » (lignes de moins de 12 mois), soit la période « archives »
 * (lignes plus anciennes), soit la période « du / au » saisie par
 * l'utilisateur — jamais plus d'un des trois à la fois.</p>
 */
public interface HistoriqueModificationRepository extends JpaRepository<HistoriqueModification, Long> {

    /**
     * Journal global paginé de la page « Historique ».
     *
     * <p>Tous les filtres sont facultatifs ({@code null} = filtre absent).
     * L'initiative et l'auteur sont récupérés en même temps (JOIN FETCH) car
     * chaque ligne du tableau partagé doit afficher son contexte sans second
     * appel. La collation {@code utf8mb4_unicode_ci} des tables rend LIKE
     * insensible à la casse et aux accents côté MySQL.</p>
     *
     * <p>Les lignes sans initiative (demande, utilisateur, rôle) restent
     * visibles : le LEFT JOIN ne les exclut que si un filtre exige l'objet.</p>
     *
     * @param recherche jokers {@code %...%} préparés par le service, ou {@code null}
     * @param finExclusive borne haute exclusive (lendemain 00 h de la date « au »)
     */
    @Query("""
            SELECT h FROM HistoriqueModification h
              LEFT JOIN FETCH h.initiative i
              LEFT JOIN FETCH h.utilisateur u
            WHERE (:recherche IS NULL OR i.code LIKE :recherche OR i.nom LIKE :recherche)
              AND (:type IS NULL OR h.typeObjet = :type)
              AND (:champ IS NULL OR h.champ = :champ)
              AND (:idUtilisateur IS NULL OR u.idUser = :idUtilisateur)
              AND (:idInitiative IS NULL OR i.idInitiative = :idInitiative)
              AND (:debut IS NULL OR h.dateChangement >= :debut)
              AND (:finExclusive IS NULL OR h.dateChangement < :finExclusive)
            """)
    Page<HistoriqueModification> rechercher(@Param("recherche") String recherche,
                                            @Param("type") String type,
                                            @Param("champ") String champ,
                                            @Param("idUtilisateur") Long idUtilisateur,
                                            @Param("idInitiative") Long idInitiative,
                                            @Param("debut") LocalDateTime debut,
                                            @Param("finExclusive") LocalDateTime finExclusive,
                                            Pageable pageable);

    /**
     * Export CSV complet : mêmes filtres que {@link #rechercher}, sans
     * pagination — l'export porte sur TOUTES les lignes filtrées, pas sur
     * l'affichage courant de la page.
     */
    @Query("""
            SELECT h FROM HistoriqueModification h
              LEFT JOIN FETCH h.initiative i
              LEFT JOIN FETCH h.utilisateur u
            WHERE (:recherche IS NULL OR i.code LIKE :recherche OR i.nom LIKE :recherche)
              AND (:type IS NULL OR h.typeObjet = :type)
              AND (:champ IS NULL OR h.champ = :champ)
              AND (:idUtilisateur IS NULL OR u.idUser = :idUtilisateur)
              AND (:idInitiative IS NULL OR i.idInitiative = :idInitiative)
              AND (:debut IS NULL OR h.dateChangement >= :debut)
              AND (:finExclusive IS NULL OR h.dateChangement < :finExclusive)
            ORDER BY h.dateChangement DESC, h.idHistorique DESC
            """)
    List<HistoriqueModification> exporter(@Param("recherche") String recherche,
                                          @Param("type") String type,
                                          @Param("champ") String champ,
                                          @Param("idUtilisateur") Long idUtilisateur,
                                          @Param("idInitiative") Long idInitiative,
                                          @Param("debut") LocalDateTime debut,
                                          @Param("finExclusive") LocalDateTime finExclusive);

    /**
     * Fil d'activité d'une initiative (fiche détaillée) : lignes de
     * l'initiative elle-même et de ses étapes / jalons, du plus récent au
     * plus ancien. Les lignes sans lien d'initiative (demande, compte, rôle)
     * n'apparaissent pas ici — elles vivent dans la page « Historique ».
     */
    @Query("""
            SELECT h FROM HistoriqueModification h
              LEFT JOIN FETCH h.utilisateur u
              WHERE h.initiative.idInitiative = :idInitiative
              AND (:type IS NULL OR h.typeObjet = :type)
            """)
    Page<HistoriqueModification> filInitiative(@Param("idInitiative") Long idInitiative,
                                               @Param("type") String type,
                                               Pageable pageable);

    /**
     * Utilisateurs réellement présents dans le journal, pour la liste
     * déroulante « auteur » : on ne propose que des choix qui mènent à un
     * résultat (et on évite d'exposer /api/users, réservé à
     * l'administrateur).
     */
    @Query("""
            SELECT DISTINCT h.utilisateur FROM HistoriqueModification h
            WHERE h.utilisateur IS NOT NULL
            ORDER BY h.utilisateur.nom, h.utilisateur.prenom
            """)
    List<Utilisateur> trouverAuteurs();
}
