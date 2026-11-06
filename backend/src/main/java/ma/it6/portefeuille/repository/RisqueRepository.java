package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Risque;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Registre global (toutes initiatives) : initiative + responsable
     * fetchés (pas de N+1). Les filtres sont optionnels.
     */
    @Query("""
            select r from Risque r
              join fetch r.initiative i
              left join fetch r.responsable s
             where (:idInitiative is null or i.idInitiative = :idInitiative)
               and (:statut is null or r.statut = :statut)
               and (:recherche is null
                    or lower(r.titre) like lower(concat('%', :recherche, '%'))
                    or lower(r.description) like lower(concat('%', :recherche, '%')))
            """)
    Page<Risque> rechercher(@Param("idInitiative") Long idInitiative,
                            @Param("statut") String statut,
                            @Param("recherche") String recherche,
                            Pageable pageable);

    /** Tous les risques non clos, initiative fetchée (calcul d'alertes). */
    @Query("select r from Risque r join fetch r.initiative i "
            + "where r.statut <> 'close' and i.archive = false")
    List<Risque> actifs();

    /** Mes risques (responsable du plan), non clos — « Mon espace ». */
    @Query("select r from Risque r join fetch r.initiative i "
            + "where r.statut <> 'close' and r.responsable.idUser = :idResponsable")
    List<Risque> mesRisques(@Param("idResponsable") Long idResponsable);
}
