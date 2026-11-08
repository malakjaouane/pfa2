package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Une tentative de connexion (Phase 1 C3 — rate-limit persistant).
 *
 * <p>Persistée en base pour survivre aux redémarrages et être partagée par
 * tous les conteneurs backend. Deux compteurs glissants indépendants
 * (compte et adresse IP) sont calculés à la lecture sur les lignes
 * {@code succes = FALSE} dans la fenêtre glissante configurée.</p>
 */
@Entity
@Table(name = "tentative_connexion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TentativeConnexion {

    public static final String TYPE_COMPTE = "COMPTE";
    public static final String TYPE_IP = "IP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tentative")
    private Long idTentative;

    @Column(name = "cle", nullable = false, length = 150)
    private String cle;

    @Column(name = "type_cle", nullable = false, length = 10)
    private String typeCle;

    @Column(name = "date_tentative", nullable = false)
    private LocalDateTime dateTentative;

    @Column(name = "succes", nullable = false)
    private Boolean succes;
}
