package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Jeton de reinitialisation de mot de passe (Phase 2B3).
 * Usage unique, expire au bout d'une heure.
 */
@Entity
@Table(name = "jeton_reinitialisation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JetonReinitialisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_jeton")
    private Long idJeton;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** Hachage SHA-256 hexadecimal (64 caracteres) du jeton opaque. */
    @Column(name = "jeton_hash", nullable = false, unique = true, length = 64)
    private String jetonHash;

    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;

    @Column(name = "utilise", nullable = false)
    private Boolean utilise = false;

    public boolean estExpire() {
        return dateExpiration != null && dateExpiration.isBefore(LocalDateTime.now());
    }
}
