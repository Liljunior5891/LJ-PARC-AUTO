package tg.parcauto.api.finance.recette;

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
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.exploitation.client.Client;
import tg.parcauto.api.exploitation.location.Location;
import tg.parcauto.api.exploitation.mission.Mission;
import tg.parcauto.api.finance.commun.ModePaiement;
import tg.parcauto.api.finance.facture.Facture;
import tg.parcauto.api.parc.camion.Camion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Recette rattachee a un camion, chapitre 15 du cahier des charges.
 * Le rattachement au camion est obligatoire : c'est lui qui rend calculable
 * la rentabilite du vehicule (chapitre 2).
 */
@Entity
@Table(name = "recette")
@Getter
@Setter
@NoArgsConstructor
public class Recette extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camion_id", nullable = false)
    private Camion camion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facture_id")
    private Facture facture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeRecette type;

    @Column(nullable = false, length = 255)
    private String libelle;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_recette", nullable = false)
    private LocalDate dateRecette;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", length = 25)
    private ModePaiement modePaiement;

    @Column(length = 80)
    private String reference;

    @Column(length = 500)
    private String justificatif;

    @Column(columnDefinition = "text")
    private String observations;
}
