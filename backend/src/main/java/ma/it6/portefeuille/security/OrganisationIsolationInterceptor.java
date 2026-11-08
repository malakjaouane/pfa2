package ma.it6.portefeuille.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
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
 */
public class OrganisationIsolationInterceptor implements HandlerInterceptor {

    @Autowired
    private EntityManager entityManager;

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

        Session session = entityManager.unwrap(Session.class);
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
