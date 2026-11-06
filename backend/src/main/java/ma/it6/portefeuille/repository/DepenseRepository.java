package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Depense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DepenseRepository extends JpaRepository<Depense, Long> {

    /**
     * Registre filtré : initiative, catégorie, période, recherche — sans
     * N+1 (initiative et saisisseur fetchés). Un {@code null} désactive
     * simplement le filtre correspondant.
     */
    @Query("""
            select d from Depense d
              join fetch d.initiative i
              left join fetch d.saisisseur s
             where (:idInitiative is null or i.idInitiative = :idInitiative)
               and (:categorie is null or d.categorie = :categorie)
               and (:du is null or d.dateDepense >= :du)
               and (:au is null or d.dateDepense <= :au)
               and (:recherche is null
                    or lower(d.libelle) like lower(concat('%', :recherche, '%')))
            """)
    Page<Depense> rechercher(@Param("idInitiative") Long idInitiative,
                             @Param("categorie") String categorie,
                             @Param("du") LocalDate du,
                             @Param("au") LocalDate au,
                             @Param("recherche") String recherche,
                             Pageable pageable);

    /** Dépenses d'une initiative (plus récentes d'abord). */
    List<Depense> findTop50ByInitiativeIdInitiativeOrderByDateDepenseDesc(
            Long idInitiative);

    /** Consommé total d'une contrôle de dépassement de budget. */
    @Query("select coalesce(sum(d.montant), 0) from Depense d "
            + "where d.initiative.idInitiative = :id")
    BigDecimal totalParInitiative(@Param("id") Long idInitiative);

    /** Consommé d'une initiative, sur une liste (widget de portefeuille). */
    @Query("select coalesce(sum(d.montant), 0) from Depense d "
            + "where d.initiative.idInitiative in :ids")
    BigDecimal totalParInitiatives(@Param("ids") List<Long> ids);

    /**
     * Montant total des lignes retenues par les mêmes filtres que la page
     * — le total affiché à côté du registre porte sur TOUTE la sélection,
     * pas sur la page courante (agrégat SQL, sans plafond de lecture).
     */
    @Query("""
            select coalesce(sum(d.montant), 0) from Depense d
             where (:idInitiative is null or d.initiative.idInitiative = :idInitiative)
               and (:categorie is null or d.categorie = :categorie)
               and (:du is null or d.dateDepense >= :du)
               and (:au is null or d.dateDepense <= :au)
               and (:recherche is null
                    or lower(d.libelle) like lower(concat('%', :recherche, '%')))
            """)
    BigDecimal totalFiltre(@Param("idInitiative") Long idInitiative,
                           @Param("categorie") String categorie,
                           @Param("du") LocalDate du,
                           @Param("au") LocalDate au,
                           @Param("recherche") String recherche);
}
