package tg.parcauto.api.presence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Lieu de travail (siege, boutique, agence, depot) : son code QR change toutes
 * les 30 secondes et le telephone qui pointe doit se trouver dans son rayon.
 */
@Entity
@Table(name = "pres_lieu")
@Getter
@Setter
@NoArgsConstructor
public class LieuPresence extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(length = 255)
    private String adresse;

    private Double latitude;

    private Double longitude;

    @Column(name = "rayon_metres", nullable = false)
    private int rayonMetres = 100;

    /** Identifiant public, porte par l'adresse du code QR. */
    @Column(name = "jeton_public", nullable = false, length = 32, updatable = false)
    private String jetonPublic;

    /** Secret qui fait changer le code QR ; ne sort jamais du serveur. */
    @Column(name = "secret_qr", nullable = false, length = 64)
    private String secretQr;

    @Column(nullable = false)
    private boolean actif = true;

    /** Gestionnaires de parc et superviseurs d'agence qui suivent ce lieu. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pres_lieu_responsable", joinColumns = @JoinColumn(name = "lieu_id"))
    @Column(name = "utilisateur_id")
    private Set<Long> responsableIds = new HashSet<>();

    public boolean aPosition() {
        return latitude != null && longitude != null;
    }
}
