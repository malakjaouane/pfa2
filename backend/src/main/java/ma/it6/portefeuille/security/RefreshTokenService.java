package ma.it6.portefeuille.security;

import ma.it6.portefeuille.entity.RefreshToken;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Jetons de rafraîchissement à <strong>rotation</strong> (migration V4).
 *
 * <ul>
 *   <li>jeton opaque aléatoire (256 bits, SecureRandom) envoyé en cookie
 *       HttpOnly ; seul son hachage SHA-256 est stocké ;</li>
 *   <li>à chaque présentation, le jeton est marqué « utilisé » et un
 *       nouveau est émis (rotation) ;</li>
 *   <li>une <strong>réutilisation</strong> d'un jeton déjà utilisé est
 *       traitée comme un vol : tous les jetons du compte sont révoqués ;</li>
 *   <li>expiration glissante ({@code app.jwt.refresh-jours}, 7 jours) ;
 *       la déconnexion et tout changement de mot de passe révoquent tout.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final int joursValidite;
    private final SecureRandom aleatoire = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.jwt.refresh-jours:7}") int joursValidite) {
        this.repository = repository;
        this.joursValidite = joursValidite;
    }

    /** Résultat d'une rotation réussie : nouveau jeton en clair + compte. */
    public record Rotation(String jeton, Utilisateur utilisateur) {
    }

    /** Émet un nouveau jeton pour le compte et renvoie sa valeur en clair. */
    @Transactional
    public String creer(Utilisateur utilisateur) {
        // Purge opportuniste des jetons expirés : la table reste petite.
        repository.purgerExpire(LocalDateTime.now().minusDays(1));

        byte[] octets = new byte[32];
        aleatoire.nextBytes(octets);
        String clair = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);

        repository.save(RefreshToken.builder()
                .jetonHash(empreinte(clair))
                .utilisateur(utilisateur)
                .dateCreation(LocalDateTime.now())
                .dateExpiration(LocalDateTime.now().plusDays(joursValidite))
                .utilise(false)
                .build());
        return clair;
    }

    /**
     * Valide puis <strong>tourne</strong> un jeton présenté.
     *
     * @return la rotation (nouveau jeton + compte), ou {@code null} si le
     * jeton est inconnu, expiré — ou s'il a déjà servi (réutilisation :
     * tous les jetons du compte sont alors révoqués).
     */
    @Transactional
    public Rotation tourner(String clair) {
        if (clair == null || clair.isBlank()) {
            return null;
        }
        Optional<RefreshToken> trouve = repository.findByJetonHash(empreinte(clair.trim()));
        if (trouve.isEmpty()) {
            return null;
        }
        RefreshToken jeton = trouve.get();

        // Compte désactivé ou supprimé entre-temps : plus aucune session.
        if (!Boolean.TRUE.equals(jeton.getUtilisateur().getActif())) {
            repository.revoquerTout(jeton.getUtilisateur().getIdUser());
            return null;
        }

        if (Boolean.TRUE.equals(jeton.getUtilise())) {
            // Réutilisation d'un jeton déjà tourné : vol probable (cookie
            // copié) — on coupe court en révoquant tout le compte.
            LOG.warn("Reutilisation de refresh token detectee : revocation du compte {}",
                    jeton.getUtilisateur().getEmail());
            repository.revoquerTout(jeton.getUtilisateur().getIdUser());
            return null;
        }
        if (jeton.estExpire()) {
            repository.delete(jeton);
            return null;
        }

        jeton.setUtilise(true);
        jeton.setDateUtilisation(LocalDateTime.now());
        repository.save(jeton);

        String nouveau = creer(jeton.getUtilisateur());
        return new Rotation(nouveau, jeton.getUtilisateur());
    }

    /** Révoque tous les jetons du compte (déconnexion, changement de mdp). */
    @Transactional
    public void revoquerTout(Long idUser) {
        repository.revoquerTout(idUser);
    }

    /** @see #revoquerTout(Long) — variante e-mail (changement de mdp). */
    @Transactional
    public void revoquerToutParEmail(String email) {
        if (email != null && !email.isBlank()) {
            repository.revoquerToutParEmail(email.trim().toLowerCase());
        }
    }

    /** Hachage SHA-256 hexadécimal du jeton en clair. */
    static String empreinte(String clair) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hache = digest.digest(clair.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexa = new StringBuilder(hache.length * 2);
            for (byte octet : hache) {
                hexa.append(String.format("%02x", octet));
            }
            return hexa.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti par la JVM : jamais atteint.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
