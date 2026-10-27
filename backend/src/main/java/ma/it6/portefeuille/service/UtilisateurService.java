package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.UserRequest;
import ma.it6.portefeuille.dto.UserResponse;
import ma.it6.portefeuille.entity.Entite;
import ma.it6.portefeuille.entity.Role;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.UtilisateurMapper;
import ma.it6.portefeuille.repository.DemandeInitiativeRepository;
import ma.it6.portefeuille.repository.EntiteRepository;
import ma.it6.portefeuille.repository.RoleRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.security.PolitiqueMotDePasse;
import ma.it6.portefeuille.security.SessionsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestion des utilisateurs (profil ADMINISTRATEUR).
 * Les mots de passe sont systématiquement stockés hashés (BCrypt).
 *
 * <p>Toute modification invalide la session en cache : le compte désactivé ou le
 * rôle évolué est pris en compte à la <strong>requête suivante</strong>, sans
 * attendre l'expiration du jeton.</p>
 */
@Service
public class UtilisateurService {

    /** Code du rôle administrateur (voir {@code DataInitializer}). */
    private static final String ROLE_ADMIN = "ADMIN";

    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final EntiteRepository entiteRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionsService sessionsService;
    private final DemandeInitiativeRepository demandeRepository;
    private final HistoriqueModificationService historiqueService;

