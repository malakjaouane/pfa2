package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

/** Accès aux plans d'abonnement (Phase 2D). */
public interface PlanRepository extends JpaRepository<Plan, Long> {

    Plan findByCode(String code);
}
