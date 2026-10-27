package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Demande d'initiative digitale (inspiration ServiceNow SPM), distincte
 * des initiatives du portefeuille. Lorsqu'une demande « Acceptée » est
 * transformée en initiative, la liaison est enregistrée dans
 * {@code id_initiative_liee} (unique : une demande ne génère qu'une
 * initiative, aucun doublon).
 */
@Entity
@Table(name = "demande_initiative")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeInitiative extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_demande")
    private Long idDemande;

    @Column(name = "titre", nullable = false, length = 200)
    private String titre;

    @Column(name = "description_besoin", columnDefinition = "TEXT")
    private String descriptionBesoin;

    @Column(name = "justification_metier", columnDefinition = "TEXT")
    private String justificationMetier;

    @Column(name = "motif_refus", columnDefinition = "TEXT")
    private String motifRefus;

    @Column(name = "date_soumission", nullable = false)
    private LocalDate dateSoumission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_entite")
    private Entite entite;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_demandeur")
    private Utilisateur demandeur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_priorite")
    private Priorite priorite;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_statut_demande")
    private StatutDemande statut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_initiative_liee")
    private Initiative initiativeLiee;

    /**
     * Vue « Archives » de la bascule Actives | Archives (mêmes règles que
     * {@code initiative.archive}) : l'archivage masque la demande de la
     * liste active SANS changer son statut de workflow, et reste
     * réversible (« Restaurer »).
     */
    @Column(name = "archive", nullable = false)
    private boolean archive;
}