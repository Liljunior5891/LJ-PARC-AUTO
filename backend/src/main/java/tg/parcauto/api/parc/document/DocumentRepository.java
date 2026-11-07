package tg.parcauto.api.parc.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long>,
        JpaSpecificationExecutor<Document> {

    @Query("""
            select d from Document d
            where d.archive = false and d.dateExpiration is not null and d.dateExpiration <= :limite
              and d.proprietaireType not in (tg.parcauto.api.parc.document.ProprietaireDocument.CLIENT_PDV, tg.parcauto.api.parc.document.ProprietaireDocument.FOURNISSEUR_PDV)
            order by d.dateExpiration asc
            """)
    List<Document> expirantAvant(@Param("limite") LocalDate limite);
}
