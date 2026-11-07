package tg.parcauto.api.pointdevente.tresorerie;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DegagementCoffreRepository extends JpaRepository<DegagementCoffre, Long> {

    Optional<DegagementCoffre> findFirstByGerantIdAndStatut(Long gerantId, DegagementCoffre.Statut statut);

    List<DegagementCoffre> findByStatutOrderByEnvoyeLeAsc(DegagementCoffre.Statut statut);

    long countByStatut(DegagementCoffre.Statut statut);

    Page<DegagementCoffre> findByGerantIdOrderByEnvoyeLeDesc(Long gerantId, Pageable pageable);

    Page<DegagementCoffre> findAllByOrderByEnvoyeLeDesc(Pageable pageable);

    List<DegagementCoffre> findByStatutOrderByTraiteLeDesc(DegagementCoffre.Statut statut, Pageable pageable);

    /** Total sorti du coffre d'un gerant vers l'entreprise. */
    @Query("""
            select coalesce(sum(d.montantValide), 0) from DegagementCoffre d
            where d.gerantId = :gerant and d.statut = tg.parcauto.api.pointdevente.tresorerie.DegagementCoffre.Statut.VALIDE
            """)
    BigDecimal sortiParCoffre(@Param("gerant") Long gerantId);

    @Query("""
            select coalesce(sum(d.montantValide), 0) from DegagementCoffre d
            where d.statut = tg.parcauto.api.pointdevente.tresorerie.DegagementCoffre.Statut.VALIDE
            """)
    BigDecimal totalValide();
}
