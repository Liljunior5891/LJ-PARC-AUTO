package tg.parcauto.api.pointdevente.materiaux;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClientPdvRepository extends JpaRepository<ClientPdv, Long> {

    boolean existsByCodeIgnoreCase(String code);

    /** Recherche par nom, code, telephone, contact ou NIF ; {@code actif} ou {@code type} nul : tous. */
    @Query("""
            select c from ClientPdv c
            where (:actif is null or c.actif = :actif)
              and (:type is null or c.type = :type)
              and (:q = '' or lower(c.nom) like concat('%', :q, '%') or lower(c.code) like concat('%', :q, '%')
                   or coalesce(c.telephone, '') like concat('%', :q, '%')
                   or lower(coalesce(c.personneContact, '')) like concat('%', :q, '%')
                   or lower(coalesce(c.nif, '')) like concat('%', :q, '%'))
            order by lower(c.nom)
            """)
    Page<ClientPdv> rechercher(@Param("q") String q, @Param("actif") Boolean actif,
                               @Param("type") ClientPdv.Type type, Pageable pageable);

    /** Fiches dont le telephone finit par ce chiffre : le numero exact est compare ensuite, sans sa mise en forme. */
    List<ClientPdv> findByTelephoneEndingWithOrderByActifDescIdAsc(String fin);

    /**
     * [clientId, encours, encours echu] des ventes validees encore dues, pour
     * les clients donnes : l'encours echu est la part dont l'echeance est passee.
     */
    @Query("""
            select v.client.id, coalesce(sum(v.resteDu), 0),
                   coalesce(sum(case when v.echeance < current_date then v.resteDu else 0 end), 0)
            from Vente v
            where v.client.id in :ids and v.resteDu > 0
              and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by v.client.id
            """)
    List<Object[]> encours(@Param("ids") List<Long> ids);

    /** [clientId, encours, encours echu] de tous les clients qui doivent quelque chose. */
    @Query("""
            select v.client.id, coalesce(sum(v.resteDu), 0),
                   coalesce(sum(case when v.echeance < current_date then v.resteDu else 0 end), 0)
            from Vente v
            where v.client is not null and v.resteDu > 0
              and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by v.client.id
            """)
    List<Object[]> tousLesEncours();
}
