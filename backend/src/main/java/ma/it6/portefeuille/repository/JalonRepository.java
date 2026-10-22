package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Jalon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface JalonRepository extends JpaRepository<Jalon, Long> {

    List<Jalon> findByInitiativeIdInitiativeOrderByDatePrevueAsc(Long idInitiative);

    /**
     * KPI « jalons en retard » : date prévue passée et jalon non atteint,
     * restreint aux initiatives fournies (les filtres du dashboard sont donc
     * appliqués aux jalons comme aux initiatives).
     */
    @Query("""
            select count(j) from Jalon j
            where j.datePrevue < :date
              and (j.statutJalon is null or j.statutJalon.libelle <> 'Atteint')
              and j.initiative.idInitiative in :ids
            """)
    long countEnRetardParInitiatives(@Param("date") LocalDate date,
                                     @Param("ids") Collection<Long> ids);

    /**
     * KPI « jalons cette semaine » (message d'accueil) : jalons non atteints
     * dont la date prévue tombe dans l'intervalle fourni, restreints aux
     * initiatives sélectionnées — mêmes filtres que les autres KPI.
     */
    @Query("""
            select count(j) from Jalon j
            where j.datePrevue >= :du and j.datePrevue <= :au
              and (j.statutJalon is null or j.statutJalon.libelle <> 'Atteint')
              and j.initiative.idInitiative in :ids
            """)
    long countEntreDatesParInitiatives(@Param("du") LocalDate du,
                                       @Param("au") LocalDate au,
                                       @Param("ids") Collection<Long> ids);

    /**
     * Score de santé : jalons (et statut) de toute une page d'initiatives
     * chargés en une seule requête — évite le N+1 du calcul par ligne.
     */
    @Query("""
            select j from Jalon j
            join fetch j.statutJalon
            where j.initiative.idInitiative in :ids
            """)
    List<Jalon> chercherParInitiatives(@Param("ids") Collection<Long> ids);
}
