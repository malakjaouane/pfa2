package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Priorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrioriteRepository extends JpaRepository<Priorite, Long> {

    List<Priorite> findAllByOrderByOrdreAsc();

    Optional<Priorite> findByLibelle(String libelle);
}