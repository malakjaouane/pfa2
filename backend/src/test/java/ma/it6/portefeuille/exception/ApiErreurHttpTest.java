package ma.it6.portefeuille.exception;

import ma.it6.portefeuille.config.SecurityConfig;
import ma.it6.portefeuille.controller.AuthController;
import ma.it6.portefeuille.controller.InitiativeController;
import ma.it6.portefeuille.security.CustomUserDetailsService;
import ma.it6.portefeuille.security.JwtService;
import ma.it6.portefeuille.security.ProtectionConnexions;
import ma.it6.portefeuille.security.SessionsService;
import ma.it6.portefeuille.service.BudgetService;
import ma.it6.portefeuille.service.DashboardService;
import ma.it6.portefeuille.service.EtapeService;
import ma.it6.portefeuille.service.HistoriqueConnexionService;
import ma.it6.portefeuille.service.HistoriqueStatutService;
import ma.it6.portefeuille.service.InitiativeService;
import ma.it6.portefeuille.service.UtilisateurService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Codes HTTP d'erreur de l'API : chaque anomalie de requête doit renvoyer la
 * status REST attendue, jamais une 500 « erreur interne ».
 *
 * <p>Avant correction, tous les cas ci-dessous répondaient 500 car le
 * {@code @ExceptionHandler(Exception.class)} du
 * {@link GlobalExceptionHandler} capturait les exceptions standard de Spring
 * MVC (méthode inconnue, JSON illisible, paramètre de type invalide…). Ces
 * tests verrouillent la correction.</p>
 *
 * <p>Contexte identique à {@code SecuriteApiTest} : vraie chaîne de sécurité,
 * services simulés.</p>
 */
@WebMvcTest(controllers = {
        InitiativeController.class,
        AuthController.class
})
@Import({SecurityConfig.class, JwtService.class})
class ApiErreurHttpTest {

    @Autowired
    private MockMvc mockMvc;

    /** Corps de connexion conforme à {@code LoginRequest}. */
    private static final String CORPS_LOGIN = """
            {"email":"admin@it6.ma","motDePasse":"Admin@123"}
            """;

    // ---- Services applicatifs simulés (l'anomalie survient avant leur appel) ----
    @MockitoBean
    private InitiativeService initiativeService;
    @MockitoBean
    private EtapeService etapeService;
    @MockitoBean
    private UtilisateurService utilisateurService;
    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private BudgetService budgetService;
    @MockitoBean
    private HistoriqueStatutService historiqueStatutService;
    @MockitoBean
    private HistoriqueConnexionService historiqueConnexionService;
    @MockitoBean
    private AuthenticationManager authenticationManager;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;
    @MockitoBean
    private SessionsService sessionsService;
    @MockitoBean
    private ProtectionConnexions protectionConnexions;

    @Nested
    @DisplayName("Une requête malformée n'est pas une erreur interne")
    class RequetesMalFormees {

        @Test
        @DisplayName("DELETE sur une route POST : 405, pas 500")
        void methodeNonSupportee() throws Exception {
            mockMvc.perform(delete("/api/auth/login").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("DELETE")));
        }

        @Test
        @DisplayName("JSON tronqué : 400 avec message exploitable, pas 500")
        void jsonMalforme() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("JSON invalide")));
        }

        @Test
        @DisplayName("Content-Type text/plain : 415, pas 500")
        void typeContenuNonSupporte() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(CORPS_LOGIN)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.status").value(415));
        }
    }

    @Nested
    @DisplayName("Paramètres invalides : 400 en nommant le paramètre")
    class ParametresInvalides {

        @Test
        @DisplayName("GET /initiatives/abc (path variable non numérique) : 400")
        void variableDeCheminInvalide() throws Exception {
            mockMvc.perform(get("/api/initiatives/abc")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("abc")));
        }

        @Test
        @DisplayName("?page=abc (paramètre de requête non numérique) : 400")
        void parametreDeRequeteInvalide() throws Exception {
            mockMvc.perform(get("/api/initiatives")
                            .param("page", "abc")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("Négociation de contenu")
    class Negotiation {

        @Test
        @DisplayName("Accept: application/xml sur une réponse JSON : 406 sans corps")
        void typeReponseNonAccepte() throws Exception {
            when(initiativeService.lister(any(), any(), any(), any(), any())).thenReturn(List.of());

            mockMvc.perform(get("/api/initiatives")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .accept(MediaType.APPLICATION_XML))
                    .andExpect(status().isNotAcceptable())
                    .andExpect(content().string(""));
        }
    }

    /**
     * Injecte un {@code Authentication} portant les permissions indiquées
     * (équivalent local de {@code jwt()}, cf. {@code SecuriteApiTest}).
     */
    private static RequestPostProcessor auth(String... codes) {
        return SecurityMockMvcRequestPostProcessors.authentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "test@it6.ma", "n/a", List.of(aut(codes))));
    }

    private static GrantedAuthority[] aut(String... codes) {
        return java.util.Arrays.stream(codes)
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(code))
                .toArray(GrantedAuthority[]::new);
    }
}
