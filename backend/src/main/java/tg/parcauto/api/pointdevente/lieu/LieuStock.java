package tg.parcauto.api.pointdevente.lieu;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Endroit ou la marchandise se trouve. Un magasin peut avoir des caisses ; un
 * entrepot ne vend pas : il stocke et sert les bons de retrait.
 */
@Entity
@Table(name = "pdv_lieu_stock")
@Getter
@Setter
@NoArgsConstructor
public class LieuStock extends BaseEntity {

    public enum Type { MAGASIN, ENTREPOT }

    @Column(nullable = false, length = 80)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(length = 255)
    private String adresse;

    @Column(length = 40)
    private String telephone;

    @Column(nullable = false)
    private boolean actif = true;

    /** Recoit le stock existant a l'activation et tout mouvement sans lieu precis. */
    @Column(nullable = false)
    private boolean principal;

    @Column(name = "visible_en_ligne", nullable = false)
    private boolean visibleEnLigne;

    /** Magasiniers affectes : ils ne voient que les lieux qui leur sont confies. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pdv_lieu_utilisateur", joinColumns = @JoinColumn(name = "lieu_id"))
    @Column(name = "utilisateur_id")
    private Set<Long> utilisateurIds = new HashSet<>();
}
