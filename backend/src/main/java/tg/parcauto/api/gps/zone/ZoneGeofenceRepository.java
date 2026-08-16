package tg.parcauto.api.gps.zone;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneGeofenceRepository extends JpaRepository<ZoneGeofence, Long> {

    List<ZoneGeofence> findByActifTrueOrderByNomAsc();

    boolean existsByNomIgnoreCase(String nom);
}
