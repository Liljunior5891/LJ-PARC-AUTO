package tg.parcauto.api.pointdevente.reglage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReglagePdvRepository extends JpaRepository<ReglagePdv, Long> {

    /** Le reglage de l'entreprise courante (le cloisonnement ne laisse voir que le sien). */
    Optional<ReglagePdv> findFirstByOrderByIdAsc();

    /**
     * Le meme reglage, verrouille jusqu'a la fin de la transaction : les depenses
     * payees par le fonds de reserve et les changements du fonds passent l'un apres l'autre.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from ReglagePdv r order by r.id")
    java.util.List<ReglagePdv> verrouiller();
}
