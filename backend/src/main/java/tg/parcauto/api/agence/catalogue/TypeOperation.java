package tg.parcauto.api.agence.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/**
 * Type d'operation d'un service (depot, retrait, envoi...) et sa regle de
 * mouvement : de combien bougent le cash et le compte electronique pour un
 * montant donne. La regle se change dans l'administration, sans developpement.
 */
@Entity
@Table(name = "agc_type_operation")
@Getter
@Setter
@NoArgsConstructor
public class TypeOperation extends BaseEntity {

    /** Compte electronique touche par l'operation. */
    public enum CompteVirtuel { FLOAT, RECHARGE, AUCUN }

    /** TRANSIT : l'argent ne fait que passer ; VENTE_CARTE : le prix encaisse est un chiffre d'affaires. */
    public enum Nature { TRANSIT, VENTE_CARTE }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceFinancier service;

    /** Reseau auquel l'operation est propre ; nul : commune a tous les reseaux du service. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reseau_id")
    private Reseau reseau;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String libelle;

    /** +1 : le cash augmente du montant ; -1 : il diminue ; 0 : il ne bouge pas. */
    @Column(name = "effet_cash", nullable = false)
    private int effetCash;

    @Column(name = "effet_virtuel", nullable = false)
    private int effetVirtuel;

    @Enumerated(EnumType.STRING)
    @Column(name = "compte_virtuel", nullable = false, length = 20)
    private CompteVirtuel compteVirtuel = CompteVirtuel.FLOAT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Nature nature = Nature.TRANSIT;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false)
    private boolean actif = true;
}
