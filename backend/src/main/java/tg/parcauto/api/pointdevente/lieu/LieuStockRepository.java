package tg.parcauto.api.pointdevente.lieu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LieuStockRepository extends JpaRepository<LieuStock, Long> {

    boolean existsByTypeAndActifTrue(LieuStock.Type type);

    /** Pour la place de marche, qui lit les offres de toutes les entreprises hors cloisonnement. */
    @Query("select count(l) > 0 from LieuStock l where l.entrepriseId = :entreprise and l.type = 'ENTREPOT' and l.actif = true")
    boolean multiLieux(@Param("entreprise") Long entreprise);

    Optional<LieuStock> findByPrincipalTrue();

    List<LieuStock> findAllByOrderByPrincipalDescNomAsc();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);

    long countByActifTrueAndTypeAndIdNot(LieuStock.Type type, Long id);

    long countByActifTrueAndType(LieuStock.Type type);
}
