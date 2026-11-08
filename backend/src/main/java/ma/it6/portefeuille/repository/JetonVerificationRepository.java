package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.JetonVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Acces aux jetons de verification d'adresse e-mail. */
public interface JetonVerificationRepository extends JpaRepository<JetonVerification, Long> {

    Optional<JetonVerification> findByJetonHash(String jetonHash);

    /** Supprime les jetons (utilises ou non) d'une adresse e-mail. */
    void deleteByEmail(String email);
}
