package tg.parcauto.api.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Photo supplementaire d'une offre en ligne, montree dans la galerie de la boutique. */
@Entity
@Table(name = "mkt_offre_photo")
@Getter
@Setter
@NoArgsConstructor
public class OffrePhoto extends BaseEntity {

    @Column(name = "offre_id", nullable = false)
    private Long offreId;

    /** Image encodee (data URL JPEG, PNG ou WebP), reduite dans le navigateur. */
    @Column(nullable = false, columnDefinition = "text")
    private String image;

    @Column(nullable = false)
    private int ordre;
}
