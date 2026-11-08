package ma.it6.portefeuille.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Vérifie, <strong>avant le démarrage</strong>, que les secrets ne sont pas
 * ceux livrés par défaut.
 *
 * <p>Les valeurs par défaut ne servent qu'au lancement local (profil
 * {@code dev}) : dès qu'un profil autre que {@code dev} / {@code test} /
 * {@code local} est actif (recette, production, conteneur…), leur utilisation
 * fait échouer le démarrage avec un message explicite, au lieu de laisser une
 * application exposée avec une clé de signature et un mot de passe de base de
 * données connus de tous. {@code ADMIN_PASSWORD} vide est refusé dans TOUS
 * les profils (impossible de créer le compte admin initial sans lui).</p>
 *
 * @see Profils#DEVELOPPEMENT profils autorisés à conserver les défauts
 */
@Component
public class VerificationSecrets implements InitializingBean {

    /** Clé de signature JWT livrée dans application.yml (usage local uniquement). */
    private static final String SECRET_JWT_DEFAUT =
            "PfaIT6ConsultingAgdalRabat2026CleSecreteTresLonguePourHS256===";

    /** Mot de passe de la base de données livré dans application.yml. */
    private static final String MOT_DE_PASSE_BDD_DEFAUT = "pfa2026";

    /** Mot de passe admin de démonstration (autorisé uniquement en dev). */
    private static final String MOT_DE_PASSE_ADMIN_DEFAUT = "Admin@123";

    /** Longueur minimale d'une clé de signature HS256 (octets). */
    private static final int TAILLE_MINIME_SECRET = 32;

    private static final Logger LOG = LoggerFactory.getLogger(VerificationSecrets.class);

    private final Environment environment;
    private final String secretJwt;
    private final String motDePasseBdd;
    private final String motDePasseAdmin;

    public VerificationSecrets(Environment environment,
                               @Value("${app.jwt.secret}") String secretJwt,
                               @Value("${spring.datasource.password}") String motDePasseBdd,
                               @Value("${app.admin.password:}") String motDePasseAdmin) {
        this.environment = environment;
        this.secretJwt = secretJwt;
        this.motDePasseBdd = motDePasseBdd;
        this.motDePasseAdmin = motDePasseAdmin;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problemes = verifier();

        // Un ADMIN_PASSWORD absent ne peut pas créer de compte admin :
        // démarrage refusé dans TOUS les profils (en dev, la valeur de
        // démonstration vient de application-dev.yml — vide signifie
        // explicitement « variable positionnée mais vide »).
        if (motDePasseAdmin == null || motDePasseAdmin.isBlank()) {
            throw new IllegalStateException("""
                    Démarrage refusé : ADMIN_PASSWORD est vide ou absent.
                      - app.admin.password (ADMIN_PASSWORD) est vide : le compte
                        administrateur initial ne peut pas être créé.
                    Définissez la variable d'environnement ADMIN_PASSWORD
                    (voir .env.example).""");
        }

        // Valeur de démonstration connue : jamais bloquante ici (le compte
        // existant de la démo l'utilise), mais signalée — hors dev elle
        // annonce un mot de passe faible que l'administrateur devra changer
        // au premier login (compte créé sur base vide).
        List<String> avertissements = new ArrayList<>();
        if (MOT_DE_PASSE_ADMIN_DEFAUT.equals(motDePasseAdmin)) {
            avertissements.add("app.admin.password (ADMIN_PASSWORD) conserve la valeur "
                    + "de démonstration connue (Admin@123).");
        }

        if (enDeveloppement()) {
            if (!problemes.isEmpty()) {
                LOG.warn("Secrets par défaut utilisés (profil de développement) : {}", problemes);
            }
        } else if (!problemes.isEmpty()) {
            String[] profils = environment.getActiveProfiles();
            String affiche = profils.length == 0 ? "(par défaut)" : String.join(", ", profils);
            throw new IllegalStateException("""
                    Démarrage refusé : configuration de sécurité non renforcée (profil actif : %s).
                      - %s
                    Définissez les variables d'environnement correspondantes
                    (voir .env.example) : JWT_SECRET, DB_PASSWORD, DB_URL, DB_USERNAME.
                    """.formatted(affiche, String.join("\n  - ", problemes)));
        }
        for (String avertissement : avertissements) {
            LOG.warn("{}{}", avertissement, enDeveloppement()
                    ? " (profil de développement : toléré)"
                    : " Hors dev, prévoyez un changement au premier login.");
        }
    }

    /** @return la liste des problèmes détectés (vide si la configuration est saine). */
    private List<String> verifier() {
        List<String> problemes = new ArrayList<>();

        if (secretJwt == null || secretJwt.isBlank()) {
            problemes.add("app.jwt.secret (JWT_SECRET) est vide.");
        } else {
            if (SECRET_JWT_DEFAUT.equals(secretJwt)) {
                problemes.add("app.jwt.secret (JWT_SECRET) conserve la valeur par défaut connue.");
            } else if (secretJwt.length() < TAILLE_MINIME_SECRET) {
                problemes.add("app.jwt.secret (JWT_SECRET) fait moins de "
                        + TAILLE_MINIME_SECRET + " caractères : signature HS256 insuffisante.");
            }
        }

        if (motDePasseBdd == null || motDePasseBdd.isBlank()) {
            problemes.add("spring.datasource.password (DB_PASSWORD) est vide.");
        } else if (MOT_DE_PASSE_BDD_DEFAUT.equals(motDePasseBdd)) {
            problemes.add("spring.datasource.password (DB_PASSWORD) conserve la valeur par défaut connue.");
        }

        return problemes;
    }

    private boolean enDeveloppement() {
        // Sans profil explicite, Spring ne remplit pas getActiveProfiles() :
        // on retombe sur les profils par défaut (« dev » déclaré dans
        // application.yml).
        return Profils.estDeveloppement(environment);
    }
}
