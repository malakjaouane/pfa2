/**
 * Entites metier du portefeuille.
 *
 * <p><b>Definition unique du filtre multi-locataires
 * {@code filtreParOrganisation}.</b> Hibernate leve une
 * {@code AnnotationException} des qu'un meme nom de filtre est declare deux
 * fois dans l'unite de persistance — meme avec une definition strictement
 * identique (voir {@code FilterDefBinder.bindFilterDef}). La declaration vit
 * donc ici, au niveau du paquet (cible {@code PACKAGE} supportee par
 * {@code @FilterDef}), et chaque entite n'applique plus que le
 * {@code @Filter} correspondant.</p>
 *
 * <p>Le parametre {@code idOrganisation} est positionne par
 * {@code OrganisationIsolationInterceptor} a partir du compte connecte ;
 * sans contexte (tests, batch), le filtre n'est pas active.</p>
 */
@FilterDef(
        name = "filtreParOrganisation",
        parameters = @ParamDef(name = "idOrganisation", type = Long.class)
)
package ma.it6.portefeuille.entity;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
