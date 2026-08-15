package tg.parcauto.api.parc.camion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CamionRepository extends JpaRepository<Camion, Long> {

    Optional<Camion> findByImmatriculationIgnoreCase(String immatriculation);

    boolean existsByImmatriculationIgnoreCase(String immatriculation);

    boolean existsByNumeroInterneIgnoreCase(String numeroInterne);

    @Query("""
            select c from Camion c
            where (:statut is null or c.statut = :statut)
              and (:actif is null or c.actif = :actif)
              and (:q is null or lower(c.immatriculation) like lower(concat('%', :q, '%'))
                   or lower(c.numeroInterne) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.marque, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.modele, '')) like lower(concat('%', :q, '%')))
            """)
    Page<Camion> rechercher(@Param("q") String q,
                            @Param("statut") StatutCamion statut,
                            @Param("actif") Boolean actif,
                            Pageable pageable);

    @Query("select c.statut as statut, count(c) as total from Camion c where c.actif = true group by c.statut")
    List<StatutCompte> compterParStatut();

    interface StatutCompte {
        StatutCamion getStatut();

        long getTotal();
    }
}
