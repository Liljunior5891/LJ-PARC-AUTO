package tg.parcauto.api.plateforme.maintenance;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.EntiteAuditee;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Maintenance programmee par l'exploitant de la plateforme sur un ou plusieurs
 * modules. Un bandeau l'annonce a partir de {@code annonceLe} ; de {@code debut}
 * a la fin (prevue, ou anticipee), l'API refuse les ecrans de ces modules a
 * toutes les entreprises. Elle se termine d'elle-meme a l'heure prevue : un
 * oubli ne bloque jamais les clients.
 */
@Entity
@Table(name = "plateforme_maintenance")
@Getter
@Setter
@NoArgsConstructor
public class Maintenance extends EntiteAuditee {

    @Column(nullable = false, length = 500)
    private String message;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plateforme_maintenance_module", joinColumns = @JoinColumn(name = "maintenance_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "module", length = 40, nullable = false)
    private Set<ModuleApplicatif> modules = EnumSet.noneOf(ModuleApplicatif.class);

    @Column(name = "annonce_le", nullable = false)
    private Instant annonceLe;

    @Column(nullable = false)
    private Instant debut;

    @Column(name = "fin_prevue", nullable = false)
    private Instant finPrevue;

    @Column(name = "terminee_le")
    private Instant termineeLe;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    public enum Statut { PROGRAMMEE, ANNONCEE, EN_COURS, TERMINEE, ANNULEE }

    /** Fin reelle : anticipee si l'exploitant l'a terminee plus tot, sinon prevue. */
    public Instant finEffective() {
        return termineeLe != null && termineeLe.isBefore(finPrevue) ? termineeLe : finPrevue;
    }

    public Statut statut(Instant maintenant) {
        if (annuleeLe != null) {
            return Statut.ANNULEE;
        }
        if (!maintenant.isBefore(finEffective())) {
            return Statut.TERMINEE;
        }
        if (!maintenant.isBefore(debut)) {
            return Statut.EN_COURS;
        }
        return maintenant.isBefore(annonceLe) ? Statut.PROGRAMMEE : Statut.ANNONCEE;
    }
}
