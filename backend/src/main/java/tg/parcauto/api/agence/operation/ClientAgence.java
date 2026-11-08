package tg.parcauto.api.agence.operation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Fiche client facultative, retrouvee par son telephone a la saisie suivante. */
@Entity
@Table(name = "agc_client")
@Getter
@Setter
@NoArgsConstructor
public class ClientAgence extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String telephone;

    @Column(length = 150)
    private String nom;

    @Column(name = "type_piece", length = 40)
    private String typePiece;

    @Column(name = "numero_piece", length = 60)
    private String numeroPiece;
}
