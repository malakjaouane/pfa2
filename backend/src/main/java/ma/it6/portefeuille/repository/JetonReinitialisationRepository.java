package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.JetonReinitialisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Acces aux jetons de reinitialisation de mot de passe. */
public interface JetonReinitialisationRepository extends JpaRepository<JetonReinitialisation, Long> {

    Optional<JetonReinitialisation> findByJetonHash(String jetonHash);

    /** Supprime les jetons (utilises ou non) d'une adresse e-mail. */
    void deleteByEmail(String email);
}
