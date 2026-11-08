package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** Accès aux jetons de rafraîchissement (rotation, révocation, purge). */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByJetonHash(String jetonHash);

    /** Révoque tous les jetons d'un compte (déconnexion, changement de mdp). */
    @Modifying
    @Query("delete from RefreshToken r where r.utilisateur.idUser = :idUser")
    void revoquerTout(@Param("idUser") Long idUser);

    /** Révoque tous les jetons d'un compte identifié par son e-mail. */
    @Modifying
    @Query("delete from RefreshToken r where r.utilisateur.email = :email")
    void revoquerToutParEmail(@Param("email") String email);

    /** Purge des jetons expirés depuis plus longtemps que le seuil. */
    @Modifying
    @Query("delete from RefreshToken r where r.dateExpiration < :seuil")
    void purgerExpire(@Param("seuil") LocalDateTime seuil);
}
