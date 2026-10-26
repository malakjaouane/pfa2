package ma.it6.portefeuille.security;

import ma.it6.portefeuille.config.SecurityConfig;
import ma.it6.portefeuille.controller.AssistantIaController;
import ma.it6.portefeuille.controller.AuthController;
import ma.it6.portefeuille.controller.BudgetController;
import ma.it6.portefeuille.controller.DashboardController;
import ma.it6.portefeuille.controller.DemandeController;
import ma.it6.portefeuille.controller.EtapeController;
import ma.it6.portefeuille.controller.HistoriqueConnexionController;
import ma.it6.portefeuille.controller.HistoriqueStatutController;
import ma.it6.portefeuille.controller.InitiativeController;
import ma.it6.portefeuille.controller.ObjectifController;
import ma.it6.portefeuille.controller.UtilisateurController;
import ma.it6.portefeuille.dto.AssistantIaResponse;
import ma.it6.portefeuille.dto.StatutIaResponse;
import ma.it6.portefeuille.entity.Permission;
import ma.it6.portefeuille.entity.Role;
import ma.it6.portefeuille.entity.RolePermission;
import ma.it6.portefeuille.entity.RolePermissionId;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.service.AssistantIaService;
import ma.it6.portefeuille.service.BudgetService;
import ma.it6.portefeuille.service.DashboardService;
import ma.it6.portefeuille.service.DemandeService;
import ma.it6.portefeuille.service.EtapeService;
import ma.it6.portefeuille.service.HistoriqueConnexionService;
import ma.it6.portefeuille.service.HistoriqueStatutService;
import ma.it6.portefeuille.service.InitiativeService;
import ma.it6.portefeuille.service.ObjectifService;
import ma.it6.portefeuille.service.UtilisateurService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de sécurité de l'API (règles d'URL + filtrage JWT).
 *
 * <p>Le contexte contient la <strong>vraie</strong> {@code SecurityConfig} et le
 * <strong>vrai</strong> {@code JwtAuthenticationFilter} : seuls les services
 * applicatifs sont simulés. On teste donc exactement ce qui protège l'API en
 * production, et non une copie des règles.</p>
 *
 * <p>Deux familles de tests :</p>
 * <ol>
 *   <li><b>Règles d'URL</b> — jeton injecté par spring-security-test : isole le
 *       mappage permission → endpoint ;</li>
 *   <li><b>Filtrage JWT</b> — vrai jeton signé, vraie validation, et le
 *       chargement de l'utilisateur (permissions fraîches, compte actif) est
 *       simulé comme le ferait la base.</li>
 * </ol>
 */
