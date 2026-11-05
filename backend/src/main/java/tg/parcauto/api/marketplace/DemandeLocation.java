package tg.parcauto.api.marketplace;

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
import java.time.LocalDate;

/**
 * Demande de location d'un vehicule faite en ligne. Acceptee, elle devient un
 * contrat de location du module Gestion de parc.
 */
@Entity
@Table(name = "mkt_demande_location")
@Getter
@Setter
@NoArgsConstructor
public class DemandeLocation extends BaseEntity {

    public enum Statut {
        NOUVELLE("Nouvelle"),
        ACCEPTEE("Acceptee"),
        REFUSEE("Refusee"),
        ANNULEE("Annulee"),
        /** Acceptee mais le client ne s'est pas presente avant la date de debut. */
        EXPIREE("Expiree"),
        /** Le client est venu en agence : la recette est enregistree. */
        CONCLUE("Conclue");

        private final String libelle;

        Statut(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    @Column(nullable = false, length = 40)
    private String numero;

    @Column(nullable = false, length = 32)
    private String jeton;

    /** Offre d'origine ; vide si elle a ete retiree depuis. */
    @Column(name = "offre_id")
    private Long offreId;

    @Column(name = "camion_id", nullable = false)
    private Long camionId;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "client_nom", nullable = false, length = 150)
    private String clientNom;

    /** Nom de famille saisi dans la boutique (vide pour les commandes anterieures a V32). */
    @Column(name = "client_nom_famille", length = 100)
    private String clientNomFamille;

    @Column(name = "client_prenoms", length = 100)
    private String clientPrenoms;

    @Column(name = "client_telephone", nullable = false, length = 40)
    private String clientTelephone;

    @Column(name = "client_email", length = 150)
    private String clientEmail;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "tarif_jour", nullable = false, precision = 16, scale = 2)
    private BigDecimal tarifJour;

    @Column(name = "montant_estime", nullable = false, precision = 16, scale = 2)
    private BigDecimal montantEstime;

    @Column(name = "lieu_depart", length = 180)
    private String lieuDepart;

    @Column(length = 180)
    private String destination;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.NOUVELLE;

    @Column(name = "motif_refus", length = 255)
    private String motifRefus;

    @Column(name = "location_id")
    private Long locationId;

    /** Recette enregistree en agence qui conclut la demande. */
    @Column(name = "recette_id")
    private Long recetteId;

    @Column(name = "date_demande", nullable = false)
    private Instant dateDemande;
}
