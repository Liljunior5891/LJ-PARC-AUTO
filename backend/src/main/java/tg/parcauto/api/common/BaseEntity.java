package tg.parcauto.api.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Socle des donnees metier : chaque ligne appartient a une entreprise cliente.
 * <p>
 * {@link TenantId} confie le cloisonnement a Hibernate : l'entreprise courante
 * est posee a l'insertion et ajoutee a chaque requete, y compris les chargements
 * par identifiant. Une entite qui herite de cette classe est donc isolee par
 * defaut ; aucun service n'a a filtrer lui-meme.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity extends EntiteAuditee {

    @TenantId
    @Column(name = "entreprise_id", nullable = false, updatable = false)
    private Long entrepriseId;
}
