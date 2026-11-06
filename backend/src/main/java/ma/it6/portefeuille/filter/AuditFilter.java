package ma.it6.portefeuille.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.it6.portefeuille.entity.JournalAudit;
import ma.it6.portefeuille.repository.JournalAuditRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.security.PrincipalUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtre d'audit des actions HTTP : enregistre QUI a fait QUOI (verbe +
 * route) depuis OÙ (IP), quel que soit le statut HTTP rendu.
 *
 * <p><b>Immuabilité</b> : le filtre ne fait qu'INSERT. Ni mise à jour ni
 * suppression n'est exposée — le journal est une preuve.</p>
 *
 * <p><b>Périmètre</b> : seules les écritures (POST / PUT / PATCH / DELETE)
 * sont retenues ; la lecture (GET) serait du bruit. La mise à jour d'une
 * initiative ({@code PUT /api/initiatives/{id}} exacte) est ignorée : son
 * champ par champ est déjà tracé ligne à ligne par le journal des
 * modifications métier.</p>
 *
 * <p><b>Confidentialité</b> : les champs de mot de passe sont masqués dans
 * le corps capturé ; le corps est tronqué. L'e-mail n'est déduit du corps
 * que pour le route de connexion — ailleurs il vient du jeton.</p>
 *
 * <p><b>Place dans la chaîne</b> : ordre 0, exécuté à l'intérieur de la
 * chaîne de sécurité Spring Security (le filtre JWT, ajout avant
 * {@code UsernamePasswordAuthenticationFilter}, a déjà rempli le
 * {@code SecurityContext}). Les rejets de sécurité (401 / 403) n'atteignent
 * jamais ce filtre — ils sont tracés par l'historique des connexions, pas
 * par le journal des actions.</p>
 *
 * <p><b>Best-effort</b> : une erreur d'enregistrement est journalisée en
 * log mais ne casse jamais la requête métier.</p>
 */
public class AuditFilter extends OncePerRequestFilter {

    private static final Logger journal = LoggerFactory.getLogger(AuditFilter.class);

    /** Écritures seulement — la lecture n'est pas une action à auditer. */
    private static final Set<String> METHODES_AUDITEES = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Mise à jour brute d'une initiative : le champ par champ est ailleurs. */
    private static final Pattern MISE_A_JOUR_INITIATIVE = Pattern.compile("^/api/initiatives/\\d+$");

    /** Connexion : l'e-mail est déduit du corps (pas encore de jeton). */
    private static final String ROUTE_CONNEXION = "/api/auth/login";

    /**
     * Champs sensibles masqués dans le corps capturé — jamais de secret en
     * clair dans le journal.
     */
    private static final Pattern CHAMPS_SENSIBLES = Pattern.compile(
            "\"(motDePasse|password|nouveauMotDePasse|motDePasseActuel|secret|token)\"\\s*:\\s*\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    /** E-mail dans le corps d'une connexion (guillemets simples ou doubles). */
    private static final Pattern EMAIL_CORPS = Pattern.compile(
            "\"email\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    /** Troncatures de sécurité (colonnes VARCHAR). */
    static final int LIMITE_ROUTE = 300;
    static final int LIMITE_CORPS = 300;

    private final JournalAuditRepository journalRepository;
    private final UtilisateurRepository utilisateurRepository;

    public AuditFilter(JournalAuditRepository journalRepository,
                       UtilisateurRepository utilisateurRepository) {
        this.journalRepository = journalRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METHODES_AUDITEES.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String methode = request.getMethod();
        String chemin = request.getRequestURI();

        // La mise à jour « nue » d'une initiative est déjà tracée champ par
        // champ par le journal des modifications : on ne la double pas ici.
        if ("PUT".equals(methode) && MISE_A_JOUR_INITIATIVE.matcher(chemin).matches()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Corps capturé avec rejeu : la requête passe au contrôleur intacte.
        ContentCachingRequestWrapper requete = new ContentCachingRequestWrapper(request);
        filterChain.doFilter(requete, response);

        enregistrer(requete, response, methode);
    }

    /** Enregistre l'action — best-effort, une erreur ne casse pas la requête. */
    private void enregistrer(ContentCachingRequestWrapper request,
                             HttpServletResponse response,
                             String methode) {
        try {
            String route = route(request);
            String corps = corps(request);
            String email = email(request, route);

            JournalAudit ligne = JournalAudit.builder()
                    .dateAction(LocalDateTime.now())
                    .idUtilisateur(idUtilisateur(email))
                    .email(email)
                    .methode(methode)
                    .route(route)
                    .statutHttp(response.getStatus())
                    .adresseIp(adresseIp(request))
                    .corps(corps)
                    .build();
            journalRepository.save(ligne);
        } catch (RuntimeException e) {
            // L'audit ne doit jamais faire échouer l'action métier.
            journal.warn("Enregistrement d'audit impossible (action {} {}): {}",
                    methode, request.getRequestURI(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Collecte des informations
    // ------------------------------------------------------------------

    /** Chemin + query, tronqué à {@value #LIMITE_ROUTE} caractères. */
    private static String route(HttpServletRequest request) {
        String chemin = request.getRequestURI();
        String query = request.getQueryString();
        String complete = query == null || query.isBlank() ? chemin : chemin + "?" + query;
        return tronquer(complete, LIMITE_ROUTE);
    }

    /** Corps filtré (secrets masqués) et tronqué à {@value #LIMITE_CORPS}. */
    private static String corps(HttpServletRequest request) {
        byte[] octets = ((ContentCachingRequestWrapper) request).getContentAsByteArray();
        if (octets.length == 0) {
            return null;
        }
        String brut = new String(octets, java.nio.charset.StandardCharsets.UTF_8);
        String filtre = CHAMPS_SENSIBLES.matcher(brut).replaceAll("$1:\"***\"");
        return tronquer(filtre, LIMITE_CORPS);
    }

    /**
     * E-mail : du jeton (utilisateur déjà authentifié), ou du corps pour la
     * connexion (le jeton n'existe pas encore). Jamais d'autre route.
     */
    private String email(HttpServletRequest request, String route) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PrincipalUser principal) {
            return principal.getUtilisateur().getEmail();
        }
        if (authentication != null && authentication.getPrincipal() instanceof String courant
                && !"anonymousUser".equals(courant)) {
            return courant;
        }
        if (route != null && route.startsWith(ROUTE_CONNEXION)) {
            String corps = corps(request);
            if (corps != null) {
                Matcher correspondance = EMAIL_CORPS.matcher(corps);
                if (correspondance.find()) {
                    return correspondance.group(1);
                }
            }
        }
        return null;
    }

    /** Identifiant de l'utilisateur — résolu par e-mail, best-effort. */
    private Long idUtilisateur(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return utilisateurRepository.findByEmail(email)
                .map(u -> u.getIdUser())
                .orElse(null);
    }

    /** Adresse IP réelle du client, en tenant compte des reverse-proxy. */
    private static String adresseIp(HttpServletRequest request) {
        String enTete = request.getHeader("X-Forwarded-For");
        if (enTete != null && !enTete.isBlank()) {
            int virgule = enTete.indexOf(',');
            return (virgule > 0 ? enTete.substring(0, virgule) : enTete).trim();
        }
        return request.getRemoteAddr();
    }

    private static String tronquer(String valeur, int limite) {
        if (valeur == null || valeur.length() <= limite) {
            return valeur;
        }
        return valeur.substring(0, limite);
    }
}
