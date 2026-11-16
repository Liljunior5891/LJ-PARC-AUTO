package tg.parcauto.api.pointdevente.associe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartMensuelleRepository extends JpaRepository<PartMensuelle, Long> {

    List<PartMensuelle> findAllByOrderByMoisAsc();
}
