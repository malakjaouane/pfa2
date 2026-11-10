package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Pièce jointe attachée à une initiative ou à un commentaire.
 *
 * <p>Le fichier binaire n'est pas stocké en base : seuls son nom, son type
 * MIME, sa taille et sa clé de stockage le sont. Le binaire réside dans le
 * {@link ma.it6.portefeuille.storage.StockageFichier} configuré (système de
 * fichiers local ou Amazon S3).</p>
 *
 * <p>L'isolation par organisation est assurée par {@link BaseOrganisation}
 * ({@code id_organisation} + filtre Hibernate {@code filtreParOrganisation}).
 */
@Entity
@Table(name = "piece_jointe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PieceJointe extends BaseOrganisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_piece_jointe")
    private Long idPieceJointe;

    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    @Column(name = "type_mime", nullable = false, length = 100)
    private String typeMime;

    @Column(name = "taille_octets", nullable = false)
    private Long tailleOctets;

    @Column(name = "cle_stockage", nullable = false, length = 500)
    private String cleStockage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_initiative")
    private Initiative initiative;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_commentaire")
    private Commentaire commentaire;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PieceJointe)) return false;
        PieceJointe that = (PieceJointe) o;
        return Objects.equals(idPieceJointe, that.idPieceJointe);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idPieceJointe);
    }
}
