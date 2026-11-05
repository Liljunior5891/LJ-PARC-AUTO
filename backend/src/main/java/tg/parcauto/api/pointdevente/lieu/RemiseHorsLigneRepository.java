package tg.parcauto.api.pointdevente.lieu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RemiseHorsLigneRepository extends JpaRepository<RemiseHorsLigne, Long> {

    Optional<RemiseHorsLigne> findByCle(String cle);

    List<RemiseHorsLigne> findByStatutOrderByFaiteLeDesc(RemiseHorsLigne.Statut statut);
}
