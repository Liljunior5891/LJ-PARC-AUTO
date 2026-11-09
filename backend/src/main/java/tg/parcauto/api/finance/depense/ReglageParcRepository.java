package tg.parcauto.api.finance.depense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReglageParcRepository extends JpaRepository<ReglageParc, Long> {

    Optional<ReglageParc> findFirstByOrderByIdAsc();
}
