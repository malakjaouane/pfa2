package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.JournalIa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JournalIaRepository extends JpaRepository<JournalIa, Long> {

    /**
     * Dernières requêtes IA, auteur et initiative chargés (lecture
     * administrative du journal sans initialisation paresseuse).
     */
    @Query("""
            select j from JournalIa j
            left join fetch j.utilisateur
            left join fetch j.initiative
            order by j.dateRequete desc
            """)
    List<JournalIa> dernieres();
}
