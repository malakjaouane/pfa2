package ma.it6.portefeuille.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.persistence.EntityManager;

/**
 * Intercepteur MVC d'isolation multi-organisation.
 *
 * <p>A chaque requete authentifiee, active le filtre Hibernate
 * {@code filtreParOrganisation} avec l'identifiant de l'organisation du compte
 * connecte (porte par {@link OrganisationContext}, lui-meme renseigne par
 * {@link JwtAuthenticationFilter}). Resultat : toutes les requetes JPA sur les
 * entites filtrees sont automatiquement bornees à
 * {@code id_organisation = :idOrganisation} — une requete de l'organisation A
 * ne peut donc jamais lire ni ecrire les donnees de l'organisation B, meme en
 * cas d'oubli dans un repository.
 *
 * <p>Le filtre est desactive et le contexte nettoye a la fin de la requete,
 * pour éviter toute fuite entre deux requetes traitee par le meme fil.
 *
 * <p>Declare {@code @Component} pour que Spring injecte l'{
 * @code EntityManager} : une instanciation avec {@code new} (comme dans
 * {@code WebConfig#addInterceptors} avant ce correctif) laissait le champ
 * {@code entityManager} a {@code null} et provocait une
 * {@code NullPointerException} sur toute requete metier authentifiee.
 *
 * <p>L'injection se fait via {@link ObjectProvider} (resolution paresseuse) :
 * les slices de test {@code @WebMvcTest} ne chargent pas la configuration JPA,
 * donc aucun bean {@code EntityManager} n'y existe — le provider retourne
 * {@code null} et l'isolation est simplement inactive dans ce contexte de test,
 * au lieu de faire echouer le chargement de tout le contexte Spring.
 */
@Component
public class OrganisationIsolationInterceptor implements HandlerInterceptor {

    private final ObjectProvider<EntityManager> entityManager;

    public OrganisationIsolationInterceptor(ObjectProvider<EntityManager> entityManager) {
        this.entityManager = entityManager;
    }

    private static final String FILTRE = "filtreParOrganisation";
    private static final String PARAMETRE = "idOrganisation";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long idOrganisation = OrganisationContext.getId();
        if (idOrganisation == null) {
            // Pas d'organisation courante (requete anonyme ou hors contexte) :
            // le filtre n'est pas active. Spring Security bloque deja l'acces
            // aux ressources metier sans authentification.
            request.setAttribute("pfa.filtreOrganisation.active", false);
            return true;
        }

        EntityManager em = entityManager.getIfAvailable();
        if (em == null) {
            // Contexte sans JPA (slice @WebMvcTest) : pas de filtre a activer.
            request.setAttribute("pfa.filtreOrganisation.active", false);
            return true;
        }
        Session session = em.unwrap(Session.class);
        session.enableFilter(FILTRE).setParameter(PARAMETRE, idOrganisation);
        request.setAttribute("pfa.filtreOrganisation.active", true);
        request.setAttribute("pfa.filtreOrganisation.session", session);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Boolean active = (Boolean) request.getAttribute("pfa.filtreOrganisation.active");
        if (Boolean.TRUE.equals(active)) {
            Session session = (Session) request.getAttribute("pfa.filtreOrganisation.session");
            if (session != null) {
                session.disableFilter(FILTRE);
            }
        }
        OrganisationContext.clear();
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        // Rien a faire : le filtre reste active pendant le rendu de la vue.
    }
}
