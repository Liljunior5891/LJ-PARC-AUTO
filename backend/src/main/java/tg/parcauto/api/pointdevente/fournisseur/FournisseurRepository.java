package tg.parcauto.api.pointdevente.fournisseur;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FournisseurRepository extends JpaRepository<Fournisseur, Long> {

    /** Tous les fournisseurs filtres ; le service calcule ensuite les dettes et pagine. */
    @Query("""
            select f from Fournisseur f
            where (:actif is null or f.actif = :actif)
              and (:q is null or lower(f.nom) like :q or lower(f.code) like :q
                   or lower(coalesce(f.personneContact, '')) like :q or coalesce(f.telephone, '') like :q
                   or lower(coalesce(f.ville, '')) like :q)
            order by lower(f.nom)
            """)
    List<Fournisseur> rechercher(@Param("q") String q, @Param("actif") Boolean actif);

    @Query("select f from Fournisseur f where lower(trim(f.nom)) = lower(trim(:nom)) order by f.actif desc, f.id")
    List<Fournisseur> parNom(@Param("nom") String nom);

    List<Fournisseur> findByActifTrueOrderByNomAsc();

    default Optional<Fournisseur> premierParNom(String nom) {
        return parNom(nom).stream().findFirst();
    }
}
