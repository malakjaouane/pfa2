package ma.it6.portefeuille.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Complète le filtre JWT : un compte marqué « changement de mot de passe
 * obligatoire » ({@code doit_changer_mot_de_passe}, V3) ne peut accéder
 * qu'aux routes de secours — la page de changement et la déconnexion.
 * Toute autre requête authentifiée reçoit un 403 explicite avec le code
 * {@code MOT_DE_PASSE_A_CHANGER}, que le frontend traduit en redirection
 * vers l'écran de changement.
 *
 * <p>Posé <strong>après</strong> {@link JwtAuthenticationFilter} : il
 * n'agit que sur des requêtes déjà authentifiées (les anonymes sont
 * traitées par la chaîne d'autorisation habituelle).</p>
 */
@Component
public class FiltreMotDePasseAChanger extends OncePerRequestFilter {

    /** Code machine renvoyé dans le corps 403 (traduit côté frontend). */
    public static final String CODE = "MOT_DE_PASSE_A_CHANGER";

    /** Seules routes accessibles tant que le marqueur est levé :
     *  le changement lui-même, et « qui suis-je » (rechargement de page :
     *  AuthContext a besoin du profil — dont le marqueur — pour afficher
     *  l'écran de changement au lieu de déconnecter l'utilisateur). */
    private static final String[] ROUTES_AUTORISEES = {
            "/api/auth/changer-mot-de-passe",
            "/api/auth/me",
            // La déconnexion reste possible même sous obligation de changer
            // de mot de passe : elle révoque les jetons et vide les cookies.
            "/api/auth/logout"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Le principal authentifié est mis à disposition par le filtre JWT
        // (attribut de requête) : aucun rechargement ici. Une authentification
        // posée autrement (sans jeton) n'a pas d'attribut — le marqueur n'est
        // alors pas déterminable et on laisse la chaîne habituelle trancher.
        Object principal = request.getAttribute(JwtAuthenticationFilter.ATT_PRINCIPAL);

        if (principal instanceof PrincipalUser compte
                && Boolean.TRUE.equals(compte.getUtilisateur().getDoitChangerMotDePasse())
                && !routeAutorisee(request.getRequestURI())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"timestamp\": \"" + LocalDateTime.now()
                    + "\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"" + CODE
                    + "\", \"message\": \"Changement de mot de passe obligatoire.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean routeAutorisee(String uri) {
        for (String route : ROUTES_AUTORISEES) {
            if (uri.equals(route)) {
                return true;
            }
        }
        return false;
    }
}
