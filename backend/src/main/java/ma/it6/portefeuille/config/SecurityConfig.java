package ma.it6.portefeuille.config;

import ma.it6.portefeuille.security.CookieService;
import ma.it6.portefeuille.security.FiltreMotDePasseAChanger;
import ma.it6.portefeuille.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Configuration Spring Security : authentification JWT sans état,
 * protection des endpoints selon les permissions, CORS pour le frontend.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final FiltreMotDePasseAChanger filtreMotDePasseAChanger;
    private final String[] originesAutorisees;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          FiltreMotDePasseAChanger filtreMotDePasseAChanger,
                          @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
                          String originesAutorisees) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.filtreMotDePasseAChanger = filtreMotDePasseAChanger;
        // Motifs séparés par des virgules (cf. application.yml).
        this.originesAutorisees = originesAutorisees == null
                ? new String[0]
                : originesAutorisees.split("\\s*,\\s*");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF activé uniquement pour les requêtes authentifiées par
                // cookie (porteuses du cookie pfa_access). Les clients qui
                // s'authentifient par l'en-tête Authorization (suite de tests,
                // scripts) n'envoient pas de cookie : ils sont exemptés de CSRF
                // (une requête cross-site ne peut pas fabriquer d'en-tête
                // Authorization, donc pas de risque CSRF).
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .requireCsrfProtectionMatcher(request -> {
                            // Les routes d'authentification (login, refresh, logout, signup…)
                            // sont exemptes : elles sont appelées avant qu'un cookie
                            // pfa_access ne existe, et ne sont pas sensibles au CSRF.
                            String chemin = request.getRequestURI();
                            if (chemin.startsWith("/api/auth/")) {
                                return false;
                            }
                            // Seules les méthodes en mutation sont concernées par le CSRF.
                            String method = request.getMethod();
                            if (!"POST".equals(method) && !"PUT".equals(method)
                                    && !"PATCH".equals(method) && !"DELETE".equals(method)) {
                                return false;
                            }
                            // Toutes les autres routes en mutation sont protégées
                            // uniquement quand le client est authentifié par cookie
                            // (présence de pfa_access). Les clients en Authorization:
                            // Bearer (suite de tests, scripts) sont exemptés.
                            Cookie[] cookies = request.getCookies();
                            if (cookies == null) {
                                return false;
                            }
                            for (Cookie cookie : cookies) {
                                if (CookieService.NOM_ACCESS.equals(cookie.getName())) {
                                    return true;
                                }
                            }
                            return false;
                        })
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login").permitAll()
                        // Parcours client (2B) : inscription, verification,
                        // mot de passe oublie et reinitialisation sont
                        // accessibles sans authentification prealable.
                        .requestMatchers("/api/auth/signup", "/api/auth/mot-de-passe-oublie",
                                "/api/auth/reinitialiser-mot-de-passe", "/api/auth/verifier-email").permitAll()
                        // Invitation : seulement l'administrateur de l'organisation.
                        .requestMatchers(HttpMethod.POST, "/api/auth/inviter").hasRole("ADMIN")
                        // Renvoi d'un nouvel access token (cookie refresh
                        // tournant) et deconnexion : sans session ouverte.
                        .requestMatchers("/api/auth/refresh", "/api/auth/logout").permitAll()
                        // Renvoi d'un nouvel access token (cookie refresh
                        // tournant) et déconnexion : sans session ouverte.
                        .requestMatchers("/api/auth/refresh", "/api/auth/logout").permitAll()
                        // Référentiels et profils (listes déroulantes) : utilisateur connecté
                        .requestMatchers(HttpMethod.GET, "/api/referentiels/**", "/api/roles", "/api/permissions").authenticated()
                        // Dashboard / KPI
                        .requestMatchers("/api/dashboard").hasAuthority("DASHBOARD_CONSULTER")
                        // Budgets et écarts : permission dédiée (16). La
                        // consultation des budgets n'est pas un simple corollaire
                        // de la lecture des initiatives — c'est une information
                        // financière, soumise à un droit explicite.
                        .requestMatchers("/api/budget/**").hasAuthority("BUDGET_CONSULTER")
                        // Objectifs stratégiques : consultation connectée, écriture avec INITIATIVE_MODIFIER
                        .requestMatchers(HttpMethod.GET, "/api/objectifs/**").authenticated()
                        .requestMatchers("/api/objectifs/**").hasAuthority("INITIATIVE_MODIFIER")
                        // Demandes d'initiatives (workflow SPM)
                        .requestMatchers(HttpMethod.GET, "/api/demandes/**")
                        .hasAnyAuthority("DEMANDE_SOUMETTRE", "DEMANDE_EVALUER")
                        .requestMatchers(HttpMethod.POST, "/api/demandes").hasAuthority("DEMANDE_SOUMETTRE")
                        .requestMatchers(HttpMethod.PUT, "/api/demandes/**").hasAuthority("DEMANDE_SOUMETTRE")
                        .requestMatchers("/api/demandes/*/soumettre").hasAuthority("DEMANDE_SOUMETTRE")
                        .requestMatchers("/api/demandes/*/evaluer", "/api/demandes/*/accepter",
                                "/api/demandes/*/refuser", "/api/demandes/*/transformer")
                        .hasAuthority("DEMANDE_EVALUER")
                        // Bascule Actives | Archives : la vue appartient
                        // aux deux acteurs de la page (ceux qui soumettent
                        // comme ceux qui évaluent) — aucun nouveau droit.
                        .requestMatchers(HttpMethod.PATCH, "/api/demandes/*/archive",
                                "/api/demandes/*/restaurer")
                        .hasAnyAuthority("DEMANDE_SOUMETTRE", "DEMANDE_EVALUER")
                        // Suppression d'une demande = ceux qui ont le droit
                        // d'en décider (DEMANDE_EVALUER : admin + responsable).
                        .requestMatchers(HttpMethod.DELETE, "/api/demandes/**")
                        .hasAuthority("DEMANDE_EVALUER")
                        // Utilisateurs : administrateur uniquement
                        .requestMatchers("/api/users/**").hasAuthority("UTILISATEUR_GERER")
                        // Historique des connexions (e-mails + IP) :
                        // administrateur uniquement, TOUS verbes (lecture,
                        // suppression d'une ligne, purge totale).
                        .requestMatchers("/api/historique-connexions/**")
                        .hasAuthority("UTILISATEUR_GERER")
                        // Dépenses : information financière — TOUS verbes
                        // (lecture ET écriture) réservés à DEPENSE_SAISIR
                        // (20). La règle doit précéder « GET /api/initiatives/** » :
                        // lire les dépenses d'une initiative n'est pas un
                        // corollaire de lire l'initiative.
                        .requestMatchers("/api/depenses/**", "/api/initiatives/*/depenses")
                        .hasAuthority("DEPENSE_SAISIR")
                        // Initiatives
                        .requestMatchers(HttpMethod.GET, "/api/initiatives/**").hasAuthority("INITIATIVE_CONSULTER")
                        .requestMatchers(HttpMethod.POST, "/api/initiatives").hasAuthority("INITIATIVE_CREER")
                        // Purge définitive d'une initiative archivée = même
                        // droit que l'archivage (INITIATIVE_ARCHIVER).
                        .requestMatchers(HttpMethod.DELETE, "/api/initiatives/**")
                        .hasAuthority("INITIATIVE_ARCHIVER")
                        // Commentaires d'initiative : ajout + suppression =
                        // permission dédiée (18). La lecture (GET) est déjà
                        // ouverte par le « /api/initiatives/** » en
                        // INITIATIVE_CONSULTER ci-dessus.
                        .requestMatchers(HttpMethod.POST, "/api/initiatives/*/commentaires")
                        .hasAuthority("COMMENTAIRE_AJOUTER")
                        .requestMatchers(HttpMethod.DELETE, "/api/commentaires/**")
                        .hasAuthority("COMMENTAIRE_AJOUTER")
                        // Registre global des risques (toutes initiatives) :
                        // lecture = INITIATIVE_CONSULTER, comme le registre
                        // par initiative.
                        .requestMatchers(HttpMethod.GET, "/api/risques", "/api/risques/export")
                        .hasAuthority("INITIATIVE_CONSULTER")
                        // Écriture des risques : permission dédiée (19),
                        // distincte de la modification de l'initiative. Doit
                        // précéder les règles générales PATCH/PUT
                        // « /api/initiatives/** » pour que les routes par
                        // initiative retombent sur RISQUE_GERER.
                        .requestMatchers("/api/risques/**", "/api/initiatives/*/risques")
                        .hasAuthority("RISQUE_GERER")
                        // Journal d'audit des actions HTTP : administrateur
                        // (UTILISATEUR_GERER). JOURNAL IMMUABLE : aucun
                        // POST/PUT/PATCH/DELETE n'est déclaré — l'envoi de
                        // ces verbes renvoie 405.
                        .requestMatchers("/api/journal-audit/**")
                        .hasAuthority("UTILISATEUR_GERER")
                        // Mon espace et notifications : tout utilisateur
                        // connecté, contenu adapté aux permissions.
                        .requestMatchers("/api/mon-espace", "/api/notifications")
                        .authenticated()
                        // Journal des modifications (audit métier) : lecture =
                        // même permission que la lecture des initiatives
                        // (pas de nouveau droit). JOURNAL IMMUABLE : aucun
                        // POST/PUT/PATCH/DELETE n'est déclaré sur ces
                        // routes — l'envoi de ces verbes renvoie 405.
                        .requestMatchers(HttpMethod.GET, "/api/historique/**")
                        .hasAuthority("INITIATIVE_CONSULTER")
                        // Réordonnancement des étapes : à placer avant la règle générale PUT
                        .requestMatchers(HttpMethod.PUT, "/api/initiatives/*/etapes/ordre")
                        .hasAuthority("ETAPE_GERER")
                        .requestMatchers(HttpMethod.PATCH, "/api/initiatives/**").hasAuthority("INITIATIVE_ARCHIVER")
                        .requestMatchers(HttpMethod.PUT, "/api/initiatives/**").hasAuthority("INITIATIVE_MODIFIER")
                        // Étapes / jalons — lecture (GET) déjà autorisée par le
                        // « /api/initiatives/** » en INITIATIVE_CONSULTER ci-dessus :
                        // les visiteurs voient donc les étapes et jalons. Écriture
                        // réservée à ETAPE_GERER / JALON_GERER.
                        .requestMatchers("/api/etapes/**", "/api/initiatives/*/etapes").hasAuthority("ETAPE_GERER")
                        .requestMatchers("/api/jalons/**", "/api/initiatives/*/jalons").hasAuthority("JALON_GERER")
                        // Gestion des permissions d'un rôle
                        .requestMatchers(HttpMethod.PUT, "/api/roles/*/permissions").hasAuthority("ROLE_GERER")
                        // Assistant IA (« Demande à ton portefeuille ») —
                        // permission dédiée (17). Le statut reste ouvert à
                        // tout utilisateur connecté (affichage explicite de
                        // l'indisponibilité) ; le journal des requêtes est
                        // réservé aux administrateurs (loi 09-08 / CNDP).
                        .requestMatchers(HttpMethod.GET, "/api/ia/statut").authenticated()
                        .requestMatchers("/api/ia/assistant", "/api/ia/resume/**").hasAuthority("IA_UTILISER")
                        .requestMatchers(HttpMethod.GET, "/api/ia/journal").hasAuthority("UTILISATEUR_GERER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(erreur(HttpStatus.UNAUTHORIZED,
                                    "Authentification requise."));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(erreur(HttpStatus.FORBIDDEN,
                                    "Accès refusé : droits insuffisants."));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Juste après le JWT : un compte marqué « changement de mot de
                // passe obligatoire » (V3) n'accorde que les routes de secours.
                .addFilterAfter(filtreMotDePasseAChanger, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS : origines configurables via {@code app.cors.allowed-origin-patterns}
     * (séparées par des virgules). Par défaut, seuls les frontaux en
     * développement local sont acceptés ; en production, on déclare
     * explicitement les domaines déployés.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(originesAutorisees));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("Authorization", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static String erreur(HttpStatus statut, String message) {
        return "{\"timestamp\": \"" + LocalDateTime.now() + "\", \"status\": " + statut.value()
                + ", \"error\": \"" + statut.getReasonPhrase() + "\", \"message\": \"" + message + "\"}";
    }

    /**
     * Force la génération du jeton CSRF (et donc l'écriture du cookie
     * {@code XSRF-TOKEN}) dès la première requête authentifiée par cookie, afin
     * que le SPA puisse le lire et le renvoyer en header {@code X-XSRF-TOKEN}
     * sur les mutations.
     */
    private static class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           Supplier<CsrfToken> csrfToken) {
            csrfToken.get(); // force la génération + écriture du cookie XSRF-TOKEN
            super.handle(request, response, csrfToken);
        }
    }
}