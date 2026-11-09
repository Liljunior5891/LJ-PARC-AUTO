package tg.parcauto.api.plateforme.entreprise;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.EntiteAuditee;

/**
 * Entreprise cliente de la plateforme : le compte auquel se rattachent ses
 * utilisateurs, ses modules actives et toutes ses donnees metier.
 * <p>
 * L'identite detaillee (logo, NIF, mentions de facture) reste dans le
 * parametrage de l'entreprise ; ici ne figure que ce dont la plateforme a
 * besoin pour gerer l'abonne.
 */
@Entity
@Table(name = "entreprise")
@Getter
@Setter
@NoArgsConstructor
public class Entreprise extends EntiteAuditee {

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(length = 100)
    private String pays;

    @Column(length = 40)
    private String telephone;

    @Column(length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutEntreprise statut = StatutEntreprise.ACTIVE;

    /**
     * Module Agences : l'entreprise peut gerer plusieurs agences. Faux par defaut :
     * une seule agence, jusqu'a ce que le super-administrateur de la plateforme l'autorise.
     */
    @Column(name = "agences_multiples", nullable = false)
    private boolean agencesMultiples;

    public boolean active() {
        return statut == StatutEntreprise.ACTIVE;
    }
}
