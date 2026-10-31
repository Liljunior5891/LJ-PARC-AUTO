package tg.parcauto.api.pointdevente.restauration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReglageRestaurationRepository extends JpaRepository<ReglageRestauration, Long> {

    /** Le cloisonnement ne laisse voir que la ligne de l'etablissement courant. */
    default Optional<ReglageRestauration> courant() {
        return findAll().stream().findFirst();
    }
}
