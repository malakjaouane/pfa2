package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Risque d'une initiative (registre) : titre court, description libre,
 * probabilité et impact notés de 1 à 5 (Très faible / Très élevé),
 * statut de traitement, échéance cible, responsable du plan d'action et
 * plan d'action associé.
 *
 * <p>La <b>santé pondérée</b> (probabilité × impact, soit 1 à 25) n'est
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

    /** Cycle de vie du risque : ouverte (défaut) -> attenuation -> close. */
    public static final String STATUT_OUVERTE = "ouverte";
    public static final String STATUT_ATTENUATION = "attenuation";
    public static final String STATUT_CLOSE = "close";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_risque")
    private Long idRisque;

    /** Titre court, visible dans les listes et les widgets. */
    @Column(name = "titre", nullable = false, length = 150)
    private String titre;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    /** Statut de traitement : {@link #STATUT_OUVERTE} par défaut. */
    @Column(name = "statut", nullable = false, length = 20)
    private String statut;

    /** Date cible de traitement (pilote les alertes d'échéance). */
    @Column(name = "echeance")
    private LocalDate echeance;

    /** 1 = Très faible, 5 = Très élevé. */
    @Column(name = "probabilite", nullable = false)
    private Integer probabilite;

    /** 1 = Très faible, 5 = Très élevé. */
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
