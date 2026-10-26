package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.HistoriqueConnexion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface HistoriqueConnexionRepository extends JpaRepository<HistoriqueConnexion, Long> {

    /**
     * Journal des connexions paginé de la page « Historique des connexions ».
     *
     * <p>Tous les filtres sont facultatifs ({@code null} = filtre absent).
     * Le compte est récupéré en même temps (JOIN FETCH) pour afficher le
     * nom sans second appel. La collation {@code utf8mb4_unicode_ci} rend
     * LIKE insensible à la casse et aux accents côté MySQL.</p>
     *
     * @param recherche  jokers {@code %...%} préparés par le service, ou {@code null}
     * @param succes     {@code true} / {@code false}, ou {@code null} pour les deux
     * @param debut      début de période (jour inclus), ou {@code null}
     * @param finExclusive borne haute exclusive (lendemain 00 h de la date « au »)
     */
    @Query("""
            SELECT c FROM HistoriqueConnexion c
              LEFT JOIN FETCH c.utilisateur u
            WHERE (:recherche IS NULL OR c.email LIKE :recherche OR c.adresseIp LIKE :recherche
                   OR u.nom LIKE :recherche OR u.prenom LIKE :recherche)
              AND (:succes IS NULL OR c.succes = :succes)
              AND (:debut IS NULL OR c.dateConnexion >= :debut)
              AND (:finExclusive IS NULL OR c.dateConnexion < :finExclusive)
            """)
    Page<HistoriqueConnexion> rechercher(@Param("recherche") String recherche,
                                         @Param("succes") Boolean succes,
                                         @Param("debut") LocalDateTime debut,
                                         @Param("finExclusive") LocalDateTime finExclusive,
                                         Pageable pageable);

    /**
     * Compteur associé à {@link #rechercher} : même WHERE, renvoyé en une
     * seule ligne (le {@code countQuery} ne sert qu'aux requêtes paginées,
     * or la page a besoin du total hors pagination pour X-Total-Count).
     *
     * <p>Le LEFT JOIN reproduit la requête de données : une tentative dont
     * le compte a été supprimé (clé étrangère SET NULL) reste comptée.</p>
     */
    @Query("""
            SELECT COUNT(c) FROM HistoriqueConnexion c
              LEFT JOIN c.utilisateur u
            WHERE (:recherche IS NULL OR c.email LIKE :recherche OR c.adresseIp LIKE :recherche
                   OR u.nom LIKE :recherche OR u.prenom LIKE :recherche)
              AND (:succes IS NULL OR c.succes = :succes)
              AND (:debut IS NULL OR c.dateConnexion >= :debut)
              AND (:finExclusive IS NULL OR c.dateConnexion < :finExclusive)
            """)
    long compter(@Param("recherche") String recherche,
                 @Param("succes") Boolean succes,
                 @Param("debut") LocalDateTime debut,
                 @Param("finExclusive") LocalDateTime finExclusive);
}
