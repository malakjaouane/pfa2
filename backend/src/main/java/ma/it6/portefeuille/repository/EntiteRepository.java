package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Entite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntiteRepository extends JpaRepository<Entite, Long> {

    List<Entite> findAllByActifTrueOrderByNomAsc();

    List<Entite> findAllByOrderByIdEntiteAsc();

    Optional<Entite> findByNom(String nom);
}