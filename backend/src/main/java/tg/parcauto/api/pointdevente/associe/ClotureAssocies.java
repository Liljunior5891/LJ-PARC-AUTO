package tg.parcauto.api.pointdevente.associe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.LocalDate;

/** Mois dont la part des associes est figee : une correction ulterieure ne la change plus. */
@Entity
@Table(name = "pdv_associe_cloture")
@Getter
@Setter
@NoArgsConstructor
public class ClotureAssocies extends BaseEntity {

    /** Premier jour du mois. */
    @Column(nullable = false)
    private LocalDate mois;
}
