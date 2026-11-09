package tg.parcauto.api.presence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PointagePresenceRepository extends JpaRepository<PointagePresence, Long> {

    Optional<PointagePresence> findByEmployeIdAndJour(Long employeId, LocalDate jour);

    @Query("select p from PointagePresence p join fetch p.employe left join fetch p.lieu where p.jour = :jour")
    List<PointagePresence> duJour(@Param("jour") LocalDate jour);

    @Query("""
            select p from PointagePresence p left join fetch p.lieu
            where p.employe.id = :employe and p.jour between :du and :au
            order by p.jour desc
            """)
    List<PointagePresence> deLEmploye(@Param("employe") Long employeId, @Param("du") LocalDate du, @Param("au") LocalDate au);

    @Query("""
            select p from PointagePresence p join fetch p.employe left join fetch p.lieu
            where p.jour between :du and :au
            """)
    List<PointagePresence> periode(@Param("du") LocalDate du, @Param("au") LocalDate au);
}
