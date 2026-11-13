package tg.parcauto.api.pointdevente.reserve;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoisFondsReserveRepository extends JpaRepository<MoisFondsReserve, Long> {

    List<MoisFondsReserve> findAllByOrderByDebutAsc();
}
