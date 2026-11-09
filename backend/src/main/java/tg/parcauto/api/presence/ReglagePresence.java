package tg.parcauto.api.presence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Horaire de l'entreprise : heure d'arrivee, tolerance, jours travailles et heure limite. */
@Entity
@Table(name = "pres_reglage")
@Getter
@Setter
@NoArgsConstructor
public class ReglagePresence extends BaseEntity {

    @Column(name = "heure_arrivee", nullable = false)
    private LocalTime heureArrivee = LocalTime.of(8, 0);

    @Column(name = "tolerance_minutes", nullable = false)
    private int toleranceMinutes = 10;

    /** 1 = lundi ... 7 = dimanche, separes par des virgules. */
    @Column(name = "jours_travailles", nullable = false, length = 20)
    private String joursTravailles = "1,2,3,4,5,6";

    /** Passe cette heure, un employe attendu qui n'a pas pointe est absent. */
    @Column(name = "heure_limite", nullable = false)
    private LocalTime heureLimite = LocalTime.of(12, 0);

    public Set<Integer> jours() {
        return Arrays.stream(joursTravailles.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Integer::valueOf).collect(Collectors.toCollection(TreeSet::new));
    }

    public void jours(Set<Integer> jours) {
        joursTravailles = new TreeSet<>(jours).stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public boolean travaille(DayOfWeek jour) {
        return jours().contains(jour.getValue());
    }
}
