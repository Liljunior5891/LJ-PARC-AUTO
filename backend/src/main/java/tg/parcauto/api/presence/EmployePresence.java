package tg.parcauto.api.presence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Employe qui pointe son arrivee avec son PIN personnel ; il peut etre relie a un compte utilisateur. */
@Entity
@Table(name = "pres_employe")
@Getter
@Setter
@NoArgsConstructor
public class EmployePresence extends BaseEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String matricule;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(length = 80)
    private String prenom;

    @Column(length = 30)
    private String telephone;

    @Column(length = 150)
    private String email;

    @Column(length = 80)
    private String fonction;

    @Column(length = 80)
    private String service;

    @Column(name = "date_embauche")
    private LocalDate dateEmbauche;

    /** Compte utilisateur converti en employe ; nul pour un employe sans compte. */
    @Column(name = "utilisateur_id")
    private Long utilisateurId;

    /** Empreinte (HMAC) du PIN : unique dans l'entreprise, le PIN n'est jamais stocke en clair. */
    @Column(name = "pin_empreinte", nullable = false, length = 64)
    private String pinEmpreinte;

    @Column(nullable = false)
    private boolean actif = true;

    /** Lieux ou l'employe peut pointer. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "pres_employe_lieu",
            joinColumns = @JoinColumn(name = "employe_id"),
            inverseJoinColumns = @JoinColumn(name = "lieu_id"))
    private Set<LieuPresence> lieux = new HashSet<>();

    public String nomComplet() {
        return prenom == null || prenom.isBlank() ? nom : prenom + " " + nom;
    }
}
