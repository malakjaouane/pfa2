package ma.it6.portefeuille.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookies de session de l'API (Phase 1 — authentification modernisée).
 *
 * <p>Deux cookies, tous les deux <strong>HttpOnly</strong> (jamais lus par
 * JavaScript, donc insensibles à une faille XSS), <strong>SameSite=Strict</strong>
 * (jamais envoyés en cross-site, première barrière CSRF) et
 * <strong>Secure</strong> (HTTPS, sauf autorisation explicite en
 * développement local via {@code COOKIE_SECURE=false}) :</p>
 *
 * <ul>
 *   <li>{@code pfa_access} — jeton d'accès court (15 min), chemin
 *       {@code /} : chaque requête le porte ;</li>
 *   <li>{@code pfa_refresh} — jeton de rafraîchissement (7 jours),
 *       chemin restreint à {@code /api/auth} : seul {@code /refresh} et
 *       {@code /logout} le lisent, et il tourne à chaque usage.</li>
 * </ul>
 *
 * <p>Le jeton reste <em>aussi</em> présent dans le corps de la réponse de
 * login : les clients non navigateurs (suite de tests, scripts) s'authentifient
 * par l'en-tête {@code Authorization} et n'ont pas de cookie.</p>
 */
@Component
public class CookieService {

    /** Cookie du jeton d'accès (15 min). */
    public static final String NOM_ACCESS = "pfa_access";

    /** Cookie du jeton de rafraîchissement (chemin restreint). */
    public static final String NOM_REFRESH = "pfa_refresh";

    private static final String CHEMIN_REFRESH = "/api/auth";

    private final boolean secure;
    private final long dureeAccessSecondes;
    private final long dureeRefreshSecondes;

    public CookieService(@Value("${app.cookie.secure:true}") boolean secure,
                         @Value("${app.jwt.expiration-ms:900000}") long expirationMs,
                         @Value("${app.jwt.refresh-jours:7}") int joursRefresh) {
        this.secure = secure;
        this.dureeAccessSecondes = Math.max(1, expirationMs / 1000);
        this.dureeRefreshSecondes = joursRefresh * 24L * 3600L;
    }

    /** Pose le cookie d'accès (HttpOnly, Secure, SameSite=Strict, chemin /). */
    public void ajouterAccess(HttpServletResponse response, String jeton) {
        ajouter(response, ResponseCookie.from(NOM_ACCESS, jeton)
                .httpOnly(true).secure(secure).sameSite("Strict")
                .path("/").maxAge(Duration.ofSeconds(dureeAccessSecondes))
                .build());
    }

    /** Pose le cookie de rafraîchissement (chemin restreint à /api/auth). */
    public void ajouterRefresh(HttpServletResponse response, String jeton) {
        ajouter(response, ResponseCookie.from(NOM_REFRESH, jeton)
                .httpOnly(true).secure(secure).sameSite("Strict")
                .path(CHEMIN_REFRESH).maxAge(Duration.ofSeconds(dureeRefreshSecondes))
                .build());
    }

    /** Invalide un cookie (déconnexion : même attributs, expiration immédiate). */
    public void viderAccess(HttpServletResponse response) {
        vider(response, NOM_ACCESS, "/");
    }

    /** @see #viderAccess(HttpServletResponse) */
    public void viderRefresh(HttpServletResponse response) {
        vider(response, NOM_REFRESH, CHEMIN_REFRESH);
    }

    /** Lit la valeur d'un cookie de la requête, ou {@code null}. */
    public String lire(HttpServletRequest request, String nom) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (nom.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static void ajouter(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static void vider(HttpServletResponse response, String nom, String chemin) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(nom, "")
                .httpOnly(true).sameSite("Strict")
                .path(chemin).maxAge(Duration.ZERO)
                .build().toString());
    }
}
