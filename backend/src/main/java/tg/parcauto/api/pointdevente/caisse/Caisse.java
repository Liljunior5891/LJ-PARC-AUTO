package tg.parcauto.api.pointdevente.caisse;

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
 * Poste d'encaissement cree par le gerant (« Caisse bar », « Caisse terrasse »).
 * Seuls les utilisateurs affectes l'ouvrent ; le gerant et l'administrateur
 * les ouvrent toutes. Une caisse ne se tient que par une personne a la fois.
 */
@Entity
@Table(name = "pdv_caisse")
@Getter
@Setter
@NoArgsConstructor
public class Caisse extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean actif = true;

    /** Comptes autorises a ouvrir la caisse (les comptes ne sont pas cloisonnes : simples identifiants). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pdv_caisse_utilisateur", joinColumns = @JoinColumn(name = "caisse_id"))
    @Column(name = "utilisateur_id")
    private Set<Long> utilisateurIds = new HashSet<>();

    /** Magasin d'ou vend la caisse ; nul tant que l'entreprise n'a pas d'entrepot. */
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieu_id")
    private tg.parcauto.api.pointdevente.lieu.LieuStock lieu;
}
