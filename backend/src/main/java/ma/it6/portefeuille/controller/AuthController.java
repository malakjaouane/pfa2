package ma.it6.portefeuille.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ma.it6.portefeuille.dto.ChangerMotDePasseRequest;
import ma.it6.portefeuille.dto.InscriptionRequest;
import ma.it6.portefeuille.dto.LoginRequest;
import ma.it6.portefeuille.dto.LoginResponse;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.entity.Organisation;
import ma.it6.portefeuille.entity.Role;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.mapper.UtilisateurMapper;
import ma.it6.portefeuille.repository.OrganisationRepository;
import ma.it6.portefeuille.repository.RoleRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.security.CookieService;
import ma.it6.portefeuille.security.CustomUserDetailsService;
import ma.it6.portefeuille.security.JwtService;
import ma.it6.portefeuille.security.JetonVerificationService;
import ma.it6.portefeuille.security.PrincipalUser;
import ma.it6.portefeuille.security.PolitiqueMotDePasse;
import ma.it6.portefeuille.security.ProtectionConnexions;
import ma.it6.portefeuille.security.RefreshTokenService;
import ma.it6.portefeuille.service.email.EmailService;
import ma.it6.portefeuille.service.email.EmailTemplateBuilder;
import ma.it6.portefeuille.service.HistoriqueConnexionService;
import ma.it6.portefeuille.service.UtilisateurService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Authentification : connexion (POST /api/auth/login), rafraîchissement du
 * jeton d'accès (POST /api/auth/refresh), déconnexion (POST /api/auth/logout),
 * consultation du compte courant (GET /api/auth/me) et changement du mot de
 * passe propre à l'utilisateur connecté (POST /api/auth/changer-mot-de-passe).
 *
 * <p>Les jetons circulent en <strong>cookies HttpOnly</strong>
 * ({@link CookieService}) : access 15 min sur toutes les routes, refresh
 * 7 jours restreint à {@code /api/auth} et tourné à chaque usage. Le corps
 * de la réponse de login contient aussi le jeton d'accès pour les clients
 * non navigateurs (en-tête {@code Authorization}), sans effet de bord : le
 * cookie reste la seule pièce persistante côté navigateur.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ProtectionConnexions protectionConnexions;
    private final UtilisateurService utilisateurService;
    private final HistoriqueConnexionService historiqueConnexionService;
    private final RefreshTokenService refreshTokenService;
    private final CookieService cookieService;
    private final JetonVerificationService jetonVerificationService;
    private final EmailService emailService;
    private final OrganisationRepository organisationRepository;
    private final RoleRepository roleRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.verification.email.lien-base:http://localhost/}")
    private String lienBaseVerification;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          CustomUserDetailsService userDetailsService,
                          ProtectionConnexions protectionConnexions,
                          UtilisateurService utilisateurService,
                          HistoriqueConnexionService historiqueConnexionService,
                          RefreshTokenService refreshTokenService,
                          CookieService cookieService,
                          JetonVerificationService jetonVerificationService,
                          EmailService emailService,
                          OrganisationRepository organisationRepository,
                          RoleRepository roleRepository,
                          UtilisateurRepository utilisateurRepository,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.protectionConnexions = protectionConnexions;
        this.utilisateurService = utilisateurService;
        this.historiqueConnexionService = historiqueConnexionService;
        this.refreshTokenService = refreshTokenService;
        this.cookieService = cookieService;
        this.jetonVerificationService = jetonVerificationService;
        this.emailService = emailService;
        this.organisationRepository = organisationRepository;
        this.roleRepository = roleRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Connexion. Les échecs sont comptabilisés par compte et par adresse IP
     * (voir {@link ProtectionConnexions}) : au-delà du seuil, l'API répond 429
     * avant même de tester les identifiants.
     *
     * <p>Chaque tentative — y compris refusée — est aussi déposée dans
     * l'historique des connexions (traçabilité loi 09-08).</p>
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpServletRequest,
                                               jakarta.servlet.http.HttpServletResponse response) {
        String adresseIp = adresseIp(httpServletRequest);
        try {
            protectionConnexions.verifierAutorise(adresseIp, request.email());
        } catch (RuntimeException refus) {
            tracer(request.email(), null, adresseIp, false, refus.getMessage());
            throw refus;
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.motDePasse()));
            PrincipalUser principal = (PrincipalUser) authentication.getPrincipal();
            // Adresse e-mail non verifiee : pas de jeton emis.
            if (!Boolean.TRUE.equals(principal.getUtilisateur().getEmailVerifie())) {
                tracer(request.email(), null, adresseIp, false, "E-mail non verifie");
                return ResponseEntity.status(403).body(Map.of(
                        "code", "EMAIL_NON_VERIFIE",
                        "message", "Votre adresse e-mail n'est pas encore verifiee. "
                                + "Verifiez votre boite mail avant de vous connecter."));
            }
            protectionConnexions.reussite(adresseIp, request.email());
            tracer(request.email(), principal.getUtilisateur().getIdUser(), adresseIp, true, null);

            String token = jwtService.generer(principal);
            // Cookies HttpOnly : access (15 min, toutes routes) + refresh
            // (7 jours, /api/auth, tourné à chaque usage).
            cookieService.ajouterAccess(response, token);
            cookieService.ajouterRefresh(response,
                    refreshTokenService.creer(principal.getUtilisateur()));
            return ResponseEntity.ok(reponse(token, principal));
        } catch (AuthenticationException echec) {
            protectionConnexions.enregistrerEchec(adresseIp, request.email());
            tracer(request.email(), null, adresseIp, false, echec.getMessage());
            throw echec;
        }
    }

    /**
     * Renvoie un nouvel access token (cookie renouvelé) en échange du cookie
     * {@code pfa_refresh} — qui est lui-même <strong>tourné</strong> : l'ancien
     * devient invalide. Un jeton déjà présenté (copie de cookie) révoque tous
     * les jetons du compte et répond 401.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response) {
        String clair = cookieService.lire(request, CookieService.NOM_REFRESH);
        RefreshTokenService.Rotation rotation = refreshTokenService.tourner(clair);

        if (rotation == null) {
            cookieService.viderAccess(response);
            cookieService.viderRefresh(response);
            return ResponseEntity.status(401).body(
                    Map.of("message", "Session expirée ou jeton de rafraîchissement invalide."));
        }

        PrincipalUser principal = new PrincipalUser(rotation.utilisateur());
        String token = jwtService.generer(principal);
        cookieService.ajouterAccess(response, token);
        cookieService.ajouterRefresh(response, rotation.jeton());
        return ResponseEntity.ok(reponse(token, principal));
    }

    /**
     * Déconnexion : révoque tous les jetons de rafraîchissement du compte et
     * vide les deux cookies. Best-effort si l'access token est déjà expiré —
     * les cookies partent dans tous les cas.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       jakarta.servlet.http.HttpServletResponse response) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getName() != null) {
                refreshTokenService.revoquerToutParEmail(authentication.getName());
            }
        } catch (RuntimeException ignore) {
            // La révocation best-effort ne doit pas empêcher la déconnexion.
        }
        cookieService.viderAccess(response);
        cookieService.viderRefresh(response);
        return ResponseEntity.ok().build();
    }

    /**
     * Inscription : cree une organisation + son premier administrateur, puis
     * envoie un e-mail de verification. Le compte existe mais ne peut pas se
     * connecter tant que l'adresse n'est pas verifiee (403
     * EMAIL_NON_VERIFIE au login).
     */
    @PostMapping("/signup")
    public ResponseEntity<?> inscription(@Valid @RequestBody InscriptionRequest request) {
        String email = request.email().toLowerCase().trim();
        if (utilisateurRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "EMAIL_DEJA_UTILISE",
                    "message", "Cette adresse e-mail est deja utilisee."));
        }
        PolitiqueMotDePasse.valider(request.motDePasse(), email);

        Organisation organisation = Organisation.builder()
                .nom(request.nomOrganisation().trim())
                .slug(slugify(request.nomOrganisation()))
                .plan("gratuit").statut("active")
                .build();
        organisation = organisationRepository.save(organisation);

        Role roleAdmin = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role ADMIN introuvable (referentiels manquants)."));

        Utilisateur u = Utilisateur.builder()
                .nom(request.nom().trim()).prenom(request.prenom().trim()).email(email)
                .motDePasse(passwordEncoder.encode(request.motDePasse()))
                .actif(true).emailVerifie(false).role(roleAdmin).entite(null)
                .build();
        u.setIdOrganisation(organisation.getIdOrganisation());
        utilisateurRepository.save(u);

        String jeton = jetonVerificationService.creer(email);
        String base = lienBaseVerification.endsWith("/") ? lienBaseVerification : lienBaseVerification + "/";
        String lien = base + "verifier-email?jeton=" + jeton;
        EmailTemplateBuilder.Modele modele = EmailTemplateBuilder.personnaliser(
                EmailTemplateBuilder.verification(EmailTemplateBuilder.LANGUE_FR), request.prenom(), lien);
        emailService.envoyer(email, modele.sujet(), modele.html(), modele.texte());

        return ResponseEntity.status(202).body(Map.of(
                "message", "Compte cree. Un e-mail de verification a ete envoye a " + email));
    }

    /**
     * Verification de l'adresse e-mail : valide le jeton a usage unique et
     * autorise la connexion du compte.
     */
    @GetMapping("/verifier-email")
    public ResponseEntity<?> verifierEmail(@RequestParam String jeton) {
        String email = jetonVerificationService.valider(jeton);
        if (email == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "JETON_INVALIDE",
                    "message", "Jeton de verification invalide, deja utilise ou expire."));
        }
        utilisateurRepository.findByEmail(email).ifPresentOrElse(
                u -> {
                    u.setEmailVerifie(true);
                    utilisateurRepository.save(u);
                },
                () -> {
                    throw new ResourceNotFoundException("Compte introuvable pour l'e-mail " + email);
                });
        return ResponseEntity.ok(Map.of(
                "message", "Adresse e-mail verifiee. Vous pouvez vous connecter."));
    }

    private static String slugify(String nom) {
        String s = nom.toLowerCase().trim();
        s = s.replace('à', 'a').replace('á', 'a').replace('â', 'a').replace('ã', 'a');
        s = s.replace('è', 'e').replace('é', 'e').replace('ê', 'e').replace('ë', 'e');
        s = s.replace('ì', 'i').replace('í', 'i').replace('î', 'i').replace('ï', 'i');
        s = s.replace('ò', 'o').replace('ó', 'o').replace('ô', 'o').replace('õ', 'o').replace('ö', 'o');
        s = s.replace('ù', 'u').replace('ú', 'u').replace('û', 'u').replace('ü', 'u');
        s = s.replace('ç', 'c').replace('ñ', 'n');
        s = s.replaceAll("[^a-z0-9]+", "-");
        return s.replaceAll("^-|-+$", "");
    }

    /**
     * Dépôt <strong>best-effort</strong> dans l'historique des connexions :
     * la traçabilité ne doit jamais être le motif de l'échec d'une
     * connexion (ni masquer l'exception d'authentification d'origine).
     */
    private void tracer(String email, Long idUtilisateur, String adresseIp, boolean succes, String motif) {
        try {
            Long id = idUtilisateur;
            if (id == null) {
                // Tentative échouée sur un compte existant : on rattache la
                // ligne au compte (un compte inconnu reste sans lien).
                try {
                    id = userDetailsService.chargerParEmail(email).getUtilisateur().getIdUser();
                } catch (RuntimeException absent) {
                    id = null;
                }
            }
            historiqueConnexionService.enregistrer(email, id, adresseIp, succes, motif);
        } catch (RuntimeException ignore) {
            // Journal secondaire : jamais bloquant pour l'authentification.
        }
    }

    /** Adresse IP réelle du client, en tenant compte des reverse-proxy. */
    private static String adresseIp(HttpServletRequest httpServletRequest) {
        String enTete = httpServletRequest.getHeader("X-Forwarded-For");
        if (enTete != null && !enTete.isBlank()) {
            int virgule = enTete.indexOf(',');
            return (virgule > 0 ? enTete.substring(0, virgule) : enTete).trim();
        }
        return httpServletRequest.getRemoteAddr();
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        PrincipalUser principal = userDetailsService.chargerParEmail(authentication.getName());
        return ResponseEntity.ok(reponse(null, principal));
    }

    /**
     * Changement du mot de passe de l'utilisateur connecté : mot de passe
     * actuel vérifié, politique appliquée (8 caractères, lettre + chiffre,
     * différent de l'email), hashage BCrypt.
     */
    @PostMapping("/changer-mot-de-passe")
    public ResponseEntity<Void> changerMotDePasse(@Valid @RequestBody ChangerMotDePasseRequest request,
                                                  jakarta.servlet.http.HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        utilisateurService.changerMotDePassePropre(
                authentication.getName(), request.motDePasseActuel(), request.nouveauMotDePasse());
        // Tous les anciens jetons de rafraîchissement partent (un vol de
        // cookie éventuel est neutralisé) — puis un nouveau est émis pour
        // ne pas déconnecter l'utilisateur qui vient de changer son mdp.
        refreshTokenService.revoquerToutParEmail(authentication.getName());
        PrincipalUser principal = userDetailsService.chargerParEmail(authentication.getName());
        cookieService.ajouterRefresh(response, refreshTokenService.creer(principal.getUtilisateur()));
        return ResponseEntity.ok().build();
    }

    private LoginResponse reponse(String token, PrincipalUser principal) {
        Utilisateur u = principal.getUtilisateur();
        // Exclut uniquement le marqueur de rôle (« ROLE_ADMINISTRATEUR »),
        // jamais les permissions — y compris une permission nommée « ROLE_GERER ».
        String marqueurRole = u.getRole() != null ? "ROLE_" + u.getRole().getCode() : null;
        List<String> permissions = principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> !a.equals(marqueurRole))
                .sorted()
                .toList();

        return new LoginResponse(
                token,
                token != null ? "Bearer" : null,
                u.getIdUser(),
                u.getNom(),
                u.getPrenom(),
                u.getEmail(),
                UtilisateurMapper.toRoleDto(u.getRole()),
                UtilisateurMapper.toEntiteDto(u.getEntite()),
                permissions,
                Boolean.TRUE.equals(u.getDoitChangerMotDePasse())
        );
    }
}