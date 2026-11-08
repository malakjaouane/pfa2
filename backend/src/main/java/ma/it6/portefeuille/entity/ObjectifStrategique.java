package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Objectif stratégique auquel une initiative peut être rattachée
 * (alignement Jira Align : initiatives contribuant à un même objectif).
 */
@Entity
@Table(name = "objectif_strategique")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObjectifStrategique extends BaseOrganisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_objectif")
    private Long idObjectif;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 200)
    private String libelle;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}