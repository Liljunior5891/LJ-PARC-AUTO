package tg.parcauto.api.pointdevente.materiaux;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReglageMateriauxRepository extends JpaRepository<ReglageMateriaux, Long> {

    /** Le cloisonnement ne laisse voir que la ligne de l'entreprise courante. */
    default Optional<ReglageMateriaux> courant() {
        return findAll().stream().findFirst();
    }
}
