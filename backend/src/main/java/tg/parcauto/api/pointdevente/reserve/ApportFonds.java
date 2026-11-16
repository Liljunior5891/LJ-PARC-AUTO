package tg.parcauto.api.pointdevente.reserve;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Apport au fonds de reserve, definitif : d'un associe, depuis son sous-compte,
 * ou de l'entreprise, verse par l'administrateur depuis le compte de l'entreprise.
 */
@Entity
@Table(name = "pdv_fonds_apport")
@Getter
@Setter
@NoArgsConstructor
public class ApportFonds extends BaseEntity {

    public enum Origine { ASSOCIE, ENTREPRISE }

    @Column(nullable = false, length = 40)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origine origine;

    @Column(name = "associe_id")
    private Long associeId;

    @Column(name = "associe_nom", length = 150)
    private String associeNom;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_apport", nullable = false)
    private Instant dateApport;

    @Column(length = 500)
    private String note;

    @Column(name = "auteur_id", nullable = false)
    private Long auteurId;

    @Column(name = "auteur_nom", nullable = false, length = 150)
    private String auteurNom;
}
