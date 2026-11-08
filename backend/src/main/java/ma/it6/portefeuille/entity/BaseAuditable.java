package ma.it6.portefeuille.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tracabilité commune à toutes les entités métier.
 *
 * <p>Trois informations, dont deux se posent d'elles-mêmes :</p>
 * <ul>
 *   <li><b>created_at</b> — date de création, <em>jamais</em> remise à jour
 *       ({@code updatable = false} + {@link #initialiserDates()}) ;</li>
 *   <li><b>updated_at</b> — date de dernière modification, maintenue à chaque
 *       enregistrement ;</li>
 *   <li><b>version</b> — verrou optimiste : deux personnes modifiant la même
 *       initiative ne se marchent pas dessus, le second reçoit un
 *       <strong>HTTP 409</strong> et son écran propose de recharger.</li>
 * </ul>
 *
 * <p>Les dates sont calculées par des callbacks JPA {@code @PrePersist} /
 * {@code @PreUpdate} plutôt que par des annotations propres à Hibernate :
 * c'est du standard, cela fonctionne à l'identique avec le schéma validé par
 * {@code ddl-auto: validate}, et le comportement est explicite à la lecture
 * (on voit qu'une date de création n'est jamais réécrite).</p>
 *
 * <p>Les colonnes sont déclarées dès la création de la table par
 * {@code database/migration/V1__baseline.sql}
 * : elles restent donc NULLables, et on refuse d'inventer une date de création
 * pour les enregistrements préexistants (ils ne seront jamais réécrits avec un
 * {@code created_at} faux). Seule {@code version} est NOT NULL, valeur par
 * défaut 0, condition requise par JPA.</p>
 *
 * <p>Ne s'applique pas aux tables d'association à clé composite
 * ({@code RolePermission}) : elles ne portent pas d'identité propre.</p>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseAuditable {

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Première persistance : fige la date de création (une seule fois) et
     * initialise la date de modification.
     */
    @PrePersist
    void initialiserDates() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    /** Enregistrement subséquent : seule la date de modification avance. */
    @PreUpdate
    void rafraichirDateModification() {
        updatedAt = LocalDateTime.now();
    }
}
