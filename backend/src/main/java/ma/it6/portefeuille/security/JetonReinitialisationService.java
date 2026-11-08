package ma.it6.portefeuille.security;

import ma.it6.portefeuille.entity.JetonReinitialisation;
import ma.it6.portefeuille.repository.JetonReinitialisationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Cycle de vie des jetons de reinitialisation de mot de passe.
 *
 * <ul>
 *   <li>creation : jeton opaque aleatoire (256 bits), seul son hachage
 *       SHA-256 est stocke ; expiration a 1 heure ;</li>
 *   <li>validation : le jeton est marque utilise ; une adresse e-mail
 *       ne conserve qu'un jeton actif a la fois ;</li>
 *   <li>reutilisation d'un jeton deja utilise : echec.</li>
 * </ul>
 */
@Service
public class JetonReinitialisationService {

    private final JetonReinitialisationRepository repository;
    private final SecureRandom aleatoire = new SecureRandom();

    public JetonReinitialisationService(JetonReinitialisationRepository repository,
                                        @Value("${app.reinitialisation.duree-heures:1}") int dureeHeures) {
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

        repository.save(JetonReinitialisation.builder()
                .email(email.toLowerCase().trim())
                .jetonHash(RefreshTokenService.empreinte(clair))
                .dateExpiration(LocalDateTime.now().plusHours(dureeHeures))
                .utilise(false)
                .build());
        return clair;
    }

    /**
     * Valide un jeton presente.
     *
     * @return l'adresse e-mail associee, ou {@code null} si le jeton est
     * inconnu, expire ou deja utilise.
     */
    @Transactional
    public String valider(String clair) {
        if (clair == null || clair.isBlank()) {
            return null;
        }
        JetonReinitialisation jeton = repository.findByJetonHash(RefreshTokenService.empreinte(clair.trim())).orElse(null);
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
}
