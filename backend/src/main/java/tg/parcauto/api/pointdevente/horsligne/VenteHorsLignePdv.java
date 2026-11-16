package tg.parcauto.api.pointdevente.horsligne;

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
 * Trace d'une vente de caisse faite sans connexion : enregistree (avec ce que le
 * gerant doit regulariser), ou en conflit jusqu'a sa reprise ou son abandon.
 */
@Entity
@Table(name = "pdv_vente_hors_ligne")
@Getter
@Setter
@NoArgsConstructor
public class VenteHorsLignePdv extends BaseEntity {

    public enum Statut { ENREGISTREE, CONFLIT, REPRISE, ABANDONNEE }

    @Column(nullable = false, length = 64)
    private String cle;

    @Column(name = "numero_provisoire", nullable = false, length = 40)
    private String numeroProvisoire;

    @Column(name = "saisie_le", nullable = false)
    private Instant saisieLe;

    @Column(name = "caissier_nom", nullable = false, length = 150)
    private String caissierNom;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut;

    @Column(name = "vente_id")
    private Long venteId;

    @Column(length = 500)
    private String message;

    @Column(length = 1000)
    private String manques;

    @Column(name = "ecarts_prix", length = 1000)
    private String ecartsPrix;

    @Column(name = "traitee_par", length = 150)
    private String traiteePar;

    @Column(name = "traitee_le")
    private Instant traiteeLe;

    @Column(length = 500)
    private String note;
}
