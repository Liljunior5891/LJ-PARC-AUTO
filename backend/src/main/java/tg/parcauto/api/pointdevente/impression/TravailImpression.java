package tg.parcauto.api.pointdevente.impression;

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

import java.time.Instant;

/** Bon a imprimer sur une imprimante de preparation ; son contenu est fige a la creation. */
@Entity
@Table(name = "pdv_travail_impression")
@Getter
@Setter
@NoArgsConstructor
public class TravailImpression extends BaseEntity {

    public enum Type { BON, ANNULATION, DUPLICATA, TEST }

    public enum Statut { EN_ATTENTE, EN_COURS, IMPRIME, ECHEC }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imprimante_id")
    private Imprimante imprimante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.EN_ATTENTE;

    @Column(name = "commande_id")
    private Long commandeId;

    private Integer envoi;

    @Column(name = "vente_id")
    private Long venteId;

    /** Ce que le bon concerne, pour les listes (« Table 5 · envoi 2 », « Ticket T-000456 »). */
    @Column(nullable = false, length = 80)
    private String reference;

    /** Contenu du bon en JSON ({@link BonCuisine}). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Column(nullable = false)
    private int tentatives;

    @Column(length = 255)
    private String erreur;

    @Column(name = "imprime_le")
    private Instant imprimeLe;

    @Column(name = "imprime_par", length = 150)
    private String imprimePar;
}
