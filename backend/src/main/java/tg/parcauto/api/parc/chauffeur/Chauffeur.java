package tg.parcauto.api.parc.chauffeur;

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

/** Fiche chauffeur, chapitre 8 du cahier des charges. */
@Entity
@Table(name = "chauffeur")
@Getter
@Setter
@NoArgsConstructor
public class Chauffeur extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String matricule;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false, length = 80)
    private String prenom;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(nullable = false, length = 30)
    private String telephone;

    @Column(name = "telephone_urgence", length = 30)
    private String telephoneUrgence;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String adresse;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "numero_permis", length = 60)
    private String numeroPermis;

    /** Categories de permis, saisies sous forme de liste : "C, CE, D". */
    @Column(name = "categories_permis", length = 100)
    private String categoriesPermis;

    @Column(name = "permis_obtention")
    private LocalDate permisObtention;

    @Column(name = "permis_expiration")
    private LocalDate permisExpiration;

    @Column(name = "date_embauche")
    private LocalDate dateEmbauche;

    @Column(name = "salaire_mensuel", precision = 16, scale = 2)
    private BigDecimal salaireMensuel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutChauffeur statut = StatutChauffeur.ACTIF;

    @Column(columnDefinition = "text")
    private String observations;

    @Column(nullable = false)
    private boolean actif = true;

    public String nomComplet() {
        return prenom + " " + nom;
    }

    /** Vrai si le permis est expire ou expire dans les {@code jours} prochains jours. */
    public boolean permisBientotExpire(int jours) {
        return permisExpiration != null && permisExpiration.isBefore(LocalDate.now().plusDays(jours));
    }
}
