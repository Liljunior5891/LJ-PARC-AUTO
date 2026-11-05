package tg.parcauto.api.pointdevente.materiaux;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

/**
 * Reglages de la quincaillerie, et controle que l'entreprise a bien choisi ce
 * type d'activite : une boutique ou un restaurant n'ont ni credit, ni devis,
 * ni conditionnements.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReglageMateriauxService {

    public static final String MATERIAUX = "MATERIAUX";

    private final ReglageMateriauxRepository repository;
    private final ModuleService moduleService;

    /** Point de vente de l'entreprise courante en mode quincaillerie et materiaux. */
    @Transactional(readOnly = true)
    public boolean estMateriaux() {
        return MATERIAUX.equals(moduleService.variante(ContexteEntreprise.exiger(), ModuleApplicatif.POINT_DE_VENTE));
    }

    /** Reglages en vigueur, si l'entreprise est en mode materiaux ; sinon une erreur metier. */
    @Transactional(readOnly = true)
    public ReglageMateriaux exiger() {
        if (!estMateriaux()) {
            throw new BusinessException("Fonction reservee au point de vente « Quincaillerie & materiaux »");
        }
        return courant();
    }

    /** Sans ligne enregistree, les valeurs par defaut. */
    @Transactional(readOnly = true)
    public ReglageMateriaux courant() {
        return repository.courant().orElseGet(ReglageMateriaux::new);
    }

    @Transactional(readOnly = true)
    public MateriauxDto.Reglages lire() {
        return MateriauxDto.Reglages.de(courant());
    }

    public MateriauxDto.Reglages modifier(MateriauxDto.Reglages r) {
        ReglageMateriaux reglage = repository.courant().orElseGet(ReglageMateriaux::new);
        reglage.setCreditActif(r.creditActif());
        reglage.setCreditCaissier(r.creditCaissier());
        reglage.setDepassementGerant(r.depassementGerant());
        reglage.setBloquerSiRetard(r.bloquerSiRetard());
        reglage.setPlafondDefaut(r.plafondDefaut());
        reglage.setDelaiPaiementJours(r.delaiPaiementJours());
        reglage.setAcompteMinimum(r.acompteMinimum());
        reglage.setClientObligatoire(r.clientObligatoire());
        reglage.setDevisActif(r.devisActif());
        reglage.setDevisValiditeJours(r.devisValiditeJours());
        reglage.setMentionsDevis(r.mentionsDevis() == null || r.mentionsDevis().isBlank()
                ? null : r.mentionsDevis().trim());
        reglage.setFactureA4(r.factureA4());
        reglage.setTvaActive(r.tvaActive());
        reglage.setConditionnementsActifs(r.conditionnementsActifs());
        reglage.setLivraisonDifferee(r.livraisonDifferee());
        reglage.setVenteSurArrivage(r.venteSurArrivage());
        return MateriauxDto.Reglages.de(repository.save(reglage));
    }
}
