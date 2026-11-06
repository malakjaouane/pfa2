package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.Commentaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentaireRepository extends JpaRepository<Commentaire, Long> {

    /** Fil de discussion d'une initiative (plus récent d'abord). */
    @Query("select c from Commentaire c left join fetch c.auteur "
            + "where c.initiative.idInitiative = :id "
            + "order by c.createdAt desc, c.idCommentaire desc")
    List<Commentaire> filInitiative(@Param("id") Long idInitiative);
}
