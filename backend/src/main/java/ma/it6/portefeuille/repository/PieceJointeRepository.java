package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.PieceJointe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PieceJointeRepository extends JpaRepository<PieceJointe, Long> {

    Optional<PieceJointe> findByIdPieceJointe(Long idPieceJointe);

    // Chemins de propriété : PieceJointe.initiative / .commentaire (M2O)
    // -> clé nichée de l'entité cible (convention : findByInitiativeIdInitiative).
    List<PieceJointe> findByInitiativeIdInitiative(Long idInitiative);

    List<PieceJointe> findByCommentaireIdCommentaire(Long idCommentaire);

    long countByIdOrganisation(Long idOrganisation);
}
