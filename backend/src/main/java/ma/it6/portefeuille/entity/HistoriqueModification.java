package ma.it6.portefeuille.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Journal générique des modifications — la preuve d'audit du portefeuille.
 *
 * <p>Une ligne = un <strong>champ</strong> d'un <strong>objet</strong> qui a
 * changé : l'ancienne valeur, la nouvelle, l'auteur, la date et l'origine
 * (manuelle ou automatique). Le même journal absorbe les transitions de statut
 * des initiatives (champ {@code statut}), la vie des étapes et jalons
 * (création / modification / suppression), le workflow des demandes, et
 * l'administration (comptes, rôles, permissions).</p>
 *
 * <p>Le journal est <strong>immuable</strong> : aucune écriture utilisateur
 * n'est exposée, aucune suppression n'existe. Les lignes de plus de 12 mois
 * quittent simplement la vue active (archivage calculé sur la date — jamais de
 * destruction).</p>
 *
 * <p>Les valeurs sont stockées en clair (jamais par clé étrangère métier) :
 * le journal doit rester lisible même si le référentiel est retouché. Les
 * libellés de statut sont copiés au moment du changement.</p>
 *
 * <p>Lien avec l'initiative : les lignes de type {@code initiative},
 * {@code etape} et {@code jalon} portent l'initiative d'appartenance (clé en
 * {@code ON DELETE CASCADE} : l'objet parti, son fil part aussi). Les lignes
 * {@code demande}, {@code utilisateur} et {@code role} n'ont aucun lien
 * d'initiative : elles survivent à la suppression de n'importe quelle
 * initiative.</p>
 */
@Entity
@Table(name = "historique_modification")
@Getter
@Setter
@NoArgsConstructor
public class HistoriqueModification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historique")
    private Long idHistorique;

    /**
     * Type d'objet journalisé, en minuscules : {@code initiative},
     * {@code etape}, {@code jalon}, {@code demande}, {@code utilisateur},
     * {@code role}. Valeur en clair (jamais une clé étrangère).
     */
    @Column(name = "type_objet", nullable = false, length = 30)
    private String typeObjet;

    /** Identifiant de l'objet concerné, dans son propre référentiel. */
    @Column(name = "id_objet", nullable = false)
    private Long idObjet;

    /**
     * Champ modifié ({@code statut}, {@code budget}, {@code creation}…).
     * C'est le code i18n côté interface : chaque clé a un libellé traduit.
     */
    @Column(name = "champ", nullable = false, length = 60)
    private String champ;

    /** Valeur avant changement — {@code null} à la création. */
    @Column(name = "ancienne_valeur", length = 500)
    private String ancienneValeur;

    /** Valeur après changement — {@code null} à la suppression. */
    @Column(name = "nouvelle_valeur", length = 500)
    private String nouvelleValeur;

    /** Motif facultatif posé par le domaine (réouverture, refus, archivage…). */
    @Column(name = "commentaire", length = 500)
    private String commentaire;

    @Column(name = "date_changement", nullable = false)
    private LocalDateTime dateChangement;

    /**
     * Origine : {@code false} = action explicite d'un utilisateur,
     * {@code true} = moteur de règles (RM07-auto, réouverture, alignement).
     */
    @Column(name = "automatique", nullable = false)
    private boolean automatique;

    /** Auteur — {@code null} hors contexte (seeds, compte depuis supprimé). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    /**
     * Initiative de rattachement (uniquement types initiative / etape /
     * jalon) : lien de navigation pour le fil de la fiche. En
     * {@code ON DELETE CASCADE}, contrairement à l'auteur.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_initiative")
    private Initiative initiative;
}
