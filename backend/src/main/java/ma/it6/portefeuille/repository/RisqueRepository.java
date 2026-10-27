package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Risque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RisqueRepository extends JpaRepository<Risque, Long> {

    /** Registre d'une initiative (tri par criticité : voir le service). */
    List<Risque> findByInitiativeIdInitiative(Long idInitiative);

    /**
     * Risques de plusieurs initiatives, initiative fetchée d'un coup
     * (le widget dashboard affiche code + nom sans N+1).
     */
    @Query("select r from Risque r join fetch r.initiative "
            + "where r.initiative.idInitiative in :ids")
    List<Risque> findByInitiatives(@Param("ids") List<Long> ids);
}
