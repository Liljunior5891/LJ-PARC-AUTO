package tg.parcauto.api.pointdevente.reserve;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeriodeFondsReserveRepository extends JpaRepository<PeriodeFondsReserve, Long> {

    List<PeriodeFondsReserve> findAllByOrderByDebutAscIdAsc();

    Optional<PeriodeFondsReserve> findFirstByFinIsNull();
}
