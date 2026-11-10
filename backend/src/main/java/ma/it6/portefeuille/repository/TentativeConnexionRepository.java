package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.TentativeConnexion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Accès aux tentatives de connexion (rate-limit persistant, Phase 1 C3). */
public interface TentativeConnexionRepository extends JpaRepository<TentativeConnexion, Long> {

    /** Nombre d'échecs pour une clé dans la fenêtre glissante. */
    @Query("""
        select count(t) from TentativeConnexion t
        where t.typeCle = :typeCle
          and t.cle = :cle
          and t.succes = false
          and t.dateTentative >= :debutFenetre
        """)
    long compterEchecsDansFenetre(
            @Param("typeCle") String typeCle,
            @Param("cle") String cle,
            @Param("debutFenetre") LocalDateTime debutFenetre);

    /** Supprime les tentatives trop anciennes (purge opportuniste). */
    @Modifying
    @Query("""
        delete from TentativeConnexion t
        where t.dateTentative < :seuil
        """)
    long purgerAvant(@Param("seuil") LocalDateTime seuil);

    /** Efface toutes les tentatives d'un compte (connexion réussie). */
    @Modifying
    @Query("""
        delete from TentativeConnexion t
        where t.typeCle = 'COMPTE'
          and t.cle = :cle
        """)
    void reinitialiserCompte(@Param("cle") String cle);

    /** Toutes les tentatives d'une clé (pour les tests). */
    List<TentativeConnexion> findByTypeCleAndCleOrderByDateTentativeDesc(
            String typeCle, String cle);
}
