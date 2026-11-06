package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Commentaire sur une initiative : fil de discussion autour du dossier.
 *
 * <p>Un commentaire est <b>immuable</b> (aucune mise à jour) et peut être
 * supprimé. L'auteur est anonymisé ({@code SET NULL}) si son compte part :
 * le fil reste consultable.</p>
 */
@Entity
@Table(name = "commentaire")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commentaire extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_commentaire")
    private Long idCommentaire;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_initiative")
    private Initiative initiative;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_auteur")
    private Utilisateur auteur;

    @Column(name = "contenu", columnDefinition = "TEXT", nullable = false)
    private String contenu;
}