    public UtilisateurService(UtilisateurRepository utilisateurRepository,
                              RoleRepository roleRepository,
                              EntiteRepository entiteRepository,
                              PasswordEncoder passwordEncoder,
                              SessionsService sessionsService,
                              DemandeInitiativeRepository demandeRepository,
                              HistoriqueModificationService historiqueService) {
        this.utilisateurRepository = utilisateurRepository;
        this.roleRepository = roleRepository;
        this.entiteRepository = entiteRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionsService = sessionsService;
        this.demandeRepository = demandeRepository;
        this.historiqueService = historiqueService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> lister() {
        return utilisateurRepository.findAllByOrderByIdUserAsc().stream()
                .map(UtilisateurMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse trouver(Long idUser) {
        return UtilisateurMapper.toResponse(getUtilisateur(idUser));
    }

    @Transactional
    public UserResponse creer(UserRequest request) {
        if (utilisateurRepository.existsByEmail(request.email().trim().toLowerCase())) {
            throw new BadRequestException("Un utilisateur avec cet email existe déjà.");
        }
        if (request.motDePasse() == null || request.motDePasse().isBlank()) {
            throw new BadRequestException("Le mot de passe est obligatoire à la création. "
                    + PolitiqueMotDePasse.ENONCE);
        }
        PolitiqueMotDePasse.valider(request.motDePasse(), request.email());

        Role role = roleRepository.findById(request.idRole())
                .orElseThrow(() -> new ResourceNotFoundException("Rôle introuvable : " + request.idRole()));

        Utilisateur u = Utilisateur.builder()
                .nom(request.nom().trim())
                .prenom(request.prenom().trim())
                .email(request.email().trim().toLowerCase())
                .motDePasse(passwordEncoder.encode(request.motDePasse()))
                .telephone(request.telephone())
                .actif(request.actif() == null || request.actif())
                .role(role)
                .entite(resoudreEntite(request.idEntite()))
                .build();

        Utilisateur enregistre = utilisateurRepository.save(u);

        // Journal d'administration : la création d'un compte laisse une
        // trace (nouvelle valeur = l'adresse, jamais le mot de passe).
        journaliserCompte(enregistre, HistoriqueModificationService.CHAMP_CREATION,
                null, enregistre.getEmail(), "Création du compte");

        return UtilisateurMapper.toResponse(enregistre);
    }

    @Transactional
    public UserResponse modifier(Long idUser, UserRequest request) {
        Utilisateur u = getUtilisateur(idUser);
        String ancienEmail = u.getEmail();

        String nouvelEmail = request.email().trim().toLowerCase();
        if (utilisateurRepository.existsByEmailAndIdUserNot(nouvelEmail, idUser)) {
            throw new BadRequestException("Un autre utilisateur possède déjà cet email.");
        }

        Role role = roleRepository.findById(request.idRole())
                .orElseThrow(() -> new ResourceNotFoundException("Rôle introuvable : " + request.idRole()));
        boolean actifApres = request.actif() == null ? Boolean.TRUE.equals(u.getActif()) : request.actif();

        // Garde-fou : jamais sans administrateur actif (RM : continuité d'exploitation).
        verifierDernierAdministrateur(u, role, actifApres);

        // Journal : valeurs figées avant application, pour le diff champ à
        // champ (le mot de passe n'entre JAMAIS dans les valeurs).
        Map<String, String> avant = valeursJournalisees(u);
        boolean motDePasseChange = request.motDePasse() != null && !request.motDePasse().isBlank();

        u.setNom(request.nom().trim());
        u.setPrenom(request.prenom().trim());
        u.setEmail(nouvelEmail);
        u.setTelephone(request.telephone());
        u.setActif(actifApres);
        u.setRole(role);
        u.setEntite(resoudreEntite(request.idEntite()));

        // Mot de passe optionnel à la modification : s'il est fourni, il est
        // validé par la politique puis ré-hashé.
        if (motDePasseChange) {
            PolitiqueMotDePasse.valider(request.motDePasse(), nouvelEmail);
            u.setMotDePasse(passwordEncoder.encode(request.motDePasse()));
        }

        Utilisateur enregistre = utilisateurRepository.save(u);

        // Journal d'administration : une ligne par champ réellement modifié
        // (y compris le changement de rôle). Le mot de passe ne laisse qu'une
        // ligne signal sans aucune valeur.
        journaliserDiffCompte(enregistre, avant);
        if (motDePasseChange) {
            journaliserCompte(enregistre, HistoriqueModificationService.CHAMP_MOT_DE_PASSE,
                    null, null, "Modification du mot de passe");
        }

        // Le compte est immédiatement ré-authentifié à la requête suivante
        // (invalidation différée jusqu'au commit pour ne pas relire trop tôt).
        sessionsService.invaliderApresCommit(ancienEmail);
        sessionsService.invaliderApresCommit(enregistre.getEmail());

        return UtilisateurMapper.toResponse(enregistre);
    }

    /**
     * Suppression <strong>définitive</strong> d'un compte (droit existant
     * {@code UTILISATEUR_GERER}, imposé par SecurityConfig sur
     * {@code DELETE /api/users/**}).
     *
     * <p>Trois garde-fous, tous en 400 avec un message explicite :</p>
     * <ol>
     *   <li><strong>son propre compte</strong> — on ne se supprime pas soi-même
     *       (sinon un dernier admin pourrait s'effacer en un clic) ;</li>
     *   <li><strong>le dernier administrateur actif</strong> — continuité
     *       d'exploitation, même garde-fou que {@link #modifier} ;</li>
     *   <li><strong>un compte qui porte encore des demandes</strong> — la clé
     *       étrangère de {@code demande_initiative.demandeur} est sans
     *       cascade : on désactive plutôt le compte (les demandes, elles,
     *       restent).</li>
     * </ol>
     *
     * <p>Les autres liens (initiatives en charge, journal des statuts,
     * connexions) sont en {@code ON DELETE SET NULL} : le compte part, ses
     * œuvres restent. La session en cache est invalidée pour couper
     * immédiatement l'accès du compte supprimé.</p>
     */
    @Transactional
    public void supprimer(Long idUser) {
        Utilisateur cible = getUtilisateur(idUser);

        // 1. Jamais de suppression de son propre compte.
        Authentication connecte = SecurityContextHolder.getContext().getAuthentication();
        if (connecte != null && connecte.isAuthenticated()
                && cible.getEmail() != null
                && cible.getEmail().equalsIgnoreCase(connecte.getName())) {
            throw new BadRequestException("Impossible de supprimer votre propre compte.");
        }

        // 2. Jamais de portefeuille sans administrateur actif.
        boolean adminActif = cible.getRole() != null
                && ROLE_ADMIN.equals(cible.getRole().getCode())
                && Boolean.TRUE.equals(cible.getActif());
        if (adminActif
                && !utilisateurRepository.existsByIdUserNotAndActifTrueAndRoleCode(idUser, ROLE_ADMIN)) {
            throw new BadRequestException(
                    "Opération refusée : ce compte est le dernier administrateur actif. "
                            + "Désactivez d'abord un autre administrateur.");
        }

        // 3. Pas de suppression d'un compte qui porte encore des demandes :
        //    on conseille la désactivation plutôt que de perdre la donnée.
        long demandes = demandeRepository.countByDemandeur_IdUser(idUser);
        if (demandes > 0) {
            throw new BadRequestException("Opération refusée : ce compte porte "
                    + demandes + " demande(s) d'évaluation. "
                    + "Désactivez le compte au lieu de le supprimer.");
        }

        utilisateurRepository.delete(cible);

        // Journal d'administration : la suppression du compte reste inscrite
        // (ancienne valeur = l'adresse). L'auteur est l'administrateur qui
        // agit — jamais le compte parti.
        journaliserCompte(cible, HistoriqueModificationService.CHAMP_SUPPRESSION,
                cible.getEmail(), null, "Suppression du compte");

        // La suppression est immédiatement effective côté session (différée
        // jusqu'au commit pour ne pas relire un compte déjà parti).
        sessionsService.invaliderApresCommit(cible.getEmail());
    }

    /**
     * Changement de mot de passe par l'utilisateur connecté (profil courant).
     *
     * <p>Contrôles : mot de passe actuel vérifié par hash, politique de
     * mot de passe appliquée, et nouveau mot de passe différent de l'actuel.
     * La session en cache est invalidée pour que le hash courant soit relu.</p>
     */
    @Transactional
    public void changerMotDePassePropre(String email, String motDePasseActuel, String nouveauMotDePasse) {
        String adresse = email == null ? "" : email.trim().toLowerCase();
        Utilisateur u = utilisateurRepository.findByEmail(adresse)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + adresse));

        if (u.getMotDePasse() == null || !passwordEncoder.matches(motDePasseActuel, u.getMotDePasse())) {
            throw new BadRequestException("Le mot de passe actuel est incorrect.");
        }
        PolitiqueMotDePasse.valider(nouveauMotDePasse, u.getEmail());
        if (passwordEncoder.matches(nouveauMotDePasse, u.getMotDePasse())) {
            throw new BadRequestException("Le nouveau mot de passe doit être différent de l'actuel.");
        }

        u.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        utilisateurRepository.save(u);

        // Journal : le CHANGEMENT est tracé, jamais la valeur.
        journaliserCompte(u, HistoriqueModificationService.CHAMP_MOT_DE_PASSE,
                null, null, "Changement de mot de passe (profil)");

        sessionsService.invaliderApresCommit(u.getEmail());
    }

    // ------------------------------------------------------------------
    // Journal d'administration
    // ------------------------------------------------------------------

    /**
     * Dépose une ligne du journal générique pour le compte {@code cible}
     * (auteur = administrateur connecté). Aucun lien d'initiative : le
     * compte n'appartient à personne.
     */
    private void journaliserCompte(Utilisateur cible,
                                   String champ,
                                   String ancienne,
                                   String nouvelle,
                                   String commentaire) {
        historiqueService.ecrire(HistoriqueModificationService.TYPE_UTILISATEUR,
                cible.getIdUser(), null, champ, ancienne, nouvelle,
                commentaire, HistoriqueModificationService.auteurCourant(), false);
    }

    /** Diff champ à champ du compte après modification. */
    private void journaliserDiffCompte(Utilisateur apres, Map<String, String> avant) {
        historiqueService.diff(HistoriqueModificationService.TYPE_UTILISATEUR,
                apres.getIdUser(), null, avant, valeursJournalisees(apres),
                HistoriqueModificationService.auteurCourant(), false);
    }

    /**
     * Valeurs affichables des champs du compte — le père du diff
     * journalisé. Le <strong>mot de passe n'entre jamais ici</strong> :
     * son changement ne laisse qu'une ligne signal sans valeur.
     */
    private static Map<String, String> valeursJournalisees(Utilisateur u) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        valeurs.put("nom", u.getNom());
        valeurs.put("prenom", u.getPrenom());
        valeurs.put("email", u.getEmail());
        valeurs.put("telephone", u.getTelephone());
        valeurs.put("actif", u.getActif() == null ? null : (Boolean.TRUE.equals(u.getActif()) ? "Oui" : "Non"));
        valeurs.put("role", u.getRole() == null ? null : u.getRole().getCode());
        valeurs.put("entite", u.getEntite() == null ? null : u.getEntite().getNom());
        return valeurs;
    }

