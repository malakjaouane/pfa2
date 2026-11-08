package ma.it6.portefeuille.entity;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import ma.it6.portefeuille.security.OrganisationContext;

/**
 * Classe mère de toutes les entitees metier multi-locataires.
 *
 * <p>Ajoute a {@link BaseAuditable} la colonne {@code id_organisation} (NOT
 * NULL, cle etrangere vers {@code organisation}) et le filtre Hibernate
 * {@code filtreParOrganisation}. Ce filtre, active par requete via
 * {@link OrganisationIsolationInterceptor}, garantit qu'une requete de
 * l'organisation A ne peut jamais lire ni ecrire les donnees de
 * l'organisation B — meme en cas d'oubli dans un repository.
 *
 * <p>La persistance d'une nouvelle entitee reprend automatiquement
 * l'organisation courante du fil de l'execution
 * ({@link OrganisationContext#getId()}) si le champ n'est pas deja renseigne.
 * En dehors de tout contexte (tests, batch), le champ reste a la charge de
 * l'appelant.
 */
@MappedSuperclass
@Getter
@Setter
@FilterDef(
        name = "filtreParOrganisation",
        parameters = @ParamDef(name = "idOrganisation", type = Long.class)
)
@Filter(
        name = "filtreParOrganisation",
        condition = "id_organisation = :idOrganisation"
)
public abstract class BaseOrganisation extends BaseAuditable {

    @Column(name = "id_organisation", nullable = false)
    private Long idOrganisation;

    /** Navigation en lecture seulement (ecriture via {@code id_organisation}). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_organisation", insertable = false, updatable = false)
    private Organisation organisation;

    /**
     * Si l'organisation n'est pas encore renseignee, reprend celle du contexte
     * de requete (positionnee par {@link OrganisationIsolationInterceptor} a
     * partir du compte connecte).
     */
    @PrePersist
    void initialiserOrganisation() {
        if (this.idOrganisation == null) {
            Long id = OrganisationContext.getId();
            if (id != null) {
                this.idOrganisation = id;
            }
        }
    }
}
