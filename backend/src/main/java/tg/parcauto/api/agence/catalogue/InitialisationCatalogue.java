package tg.parcauto.api.agence.catalogue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ecrit le catalogue par defaut pour une entreprise qui n'a encore aucun service. */
@Component
@RequiredArgsConstructor
public class InitialisationCatalogue {

    private final ServiceFinancierRepository services;
    private final ReseauRepository reseaux;
    private final TypeOperationRepository types;

    @Transactional(propagation = Propagation.MANDATORY)
    public void remplirSiVide() {
        if (services.count() > 0) {
            return;
        }
        int ordreService = 0;
        for (CatalogueParDefaut.Service s : CatalogueParDefaut.SERVICES) {
            ServiceFinancier service = new ServiceFinancier();
            service.setCode(s.code());
            service.setNom(s.nom());
            service.setCategorie(s.categorie());
            service.setOrdre(ordreService += 10);
            service = services.save(service);
            int ordre = 0;
            for (String[] r : s.reseaux()) {
                Reseau reseau = new Reseau();
                reseau.setService(service);
                reseau.setCode(r[0]);
                reseau.setNom(r[1]);
                reseau.setOrdre(ordre += 10);
                reseaux.save(reseau);
            }
            ordre = 0;
            for (CatalogueParDefaut.Type t : s.types()) {
                TypeOperation type = new TypeOperation();
                type.setService(service);
                type.setCode(t.code());
                type.setLibelle(t.libelle());
                type.setEffetCash(t.cash());
                type.setEffetVirtuel(t.virtuel());
                type.setCompteVirtuel(t.compte());
                type.setNature(t.nature());
                type.setOrdre(ordre += 10);
                types.save(type);
            }
        }
    }
}
