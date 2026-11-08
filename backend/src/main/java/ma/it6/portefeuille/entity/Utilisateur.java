package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilisateur de l'application.
 * Le mot de passe est stocké hashé (BCrypt), jamais en clair.
 */
@Entity
@Table(name = "utilisateur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur extends BaseOrganisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user")
    private Long idUser;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "prenom", nullable = false, length = 100)
    private String prenom;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "mot_de_passe", nullable = false, length = 255)
    private String motDePasse;

    @Column(name = "telephone", length = 20)
    private String telephone;

    @Column(name = "actif", nullable = false)
    private Boolean actif = true;

    /**
     * Changement de mot de passe obligatoire au prochain login (V3).
     * Posé à la création d'un compte administrateur hors profil dev ;
     * tant qu'il est à TRUE, le filtre {@code FiltreMotDePasseAChanger}
     * n'autorise que {@code POST /api/auth/changer-mot-de-passe} et le
     * marqueur retombe à FALSE dès l'enregistrement.
     */
    @Builder.Default
    @Column(name = "doit_changer_mot_de_passe", nullable = false)
    private Boolean doitChangerMotDePasse = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_role")
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entite")
    private Entite entite;

    @OneToMany(mappedBy = "responsable")
    private List<Initiative> initiativesResponsables = new ArrayList<>();
}