package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.PieceJointe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PieceJointeRepository extends JpaRepository<PieceJointe, Long> {

    Optional<PieceJointe> findByIdPieceJointe(Long idPieceJointe);

    List<PieceJointe> findByIdInitiative(Long idInitiative);

    List<PieceJointe> findByIdCommentaire(Long idCommentaire);

    long countByIdOrganisation(Long idOrganisation);
}
