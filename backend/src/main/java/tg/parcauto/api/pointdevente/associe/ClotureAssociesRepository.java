package tg.parcauto.api.pointdevente.associe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClotureAssociesRepository extends JpaRepository<ClotureAssocies, Long> {

    List<ClotureAssocies> findAllByOrderByMoisAsc();
}
