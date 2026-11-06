package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dépense déclarée sur une initiative : suivi budgétaire opérationnel
 * (contrôle de dépassement de budget côté tableau de bord).
 */
@Entity
@Table(name = "depense")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Depense extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_depense")
    private Long idDepense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_initiative")
    private Initiative initiative;

    @Column(name = "libelle", nullable = false, length = 150)
    private String libelle;

    /** Strictement positif (contrainte CHECK en base). */
    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_depense", nullable = false)
    private LocalDate dateDepense;

    @Column(name = "categorie", length = 50)
    private String categorie;

    /** Qui a saisi la dépense (anonymisé si le compte part). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_saisisseur")
    private Utilisateur saisisseur;
}