@WebMvcTest(controllers = {
        InitiativeController.class,
        HistoriqueStatutController.class,
        HistoriqueConnexionController.class,
        EtapeController.class,
        UtilisateurController.class,
        ObjectifController.class,
        DashboardController.class,
        BudgetController.class,
        AuthController.class,
        AssistantIaController.class,
        DemandeController.class
})
@Import({SecurityConfig.class, JwtService.class})
class SecuriteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    /** Clé de signature lue dans application.yml (même bean réel qu'en prod). */
    @Value("${app.jwt.secret}")
    private String secretJwt;

    // ---- Services applicatifs simulés (la sécurité ne doit pas les appeler) ----
    @MockitoBean
    private InitiativeService initiativeService;
    @MockitoBean
    private ObjectifService objectifService;
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
    @MockitoBean
    private AssistantIaService assistantIaService;
    @MockitoBean
    private DemandeService demandeService;

    // =================================================================
    // 0. Assistant IA : permission dédiée IA_UTILISER + journal admin
    // =================================================================
    @Nested
    @DisplayName("Assistant IA (règles d'URL, permission IA_UTILISER)")
    class AssistantIa {

        @Test
        @DisplayName("statut : 401 sans jeton, 200 pour tout utilisateur connecté")
        void statut() throws Exception {
            when(assistantIaService.statut())
                    .thenReturn(new StatutIaResponse(true, true, true, "claude-haiku-4-5"));

            mockMvc.perform(get("/api/ia/statut"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/ia/statut")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.disponible").value(true))
                    .andExpect(jsonPath("$.modele").value("claude-haiku-4-5"));
        }

        @Test
        @DisplayName("question : 403 sans IA_UTILISER, 200 avec")
        void question() throws Exception {
            when(userDetailsService.chargerParEmail(anyString()))
                    .thenReturn(principal("test@it6.ma", "ADMIN", "IA_UTILISER"));
            when(assistantIaService.poserQuestion(anyString(), anyString(), any()))
                    .thenReturn(new AssistantIaResponse("Réponse sourcée.", "claude-haiku-4-5", 42L));

            // Simple lecture des initiatives : ne donne PAS le droit de poser des questions.
            mockMvc.perform(post("/api/ia/assistant")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\":\"Combien d'initiatives ?\",\"langue\":\"fr\"}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/ia/assistant")
                            .with(auth("IA_UTILISER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\":\"Combien d'initiatives ?\",\"langue\":\"fr\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reponse").value("Réponse sourcée."));
        }

        @Test
        @DisplayName("résumé : 403 sans IA_UTILISER, 200 avec")
        void resume() throws Exception {
            when(userDetailsService.chargerParEmail(anyString()))
                    .thenReturn(principal("test@it6.ma", "ADMIN", "IA_UTILISER"));
            when(assistantIaService.resumerInitiative(eq(1L), anyString(), any()))
                    .thenReturn(new AssistantIaResponse("Résumé.", "claude-haiku-4-5", 30L));

            mockMvc.perform(post("/api/ia/resume/1")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"langue\":\"fr\"}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/ia/resume/1")
                            .with(auth("IA_UTILISER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"langue\":\"fr\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("journal : réservé aux administrateurs (UTILISATEUR_GERER)")
        void journal() throws Exception {
            when(assistantIaService.journal()).thenReturn(List.of());

            mockMvc.perform(get("/api/ia/journal")
                            .with(auth("IA_UTILISER")))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/ia/journal")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isOk());
        }
    }

    // =================================================================
    // 1. Règles d'URL : chaque endpoint exige la bonne permission
    // =================================================================
    @Nested
    @DisplayName("Règles d'URL (jeton injecté par spring-security-test)")
    class ReglesDUrl {

        @Test
        @DisplayName("sans jeton : 401 sur une ressource protégée")
        void sansJeton() throws Exception {
            mockMvc.perform(get("/api/initiatives"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Authentification requise."));
        }

        @Test
        @DisplayName("/api/auth/login est ouvert : on atteint le contrôleur, pas le EntryPoint")
        void loginAccessible() throws Exception {
            // Le contrôleur tourne vraiment (il renvoie « identifiants incorrects »)
            // : si les règles d'URL bloquaient l'appel, on verrait
            // « Authentification requise. » du EntryPoint.
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("mauvais identifiants"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"x@it6.ma\",\"motDePasse\":\"Xyz123456\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect."));
        }

        @Test
        @DisplayName("VISITEUR : lit les initiatives, n'en crée pas et ne gère pas les comptes")
        void visiteur() throws Exception {
            when(initiativeService.lister(any())).thenReturn(List.of());
            when(etapeService.lister(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/initiatives")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/initiatives/1/etapes")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/initiatives")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());

            // Lecture des étapes permise, MAIS PAS leur écriture.
            mockMvc.perform(post("/api/initiatives/1/etapes")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/users")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("RESPONSABLE : crée les initiatives et gère les étapes, mais pas les comptes")
        void responsable() throws Exception {
            when(initiativeService.lister(any())).thenReturn(List.of());

            mockMvc.perform(post("/api/initiatives")
                            .with(auth("INITIATIVE_CREER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPS_INITIATIVE))
                    .andExpect(status().isCreated());

            mockMvc.perform(put("/api/etapes/1")
                            .with(auth("ETAPE_GERER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPS_ETAPE))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/users")
                            .with(auth("INITIATIVE_CREER", "ETAPE_GERER")))
                    .andExpect(status().isForbidden());

            // Le dashboard exige DASHBOARD_CONSULTER, pas la simple lecture.
            mockMvc.perform(get("/api/dashboard")
                            .with(auth("INITIATIVE_CREER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN : gère les comptes et le dashboard")
        void administrateur() throws Exception {
            when(utilisateurService.lister()).thenReturn(List.of());
            when(dashboardService.calculer(any())).thenReturn(null);

            mockMvc.perform(get("/api/users")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/dashboard")
                            .with(auth("DASHBOARD_CONSULTER")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("réordonnancement des étapes : ETAPE_GERER compte, pas la règle générale PUT")
        void reordonnerEtapes() throws Exception {
            when(etapeService.reordonner(any(), any())).thenReturn(List.of());

            // La règle spécifique doit primer sur « PUT /api/initiatives/** » :
            // ETAPE_GERER seul suffit, sans INITIATIVE_MODIFIER.
            mockMvc.perform(put("/api/initiatives/1/etapes/ordre")
                            .with(auth("ETAPE_GERER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[1,2]"))
                    .andExpect(status().isOk());

            // Et l'inverse : INITIATIVE_MODIFIER seul ne donne pas ce droit.
            mockMvc.perform(put("/api/initiatives/1/etapes/ordre")
                            .with(auth("INITIATIVE_MODIFIER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[1,2]"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("journal des statuts : lecture INITIATIVE_CONSULTER, suppression UTILISATEUR_GERER")
        void journalDesStatuts() throws Exception {
            when(historiqueStatutService.lister(any(), anyInt(), anyInt(), anyBoolean()))
                    .thenReturn(List.of());
            when(historiqueStatutService.compter(any())).thenReturn(0L);
            when(historiqueStatutService.auteurs()).thenReturn(List.of());

            // Sans jeton : le journal est une ressource protégée.
            mockMvc.perform(get("/api/historique-statuts"))
                    .andExpect(status().isUnauthorized());

            // Lecture ouverte à qui consulte les initiatives : aucun nouveau
            // droit n'est créé pour la page « Historique des statuts ».
            mockMvc.perform(get("/api/historique-statuts")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Total-Count", "0"));

            mockMvc.perform(get("/api/historique-statuts/auteurs")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());

            // Une permission qui n'est PAS la consultation ne suffit pas.
            mockMvc.perform(get("/api/historique-statuts")
                            .with(auth("DEMANDE_SOUMETTRE")))
                    .andExpect(status().isForbidden());

            // Création/Modification : aucun POST/PUT n'est déclaré sur le journal.
            mockMvc.perform(post("/api/historique-statuts")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed());

            // Suppression : réservée aux administrateurs (UTILISATEUR_GERER,
            // droit existant) — la consultation seule ne suffit pas.
            mockMvc.perform(delete("/api/historique-statuts/1"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/historique-statuts/1")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/historique-statuts/1")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isNoContent());

            // Purge TOUT le journal (bouton admin) : mêmes règles que la
            // suppression d'une ligne, sur la collection sans identifiant.
            mockMvc.perform(delete("/api/historique-statuts"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/historique-statuts")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/historique-statuts")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("demandes : recherche pour les acteurs, suppression DEMANDE_EVALUER")
        void demandesRechercheEtSuppression() throws Exception {
            when(demandeService.lister(any())).thenReturn(List.of());

            // Recherche : ouverte à ceux qui soumettent ou évaluent.
            mockMvc.perform(get("/api/demandes"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/demandes").with(auth("VISITEUR")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/demandes").param("recherche", "cloud")
                            .with(auth("DEMANDE_SOUMETTRE")))
                    .andExpect(status().isOk());

            // Suppression : « juste qui a le droit » = DEMANDE_EVALUER.
            mockMvc.perform(delete("/api/demandes/1"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/demandes/1").with(auth("VISITEUR")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/demandes/1").with(auth("DEMANDE_SOUMETTRE")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/demandes/1").with(auth("DEMANDE_EVALUER")))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("purge d'une initiative archivée : INITIATIVE_ARCHIVER")
        void purgeInitiativeArchivee() throws Exception {
            mockMvc.perform(delete("/api/initiatives/1"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/initiatives/1")
                            .with(auth("INITIATIVE_CREER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/initiatives/1")
                            .with(auth("INITIATIVE_ARCHIVER")))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("suppression d'un utilisateur : UTILISATEUR_GERER (règle /api/users/**)")
        void suppressionUtilisateur() throws Exception {
            // Même droit que la lecture des comptes : aucun nouveau droit créé.
            mockMvc.perform(delete("/api/users/1"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/users/1")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/users/1")
                            .with(auth("DEMANDE_EVALUER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/users/1")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("historique des connexions : administrateurs seuls, tous verbes")
        void historiqueDesConnexions() throws Exception {
            when(historiqueConnexionService.lister(any(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            when(historiqueConnexionService.compter(any())).thenReturn(0L);

            // Lecture : e-mails + IP = données de contrôle d'accès, le droit
            // de consultation des initiatives ne suffit PAS.
            mockMvc.perform(get("/api/historique-connexions"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/historique-connexions")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/historique-connexions")
                            .with(auth("DEMANDE_EVALUER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/historique-connexions")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Total-Count", "0"));

            // Aucun POST/PUT n'est déclaré sur le journal (405, pas 403).
            mockMvc.perform(post("/api/historique-connexions")
                            .with(auth("UTILISATEUR_GERER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed());

            // Suppression d'une ligne : mêmes règles que la lecture.
            mockMvc.perform(delete("/api/historique-connexions/1"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/historique-connexions/1")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/historique-connexions/1")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isNoContent());

            // Purge TOUT (bouton admin), collection sans identifiant.
            mockMvc.perform(delete("/api/historique-connexions"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/historique-connexions")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/historique-connexions")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("objectifs : lecture à tout compte connecté, suppression réservée à INITIATIVE_MODIFIER")
        void objectifs() throws Exception {
            when(objectifService.lister()).thenReturn(List.of());

            // Lecture du référentiel : tout compte authentifié suffit.
            mockMvc.perform(get("/api/objectifs"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/objectifs")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());

            // Suppression : même droit que la création et la modification
            // (règle SecurityConfig existante, aucun nouveau droit créé).
            mockMvc.perform(delete("/api/objectifs/7"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/objectifs/7")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/objectifs/7")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/objectifs/7")
                            .with(auth("INITIATIVE_MODIFIER")))
                    .andExpect(status().isNoContent());
        }
    }

    // =================================================================
    // 2. Filtrage JWT : le jeton ne fait pas foi à lui seul
    // =================================================================
    @Nested
    @DisplayName("Filtrage JWT (vrai jeton signé)")
    class FiltrageJwt {

        @Test
        @DisplayName("jeton d'une autre application (signature invalide) : 401")
        void signatureInvalide() throws Exception {
            // Même structure, mais signé avec une autre clé.
            JwtService autreCle = new JwtService(
                    "UneAutreCleDeSignatureToutDifferent000000000000", 28800000);
            String jeton = autreCle.generer(principal("v@it6.ma", "VISITEUR", "INITIATIVE_CONSULTER"));

            when(sessionsService.chargerActif("v@it6.ma"))
                    .thenReturn(Optional.of(principal("v@it6.ma", "VISITEUR", "INITIATIVE_CONSULTER")));

            mockMvc.perform(get("/api/initiatives").header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("jeton expiré : 401, même si la signature est bonne")
        void jetonExpire() throws Exception {
            // Même clé, mais expiration déjà passée.
            JwtService expire = new JwtService(secretJwt, -10_000);
            String jeton = expire.generer(principal("v@it6.ma", "VISITEUR", "INITIATIVE_CONSULTER"));

            when(sessionsService.chargerActif("v@it6.ma"))
                    .thenReturn(Optional.of(principal("v@it6.ma", "VISITEUR", "INITIATIVE_CONSULTER")));

            mockMvc.perform(get("/api/initiatives").header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("compte désactivé depuis : 401 immédiat, sans attendre l'expiration du jeton")
        void compteDesactive() throws Exception {
            String jeton = jwtService.generer(
                    principal("v@it6.ma", "VISITEUR", "INITIATIVE_CONSULTER", "INITIATIVE_CREER"));

            // Le jeton est valide, mais la base dit « désactivé » : c'est la
            // base qui fait foi.
            when(sessionsService.chargerActif("v@it6.ma")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/initiatives").header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(post("/api/initiatives")
                            .header("Authorization", "Bearer " + jeton)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("permissions fraîches : ce sont celles de la base, pas celles encodées dans le jeton")
        void permissionsFraiches() throws Exception {
            // Le jeton a été émis AVANT l'attribution de la permission :
            // il ne contient que la consultation.
            String jeton = jwtService.generer(principal("r@it6.ma", "RESPONSABLE", "INITIATIVE_CONSULTER"));

            // Depuis, le rôle a reçu INITIATIVE_CREER.
            when(sessionsService.chargerActif("r@it6.ma")).thenReturn(Optional.of(
                    principal("r@it6.ma", "RESPONSABLE", "INITIATIVE_CONSULTER", "INITIATIVE_CREER")));

            when(initiativeService.creer(any())).thenReturn(null);

            mockMvc.perform(post("/api/initiatives")
                            .header("Authorization", "Bearer " + jeton)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPS_INITIATIVE))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("révocation : la permission retirée à la base est perdue immédiatement")
        void permissionRetiree() throws Exception {
            String jeton = jwtService.generer(
                    principal("r@it6.ma", "RESPONSABLE", "INITIATIVE_CREER"));

            // Le jeton porte encore INITIATIVE_CREER, mais la base a changé.
            when(sessionsService.chargerActif("r@it6.ma")).thenReturn(Optional.of(
                    principal("r@it6.ma", "RESPONSABLE", "INITIATIVE_CONSULTER")));

            mockMvc.perform(post("/api/initiatives")
                            .header("Authorization", "Bearer " + jeton)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("jeton bien formé et compte actif : accès accordé")
        void jetonValide() throws Exception {
            String jeton = jwtService.generer(
                    principal("v@it6.ma", "VISITEUR", "INITIATIVE_CONSULTER"));

            when(sessionsService.chargerActif("v@it6.ma"))
                    .thenReturn(Optional.of(principal("v@it6.ma", "VISITEUR", "INITIATIVE_CONSULTER")));
            when(initiativeService.lister(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/initiatives").header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isOk());
        }
    }

    // =================================================================
    // =================================================================
    // 3. Phase 5 : budget, journal des statuts et transitions
    // =================================================================
    @Nested
    @DisplayName("Budgets, journal et transitions de statut")
    class Phase5 {

        @Test
        @DisplayName("sans jeton : le budget est prot�g� comme les autres ressources")
        void budgetSansJeton() throws Exception {
            mockMvc.perform(get("/api/budget"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/budget/entites"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("VISITEUR : pas de budget (permission 16 d�di�e)")
        void budgetRefuseAuVisiteur() throws Exception {
            mockMvc.perform(get("/api/budget")
                            .with(auth("INITIATIVE_CONSULTER", "DASHBOARD_CONSULTER")))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/budget/entites")
                            .with(auth("INITIATIVE_CONSULTER", "DASHBOARD_CONSULTER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN : acc�s aux agr�gats budg�taires")
        void budgetAccorde() throws Exception {
            when(budgetService.global(any())).thenReturn(null);
            when(budgetService.parEntite(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/budget")
                            .with(auth("BUDGET_CONSULTER")))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/budget/entites")
                            .with(auth("BUDGET_CONSULTER")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("glisser-d�poser : changer de statut exige INITIATIVE_MODIFIER")
        void changerStatut() throws Exception {
            when(initiativeService.changerStatut(any(), any())).thenReturn(null);

            mockMvc.perform(put("/api/initiatives/1/statut")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPS_CHANGEMENT_STATUT))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/initiatives/1/statut")
                            .with(auth("INITIATIVE_MODIFIER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPS_CHANGEMENT_STATUT))
                    .andExpect(status().isOk());

            // L'absence de corps valide est refus�e AVANT la r�gle d'URL ?
            // Non : Spring Security tourne en amont du validateur de beans.
            mockMvc.perform(put("/api/initiatives/1/statut")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPS_CHANGEMENT_STATUT))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("journal des statuts : lecture seule, via la consultation des initiatives")
        void historiqueStatuts() throws Exception {
            when(initiativeService.trouver(any())).thenReturn(null);
            when(historiqueStatutService.historique(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/initiatives/1/historique-statuts")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/initiatives/1/historique-statuts"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("champ de tri inconnu : 400 explicite, jamais un tri silencieux")
        void triNonAutorise() throws Exception {
            when(initiativeService.lister(any(), any(), any(), any(), any()))
                    .thenThrow(new BadRequestException("Champ de tri non autoris�."));

            mockMvc.perform(get("/api/initiatives")
                            .param("tri", "motDePasse")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isBadRequest());
        }
    }
    // Helpers
    // =================================================================

    /**
     * Corps d'une initiative conforme à {@code InitiativeRequest} : sans cela,
     * la validation {@code @Valid} renverrait 400 <em>avant</em> que la règle de
     * sécurité ne soit même évaluée, et le test ne prouverait rien.
     */
    private static final String CORPS_INITIATIVE = """
            {"code":"TEST-SEC-001","nom":"Initiative de test",\
            "idEntite":1,"idStatut":2,"idPriorite":2}
            """;

    /** Corps d'une étape conforme à {@code EtapeRequest} (seul « nom » est exigé). */
    private static final String CORPS_ETAPE = """
            {"nom":"Étape de test","ordre":1,"avancement":0}
            """;

    /** Corps d'un changement de statut (Kanban), conforme à {@code ChangerStatutRequest}. */
    private static final String CORPS_CHANGEMENT_STATUT = """
            {"idStatut":3,"commentaire":"Glisser-déposer depuis le Kanban"}
            """;

    /**
     * Injecte un {@code Authentication} portant les permissions indiquées.
     *
     * <p>Équivalent de {@code jwt()} de spring-security-test, mais sans la
     * dépendance oauth2-jose : ici, seule la <strong>règle d'URL</strong> est
     * testée (le passage réel du filtre JWT est couvert par le nested
     * {@code FiltrageJwt}).</p>
     */
    private static RequestPostProcessor auth(String... codes) {
        Authentication authentification = new UsernamePasswordAuthenticationToken(
                "test@it6.ma", "n/a", List.of(aut(codes)));
        return SecurityMockMvcRequestPostProcessors.authentication(authentification);
    }

    /**
     * Autorités au format attendu par {@code jwt().authorities(...)}
     * (signature varargs de spring-security-test).
     */
    private static GrantedAuthority[] aut(String... codes) {
        return java.util.Arrays.stream(codes)
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(code))
                .toArray(GrantedAuthority[]::new);
    }

    /** Construit un utilisateur réellement typé, comme le ferait la base. */
    private static PrincipalUser principal(String email, String codeRole, String... permissions) {
        Role role = new Role();
        role.setIdRole(codeRole.equals("ADMIN") ? 1L : codeRole.equals("RESPONSABLE") ? 2L : 3L);
        role.setCode(codeRole);
        role.setLibelle(codeRole);

        List<RolePermission> rolePermissions = new ArrayList<>();
        long idPermission = 1;
        for (String code : permissions) {
            Permission permission = new Permission();
            permission.setIdPermission(idPermission);
            permission.setCode(code);
            permission.setLibelle(code);

            RolePermission rp = new RolePermission();
            rp.setId(new RolePermissionId(role.getIdRole(), idPermission));
            rp.setRole(role);
            rp.setPermission(permission);
            rolePermissions.add(rp);
            idPermission++;
        }
        role.setRolePermissions(rolePermissions);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setIdUser(1L);
        utilisateur.setNom("Test");
        utilisateur.setPrenom("Sécurité");
        utilisateur.setEmail(email);
        utilisateur.setMotDePasse("{bcrypt}$2a$10$exemple");
        utilisateur.setActif(true);
        utilisateur.setRole(role);

        return new PrincipalUser(utilisateur);
    }
}
