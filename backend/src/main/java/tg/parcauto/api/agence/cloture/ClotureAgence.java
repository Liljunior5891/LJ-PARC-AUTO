package tg.parcauto.api.agence.cloture;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Cloture d'une journee de caisse d'une agence : soldes theoriques, soldes reels et ecarts. */
@Entity
@Table(name = "agc_cloture")
@Getter
@Setter
@NoArgsConstructor
public class ClotureAgence extends BaseEntity {

    public enum Statut { SOUMISE, VALIDEE, REJETEE, ROUVERTE }

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(nullable = false, updatable = false)
    private LocalDate journee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut;

    @Column(length = 1000)
    private String observations;

    @Column(name = "soumise_le")
    private Instant soumiseLe;

    @Column(name = "soumise_par_id")
    private Long soumiseParId;

    @Column(name = "soumise_par_nom", length = 150)
    private String soumiseParNom;

    @Column(name = "validee_le")
    private Instant valideeLe;

    @Column(name = "validee_par_id")
    private Long valideeParId;

    @Column(name = "validee_par_nom", length = 150)
    private String valideeParNom;

    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;

    @Column(name = "rouverte_le")
    private Instant rouverteLe;

    @Column(name = "rouverte_par_nom", length = 150)
    private String rouverteParNom;

    @Column(name = "motif_reouverture", length = 500)
    private String motifReouverture;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agc_cloture_ligne", joinColumns = @JoinColumn(name = "cloture_id"))
    @OrderBy("libelle")
    private List<LigneCloture> lignes = new ArrayList<>();
}
