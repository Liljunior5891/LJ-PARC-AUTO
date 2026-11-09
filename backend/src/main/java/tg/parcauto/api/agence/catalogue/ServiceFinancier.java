package tg.parcauto.api.agence.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/**
 * Service propose au guichet (Mixx by Yas, Moov Money, Rapid Transfer...),
 * range dans une categorie (Mobile Money, Transfert d'argent...). Chaque agence
 * a un float par service dont un type d'operation touche le virtuel : les
 * reseaux d'un meme service (Western Union, MoneyGram...) partagent ce float.
 */
@Entity
@Table(name = "agc_service")
@Getter
@Setter
@NoArgsConstructor
public class ServiceFinancier extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(nullable = false, length = 60)
    private String categorie;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false)
    private boolean actif = true;

    /** Logo reduit par le navigateur, en data URL (image/jpeg, image/png ou image/webp) ; nul sans logo. */
    @Column(columnDefinition = "text")
    private String logo;

    /**
     * Commission versee plus tard par l'operateur : acquise a la transaction,
     * elle n'est creditee qu'au rapprochement avec le versement. Faux : creditee
     * aussitot sur le sous-compte des commissions.
     */
    @Column(name = "commission_differee", nullable = false)
    private boolean commissionDifferee = false;
}
