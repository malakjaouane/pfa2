package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.ObjectifStrategique;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ObjectifStrategiqueRepository extends JpaRepository<ObjectifStrategique, Long> {

    List<ObjectifStrategique> findAllByOrderByIdObjectifAsc();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdObjectifNot(String code, Long idObjectif);

    Optional<ObjectifStrategique> findByCode(String code);
}