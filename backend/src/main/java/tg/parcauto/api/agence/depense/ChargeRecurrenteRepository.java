package tg.parcauto.api.agence.depense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargeRecurrenteRepository extends JpaRepository<ChargeRecurrente, Long> {

    List<ChargeRecurrente> findByAgenceIdOrderByLibelleAsc(Long agenceId);
}
