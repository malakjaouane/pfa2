package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.MembreInitiative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Accès aux membres d'initiative (Phase 2C - droits par initiative). */
public interface MembreInitiativeRepository extends JpaRepository<MembreInitiative, Long> {

    /** Un utilisateur est-il membre d'une initiative (quel que soit son rôle) ? */
    @Query("""
        select count(m) > 0 from MembreInitiative m
        where m.idUser = :idUser and m.idInitiative = :idInitiative
        """)
    boolean estMembre(@Param("idUser") Long idUser, @Param("idInitiative") Long idInitiative);

    /** Un utilisateur peut-il modifier l'initiative (responsable ou
     *  membre contributeur/responsable) ? */
    @Query("""
        select count(m) > 0 from MembreInitiative m
        where m.idUser = :idUser
          and m.idInitiative = :idInitiative
          and m.role in ('responsable', 'contributeur')
        """)
    boolean peutModifier(@Param("idUser") Long idUser, @Param("idInitiative") Long idInitiative);

    List<MembreInitiative> findByIdInitiative(Long idInitiative);
    List<MembreInitiative> findByIdUser(Long idUser);
}
