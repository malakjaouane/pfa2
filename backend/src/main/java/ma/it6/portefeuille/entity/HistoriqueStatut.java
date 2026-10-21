package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Journal chronologique des transitions de statut d'une initiative.
 *
 * <p>Le portefeuille est un outil de pilotage partagé : savoir <em>qui</em> a
 * passé une initiative « En pause » et <em>pourquoi</em> fait partie du
 * livrable, au même titre que l'avancement.</p>
 *
 * <p>Les libellés de statut sont copiés en clair (et non référencés par clé
 * étrangère) : le journal doit rester lisible même si le référentiel de
 * statuts est retouché par la suite.</p>
 */
@Entity
@Table(name = "historique_statut")
@Getter
@Setter
@NoArgsConstructor
public class HistoriqueStatut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historique")
    private Long idHistorique;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_initiative", nullable = false)
    private Initiative initiative;

    /** Libellé du statut avant la transition ; {@code null} à la création. */
    @Column(name = "ancien_statut", length = 50)
    private String ancienStatut;

    @Column(name = "nouveau_statut", nullable = false, length = 50)
    private String nouveauStatut;

    /** Motif saisi par l'utilisateur (facultatif). */
    @Column(name = "commentaire", length = 500)
    private String commentaire;

    @Column(name = "date_changement", nullable = false)
    private LocalDateTime dateChangement;

    /**
     * Origine du changement : {@code true} lorsque la transition a été
     * déclenchée par le moteur de règles (RM07-auto, réouverture, RM17,
     * alignement au démarrage), {@code false} pour une action explicite
     * d'un utilisateur (formulaire, Kanban, archivage).
     *
     * <p>Le journal étant en lecture seule côté interface, cette colonne
     * alimente l'affichage « manuel / automatique » de la page
     * « Historique des statuts » sans jamais être modifiable.</p>
     */
    @Column(name = "automatique", nullable = false)
    private boolean automatique;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;
}
