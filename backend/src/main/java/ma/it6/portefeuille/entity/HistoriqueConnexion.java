package ma.it6.portefeuille.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tentative de connexion, réussie ou non (traçabilité loi 09-08).
 *
 * <p>Chaque passage par {@code POST /api/auth/login} dépose une ligne ici,
 * y compris les échecs (mauvais mot de passe, compte inconnu, verrouillage
 * 429) : c'est le journal de contrôle des accès attendu d'un outil de
 * pilotage partagé.</p>
 *
 * <p>L'e-mail saisi est copié en clair, indépendamment du compte : une
 * tentative sur une adresse inexistante reste traçable. Le lien vers le
 * compte n'est renseigné que lorsque l'utilisateur est identifié ; la clé
 * étrangère est en {@code ON DELETE SET NULL} (la suppression d'un compte
 * n'efface pas le journal des tentatives).</p>
 */
@Entity
@Table(name = "historique_connexion")
@Getter
@Setter
@NoArgsConstructor
public class HistoriqueConnexion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_connexion")
    private Long idConnexion;

    @Column(name = "date_connexion", nullable = false, updatable = false)
    private LocalDateTime dateConnexion;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Column(name = "adresse_ip", nullable = false, length = 45)
    private String adresseIp;

    @Column(name = "succes", nullable = false)
    private boolean succes;

    @Column(name = "motif", length = 200)
    private String motif;

    /** Horodatage serveur à l'insertion (aucun apport du client). */
    @PrePersist
    private void horodater() {
        if (dateConnexion == null) {
            dateConnexion = LocalDateTime.now();
        }
    }
}
