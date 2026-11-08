package tg.parcauto.api.agence.cloture;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Compte controle a la cloture : solde theorique (registre), solde reel et ecart. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LigneCloture {

    @Column(name = "compte_id", nullable = false)
    private Long compteId;

    @Column(nullable = false, length = 120)
    private String libelle;

    @Column(nullable = false, length = 20)
    private String famille;

    @Column(name = "solde_theorique", nullable = false)
    private long soldeTheorique;

    @Column(name = "solde_reel")
    private Long soldeReel;

    private Long ecart;

    @Column(length = 500)
    private String justification;
}
