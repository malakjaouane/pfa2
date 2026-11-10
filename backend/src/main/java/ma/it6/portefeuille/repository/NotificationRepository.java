package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByIdUtilisateurOrderByDateEnvoiDesc(Long idUtilisateur);
}
