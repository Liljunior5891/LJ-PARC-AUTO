package tg.parcauto.api.agence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReglageAgenceRepository extends JpaRepository<ReglageAgence, Long> {

    Optional<ReglageAgence> findFirstByOrderByIdAsc();
}
