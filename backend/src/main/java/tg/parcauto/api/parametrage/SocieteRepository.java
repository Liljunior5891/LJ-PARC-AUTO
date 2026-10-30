package tg.parcauto.api.parametrage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocieteRepository extends JpaRepository<Societe, Long> {

    /** La table ne porte qu'une ligne : la premiere est la bonne. */
    default Optional<Societe> lire() {
        return findAll().stream().findFirst();
    }
}
