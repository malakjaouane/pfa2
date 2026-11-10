package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Membre d'une initiative avec un rôle (Phase 2C - droits par initiative).
 *
 * <p>Rôles : <strong>responsable</strong> (pilote l'initiative),
 * <strong>contributeur</strong> (modifie uniquement ses initiatives),
 * <strong>lecteur</strong> (consultation seule).</p>
 *
 * <p>Le champ {@code date_ajout} initialement déclaré a été retiré : il
 * n'était renseigné nulle part (INSERT en échec sous NOT NULL) et ne
 * correspond à aucune colonne des migrations — l'entité épouse ici le
 * schéma réel de V10.</p>
 */
@Entity
@Table(name = "membre_initiative")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembreInitiative {

    public static final String ROLE_RESPONSABLE = "responsable";
    public static final String ROLE_CONTRIBUTEUR = "contributeur";
    public static final String ROLE_LECTEUR = "lecteur";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_membre")
    private Long idMembre;

    @Column(name = "id_user", nullable = false)
    private Long idUser;

    @Column(name = "id_initiative", nullable = false)
    private Long idInitiative;

    @Column(name = "role", nullable = false, length = 30)
    private String role;
}
