package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Journal des requêtes adressées à l'assistant IA (« Demande à ton
 * portefeuille »).
 *
 * <p>Traçabilité complète exigée par le cahier des charges et la loi
 * 09-08 / CNDP : chaque question posée ou résumé généré est enregistré
 * avec son auteur, son statut (réussite, erreur, assistant désactivé) et
 * sa durée d'exécution. Le journal est consultable par les
 * administrateurs et n'est modifiable par personne.</p>
 *
 * <p>La demande et la réponse sont conservées telles quelles (tronquées à
 * des tailles raisonnables) : l'administrateur doit pouvoir vérifier ce
 * qui a réellement été transmis au modèle.</p>
 */
@Entity
@Table(name = "journal_ia")
@Getter
@Setter
@NoArgsConstructor
public class JournalIa {

    /** Type de requête : question conversationnelle. */
    public static final String TYPE_QUESTION = "QUESTION";
    /** Type de requête : résumé d'une initiative. */
    public static final String TYPE_RESUME = "RESUME";

    /** Statut : réponse obtenue du modèle. */
    public static final String STATUT_OK = "OK";
    /** Statut : appel au modèle en échec (réseau, quota, refus). */
    public static final String STATUT_ERREUR = "ERREUR";
    /** Statut : requête refusée avant appel (kill switch, clé absente). */
    public static final String STATUT_NON_DISPONIBLE = "NON_DISPONIBLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ia")
    private Long idIa;

    /** Auteur de la requête ; {@code null} si le compte est supprimé. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Column(name = "type_requete", nullable = false, length = 20)
    private String typeRequete;

    /** Question posée, ou libellé du résumé demandé (« Résumé de … »). */
    @Column(name = "demande", nullable = false, length = 2000)
    private String demande;

    /** Réponse du modèle ; {@code null} si la requête a échoué. */
    @Column(name = "reponse", columnDefinition = "TEXT")
    private String reponse;

    /** Initiative concernée pour un résumé ; {@code null} pour une question. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_initiative")
    private Initiative initiative;

    @Column(name = "modele", length = 50)
    private String modele;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut;

    /** Motif d'échec en clair (jamais la clé API). */
    @Column(name = "message_erreur", length = 300)
    private String messageErreur;

    @Column(name = "duree_ms")
    private Long dureeMs;

    @Column(name = "date_requete", nullable = false)
    private LocalDateTime dateRequete;
}
