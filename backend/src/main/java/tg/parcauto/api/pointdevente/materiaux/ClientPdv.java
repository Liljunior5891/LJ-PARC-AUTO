package tg.parcauto.api.pointdevente.materiaux;

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
import java.time.LocalDate;

/**
 * Compte client du point de vente : particulier, artisan ou entreprise du BTP.
 * Son solde n'est pas stocke : c'est la somme des restes dus de ses ventes
 * validees, toujours juste.
 */
@Entity
@Table(name = "pdv_client")
@Getter
@Setter
@NoArgsConstructor
public class ClientPdv extends BaseEntity {

    public enum Type {
        PARTICULIER("Particulier"), ARTISAN("Artisan / macon"), ENTREPRISE("Entreprise"),
        ADMINISTRATION("Administration"), ONG("ONG / Association");

        private final String libelle;

        Type(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }

        /** Une personne se decrit par son nom et ses prenoms, les autres par une raison sociale. */
        public boolean estPersonne() {
            return this == PARTICULIER || this == ARTISAN;
        }
    }

    @Column(nullable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type = Type.PARTICULIER;

    /** Nom affiche partout (caisse, devis, factures) : raison sociale, ou prenoms et nom d'une personne. */
    @Column(nullable = false, length = 150)
    private String nom;

    @Column(name = "nom_famille", length = 70)
    private String nomFamille;

    @Column(length = 70)
    private String prenoms;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(length = 100)
    private String profession;

    @Column(name = "forme_juridique", length = 40)
    private String formeJuridique;

    @Column(length = 60)
    private String rccm;

    @Column(name = "secteur_activite", length = 100)
    private String secteurActivite;

    @Column(name = "organisme_tutelle", length = 150)
    private String organismeTutelle;

    @Column(name = "numero_agrement", length = 60)
    private String numeroAgrement;

    @Column(length = 80)
    private String ville;

    @Column(length = 80)
    private String pays;

    @Column(name = "personne_contact", length = 120)
    private String personneContact;

    @Column(name = "fonction_contact", length = 100)
    private String fonctionContact;

    @Column(name = "telephone_contact", length = 40)
    private String telephoneContact;

    @Column(name = "conditions_commerciales", columnDefinition = "text")
    private String conditionsCommerciales;

    @Column(length = 40)
    private String telephone;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String adresse;

    @Column(length = 60)
    private String nif;

    /** Nul : plafond par defaut des reglages ; zero : aucun credit. */
    @Column(name = "plafond_credit", precision = 16, scale = 2)
    private BigDecimal plafondCredit;

    /** Nul : delai par defaut des reglages. */
    @Column(name = "delai_paiement_jours")
    private Integer delaiPaiementJours;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private boolean actif = true;
}
