package tg.parcauto.api.agence.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BaremeRepository extends JpaRepository<Bareme, Long> {

    List<Bareme> findByServiceIdOrderByDateDebutDescIdDesc(Long serviceId);

    List<Bareme> findAllByOrderByServiceIdAscDateDebutDescIdDesc();

    List<Bareme> findByPrecedentId(Long precedentId);
}
