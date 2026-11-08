package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Objet principal du portefeuille d'initiatives digitales.
 */
@Entity
@Table(name = "initiative")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Initiative extends BaseOrganisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_initiative")
    private Long idInitiative;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "nom", nullable = false, length = 200)
    private String nom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin_prevue")
    private LocalDate dateFinPrevue;

    @Column(name = "budget", nullable = false, precision = 12, scale = 2)
    private BigDecimal budget = BigDecimal.ZERO;

    @Column(name = "avancement", nullable = false)
    private Integer avancement = 0;

    @Column(name = "archive", nullable = false)
    private Boolean archive = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_entite")
    private Entite entite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable")
    private Utilisateur responsable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_statut")
    private StatutInitiative statut;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_priorite")
    private Priorite priorite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_objectif")
    private ObjectifStrategique objectif;

    @OneToMany(mappedBy = "initiative", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<Etape> etapes = new ArrayList<>();

    @OneToMany(mappedBy = "initiative", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Jalon> jalons = new ArrayList<>();
}