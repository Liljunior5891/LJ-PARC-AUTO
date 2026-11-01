package tg.parcauto.api.parc.camion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.EntiteAuditee;

/** Type de vehicule : berline, SUV, plateau, benne, citerne, pelleteuse... */
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

    /** Famille du type : VOITURE, UTILITAIRE, CAMION ou ENGIN ; vide pour « Autre ». */
    @Column(length = 20)
    private String categorie;

    /** Dessin de profil affiche sur la carte du vehicule (cle connue de l'interface). */
    @Column(length = 30)
    private String dessin;

    @Column(nullable = false)
    private Integer ordre = 100;

    @Column(nullable = false)
    private boolean actif = true;
}
