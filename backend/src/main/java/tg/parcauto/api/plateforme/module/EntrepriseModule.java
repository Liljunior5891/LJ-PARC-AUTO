package tg.parcauto.api.plateforme.module;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.EntiteAuditee;

import java.time.Instant;

/**
 * Souscription d'une entreprise a un module.
 * <p>
 * La plateforme lit cette table pour toutes les entreprises (console
 * d'administration, controle d'acces avant tout contexte) : elle n'est donc pas
 * cloisonnee par Hibernate et chaque requete nomme l'entreprise explicitement.
 * Desactiver un module conserve la ligne et les donnees du module.
 */
@Entity
@Table(name = "entreprise_module")
@Getter
@Setter
@NoArgsConstructor
public class EntrepriseModule extends EntiteAuditee {

    @Column(name = "entreprise_id", nullable = false, updatable = false)
    private Long entrepriseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private ModuleApplicatif module;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "active_le")
    private Instant activeLe;

    /** Declinaison choisie (voir {@link ModuleApplicatif#getVariantes()}) ; nulle pour un module sans variante. */
    @Column(length = 40)
    private String variante;
}
