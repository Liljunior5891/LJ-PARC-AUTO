package tg.parcauto.api.pointdevente.caisse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MouvementCaisseRepository extends JpaRepository<MouvementCaisse, Long> {

    List<MouvementCaisse> findBySessionIdOrderByDateMouvementAscIdAsc(Long sessionId);
}
