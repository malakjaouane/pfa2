package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Acces aux invitations de collegues. */
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByJetonHash(String jetonHash);

    /** Supprime les invitations (utilisees ou non) d'une adresse e-mail. */
    void deleteByEmail(String email);
}
