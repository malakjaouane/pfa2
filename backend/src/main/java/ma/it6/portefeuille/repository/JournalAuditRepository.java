package ma.it6.portefeuille.repository;

import ma.it6.portefeuille.entity.JournalAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface JournalAuditRepository extends JpaRepository<JournalAudit, Long> {

    /**
     * Consultation du journal : filtres e-mail / méthode / route / période,
     * tri date décroissante. Un {@code null} désactive le filtre.
     */
    @Query("""
            select a from JournalAudit a
             where (:email is null or lower(a.email) like lower(concat('%', :email, '%')))
               and (:methode is null or a.methode = :methode)
               and (:route is null or lower(a.route) like lower(concat('%', :route, '%')))
               and (:du is null or a.dateAction >= :du)
               and (:fin is null or a.dateAction < :fin)
            order by a.dateAction desc
            """)
    Page<JournalAudit> rechercher(@Param("email") String email,
                                  @Param("methode") String methode,
                                  @Param("route") String route,
                                  @Param("du") LocalDateTime du,
                                  @Param("fin") LocalDateTime fin,
                                  Pageable pageable);
}
