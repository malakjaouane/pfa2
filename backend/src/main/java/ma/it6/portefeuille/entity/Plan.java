package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Plan d'abonnement (Phase 2D).
 *
 * <p>Définit les limites d'une organisation (nombre d'utilisateurs,
 * nombre d'initiatives, accès à l'assistant IA). Les limites sont
 * vérifiées côté serveur avant toute création d'initiative ou
 * d'utilisateur.</p>
 */
@Entity
@Table(name = "plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plan")
    private Long idPlan;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "nb_utilisateurs_max", nullable = false)
    private Integer nbUtilisateursMax;

    @Column(name = "nb_initiatives_max", nullable = false)
    private Integer nbInitiativesMax;

    @Column(name = "ia_incluse", nullable = false)
    private Boolean iaIncluse = false;

    /**
     * {@code created_at} a été retiré : il n'est lu nulle part dans le code
     * et n'existe dans aucune migration (V11 ne le crée pas). L'entité épouse
     * ici le schéma réel pour que l'application puisse démarrer sur la base
     * démo existante.
     */
}
