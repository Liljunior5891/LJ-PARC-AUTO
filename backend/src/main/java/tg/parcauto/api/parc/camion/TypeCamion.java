package tg.parcauto.api.parc.camion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.EntiteAuditee;

/** Type de carrosserie du camion : plateau, benne, citerne... */
@Entity
@Table(name = "type_camion")
@Getter
@Setter
@NoArgsConstructor
public class TypeCamion extends EntiteAuditee {

    @Column(nullable = false, unique = true, length = 80)
    private String nom;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private Integer ordre = 100;

    @Column(nullable = false)
    private boolean actif = true;
}
