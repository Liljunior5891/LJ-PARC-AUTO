package tg.parcauto.api.agence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.Instant;

/** Ligne du journal d'audit des agences ; la base refuse toute modification ou suppression. */
@Entity
@Table(name = "agc_journal_audit")
@Getter
@Setter
@NoArgsConstructor
public class JournalAudit extends BaseEntity {

    @Column(name = "agence_id")
    private Long agenceId;

    @Column(name = "date_action", nullable = false)
    private Instant dateAction;

    @Column(name = "auteur_id")
    private Long auteurId;

    @Column(name = "auteur_nom", length = 150)
    private String auteurNom;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 60)
    private String objet;

    @Column(name = "objet_id")
    private Long objetId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(columnDefinition = "text")
    private String avant;

    @Column(columnDefinition = "text")
    private String apres;
}
