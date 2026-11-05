package tg.parcauto.api.pointdevente.materiaux;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DevisRepository extends JpaRepository<Devis, Long> {

    /** Devis filtres par statut et par texte (numero ou client) ; {@code statut} nul : tous. */
    @Query("""
            select d from Devis d left join d.client c
            where (:statut is null or d.statut = :statut)
              and (:clientId is null or c.id = :clientId)
              and (:q = '' or lower(d.numero) like concat('%', :q, '%')
                   or lower(coalesce(d.clientNom, '')) like concat('%', :q, '%'))
            order by d.dateDevis desc, d.id desc
            """)
    Page<Devis> rechercher(@Param("q") String q, @Param("statut") Devis.Statut statut,
                           @Param("clientId") Long clientId, Pageable pageable);

    @Query("""
            select distinct d from Devis d left join fetch d.client left join fetch d.lignes l
            left join fetch l.article left join fetch l.conditionnement
            where d.id = :id
            """)
    Optional<Devis> detail(@Param("id") Long id);
}
