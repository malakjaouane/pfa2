package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.PreferenceNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenceNotificationRepository extends JpaRepository<PreferenceNotification, Long> {

    // Chemin de propriété : PreferenceNotification.utilisateur -> Utilisateur.idUser
    // (convention du dépôt : findByInitiativeIdInitiative, cf. EtapeRepository).
    Optional<PreferenceNotification> findByUtilisateurIdUser(Long idUtilisateur);
}
