package tg.parcauto.api.pointdevente.restauration;

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
 * Table d'une salle. Sa position et sa taille sont exprimees en unites d'un
 * plan de {@value #LARGEUR_PLAN} x {@value #HAUTEUR_PLAN} : le plan se dessine
 * a l'identique sur une tablette comme sur un grand ecran.
 */
@Entity
@Table(name = "pdv_table")
@Getter
@Setter
@NoArgsConstructor
public class TableRestaurant extends BaseEntity {

    public static final int LARGEUR_PLAN = 100;
    public static final int HAUTEUR_PLAN = 60;

    public enum Forme { CARREE, RONDE, RECTANGLE }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salle_id", nullable = false)
    private Salle salle;

    @Column(nullable = false, length = 40)
    private String nom;

    @Column(nullable = false)
    private int places = 4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Forme forme = Forme.CARREE;

    @Column(name = "pos_x", nullable = false)
    private int posX;

    @Column(name = "pos_y", nullable = false)
    private int posY;

    @Column(nullable = false)
    private int largeur = 10;

    @Column(nullable = false)
    private int hauteur = 10;

    @Column(nullable = false)
    private boolean actif = true;

    /**
     * Jeton imprime dans le code QR de la table : imprevisible, il suffit a
     * retrouver l'etablissement et la table sans compte client. Le regenerer
     * rend les anciens codes inutilisables.
     */
    @Column(name = "jeton_qr", nullable = false, length = 40)
    private String jetonQr = nouveauJeton();

    public static String nouveauJeton() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
