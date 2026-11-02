package tg.parcauto.api.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OffrePhotoRepository extends JpaRepository<OffrePhoto, Long> {

    List<OffrePhoto> findByOffreIdOrderByOrdreAscIdAsc(Long offreId);

    @Modifying
    @Query("delete from OffrePhoto p where p.offreId = :offreId")
    void supprimerDeLOffre(@org.springframework.data.repository.query.Param("offreId") Long offreId);

    /** [offreId, nombre de photos] de chaque offre qui a une galerie. */
    @Query("select p.offreId, count(p) from OffrePhoto p group by p.offreId")
    List<Object[]> nombresParOffre();
}
