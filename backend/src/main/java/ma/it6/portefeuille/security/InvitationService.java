package ma.it6.portefeuille.security;

import ma.it6.portefeuille.entity.Invitation;
import ma.it6.portefeuille.entity.Invitation;
import ma.it6.portefeuille.repository.InvitationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Cycle de vie des invitations de collegues (2B4).
 *
 * <ul>
 *   <li>creation : jeton opaque aleatoire, expiration a 7 jours ; une
 *       adresse e-mail ne conserve qu'une invitation active a la fois ;</li>
 *   <li>validation : le jeton est marque utilise ; une adresse e-mail
 *       ne conserve qu'un jeton actif a la fois ;</li>
 *   <li>reutilisation d'un jeton deja utilise : echec.</li>
 * </ul>
 */
@Service
public class InvitationService {

    private final InvitationRepository repository;
    private final SecureRandom aleatoire = new SecureRandom();

    public InvitationService(InvitationRepository repository,
                             @Value("${app.invitation.duree-jours:7}") int dureeJours) {
        this.repository = repository;
        this.dureeJours = dureeJours;
    }

    private final int dureeJours;

    /** Cree une invitation et retourne son jeton en clair. */
    @Transactional
    public String creer(String email, Long idOrganisation, Long idRole, Long creePar) {
        repository.deleteByEmail(email);

        byte[] octets = new byte[32];
        aleatoire.nextBytes(octets);
        String clair = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);

        repository.save(Invitation.builder()
                .email(email.toLowerCase().trim())
                .idOrganisation(idOrganisation)
                .idRole(idRole)
                .jetonHash(RefreshTokenService.empreinte(clair))
                .dateExpiration(LocalDateTime.now().plusDays(dureeJours))
                .utilise(false)
                .creePar(creePar)
                .creeLe(LocalDateTime.now())
                .build());
        return clair;
    }

    /**
     * Valide un jeton d'invitation.
     *
     * @return l'acceptation (email, organisation, role) ou {@code null} si
     * le jeton est inconnu, expire ou deja utilise.
     */
    @Transactional
    public Invitation.Acceptation valider(String clair) {
        if (clair == null || clair.isBlank()) {
            return null;
        }
        Invitation invitation = repository.findByJetonHash(RefreshTokenService.empreinte(clair.trim())).orElse(null);
        if (invitation == null) {
            return null;
        }
        if (Boolean.TRUE.equals(invitation.getUtilise()) || invitation.estExpire()) {
            if (invitation.estExpire()) {
                repository.delete(invitation);
            }
            return null;
        }
        invitation.setUtilise(true);
        repository.save(invitation);
        return new Invitation.Acceptation(invitation.getEmail(), invitation.getIdOrganisation(), invitation.getIdRole());
    }
}
