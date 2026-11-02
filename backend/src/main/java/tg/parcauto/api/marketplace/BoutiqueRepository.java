package tg.parcauto.api.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoutiqueRepository extends JpaRepository<Boutique, Long> {

    /** La boutique de l'entreprise courante (une seule, filtree par Hibernate). */
    Optional<Boutique> findFirstBy();
}
