package tg.parcauto.api.parametrage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/**
 * Identite de l'entreprise, saisie une fois et reprise partout : entete des
 * factures, entete des rapports et page de connexion.
 * <p>
 * Une ligne par entreprise cliente, garantie par une contrainte d'unicite.
 */
@Entity
@Table(name = "parametre_societe")
@Getter
@Setter
@NoArgsConstructor
public class Societe extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(length = 60)
    private String sigle;

    @Column(length = 60)
    private String formeJuridique;

    @Column(length = 255)
    private String adresse;

    @Column(length = 100)
    private String ville;

    @Column(length = 100)
    private String pays;

    @Column(length = 40)
    private String telephone;

    @Column(length = 150)
    private String email;

    @Column(name = "site_web", length = 150)
    private String siteWeb;

    /** Numero d'identification fiscale, porte sur les factures. */
    @Column(length = 60)
    private String nif;

    @Column(length = 60)
    private String rccm;

    @Column(length = 120)
    private String banque;

    @Column(length = 60)
    private String compteBancaire;

    /**
     * Taux de TVA applique aux factures, en pourcentage.
     * <p>
     * Chaque facture conserve le taux en vigueur au moment de son emission :
     * changer ce parametre n'altere donc pas les factures deja etablies.
     */
    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal tauxTva = java.math.BigDecimal.valueOf(18);

    /** Format des documents imprimes : « A4 » ou « A5 », choisi par l'administrateur. */
    @Column(name = "format_documents", nullable = false, length = 2)
    private String formatDocuments = "A4";

    /** Mentions ajoutees en bas de chaque facture. */
    @Column(name = "mentions_facture", length = 500)
    private String mentionsFacture;

    /**
     * Logo encode en base64. Le garder en base plutot que sur disque evite un
     * service de fichiers statiques : la page de connexion le recupere dans la
     * meme reponse que le nom, et le PDF le decode directement.
     */
    @Column(name = "logo_base64", columnDefinition = "text")
    private String logoBase64;

    @Column(name = "logo_type", length = 60)
    private String logoType;
}
