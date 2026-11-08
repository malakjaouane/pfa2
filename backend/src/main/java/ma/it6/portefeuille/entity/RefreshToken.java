package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Jeton de rafraîchissement à rotation (migration V4).
 *
 * <p>Le jeton n'est stocké que sous forme de hachage SHA-256
 * ({@code jetonHash}) : le clair circule uniquement dans le cookie
 * HttpOnly côté client. Après présentation, la ligne est marquée
 * {@code utilise = true} et un nouveau jeton est émis ; une seconde
 * présentation de la même ligne est une réutilisation et déclenche la
 * révocation de tous les jetons du compte.</p>
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_refresh")
    private Long idRefresh;

    /** Hachage SHA-256 hexadécimal (64 caractères) du jeton opaque. */
    @Column(name = "jeton_hash", nullable = false, unique = true, length = 64)
    private String jetonHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_user", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;

    @Column(name = "utilise", nullable = false)
    private Boolean utilise = false;

    @Column(name = "date_utilisation")
    private LocalDateTime dateUtilisation;

    public boolean estExpire() {
        return dateExpiration != null && dateExpiration.isBefore(LocalDateTime.now());
    }
}
