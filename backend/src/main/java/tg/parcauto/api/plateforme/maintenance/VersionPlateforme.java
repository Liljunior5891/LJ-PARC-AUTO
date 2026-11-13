package tg.parcauto.api.plateforme.maintenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.EntiteAuditee;

import java.time.Instant;

/**
 * Version publiee de la plateforme : numero, nouveautes et corrections. La plus
 * recente est montree une fois a chaque utilisateur ; avec {@code rechargement},
 * les navigateurs restes ouverts sur une version anterieure sont invites a
 * recharger la page.
 */
@Entity
@Table(name = "plateforme_version")
@Getter
@Setter
@NoArgsConstructor
public class VersionPlateforme extends EntiteAuditee {

    @Column(nullable = false, length = 30)
    private String numero;

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private boolean rechargement = true;

    @Column(name = "publiee_le", nullable = false)
    private Instant publieeLe;
}
