package tg.parcauto.api.pointdevente.reglage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReglagePdvRepository extends JpaRepository<ReglagePdv, Long> {

    /** Le reglage de l'entreprise courante (le cloisonnement ne laisse voir que le sien). */
    Optional<ReglagePdv> findFirstByOrderByIdAsc();
}
