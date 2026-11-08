package ma.it6.portefeuille.config;

import ma.it6.portefeuille.security.OrganisationIsolationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration MVC : enregistre l'intercepteur d'isolation par organisation
 * (Phase 2A SaaS).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OrganisationIsolationInterceptor())
                // Intercepte toutes les requetes API (le filtre Hibernate n'est
                // active que si une organisation est courante, verifie a
                // l'interieur).
                .addPathPatterns("/api/**");
    }
}
