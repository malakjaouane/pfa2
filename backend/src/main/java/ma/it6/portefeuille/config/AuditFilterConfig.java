package ma.it6.portefeuille.config;

import ma.it6.portefeuille.filter.AuditFilter;
import ma.it6.portefeuille.repository.JournalAuditRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enregistrement du {@link AuditFilter} : ordre 0, à l'intérieur de la
 * chaîne de sécurité (après le filtre JWT, qui remplit le
 * {@code SecurityContext}).
 *
 * <p><b>Pourquoi pas {@code @Component} sur le filtre ?</b> Parce que les
 * tests {@code @WebMvcTest} (notamment {@code SecuriteApiTest}) composent un
 * contexte réduit sans dépôt JPA : un filtre annoté {@code @Component} y
 * serait instancié et ferait échouer le chargement. Registré via
 * {@code FilterRegistrationBean}, il n'apparaît que dans le contexte complet
 * ({@code @SpringBootTest} / application réelle).</p>
 */
@Configuration
public class AuditFilterConfig {

    @Bean
    public FilterRegistrationBean<AuditFilter> auditFilter(
            JournalAuditRepository journalRepository,
            UtilisateurRepository utilisateurRepository) {
        FilterRegistrationBean<AuditFilter> registration =
                new FilterRegistrationBean<>(new AuditFilter(journalRepository, utilisateurRepository));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(0);
        registration.setName("auditFilter");
        return registration;
    }
}
