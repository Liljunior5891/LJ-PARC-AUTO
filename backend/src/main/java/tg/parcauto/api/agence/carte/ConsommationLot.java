package tg.parcauto.api.agence.carte;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Cartes prises a un lot par une vente, a son cout unitaire. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ConsommationLot {

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Column(nullable = false)
    private int quantite;

    @Column(name = "cout_unitaire", nullable = false)
    private long coutUnitaire;
}
