package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Organisation (tenant) du mode SaaS.
 *
 * <p>Chaque organisation est un silo de donnees etanche : les utilisateurs,
 * initiatives, entites, objectifs, risques, depenses, commentaires et
 * journaux lui appartiennent exclusivement. L'isolation est posee au niveau
 * physique (colonne {@code id_organisation} NOT NULL + cle etrangere sur
 * chaque table metier, migration V5) et au niveau requete (filtre Hibernate
 * {@code filtreParOrganisation} active par l'intercepteur
 * {@code OrganisationIsolationInterceptor}).
 */
@Entity
@Table(name = "organisation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organisation extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_organisation")
    private Long idOrganisation;

    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "plan", nullable = false, length = 40)
    private String plan = "gratuit";

    @Column(name = "statut", nullable = false, length = 40)
    private String statut = "active";
}
