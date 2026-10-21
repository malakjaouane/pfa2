package ma.it6.portefeuille.config;

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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Configuration Spring Security : authentification JWT sans état,
 * protection des endpoints selon les permissions, CORS pour le frontend.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final String[] originesAutorisees;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
                          String originesAutorisees) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
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
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login").permitAll()
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
                        // Utilisateurs : administrateur uniquement
                        .requestMatchers("/api/users/**").hasAuthority("UTILISATEUR_GERER")
                        // Initiatives
                        .requestMatchers(HttpMethod.GET, "/api/initiatives/**").hasAuthority("INITIATIVE_CONSULTER")
                        .requestMatchers(HttpMethod.POST, "/api/initiatives").hasAuthority("INITIATIVE_CREER")
                        // Journal des statuts : lecture = même permission que
                        // la lecture des initiatives (pas de nouveau droit).
                        // Suppression d'une ligne = administrateurs seulement
                        // (UTILISATEUR_GERER, droit existant). Aucun POST/PUT.
                        .requestMatchers(HttpMethod.GET, "/api/historique-statuts/**")
                        .hasAuthority("INITIATIVE_CONSULTER")
                        .requestMatchers(HttpMethod.DELETE, "/api/historique-statuts/**")
                        .hasAuthority("UTILISATEUR_GERER")
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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static String erreur(HttpStatus statut, String message) {
        return "{\"timestamp\": \"" + LocalDateTime.now() + "\", \"status\": " + statut.value()
                + ", \"error\": \"" + statut.getReasonPhrase() + "\", \"message\": \"" + message + "\"}";
    }
}