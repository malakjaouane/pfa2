package ma.it6.portefeuille.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Filtre JWT : lit le jeton « Bearer » de l'en-tête {@code Authorization}
 * <strong>ou</strong> le cookie d'accès {@code pfa_access} (HttpOnly, 15 min),
 * le valide <strong>puis recharge l'utilisateur depuis la base</strong> avant
 * de positionner l'authentification.
 *
 * <p>Le jeton ne fait pas foi à lui seul : à chaque requête, le compte doit
 * toujours exister et être actif, et ce sont les permissions fraîchement
 * chargées qui sont utilisées (et non celles encodées dans le jeton). Un
 * compte désactivé ou un rôle modifié a donc effet immédiat, sans attendre
 * l'expiration du jeton.</p>
 *
 * <p>L'en-tête l'emporte sur le cookie : un client non navigateur (suite de
 * tests, script) s'authentifie par {@code Authorization} sans jamais porter
 * de cookie.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXE = "Bearer ";

    /**
     * Attribut de requête portant le {@code PrincipalUser} authentifié :
     * posé après validation du jeton, lu par le filtre suivant
     * ({@code FiltreMotDePasseAChanger}) pour éviter un second
     * chargement de l'utilisateur.
     */
    public static final String ATT_PRINCIPAL = "pfa.principal";

    private final JwtService jwtService;
    private final SessionsService sessionsService;
    private final CookieService cookieService;

    public JwtAuthenticationFilter(JwtService jwtService, SessionsService sessionsService,
                                   CookieService cookieService) {
        this.jwtService = jwtService;
        this.sessionsService = sessionsService;
        this.cookieService = cookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extraireJeton(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String email = jwtService.extraireEmail(token);
                if (email != null && jwtService.estValide(token, email)) {
                    Optional<PrincipalUser> utilisateur = sessionsService.chargerActif(email);
                    if (utilisateur.isPresent()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        email, null, utilisateur.get().getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        request.setAttribute(ATT_PRINCIPAL, utilisateur.get());
                    } else {
                        // Compte supprimé ou désactivé : on rejette explicitement
                        // le jeton pour éviter tout traitement ultérieur.
                        refuser(response, "Session expirée ou compte désactivé.");
                        return;
                    }
                } else {
                    // Jeton expiré : on renvoie un 401 explicite plutôt que de
                    // laisser une chaîne de filtres renvoyer une page d'erreur.
                    // Le frontend répond par un rafraîchissement (cookie refresh).
                    refuser(response, "Jeton expiré ou invalide.");
                    return;
                }
            } catch (Exception ignore) {
                // Jeton malformé ou signature invalide : contexte laissé vide.
                refuser(response, "Jeton invalide.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Jeton d'accès : en-tête {@code Authorization} d'abord, sinon cookie. */
    private String extraireJeton(HttpServletRequest request) {
        String entete = request.getHeader("Authorization");
        if (entete != null && entete.startsWith(PREFIXE)) {
            return entete.substring(PREFIXE.length());
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return cookieService.lire(request, CookieService.NOM_ACCESS);
        }
        return null;
    }

    private static void refuser(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"timestamp\": \"" + LocalDateTime.now()
                + "\", \"status\": 401, \"error\": \"Unauthorized\", \"message\": \""
                + message + "\"}");
    }
}
