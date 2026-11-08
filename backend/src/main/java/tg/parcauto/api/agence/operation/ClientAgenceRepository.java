package tg.parcauto.api.agence.operation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientAgenceRepository extends JpaRepository<ClientAgence, Long> {

    Optional<ClientAgence> findByTelephone(String telephone);

    @Query("""
            select c from ClientAgence c
            where c.telephone like :motif or lower(c.nom) like :motif
            order by c.modifieLe desc nulls last
            """)
    List<ClientAgence> rechercher(@Param("motif") String motif, Pageable pageable);
}
