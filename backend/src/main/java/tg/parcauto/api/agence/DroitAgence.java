package tg.parcauto.api.agence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;
import tg.parcauto.api.security.Role;

/** Droit accorde a un profil de l'agence ; sa presence vaut accord. */
@Entity
@Table(name = "agc_permission")
@Getter
@Setter
@NoArgsConstructor
public class DroitAgence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "entreprise_id", nullable = false, updatable = false)
    private Long entrepriseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PermissionAgence permission;

    public DroitAgence(Role role, PermissionAgence permission) {
        this.role = role;
        this.permission = permission;
    }
}
