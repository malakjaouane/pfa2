package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Initiative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InitiativeRepository extends JpaRepository<Initiative, Long>, JpaSpecificationExecutor<Initiative> {

    Optional<Initiative> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdInitiativeNot(String code, Long idInitiative);

    long countByIdOrganisation(Long idOrganisation);

    /**
     * Nombre d'initiatives rattachées à un objectif stratégique —
     * garde-fou de la suppression d'un objectif : la clé étrangère est en
     * {@code ON DELETE SET NULL}, le serveur refuse donc plutôt que de
     * détacher silencieusement les initiatives.
     */
    long countByObjectif_IdObjectif(Long idObjectif);

    @Query("""
            select distinct i from Initiative i
            left join fetch i.entite
            left join fetch i.responsable
            left join fetch i.statut
            left join fetch i.priorite
            """)
    List<Initiative> findAllFetch();

    Optional<Initiative> findByIdInitiative(Long idInitiative);

    /**
     * Initiatives dont les dépenses dépassent le budget — alimente
     * l'alerte {@code budgetDepasse} en une seule requête (agrégat de
     * dépenses comparé au budget, pas de boucle applicative).
     */
    @Query("""
            select i from Initiative i
             where i.archive = false
               and i.budget is not null
               and (select coalesce(sum(d.montant), 0)
                      from Depense d where d.initiative = i) > i.budget
            """)
    List<Initiative> budgetDepasse();

    /**
     * Initiatives pilotées par un responsable (« Mon espace »), hors
     * archives — relations du mapper fetchées d'un coup (pas de N+1 sur
     * les listes de la fiche personnelle).
     */
    @Query("""
            select distinct i from Initiative i
              left join fetch i.entite
              left join fetch i.responsable
              left join fetch i.statut
              left join fetch i.priorite
              left join fetch i.objectif
             where i.archive = false
               and i.responsable.idUser = :idResponsable
             order by i.dateFinPrevue asc nulls last, i.idInitiative asc
            """)
    List<Initiative> parResponsable(@Param("idResponsable") Long idResponsable);

    /**
     * Fiche « étapes » pour le résumé IA : une seule collection fetchée
     * (interdiction JPA de fetcher deux List bag d'un coup — les jalons
     * sont chargés à part via {@code JalonRepository.chercherParInitiatives}).
     */
    @Query("""
            select distinct i from Initiative i
            left join fetch i.entite
            left join fetch i.responsable
            left join fetch i.statut
            left join fetch i.priorite
            left join fetch i.etapes e
            left join fetch e.statutEtape
            where i.idInitiative = :id
            """)
    Optional<Initiative> findByIdInitiativeAvecEtapes(@Param("id") Long idInitiative);

    /**
     * Initiatives potentiellement en retard : non archivées, date de fin
     * prévue dépassée, statut != Terminée et avancement &lt; 100 %.
     */
    @Query("""
            select distinct i from Initiative i
            left join fetch i.entite
            left join fetch i.responsable
            left join fetch i.statut
            left join fetch i.priorite
            where i.archive = false
              and i.dateFinPrevue is not null
              and i.dateFinPrevue < :today
              and i.statut.libelle <> 'Terminée'
              and i.avancement < 100
            order by i.dateFinPrevue asc
            """)
    List<Initiative> findEnRetard(@Param("today") LocalDate today);
}
