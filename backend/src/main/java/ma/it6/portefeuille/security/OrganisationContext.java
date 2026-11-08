package ma.it6.portefeuille.security;

/**
 * Contexte d'organisation courant du fil d'execution (ThreadLocal).
 *
 * <p>Positionne par {@link ma.it6.portefeuille.security.JwtAuthenticationFilter}
 * a partir du compte connecte, puis lu par
 * {@link ma.it6.portefeuille.security.OrganisationIsolationInterceptor} pour
 * activer le filtre Hibernate d'isolation par requete. Nettoye a la fin de
 * chaque requete pour eviter toute fuite entre deux requetes sur le meme fil
 * (serveur assemble / tests).
 */
public final class OrganisationContext {

    private OrganisationContext() {
    }

    private static final ThreadLocal<Long> ID = new ThreadLocal<>();

    public static void setId(Long idOrganisation) {
        ID.set(idOrganisation);
    }

    public static Long getId() {
        return ID.get();
    }

    public static void clear() {
        ID.remove();
    }
}
