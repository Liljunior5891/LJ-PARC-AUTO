package tg.parcauto.api.marketplace;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.marketplace.paiement.PaiementService;
import tg.parcauto.api.parametrage.SocieteService;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.text.Normalizer;
import java.util.Locale;

/** Reglages de la boutique en ligne de l'entreprise. */
@Service
@RequiredArgsConstructor
@Transactional
public class BoutiqueService {

    private final BoutiqueRepository repository;
    private final SocieteService societeService;
    private final PaiementService paiementService;
    private final JdbcTemplate jdbc;

    /**
     * Boutique de l'entreprise. Tant qu'elle n'a jamais ete enregistree, une
     * proposition est renvoyee (nom et adresse tires de la societe), sans rien
     * ecrire : la boutique nait a son premier enregistrement.
     */
    @Transactional(readOnly = true)
    public MarketplaceDto.BoutiqueReponse lire() {
        return versReponse(repository.findFirstBy().orElseGet(this::proposition));
    }

    public MarketplaceDto.BoutiqueReponse enregistrer(MarketplaceDto.BoutiqueRequete r) {
        Boutique boutique = repository.findFirstBy().orElseGet(Boutique::new);
        String slug = r.slug().trim().toLowerCase(Locale.ROOT);
        Integer prises = jdbc.queryForObject(
                "select count(*) from mkt_boutique where slug = ? and entreprise_id <> ?",
                Integer.class, slug, ContexteEntreprise.exiger());
        if (prises != null && prises > 0) {
            throw new BusinessException("L'adresse « " + slug + " » est deja prise par une autre boutique");
        }
        if (!r.retraitActif() && !r.livraisonActive()) {
            throw new BusinessException("Proposez au moins le retrait en boutique ou la livraison");
        }
        if (!r.paiementRemise() && !r.paiementMobileMoney()) {
            throw new BusinessException("Acceptez au moins un mode de paiement");
        }
        if (r.paiementMobileMoney() && !r.paiementRemise() && !paiementService.disponible()) {
            throw new BusinessException("Le paiement mobile money en ligne n'est pas encore disponible : "
                    + "gardez le paiement au retrait / a la livraison");
        }
        boutique.setSlug(slug);
        boutique.setNom(r.nom().trim());
        boutique.setSlogan(vide(r.slogan()));
        boutique.setDescription(vide(r.description()));
        boutique.setCategorie(vide(r.categorie()));
        boutique.setCouverture(vide(r.couverture()));
        boutique.setTelephone(vide(r.telephone()));
        boutique.setWhatsapp(vide(r.whatsapp()));
        boutique.setEmail(vide(r.email()));
        boutique.setAdresse(vide(r.adresse()));
        boutique.setVille(vide(r.ville()));
        boutique.setHoraires(vide(r.horaires()));
        boutique.setPubliee(r.publiee());
        boutique.setRetraitActif(r.retraitActif());
        boutique.setLivraisonActive(r.livraisonActive());
        boutique.setFraisLivraison(r.livraisonActive() ? r.fraisLivraison() : null);
        boutique.setZoneLivraison(r.livraisonActive() ? vide(r.zoneLivraison()) : null);
        boutique.setPaiementRemise(r.paiementRemise());
        boutique.setPaiementMobileMoney(r.paiementMobileMoney());
        boutique.setMessageConfirmation(vide(r.messageConfirmation()));
        return versReponse(repository.save(boutique));
    }

    /** Boutique publiee de l'entreprise courante, pour les pages publiques. */
    @Transactional(readOnly = true)
    public Boutique publiee() {
        return repository.findFirstBy()
                .filter(Boutique::isPubliee)
                .orElseThrow(() -> new ResourceNotFoundException("Cette boutique n'est pas ouverte en ligne"));
    }

    /** Boutique de l'entreprise courante, publiee ou non (suivi d'une commande deja passee). */
    @Transactional(readOnly = true)
    public Boutique courante() {
        return repository.findFirstBy().orElseThrow(() -> new ResourceNotFoundException("Boutique introuvable"));
    }

    @Transactional(readOnly = true)
    public String logo() {
        return societeService.lire().logo();
    }

    /** Le mobile money n'est propose que si la boutique l'accepte et qu'un fournisseur est branche. */
    public boolean mobileMoneyOuvert(Boutique boutique) {
        return boutique.isPaiementMobileMoney() && paiementService.disponible();
    }

    private Boutique proposition() {
        var societe = societeService.lire();
        Boutique boutique = new Boutique();
        boutique.setNom(societe.nom());
        boutique.setSlug(slugLibre(slugDe(societe.nom())));
        boutique.setTelephone(societe.telephone());
        boutique.setEmail(societe.email());
        boutique.setAdresse(societe.adresse());
        boutique.setVille(societe.ville());
        return boutique;
    }

    /** Adresse proposee : celle tiree du nom si elle est libre, sinon suffixee d'un numero. */
    private String slugLibre(String base) {
        String candidat = base;
        for (int i = 2; i < 100; i++) {
            Integer prises = jdbc.queryForObject("select count(*) from mkt_boutique where slug = ?",
                    Integer.class, candidat);
            if (prises == null || prises == 0) {
                return candidat;
            }
            candidat = base + "-" + i;
        }
        return base + "-" + System.currentTimeMillis() % 10000;
    }

    static String slugDe(String nom) {
        String sansAccents = Normalizer.normalize(nom == null ? "" : nom, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = sansAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.length() > 50) {
            slug = slug.substring(0, 50).replaceAll("-$", "");
        }
        return slug.length() < 3 ? "boutique" : slug;
    }

    private MarketplaceDto.BoutiqueReponse versReponse(Boutique b) {
        return new MarketplaceDto.BoutiqueReponse(b.getId(), b.getSlug(), b.getNom(), b.getSlogan(),
                b.getDescription(), b.getCategorie(), b.getCouverture(), logo(), b.getTelephone(), b.getWhatsapp(),
                b.getEmail(), b.getAdresse(), b.getVille(), b.getHoraires(), b.isPubliee(), b.isRetraitActif(),
                b.isLivraisonActive(), b.getFraisLivraison(), b.getZoneLivraison(), b.isPaiementRemise(),
                b.isPaiementMobileMoney(), b.getMessageConfirmation(), paiementService.disponible());
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
