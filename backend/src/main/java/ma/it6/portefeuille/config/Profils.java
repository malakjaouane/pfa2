package ma.it6.portefeuille.config;

import org.springframework.core.env.Environment;

/**
 * Détecte les profils de développement : {@code dev} (profil par défaut),
 * {@code test} et {@code local}. Seuls ces profils tolèrent les valeurs
 * de démonstration (secrets par défaut, comptes de démo).
 */
final class Profils {

    /** Profils pour lesquels les valeurs de démonstration restent tolérées. */
    static final String[] DEVELOPPEMENT = {"dev", "test", "local"};

    private Profils() {
    }

    /**
     * @return {@code true} si l'un des profils de développement est actif
     * (ou si aucun profil n'est actif — on retombe alors sur le profil
     * par défaut déclaré dans application.yml : {@code dev}).
     */
    static boolean estDeveloppement(Environment environment) {
        String[] profils = environment.getActiveProfiles();
        if (profils.length == 0) {
            profils = environment.getDefaultProfiles();
        }
        for (String attendu : DEVELOPPEMENT) {
            for (String actif : profils) {
                if (attendu.equalsIgnoreCase(actif)) {
                    return true;
                }
            }
        }
        return false;
    }
}
