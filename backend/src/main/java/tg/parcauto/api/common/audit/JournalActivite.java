package tg.parcauto.api.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.Instant;

/**
 * Ligne du journal d'audit des modules de la plateforme (hors Agences, qui a
 * son propre journal). Ecrite par {@link EcritureJournal}, jamais par JPA ; la
 * base refuse toute modification ou suppression.
 */
@Entity
@Table(name = "journal_activite")
@Getter
@Setter
@NoArgsConstructor
public class JournalActivite extends BaseEntity {

    @Column(name = "date_action", nullable = false)
    private Instant dateAction;

    @Column(name = "auteur_id")
    private Long auteurId;

    @Column(name = "auteur_nom", length = 150)
    private String auteurNom;

    @Column(nullable = false, length = 30)
    private String module;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(nullable = false, length = 60)
    private String objet;

    @Column(name = "objet_libelle", nullable = false, length = 80)
    private String objetLibelle;

    @Column(name = "objet_id")
    private Long objetId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(columnDefinition = "text")
    private String avant;

    @Column(columnDefinition = "text")
    private String apres;

    @Column(name = "adresse_ip", length = 60)
    private String adresseIp;
}
