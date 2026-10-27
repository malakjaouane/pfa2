package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Risque d'une initiative (registre) : description libre, probabilité et
 * impact notés de 1 à 3 (Faible / Moyen / Élevé), responsable du plan
 * d'action et plan d'action associé.
 *
 * <p>La <b>santé pondérée</b> (probabilité × impact, soit 1 à 9) n'est
 * jamais stockée : elle est recalculée à chaque lecture par le mapper,
 * ce qui interdit toute divergence entre la valeur affichée et la valeur
 * réelle.</p>
 */
@Entity
@Table(name = "risque")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Risque extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_risque")
    private Long idRisque;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    /** 1 = Faible, 2 = Moyen, 3 = Élevé. */
    @Column(name = "probabilite", nullable = false)
    private Integer probabilite;

    /** 1 = Faible, 2 = Moyen, 3 = Élevé. */
    @Column(name = "impact", nullable = false)
    private Integer impact;

    @Column(name = "plan_action", columnDefinition = "TEXT")
    private String planAction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_initiative")
    private Initiative initiative;

    /** Qui pilote le plan d'action (optionnel). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable")
    private Utilisateur responsable;
}
