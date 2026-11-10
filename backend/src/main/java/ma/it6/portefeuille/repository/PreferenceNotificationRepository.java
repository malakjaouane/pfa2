package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.PreferenceNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenceNotificationRepository extends JpaRepository<PreferenceNotification, Long> {

    Optional<PreferenceNotification> findByIdUtilisateur(Long idUtilisateur);
}
