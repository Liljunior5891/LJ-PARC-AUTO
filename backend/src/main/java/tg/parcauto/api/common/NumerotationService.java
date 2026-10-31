package tg.parcauto.api.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.time.LocalDate;

/**
 * Attribue les numeros de piece (contrats, missions, livraisons) au format
 * {@code PREFIXE-ANNEE-0001}.
 * <p>
 * L'increment est realise par un UPSERT atomique : deux creations simultanees
 * ne peuvent pas obtenir le meme numero.
 */
@Service
@RequiredArgsConstructor
public class NumerotationService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(propagation = Propagation.REQUIRED)
    public String prochainNumero(String prefixe) {
        int annee = LocalDate.now().getYear();
        Number valeur = (Number) em.createNativeQuery("""
                        insert into compteur_numero (entreprise_id, prefixe, annee, valeur)
                        values (:entreprise, :prefixe, :annee, 1)
                        on conflict (entreprise_id, prefixe, annee)
                        do update set valeur = compteur_numero.valeur + 1
                        returning valeur
                        """)
                .setParameter("entreprise", ContexteEntreprise.exiger())
                .setParameter("prefixe", prefixe)
                .setParameter("annee", annee)
                .getSingleResult();

        return "%s-%d-%04d".formatted(prefixe, annee, valeur.longValue());
    }
}
