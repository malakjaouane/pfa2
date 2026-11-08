package ma.it6.portefeuille.security;

import ma.it6.portefeuille.config.SecurityConfig;
import ma.it6.portefeuille.controller.AssistantIaController;
import ma.it6.portefeuille.controller.AuthController;
import ma.it6.portefeuille.controller.BudgetController;
import ma.it6.portefeuille.controller.CommentaireController;
import ma.it6.portefeuille.controller.DashboardController;
import ma.it6.portefeuille.controller.DemandeController;
import ma.it6.portefeuille.controller.DepenseController;
import ma.it6.portefeuille.controller.EtapeController;
import ma.it6.portefeuille.controller.HistoriqueConnexionController;
import ma.it6.portefeuille.controller.HistoriqueController;
import ma.it6.portefeuille.controller.InitiativeController;
import ma.it6.portefeuille.controller.JournalAuditController;
import ma.it6.portefeuille.controller.MonEspaceController;
import ma.it6.portefeuille.controller.NotificationController;
import ma.it6.portefeuille.controller.ObjectifController;
import ma.it6.portefeuille.controller.RisqueController;
import ma.it6.portefeuille.controller.UtilisateurController;
import ma.it6.portefeuille.dto.AssistantIaResponse;
import ma.it6.portefeuille.dto.RisqueResponse;
import ma.it6.portefeuille.dto.StatutIaResponse;
import ma.it6.portefeuille.entity.Permission;
import ma.it6.portefeuille.entity.Role;
import ma.it6.portefeuille.entity.RolePermission;
import ma.it6.portefeuille.entity.RolePermissionId;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.OrganisationRepository;
import ma.it6.portefeuille.repository.RoleRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.service.email.EmailService;
import ma.it6.portefeuille.service.AssistantIaService;
import ma.it6.portefeuille.service.AlerteService;
import ma.it6.portefeuille.service.BudgetService;
import ma.it6.portefeuille.service.CommentaireService;
import ma.it6.portefeuille.service.DashboardService;
import ma.it6.portefeuille.service.DemandeService;
import ma.it6.portefeuille.service.DepenseService;
import ma.it6.portefeuille.service.EtapeService;
import ma.it6.portefeuille.service.HistoriqueConnexionService;
import ma.it6.portefeuille.service.HistoriqueModificationService;
import ma.it6.portefeuille.service.InitiativeService;
import ma.it6.portefeuille.service.JournalAuditService;
import ma.it6.portefeuille.service.MonEspaceService;
import ma.it6.portefeuille.service.ObjectifService;
import ma.it6.portefeuille.service.RisqueService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        HistoriqueController.class,
        HistoriqueConnexionController.class,
        EtapeController.class,
        UtilisateurController.class,
        ObjectifController.class,
        DashboardController.class,
        BudgetController.class,
        AuthController.class,
        AssistantIaController.class,
        DemandeController.class,
        RisqueController.class,
        CommentaireController.class,
        DepenseController.class,
        JournalAuditController.class,
        NotificationController.class,
        MonEspaceController.class
})
@Import({SecurityConfig.class, JwtService.class, FiltreMotDePasseAChanger.class, CookieService.class})
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
    private HistoriqueModificationService historiqueService;
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
    @MockitoBean
    private RisqueService risqueService;
    @MockitoBean
    private CommentaireService commentaireService;
    @MockitoBean
    private DepenseService depenseService;
    @MockitoBean
    private JournalAuditService journalAuditService;
    @MockitoBean
    private AlerteService alerteService;
    @MockitoBean
    private MonEspaceService monEspaceService;
    @MockitoBean
    private RefreshTokenService refreshTokenService;
    @MockitoBean
    private UtilisateurRepository utilisateurRepository;
    @MockitoBean
    private RoleRepository roleRepository;
    @MockitoBean
    private OrganisationRepository organisationRepository;
    @MockitoBean
    private JetonVerificationService jetonVerificationService;
    @MockitoBean
    private JetonReinitialisationService jetonReinitialisationService;
    @MockitoBean
    private InvitationService invitationService;
    @MockitoBean
    private EmailService emailService;
    @MockitoBean
    private PasswordEncoder passwordEncoder;

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
        @DisplayName("journal des modifications : lecture INITIATIVE_CONSULTER, aucune écriture possible")
        void journalDesModifications() throws Exception {
            when(historiqueService.lister(any(), anyInt(), anyInt(), anyBoolean()))
                    .thenReturn(new HistoriqueModificationService.PageJournal(List.of(), 0L));
            when(historiqueService.auteurs()).thenReturn(List.of());
            when(historiqueService.exporter(any())).thenReturn(List.of());

            // Sans jeton : le journal est une ressource protégée.
            mockMvc.perform(get("/api/historique"))
                    .andExpect(status().isUnauthorized());

            // Lecture ouverte à qui consulte les initiatives : aucun nouveau
            // droit n'est créé pour la page « Historique ».
            mockMvc.perform(get("/api/historique")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Total-Count", "0"));

            mockMvc.perform(get("/api/historique/auteurs")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());

            // Export complet : mêmes droits que la lecture, aucun droit ouvert.
            mockMvc.perform(get("/api/historique/export")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());

            // Une permission qui n'est PAS la consultation ne suffit pas.
            mockMvc.perform(get("/api/historique")
                            .with(auth("DEMANDE_SOUMETTRE")))
                    .andExpect(status().isForbidden());

            // Création/Modification : aucun POST/PUT n'est déclaré sur le journal.
            mockMvc.perform(post("/api/historique")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed());

            // JOURNAL IMMUABLE : aucune suppression n'existe — même un
            // administrateur (UTILISATEUR_GERER) reçoit un refus explicite,
            // pas un 204 : la preuve d'audit ne se retouche pas.
            mockMvc.perform(delete("/api/historique"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/historique")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(delete("/api/historique")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(delete("/api/historique/1")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("demandes : recherche + vue Archives pour les acteurs, suppression DEMANDE_EVALUER")
        void demandesRechercheEtSuppression() throws Exception {
            when(demandeService.lister(any(), any())).thenReturn(List.of());

            // Recherche : ouverte à ceux qui soumettent ou évaluent.
            mockMvc.perform(get("/api/demandes"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/demandes").with(auth("VISITEUR")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/demandes").param("recherche", "cloud")
                            .with(auth("DEMANDE_SOUMETTRE")))
                    .andExpect(status().isOk());
            // Vue « Archives » : même droit que la vue « Actives » (GET).
            mockMvc.perform(get("/api/demandes").param("archive", "true")
                            .with(auth("DEMANDE_EVALUER")))
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
        @DisplayName("demandes : archivage PATCH /archive et /restaurer pour les deux acteurs")
        void demandesArchivage() throws Exception {
            // Bascule Actives | Archives : ouverte aux deux acteurs de la
            // page (ceux qui soumettent comme ceux qui évaluent) — aucun
            // droit nouveau créé ; hors périmètre demandes -> 403.
            mockMvc.perform(patch("/api/demandes/1/archive"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(patch("/api/demandes/1/archive").with(auth("VISITEUR")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/demandes/1/archive").with(auth("INITIATIVE_CREER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/demandes/1/archive").with(auth("DEMANDE_SOUMETTRE")))
                    .andExpect(status().isOk());
            mockMvc.perform(patch("/api/demandes/1/restaurer").with(auth("DEMANDE_EVALUER")))
                    .andExpect(status().isOk());
            mockMvc.perform(patch("/api/demandes/1/restaurer").with(auth("VISITEUR")))
                    .andExpect(status().isForbidden());
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

        @Test
        @DisplayName("risques : lecture INITIATIVE_CONSULTER, écriture RISQUE_GERER")
        void risques() throws Exception {
            when(risqueService.lister(any())).thenReturn(List.of());
            when(risqueService.listerGlobal(any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
            when(risqueService.creer(any(), any()))
                    .thenReturn(new RisqueResponse(9L, "Panne fournisseur",
                            "Panne du fournisseur principal", "ouverte", null,
                            3, 3, null, null, 5L, "I-5", 9, "AMBRE"));
            when(risqueService.modifier(any(), any()))
                    .thenReturn(new RisqueResponse(9L, "Panne fournisseur",
                            "Panne du fournisseur principal", "ouverte", null,
                            3, 2, null, null, 5L, "I-5", 6, "AMBRE"));

            // Registre par initiative : le GET est rattrapé par la règle
            // /api/initiatives/** (INITIATIVE_CONSULTER) — le visiteur lit,
            // il n'écrit pas.
            mockMvc.perform(get("/api/initiatives/5/risques"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/initiatives/5/risques")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());

            // Registre global : lecture = INITIATIVE_CONSULTER comme le
            // registre par initiative.
            mockMvc.perform(get("/api/risques")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());

            // Création (POST initiative-scoped) : RISQUE_GERER — gérer les
            // risques est un métier distinct, pas un corollaire de la
            // modification de l'initiative.
            mockMvc.perform(post("/api/initiatives/5/risques")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPS_RISQUE))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/initiatives/5/risques")
                            .with(auth("INITIATIVE_MODIFIER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPS_RISQUE))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/initiatives/5/risques")
                            .with(auth("RISQUE_GERER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPS_RISQUE))
                    .andExpect(status().isCreated());

            // Modification / suppression : mêmes règles sur /api/risques/**.
            mockMvc.perform(put("/api/risques/9")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/risques/9")
                            .with(auth("RISQUE_GERER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPS_RISQUE))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/risques/9")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/risques/9")
                            .with(auth("RISQUE_GERER")))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("commentaires : lecture via consultation, ajout COMMENTAIRE_AJOUTER")
        void commentaires() throws Exception {
            when(commentaireService.lister(any())).thenReturn(List.of());
            when(commentaireService.creer(any(), any(), any()))
                    .thenReturn(new ma.it6.portefeuille.dto.CommentaireResponse(
                            1L, 1L, "Commentaire de test", null, null));

            // Lecture ouverte par la règle /api/initiatives/** en
            // INITIATIVE_CONSULTER — le visiteur lit le fil.
            mockMvc.perform(get("/api/initiatives/1/commentaires")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());

            // Ajout : COMMENTAIRE_AJOUTER, jamais INITIATIVE_CONSULTER seul.
            mockMvc.perform(post("/api/initiatives/1/commentaires")
                            .with(auth("INITIATIVE_CONSULTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contenu\":\"Commentaire de test\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/initiatives/1/commentaires")
                            .with(auth("COMMENTAIRE_AJOUTER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contenu\":\"Commentaire de test\"}"))
                    .andExpect(status().isCreated());

            // Suppression : même permission.
            mockMvc.perform(delete("/api/commentaires/1")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/commentaires/1")
                            .with(auth("COMMENTAIRE_AJOUTER")))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("dépenses : tous verbes réservés à DEPENSE_SAISIR, y compris la lecture")
        void depenses() throws Exception {
            when(depenseService.lister(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(new ma.it6.portefeuille.service.DepenseService.PageDepenses(
                            List.of(), 0L, java.math.BigDecimal.ZERO));
            when(depenseService.creer(any(), any(), any()))
                    .thenReturn(new ma.it6.portefeuille.dto.DepenseResponse(
                            1L, 1L, "I-1", "Initiative test", "Dépense de test",
                            java.math.BigDecimal.TEN,
                            java.time.LocalDate.now(), "materiel", null, null));

            // Lecture refusée sans DEPENSE_SAISIR (information financière).
            mockMvc.perform(get("/api/depenses")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/initiatives/1/depenses")
                            .with(auth("INITIATIVE_MODIFIER")))
                    .andExpect(status().isForbidden());

            // Lecture accordée avec DEPENSE_SAISIR.
            mockMvc.perform(get("/api/depenses")
                            .with(auth("DEPENSE_SAISIR")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Total-Count", "0"));

            // Écriture : mêmes règles.
            mockMvc.perform(post("/api/initiatives/1/depenses")
                            .with(auth("INITIATIVE_MODIFIER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"libelle\":\"Dépense de test\",\"montant\":10,\"dateDepense\":\"2026-01-01\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/initiatives/1/depenses")
                            .with(auth("DEPENSE_SAISIR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"libelle\":\"Dépense de test\",\"montant\":10,\"dateDepense\":\"2026-01-01\"}"))
                    .andExpect(status().isCreated());
            mockMvc.perform(put("/api/depenses/1")
                            .with(auth("DEPENSE_SAISIR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"libelle\":\"Dépense modifiée\",\"montant\":20,\"dateDepense\":\"2026-01-02\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/depenses/1")
                            .with(auth("DEPENSE_SAISIR")))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("journal d'audit : administrateur seul (UTILISATEUR_GERER), lecture seule")
        void journalAudit() throws Exception {
            when(journalAuditService.lister(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(new JournalAuditService.PageJournal(List.of(), 0L));

            // Responsable : refusé (il ne voit pas le journal des actions).
            mockMvc.perform(get("/api/journal-audit")
                            .with(auth("INITIATIVE_MODIFIER", "RISQUE_GERER")))
                    .andExpect(status().isForbidden());

            // Administrateur : accordé.
            mockMvc.perform(get("/api/journal-audit")
                            .with(auth("UTILISATEUR_GERER")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Total-Count", "0"));

            // Journal immuable : aucun verbe d'écriture n'est déclaré.
            mockMvc.perform(post("/api/journal-audit")
                            .with(auth("UTILISATEUR_GERER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("mon espace et notifications : tout utilisateur connecté")
        void monEspaceEtNotifications() throws Exception {
            when(monEspaceService.consulter())
                    .thenReturn(new ma.it6.portefeuille.dto.MonEspaceResponse(
                            null, null, List.of(), List.of(), List.of(),
                            List.of(), List.of(), List.of(), null, null));
            when(alerteService.toutes()).thenReturn(List.of());

            // Sans jeton : 401.
            mockMvc.perform(get("/api/mon-espace"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isUnauthorized());

            // Avec n'importe quelle permission : accordé.
            mockMvc.perform(get("/api/mon-espace")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/notifications")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
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
    // 2bis. Changement de mot de passe obligatoire (filtre V3)
    // =================================================================
    @Nested
    @DisplayName("Changement de mot de passe obligatoire (marqueur V3)")
    class MotDePasseObligatoire {

        /** Principal authentifié dont le compte porte le marqueur V3. */
        private PrincipalUser compteAMarque() {
            PrincipalUser principal = principal("admin@it6.ma", "ADMIN", "INITIATIVE_CONSULTER");
            principal.getUtilisateur().setDoitChangerMotDePasse(true);
            return principal;
        }

        @Test
        @DisplayName("compte marqué : route métier bloquée en 403 avec le code MOT_DE_PASSE_A_CHANGER")
        void routeMetierBloquee() throws Exception {
            String jeton = jwtService.generer(compteAMarque());
            when(sessionsService.chargerActif("admin@it6.ma")).thenReturn(Optional.of(compteAMarque()));

            mockMvc.perform(get("/api/initiatives").header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_A_CHANGER"));

            mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_A_CHANGER"));
        }

        @Test
        @DisplayName("compte marqué : la route de secours changer-mot-de-passe passe le filtre")
        void routeDeSecoursAccordee() throws Exception {
            String jeton = jwtService.generer(compteAMarque());
            when(sessionsService.chargerActif("admin@it6.ma")).thenReturn(Optional.of(compteAMarque()));
            when(userDetailsService.chargerParEmail("admin@it6.ma")).thenReturn(compteAMarque());
            when(refreshTokenService.creer(any())).thenReturn("refresh-test");

            mockMvc.perform(post("/api/auth/changer-mot-de-passe")
                            .header("Authorization", "Bearer " + jeton)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"motDePasseActuel\":\"Admin@123\",\"nouveauMotDePasse\":\"Nouveau2026\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("compte marqué : /api/auth/me reste lisible (le profil porte le marqueur)")
        void profilLisible() throws Exception {
            String jeton = jwtService.generer(compteAMarque());
            when(sessionsService.chargerActif("admin@it6.ma")).thenReturn(Optional.of(compteAMarque()));
            when(userDetailsService.chargerParEmail("admin@it6.ma")).thenReturn(compteAMarque());

            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.doitChangerMotDePasse").value(true));
        }

        @Test
        @DisplayName("compte marqué : le login renvoie le marqueur au frontend")
        void loginTransporteLeMarqueur() throws Exception {
            PrincipalUser compte = compteAMarque();
            when(authenticationManager.authenticate(any()))
                    .thenReturn(new org.springframework.security.authentication
                            .UsernamePasswordAuthenticationToken(
                            compte, null, compte.getAuthorities()));
            when(refreshTokenService.creer(any())).thenReturn("refresh-test");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"admin@it6.ma\",\"motDePasse\":\"Admin@123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.doitChangerMotDePasse").value(true));
        }

        @Test
        @DisplayName("compte NON marqué : accès normal, marqueur à false")
        void compteNonMarque() throws Exception {
            PrincipalUser normal = principal("v@it6.ma", "VISITEUR", "INITIATIVE_CONSULTER");
            normal.getUtilisateur().setDoitChangerMotDePasse(false);
            String jeton = jwtService.generer(normal);

            when(sessionsService.chargerActif("v@it6.ma")).thenReturn(Optional.of(normal));
            when(initiativeService.lister(any())).thenReturn(List.of());
            when(userDetailsService.chargerParEmail("v@it6.ma")).thenReturn(normal);

            mockMvc.perform(get("/api/initiatives").header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.doitChangerMotDePasse").value(false));
        }
    }

    // =================================================================
    // 2 bis. Phase 1 C : cookies HttpOnly (access 15 min) + refresh rotatif
    // =================================================================
    @Nested
    @DisplayName("Cookies de session et refresh rotatif (V4)")
    class CookiesSession {

        private PrincipalUser visiteur() {
            PrincipalUser p = principal("v@it6.ma", "VISITEUR", "INITIATIVE_CONSULTER");
            p.getUtilisateur().setDoitChangerMotDePasse(false);
            return p;
        }

        @Test
        @DisplayName("login : cookie pfa_access HttpOnly + SameSite=Strict, jeton aussi dans le corps")
        void loginPoseCookiesHttpOnly() throws Exception {
            PrincipalUser compte = visiteur();
            when(authenticationManager.authenticate(any()))
                    .thenReturn(new UsernamePasswordAuthenticationToken(
                            compte, null, compte.getAuthorities()));
            when(refreshTokenService.creer(any())).thenReturn("refresh-clair");

            String[] cookies = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"v@it6.ma\",\"motDePasse\":\"Visi@123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andReturn().getResponse().getHeaders("Set-Cookie").toArray(new String[0]);

            String access = java.util.Arrays.stream(cookies)
                    .filter(c -> c.startsWith("pfa_access=")).findFirst().orElse("");
            String refresh = java.util.Arrays.stream(cookies)
                    .filter(c -> c.startsWith("pfa_refresh=")).findFirst().orElse("");

            org.assertj.core.api.Assertions.assertThat(access)
                    .contains("HttpOnly").contains("SameSite=Strict")
                    .contains("Path=/").doesNotContain("Path=/api/auth");
            org.assertj.core.api.Assertions.assertThat(refresh)
                    .contains("HttpOnly").contains("SameSite=Strict")
                    .contains("Path=/api/auth");
        }

        @Test
        @DisplayName("authentification par le seul cookie pfa_access (sans en-tête Authorization)")
        void authentificationParCookie() throws Exception {
            PrincipalUser compte = visiteur();
            String jeton = jwtService.generer(compte);
            when(sessionsService.chargerActif("v@it6.ma")).thenReturn(Optional.of(compte));
            when(initiativeService.lister(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/initiatives")
                            .cookie(new jakarta.servlet.http.Cookie("pfa_access", jeton)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("refresh sans cookie : 401 (pas de session ouverte)")
        void refreshSansCookie() throws Exception {
            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("refresh avec cookie valide : rotation (nouveau refresh) + nouvel access")
        void refreshAvecCookieValide() throws Exception {
            PrincipalUser compte = visiteur();
            when(refreshTokenService.tourner("clair-ok"))
                    .thenReturn(new RefreshTokenService.Rotation("clair-suivant", compte.getUtilisateur()));

            String[] cookies = mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("pfa_refresh", "clair-ok")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andReturn().getResponse().getHeaders("Set-Cookie").toArray(new String[0]);

            String access = java.util.Arrays.stream(cookies)
                    .filter(c -> c.startsWith("pfa_access=")).findFirst().orElse("");
            String refresh = java.util.Arrays.stream(cookies)
                    .filter(c -> c.startsWith("pfa_refresh=")).findFirst().orElse("");

            org.assertj.core.api.Assertions.assertThat(access).contains("HttpOnly");
            org.assertj.core.api.Assertions.assertThat(refresh).contains("clair-suivant");
        }

        @Test
        @DisplayName("refresh d'un jeton déjà présenté (vol) : 401 + cookies vidés")
        void refreshReutilisation() throws Exception {
            when(refreshTokenService.tourner("deja-vole")).thenReturn(null);

            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("pfa_refresh", "deja-vole")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Set-Cookie",
                            org.hamcrest.Matchers.containsString("pfa_access=")))
                    .andExpect(header().string("Set-Cookie",
                            org.hamcrest.Matchers.containsString("Max-Age=0")));
        }

        @Test
        @DisplayName("logout : révocation des refresh + cookies vidés")
        void logoutRevoqueEtVide() throws Exception {
            PrincipalUser compte = visiteur();
            String jeton = jwtService.generer(compte);
            when(sessionsService.chargerActif("v@it6.ma")).thenReturn(Optional.of(compte));

            String[] cookies = mockMvc.perform(post("/api/auth/logout")
                            .cookie(new jakarta.servlet.http.Cookie("pfa_access", jeton)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getHeaders("Set-Cookie").toArray(new String[0]);

            verify(refreshTokenService).revoquerToutParEmail("v@it6.ma");
            org.assertj.core.api.Assertions.assertThat(java.util.Arrays.asList(cookies))
                    .anyMatch(c -> c.startsWith("pfa_access=") && c.contains("Max-Age=0"))
                    .anyMatch(c -> c.startsWith("pfa_refresh=") && c.contains("Max-Age=0"));
        }
    }

    // =================================================================
    // 2B3 : Mot de passe oublie / reinitialisation
    // =================================================================
    @Nested
    @DisplayName("Mot de passe oublie / reinitialisation (2B3)")
    class MotDePasseOublie {

        @Test
        @DisplayName("adresse inconnue : 200 sans envoi d'e-mail (ne revele rien)")
        void emailInconnu() throws Exception {
            when(utilisateurRepository.existsByEmail("inconnu@it6.ma")).thenReturn(false);

            mockMvc.perform(post("/api/auth/mot-de-passe-oublie")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"inconnu@it6.ma\"}"))
                    .andExpect(status().isOk());

            verify(emailService, never()).envoyer(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("adresse connue : 200 et e-mail envoye")
        void emailConnu() throws Exception {
            when(utilisateurRepository.existsByEmail("admin@it6.ma")).thenReturn(true);
            when(jetonReinitialisationService.creer("admin@it6.ma")).thenReturn("jeton-test");

            mockMvc.perform(post("/api/auth/mot-de-passe-oublie")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"admin@it6.ma\"}"))
                    .andExpect(status().isOk());

            verify(emailService, times(1)).envoyer(eq("admin@it6.ma"), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("jeton invalide : 400")
        void jetonInvalide() throws Exception {
            when(jetonReinitialisationService.valider("mauvais")).thenReturn(null);

            mockMvc.perform(post("/api/auth/reinitialiser-mot-de-passe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"jeton\":\"mauvais\",\"nouveauMotDePasse\":\"Nouveau2026!\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
        }

        @Test
        @DisplayName("jeton valide : mot de passe change, sessions revoquees")
        void jetonValide() throws Exception {
            when(jetonReinitialisationService.valider("bon-jeton")).thenReturn("admin@it6.ma");
            when(utilisateurRepository.findByEmail("admin@it6.ma"))
                    .thenReturn(Optional.of(utilisateurTest("admin@it6.ma")));
            when(passwordEncoder.encode(anyString())).thenReturn("mot-de-passe-encode");

            ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);

            mockMvc.perform(post("/api/auth/reinitialiser-mot-de-passe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"jeton\":\"bon-jeton\",\"nouveauMotDePasse\":\"Nouveau2026!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("reinitialise")));

            verify(utilisateurRepository).save(captor.capture());
            Utilisateur sauvegarde = captor.getValue();
            org.assertj.core.api.Assertions.assertThat(sauvegarde.getEmailVerifie()).isTrue();
            org.assertj.core.api.Assertions.assertThat(sauvegarde.getMotDePasse()).isEqualTo("mot-de-passe-encode");
            verify(refreshTokenService).revoquerToutParEmail("admin@it6.ma");
        }
    }

    /** Utilisateur minimal typé comme en base, pour les tests de reinitialisation. */
    private static Utilisateur utilisateurTest(String email) {
        Utilisateur u = new Utilisateur();
        u.setIdUser(1L);
        u.setNom("Test");
        u.setPrenom("Securite");
        u.setEmail(email);
        u.setMotDePasse("{bcrypt}$2a$10$ancien");
        u.setActif(true);
        u.setEmailVerifie(false);
        return u;
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
        @DisplayName("fil d'activité de la fiche : lecture seule, via la consultation des initiatives")
        void historiqueFiche() throws Exception {
            when(initiativeService.trouver(any())).thenReturn(null);
            when(historiqueService.historiqueInitiative(any(), any(), anyInt(), anyInt()))
                    .thenReturn(new HistoriqueModificationService.PageJournal(List.of(), 0L));

            mockMvc.perform(get("/api/initiatives/1/historique")
                            .with(auth("INITIATIVE_CONSULTER")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Total-Count", "0"));

            mockMvc.perform(get("/api/initiatives/1/historique"))
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

    /** Corps d'un risque conforme à {@code RisqueRequest} (titre, description et notes 1-5 exigés). */
    private static final String CORPS_RISQUE = """
            {"titre":"Panne fournisseur","description":"Risque de test",\
            "probabilite":3,"impact":3}
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
        utilisateur.setEmailVerifie(true);

        return new PrincipalUser(utilisateur);
    }
}
