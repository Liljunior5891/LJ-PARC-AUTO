package tg.parcauto.api.parc.camion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Couleur du vehicule, avec son code hexadecimal pour l'affichage. */
@Entity
@Table(name = "couleur_camion")
@Getter
@Setter
@NoArgsConstructor
public class CouleurCamion extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String nom;

    @Column(name = "code_hexa", length = 20)
    private String codeHexa;

    @Column(nullable = false)
    private Integer ordre = 100;

    @Column(nullable = false)
    private boolean actif = true;
}
