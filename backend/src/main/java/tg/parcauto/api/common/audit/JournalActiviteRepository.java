package tg.parcauto.api.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JournalActiviteRepository extends JpaRepository<JournalActivite, Long>, JpaSpecificationExecutor<JournalActivite> {

    /** Types d'objets deja traces, pour le filtre de l'ecran : [objet, libelle]. */
    @Query("select distinct j.objet, j.objetLibelle from JournalActivite j order by j.objetLibelle")
    List<Object[]> objets();

    /** Auteurs deja presents dans le journal : [id, nom]. */
    @Query("select distinct j.auteurId, j.auteurNom from JournalActivite j where j.auteurId is not null order by j.auteurNom")
    List<Object[]> auteurs();
}
