package ma.it6.portefeuille.security;

import ma.it6.portefeuille.entity.JetonVerification;
import ma.it6.portefeuille.repository.JetonVerificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Cycle de vie des jetons de verification d'adresse e-mail.
 *
 * <ul>
 *   <li>creation : jeton opaque aleatoire (256 bits), seul son hachage
 *       SHA-256 est stocke ; expiration a 24 heures ;</li>
 *   <li>validation : le jeton est marque utilise ; une adresse e-mail
 *       ne conserve qu'un jeton actif a la fois (les precedents sont
 *       supprimes a la creation) ;</li>
 *   <li>reutilisation d'un jeton deja utilise : echec (le compte
 *       concerned doit redemander une verification).</li>
 * </ul>
 */
@Service
public class JetonVerificationService {

    private final JetonVerificationRepository repository;
    private final SecureRandom aleatoire = new SecureRandom();

    public JetonVerificationService(JetonVerificationRepository repository,
                                    @Value("${app.verification.email-duree-heures:24}") int dureeHeures) {
        this.repository = repository;
        this.dureeHeures = dureeHeures;
    }

    private final int dureeHeures;

    /** Cree un nouveau jeton pour l'adresse e-mail et retourne sa valeur en clair. */
    @Transactional
    public String creer(String email) {
        repository.deleteByEmail(email);

        byte[] octets = new byte[32];
        aleatoire.nextBytes(octets);
        String clair = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);

        repository.save(JetonVerification.builder()
                .email(email.toLowerCase().trim())
                .jetonHash(empreinte(clair))
                .dateExpiration(LocalDateTime.now().plusHours(dureeHeures))
                .utilise(false)
                .build());
        return clair;
    }

    /**
     * Valide un jeton presente.
     *
     * @return l'adresse e-mail verifiee, ou {@code null} si le jeton est
     * inconnu, expire ou deja utilise.
     */
    @Transactional
    public String valider(String clair) {
        if (clair == null || clair.isBlank()) {
            return null;
        }
        JetonVerification jeton = repository.findByJetonHash(empreinte(clair.trim())).orElse(null);
        if (jeton == null) {
            return null;
        }
        if (Boolean.TRUE.equals(jeton.getUtilise()) || jeton.estExpire()) {
            if (jeton.estExpire()) {
                repository.delete(jeton);
            }
            return null;
        }
        jeton.setUtilise(true);
        repository.save(jeton);
        return jeton.getEmail();
    }

    /** Hachage SHA-256 hexadecimal du jeton en clair. */
    static String empreinte(String clair) {
        return RefreshTokenService.empreinte(clair);
    }
}
