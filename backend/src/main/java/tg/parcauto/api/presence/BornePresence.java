package tg.parcauto.api.presence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.Instant;

/**
 * Tablette ou ecran pose sur un lieu : il affiche le code QR qui change et un
 * clavier PIN. Appairee une fois par le gerant avec un code a 6 chiffres, elle
 * garde un jeton dont seule l'empreinte est stockee.
 */
@Entity
@Table(name = "pres_borne")
@Getter
@Setter
@NoArgsConstructor
public class BornePresence extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "lieu_id")
    private LieuPresence lieu;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(name = "jeton_empreinte", length = 64)
    private String jetonEmpreinte;

    @Column(name = "code_appairage", length = 6)
    private String codeAppairage;

    @Column(name = "code_expire_le")
    private Instant codeExpireLe;

    @Column(name = "appairee_le")
    private Instant appaireeLe;

    @Column(name = "dernier_contact")
    private Instant dernierContact;

    @Column(nullable = false)
    private boolean actif = true;
}
