package tg.parcauto.api.agence.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReseauRepository extends JpaRepository<Reseau, Long> {

    List<Reseau> findAllByOrderByOrdreAscNomAsc();

    boolean existsByServiceIdAndCodeIgnoreCase(Long serviceId, String code);
}
