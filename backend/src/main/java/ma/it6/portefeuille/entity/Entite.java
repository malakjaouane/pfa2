package ma.it6.portefeuille.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Unité organisationnelle (entité) à laquelle sont rattachés des
 * utilisateurs et des initiatives.
 */
@Entity
@Table(name = "entite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entite extends BaseOrganisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entite")
    private Long idEntite;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "actif", nullable = false)
    private Boolean actif = true;

    @OneToMany(mappedBy = "entite")
    private List<Utilisateur> utilisateurs = new ArrayList<>();

    @OneToMany(mappedBy = "entite")
    private List<Initiative> initiatives = new ArrayList<>();
}