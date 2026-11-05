package tg.parcauto.api.pointdevente.materiaux;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BonLivraisonRepository extends JpaRepository<BonLivraison, Long> {

    /** Bons d'une vente, du plus recent au plus ancien. */
    List<BonLivraison> findByVenteIdOrderByDateBonDescIdDesc(Long venteId);
}
