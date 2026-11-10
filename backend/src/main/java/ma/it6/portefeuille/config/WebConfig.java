package ma.it6.portefeuille.config;

import ma.it6.portefeuille.security.OrganisationIsolationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration MVC : enregistre l'intercepteur d'isolation par organisation
 * (Phase 2A SaaS).
 *
 * <p>L'intercepteur est un bean Spring {@code @Component} (son {@code
 * EntityManager} est injecte) : il ne faut surtout pas l'instancier avec
 * {@code new}, sinon l'injection est silencieusement ignorée et toute requete
 * metier authentifiee aboutit sur une {@code NullPointerException}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final OrganisationIsolationInterceptor isolationInterceptor;

    public WebConfig(OrganisationIsolationInterceptor isolationInterceptor) {
        this.isolationInterceptor = isolationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(isolationInterceptor)
                // Intercepte toutes les requetes API (le filtre Hibernate n'est
                // active que si une organisation est courante, verifie a
                // l'interieur).
                .addPathPatterns("/api/**");
    }
}
