package tg.parcauto.api.pointdevente.impression;

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
import tg.parcauto.api.pointdevente.caisse.Caisse;

/**
 * Imprimante de preparation (cuisine, bar) : elle recoit les bons des postes
 * qui lui sont rattaches. En mode navigateur, la caisse imprime les bons par
 * la fenetre d'impression ; en mode agent, un programme installe dans le
 * restaurant les envoie directement a son adresse reseau.
 */
@Entity
@Table(name = "pdv_imprimante")
@Getter
@Setter
@NoArgsConstructor
public class Imprimante extends BaseEntity {

    public enum Mode { NAVIGATEUR, AGENT }

    @Column(nullable = false, length = 80)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Mode mode = Mode.NAVIGATEUR;

    @Column(name = "adresse_ip", length = 64)
    private String adresseIp;

    @Column(nullable = false)
    private int port = 9100;

    @Column(name = "largeur_papier", nullable = false)
    private int largeurPapier = 80;

    @Column(nullable = false)
    private int copies = 1;

    @Column(nullable = false)
    private boolean coupe = true;

    @Column(nullable = false)
    private boolean bip;

    /** Caisse qui imprime les bons par le navigateur ; nulle : toutes les caisses. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caisse_id")
    private Caisse caisse;

    @Column(nullable = false)
    private boolean actif = true;
}
