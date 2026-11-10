package tg.parcauto.api.plateforme.abonnement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.EntiteAuditee;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abonnement en cours d'une entreprise : essai gratuit ou periode payee.
 * <p>
 * Table de la plateforme, non cloisonnee : chaque requete nomme l'entreprise.
 */
@Entity
@Table(name = "abo_abonnement")
@Getter
@Setter
@NoArgsConstructor
public class Abonnement extends EntiteAuditee {

    @Column(name = "entreprise_id", nullable = false, updatable = false)
    private Long entrepriseId;

    /** Nulle pendant l'essai : aucune limite. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "formule_id")
    private Formule formule;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Periodicite periodicite;

    @Column(nullable = false)
    private boolean essai = true;

    @Column(nullable = false)
    private LocalDate debut;

    /** Dernier jour couvert ; la grace commence le lendemain. */
    @Column(nullable = false)
    private LocalDate fin;

    @Column(length = 300)
    private String modules;

    public Set<ModuleApplicatif> modulesPayes() {
        return lireModules(modules);
    }

    public void definirModules(Set<ModuleApplicatif> liste) {
        modules = ecrireModules(liste);
    }

    static Set<ModuleApplicatif> lireModules(String valeur) {
        Set<ModuleApplicatif> set = EnumSet.noneOf(ModuleApplicatif.class);
        if (valeur != null && !valeur.isBlank()) {
            Arrays.stream(valeur.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(s -> set.add(ModuleApplicatif.valueOf(s)));
        }
        return set;
    }

    static String ecrireModules(Set<ModuleApplicatif> liste) {
        return liste == null || liste.isEmpty() ? null
                : liste.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
