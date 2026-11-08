package tg.parcauto.api.agence.tresorerie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/**
 * Compte d'une agence : caisse, float d'un service, recharge, commissions,
 * banque. Son solde n'est jamais saisi : il est la somme des lignes du registre,
 * gardee ici en cache et mise a jour sous verrou a chaque ecriture.
 */
@Entity
@Table(name = "agc_compte")
@Getter
@Setter
@NoArgsConstructor
public class CompteAgence extends BaseEntity {

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TypeCompte type;

    @Column(nullable = false, length = 120)
    private String libelle;

    /** Service du float ou du compte de recharge. */
    @Column(name = "service_id", updatable = false)
    private Long serviceId;

    /** Caissier d'une caisse de caissier. */
    @Column(name = "utilisateur_id", updatable = false)
    private Long utilisateurId;

    @Column(length = 120)
    private String banque;

    @Column(name = "numero_compte", length = 60)
    private String numeroCompte;

    /** En francs CFA, entiers. Modifie uniquement par le registre. */
    @Column(nullable = false)
    private long solde;

    /** Solde negatif tolere ; 0 : le compte ne descend jamais sous zero. */
    @Column(name = "decouvert_autorise", nullable = false)
    private long decouvertAutorise;

    /** En dessous de ce solde, une alerte est levee (cash ou virtuel insuffisant) ; nul : pas d'alerte. */
    @Column(name = "seuil_alerte")
    private Long seuilAlerte;

    /** Exclu de la tresorerie disponible. */
    @Column(nullable = false)
    private boolean bloque;

    @Column(nullable = false)
    private boolean actif = true;
}
