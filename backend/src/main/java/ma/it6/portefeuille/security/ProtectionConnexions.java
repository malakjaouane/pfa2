package ma.it6.portefeuille.security;

import ma.it6.portefeuille.entity.TentativeConnexion;
import ma.it6.portefeuille.exception.TooManyRequestsException;
import ma.it6.portefeuille.repository.TentativeConnexionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Limitation du nombre de tentatives de connexion (anti force brute).
 *
 * <p>Deux compteurs glissants, indépendants :</p>
 * <ul>
 *   <li><strong>par compte</strong> (e-mail) — empêche d'épuiser le dictionnaire sur un
 *       seul e-mail ;</li>
 *   <li><strong>par adresse IP</strong> — empêche d'essayer plusieurs comptes
 *       depuis une même source.</li>
 * </ul>
 *
 * <p>Contrairement à l'implémentation en mémoire précédente, les tentatives sont
 * <strong>persistées en base</strong> (table {@code tentative_connexion},
 * migration V9) : elles survivent aux redémarrages et sont partagées par tous
 * les conteneurs backend. Une connexion réussie réinitialise le compteur du
 * compte concerné.</p>
 */
@Service
public class ProtectionConnexions {

    private static final String INCONNU = "-";

    private final TentativeConnexionRepository repository;

    private final long fenetreMs;
    private final int maxParCompte;
    private final int maxParAdresse;

    public ProtectionConnexions(TentativeConnexionRepository repository,
                                @Value("${app.securite.tentatives.max-compte:5}") int maxParCompte,
                                @Value("${app.securite.tentatives.max-ip:20}") int maxParAdresse,
                                @Value("${app.securite.tentatives.fenetre-ms:900000}") long fenetreMs) {
        this.repository = repository;
        this.maxParCompte = maxParCompte;
        this.maxParAdresse = maxParAdresse;
        this.fenetreMs = fenetreMs;
    }

    /**
     * Lève {@link TooManyRequestsException} (429) si la fenêtre de temps est
     * saturée pour le compte ou pour l'adresse.
     */
    @Transactional(readOnly = true)
    public void verifierAutorise(String adresseIp, String email) {
        LocalDateTime debutFenetre = LocalDateTime.now().minusNanos(fenetreMs * 1_000_000);

        long echecsCompte = repository.compterEchecsDansFenetre(
                TentativeConnexion.TYPE_COMPTE, cleCompte(email), debutFenetre);
        long echecsIp = repository.compterEchecsDansFenetre(
                TentativeConnexion.TYPE_IP, adresseIp, debutFenetre);

        if (echecsCompte >= maxParCompte || echecsIp >= maxParAdresse) {
            throw new TooManyRequestsException(
                    "Trop de tentatives de connexion. Réessayez dans quelques minutes.");
        }
    }

    /** Note un échec d'authentification (identifiants incorrects). */
    @Transactional
    public void enregistrerEchec(String adresseIp, String email) {
        LocalDateTime maintenant = LocalDateTime.now();

        repository.save(TentativeConnexion.builder()
                .cle(cleCompte(email))
                .typeCle(TentativeConnexion.TYPE_COMPTE)
                .dateTentative(maintenant)
                .succes(false)
                .build());

        repository.save(TentativeConnexion.builder()
                .cle(adresseIp)
                .typeCle(TentativeConnexion.TYPE_IP)
                .dateTentative(maintenant)
                .succes(false)
                .build());

        // Purge opportuniste des tentatives trop anciennes.
        repository.purgerAvant(maintenant.minusNanos(fenetreMs * 1_000_000));
    }

    /** Une connexion réussie remet le compteur du compte à zéro. */
    @Transactional
    public void reussite(String adresseIp, String email) {
        repository.reinitialiserCompte(cleCompte(email));
    }

    // ------------------------------------------------------------------
    // Tâche de fond : purge des tentatives plus vieilles que la fenêtre,
    // toutes les 10 minutes.
    // ------------------------------------------------------------------

    /** Purge les tentatives expirées (tâche planifiée). */
    @Scheduled(fixedDelayString = "${app.securite.tentatives.purge-delay-ms:600000}")
    @Transactional
    public void purgerExpirées() {
        repository.purgerAvant(LocalDateTime.now().minusNanos(fenetreMs * 1_000_000));
    }

    // ------------------------------------------------------------------
    // Internes
    // ------------------------------------------------------------------

    private static String cleCompte(String email) {
        return email == null || email.isBlank() ? INCONNU : email.trim().toLowerCase();
    }
}
