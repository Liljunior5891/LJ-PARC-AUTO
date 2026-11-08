package tg.parcauto.api.agence.credit;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Echeance prevue d'un credit : date et montant a rembourser. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EcheanceCredit {

    @Column(name = "date_prevue", nullable = false)
    private LocalDate datePrevue;

    @Column(nullable = false)
    private long montant;
}
