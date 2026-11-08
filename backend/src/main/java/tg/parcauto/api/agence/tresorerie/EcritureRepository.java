package tg.parcauto.api.agence.tresorerie;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EcritureRepository extends JpaRepository<Ecriture, Long> {

    boolean existsByEcritureAnnuleeId(Long ecritureId);

    /** Journees ou chaque agence a eu des ecritures : [agenceId, journee]. */
    @org.springframework.data.jpa.repository.Query("""
            select distinct e.agenceId, e.journee from Ecriture e
            where e.agenceId in :agences and e.journee >= :depuis and e.journee < :avant
            """)
    java.util.List<Object[]> journeesActives(@org.springframework.data.repository.query.Param("agences") java.util.Collection<Long> agences,
                                             @org.springframework.data.repository.query.Param("depuis") java.time.LocalDate depuis,
                                             @org.springframework.data.repository.query.Param("avant") java.time.LocalDate avant);

    long countByAgenceIdAndJournee(Long agenceId, java.time.LocalDate journee);
}
