package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Invitation d'un collegue a rejoindre une organisation (Phase 2B4).
 * Jeton a usage unique ; l'acceptation cree le compte dans
 * l'organisation de l'invitateur, avec le role choisi.
 */
@Entity
@Table(name = "invitation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_invitation")
    private Long idInvitation;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "id_organisation", nullable = false)
    private Long idOrganisation;

    @Column(name = "id_role", nullable = false)
    private Long idRole;

    /** Hachage SHA-256 hexadecimal (64 caracteres) du jeton opaque. */
    @Column(name = "jeton_hash", nullable = false, unique = true, length = 64)
    private String jetonHash;

    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;

    @Column(name = "utilise", nullable = false)
    private Boolean utilise = false;

    @Column(name = "cree_par", nullable = false)
    private Long creePar;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    public boolean estExpire() {
        return dateExpiration != null && dateExpiration.isBefore(LocalDateTime.now());
    }

    /** Résultat d'une validation réussie. */
    public record Acceptation(String email, Long idOrganisation, Long idRole) {}
}
