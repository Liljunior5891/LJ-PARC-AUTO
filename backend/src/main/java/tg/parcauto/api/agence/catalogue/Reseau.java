package tg.parcauto.api.agence.catalogue;

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

/** Reseau d'un service qui en regroupe plusieurs (Western Union sous Rapid Transfer), consultable a part. */
@Entity
@Table(name = "agc_reseau")
@Getter
@Setter
@NoArgsConstructor
public class Reseau extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceFinancier service;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false)
    private boolean actif = true;
}