    /**
     * Refuse une modification qui laisserait le portefeuille sans aucun
     * compte administrateur actif : on ne peut ni désactiver ni rétrograder
     * le dernier administrateur en exercice.
     */
    private void verifierDernierAdministrateur(Utilisateur u, Role roleApres, boolean actifApres) {
        boolean actifAvant = Boolean.TRUE.equals(u.getActif());
        boolean adminAvant = u.getRole() != null && ROLE_ADMIN.equals(u.getRole().getCode());
        boolean adminApres = ROLE_ADMIN.equals(roleApres.getCode());

        if (!adminAvant || !actifAvant) {
            return; // ce compte n'est pas (ou plus) un administrateur actif
        }
        if (adminApres && actifApres) {
            return; // il le reste
        }
        if (!utilisateurRepository.existsByIdUserNotAndActifTrueAndRoleCode(u.getIdUser(), ROLE_ADMIN)) {
            throw new BadRequestException(
                    "Opération refusée : ce compte est le dernier administrateur actif. "
                            + "Désactivez d'abord un autre administrateur.");
        }
    }

    private Utilisateur getUtilisateur(Long idUser) {
        return utilisateurRepository.findById(idUser)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + idUser));
    }

    private Entite resoudreEntite(Long idEntite) {
        if (idEntite == null) {
            return null;
        }
        return entiteRepository.findById(idEntite)
                .orElseThrow(() -> new ResourceNotFoundException("Entité introuvable : " + idEntite));
    }
}
