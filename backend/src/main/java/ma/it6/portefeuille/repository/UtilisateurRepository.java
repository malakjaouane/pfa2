package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdUserNot(String email, Long idUser);

    long countByIdOrganisation(Long idOrganisation);

    /**
     * Charge un utilisateur avec son rôle et ses permissions (autorisations
     * utilisées par Spring Security).
     */
    @Query("""
            select distinct u from Utilisateur u
            join fetch u.role r
            left join fetch u.entite
            left join fetch r.rolePermissions rp
            left join fetch rp.permission
            where u.email = :email
            """)
    Optional<Utilisateur> findByEmailWithPermissions(@Param("email") String email);

    /**
     * Comptes <strong>actifs</strong> portant un rôle donné.
     * Sert au garde-fou « dernier administrateur ».
     */
    boolean existsByIdUserNotAndActifTrueAndRoleCode(Long idUser, String codeRole);

    List<Utilisateur> findAllByOrderByIdUserAsc();
}