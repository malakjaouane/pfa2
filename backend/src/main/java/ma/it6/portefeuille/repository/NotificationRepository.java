package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Chemin de propriété : Notification.utilisateur -> Utilisateur.idUser
    // (une propriété « idUtilisateur » n'existe pas sur l'entité : la
    // forme dérivée antérieure faisait échouer le démarrage du contexte).
    List<Notification> findByUtilisateurIdUserOrderByDateEnvoiDesc(Long idUtilisateur);
}
