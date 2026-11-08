package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux organisations (tenants SaaS). */
public interface OrganisationRepository extends JpaRepository<Organisation, Long> {

    Organisation findBySlug(String slug);
}
