package tg.parcauto.api.pointdevente.restauration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Reglages du mode restaurant, une ligne par etablissement, creee a la premiere modification. */
@Entity
@Table(name = "pdv_reglage_restauration")
@Getter
@Setter
@NoArgsConstructor
public class ReglageRestauration extends BaseEntity {

    /** Les clients peuvent commander en scannant le code QR de leur table. */
    @Column(name = "qr_actif", nullable = false)
    private boolean qrActif = true;

    /** Un serveur valide la commande du client avant qu'elle parte en cuisine. */
    @Column(name = "qr_validation", nullable = false)
    private boolean qrValidation = true;

    /** Mot d'accueil affiche en tete de la carte en ligne. */
    @Column(name = "message_accueil", length = 255)
    private String messageAccueil;
}
