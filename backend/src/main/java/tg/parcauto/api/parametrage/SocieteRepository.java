package tg.parcauto.api.parametrage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocieteRepository extends JpaRepository<Societe, Long> {

    /** Une ligne par entreprise ; le cloisonnement ne laisse voir que celle de l'entreprise courante. */
    default Optional<Societe> lire() {
        return findAll().stream().findFirst();
    }
}
