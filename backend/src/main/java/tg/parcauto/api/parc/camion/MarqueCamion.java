package tg.parcauto.api.parc.camion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/**
 * Marque de camion du referentiel.
 * <p>
 * Le logo est reference par une URL : les fichiers de logos sont des marques
 * deposees, l'entreprise fournit les siens. A defaut, l'interface affiche une
 * pastille aux couleurs de la marque.
 */
@Entity
@Table(name = "marque_camion")
@Getter
@Setter
@NoArgsConstructor
public class MarqueCamion extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String nom;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 20)
    private String couleur;

    @Column(length = 80)
    private String pays;

    /** Ordre d'affichage dans les listes deroulantes. */
    @Column(nullable = false)
    private Integer ordre = 100;

    @Column(nullable = false)
    private boolean actif = true;

    /** Initiales affichees a defaut de logo. */
    public String monogramme() {
        String[] mots = nom.trim().split("[\s-]+");
        if (mots.length == 1) {
            return mots[0].substring(0, Math.min(2, mots[0].length())).toUpperCase();
        }
        return ("" + mots[0].charAt(0) + mots[1].charAt(0)).toUpperCase();
    }
}
