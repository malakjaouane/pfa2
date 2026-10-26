package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.DemandeInitiative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DemandeInitiativeRepository extends JpaRepository<DemandeInitiative, Long> {

    /**
     * Liste complète avec relations éager (fetch join) et filtre de
     * recherche libre : titre, description du besoin ou justification
     * — le joker {@code %...%} est construit par le service ; {@code null}
     * = sans filtre. La collation {@code utf8mb4_unicode_ci} rend le
     * {@code LIKE} insensible à la casse et aux accents.
     */
    @Query("""
            select distinct d from DemandeInitiative d
            left join fetch d.entite
            left join fetch d.demandeur
            left join fetch d.priorite
            left join fetch d.statut
            left join fetch d.initiativeLiee
            where (:recherche is null
                   or d.titre like :recherche
                   or d.descriptionBesoin like :recherche
                   or d.justificationMetier like :recherche)
            order by d.dateSoumission desc, d.idDemande desc
            """)
    List<DemandeInitiative> findAllFetch(@Param("recherche") String recherche);

    /**
     * Nombre de demandes portées par un compte (demandeur) — garde-fou de
     * la suppression d'un utilisateur : la clé étrangère est sans cascade,
     * un compte qui porte encore des demandes se désactive mais ne se
     * supprime pas.
     */
    long countByDemandeur_IdUser(Long idUser);
}