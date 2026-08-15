package tg.parcauto.api.parc.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("""
            select d from Document d
            where (:proprietaireType is null or d.proprietaireType = :proprietaireType)
              and (:proprietaireId is null or d.proprietaireId = :proprietaireId)
              and (:type is null or d.type = :type)
              and d.archive = false
            """)
    Page<Document> rechercher(@Param("proprietaireType") ProprietaireDocument proprietaireType,
                              @Param("proprietaireId") Long proprietaireId,
                              @Param("type") TypeDocument type,
                              Pageable pageable);

    @Query("""
            select d from Document d
            where d.archive = false and d.dateExpiration is not null and d.dateExpiration <= :limite
            order by d.dateExpiration asc
            """)
    List<Document> expirantAvant(@Param("limite") LocalDate limite);
}
