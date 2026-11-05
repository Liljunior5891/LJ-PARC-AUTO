package tg.parcauto.api.pointdevente.lieu;

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
import tg.parcauto.api.pointdevente.materiaux.BonLivraison;

import java.time.Instant;

/**
 * Remise saisie sans connexion puis envoyee au serveur. La cle evite de l'enregistrer
 * deux fois si l'envoi est repete. Sans stock ou sans reste a servir, c'est un conflit :
 * la marchandise est deja partie, et seul le gerant peut decider de la suite.
 */
@Entity
@Table(name = "pdv_remise_hors_ligne")
@Getter
@Setter
@NoArgsConstructor
public class RemiseHorsLigne extends BaseEntity {

    public enum Statut { ENREGISTREE, CONFLIT, TRAITEE }

    @Column(nullable = false, length = 64)
    private String cle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bon_retrait_id", nullable = false)
    private BonRetrait bonRetrait;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bon_livraison_id")
    private BonLivraison bonLivraison;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut;

    @Column(length = 1000)
    private String detail;

    @Column(nullable = false, length = 150)
    private String auteur;

    @Column(name = "faite_le", nullable = false)
    private Instant faiteLe;

    @Column(name = "recu_par", length = 150)
    private String recuPar;

    @Column(name = "traitee_par", length = 150)
    private String traiteePar;

    @Column(name = "traitee_le")
    private Instant traiteeLe;
}
