package tg.parcauto.api.pointdevente.vente;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.stock.TypeMouvementStock;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Encaissement et annulation des ventes.
 * <p>
 * Tout est recalcule ici a partir du catalogue : le poste de caisse transmet
 * articles, quantites et remises, jamais un prix ni un total. Ticket, lignes et
 * sorties de stock sont ecrits dans une seule transaction : une rupture de
 * stock decouverte en cours de route annule l'ensemble.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VenteService {

    private final VenteRepository repository;
    private final tg.parcauto.api.pointdevente.caisse.SessionTravailService sessionsTravail;
    private final ArticleRepository articleRepository;
    private final StockService stockService;
    private final NumerotationService numerotation;
    private final ApplicationEventPublisher evenements;

    public VenteDto.Ticket encaisser(VenteDto.Encaissement demande, UtilisateurPrincipal caissier) {
        List<LigneVente> lignes = new ArrayList<>();
        for (VenteDto.LigneDemande demandeLigne : demande.lignes()) {
            Article article = articleRepository.findById(demandeLigne.articleId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Article", demandeLigne.articleId()));
            if (!article.isActif()) {
                throw new BusinessException("L'article " + article.getNom() + " n'est plus en vente");
            }
            BigDecimal montant = montant(article.getPrixVente().multiply(demandeLigne.quantite()));
            BigDecimal remiseLigne = montant(demandeLigne.remise());
            if (remiseLigne.compareTo(montant) > 0) {
                throw new BusinessException("La remise sur " + article.getNom() + " depasse le montant de la ligne");
            }
            LigneVente ligne = new LigneVente();
            ligne.setArticle(article);
            ligne.setLibelle(article.getNom());
            ligne.setPrixUnitaire(article.getPrixVente());
            ligne.setPrixAchat(article.getPrixAchat());
            ligne.setQuantite(demandeLigne.quantite());
            ligne.setRemise(remiseLigne);
            ligne.setTotal(montant.subtract(remiseLigne));
            lignes.add(ligne);
        }
        Vente vente = enregistrer(lignes, demande.reglement(), caissier, null, null);
        for (LigneVente ligne : vente.getLignes()) {
            if (ligne.getArticle().isSuiviStock()) {
                stockService.appliquer(ligne.getArticle(), TypeMouvementStock.VENTE, ligne.getQuantite(),
                        null, "Commande " + vente.getNumero(), vente.getId());
            }
        }
        return VenteDto.Ticket.de(vente);
    }

    /**
     * Ticket de l'addition d'une table. Les lignes viennent de la commande, a
     * ses prix ; le stock est deja sorti a l'envoi en cuisine et n'est pas
     * touche ici.
     */
    public Vente encaisserAddition(List<LigneVente> lignes, VenteDto.Reglement reglement,
                                   UtilisateurPrincipal caissier, Long commandeId, String tableNom) {
        return enregistrer(lignes, reglement, caissier, commandeId, tableNom);
    }

    private Vente enregistrer(List<LigneVente> lignes, VenteDto.Reglement reglement, UtilisateurPrincipal caissier,
                              Long commandeId, String tableNom) {
        // Pas de vente sans session ouverte par un gerant sur une caisse du vendeur.
        SessionCaisse session = sessionsTravail.exiger(caissier, reglement.sessionId());

        Vente vente = new Vente();
        vente.setSession(session);
        vente.setDateVente(Instant.now());
        vente.setCaissierNom(caissier.getNomComplet());
        vente.setClientNom(vide(reglement.clientNom()));
        vente.setCommandeId(commandeId);
        vente.setTableNom(tableNom);

        BigDecimal totalBrut = BigDecimal.ZERO;
        for (LigneVente ligne : lignes) {
            vente.ajouter(ligne);
            totalBrut = totalBrut.add(ligne.getTotal());
        }

        BigDecimal remise = montant(reglement.remise());
        if (remise.compareTo(totalBrut) > 0) {
            throw new BusinessException("La remise depasse le total de la commande");
        }
        BigDecimal total = totalBrut.subtract(remise);
        vente.setTotalBrut(totalBrut);
        vente.setRemise(remise);
        vente.setTotal(total);
        regler(vente, reglement, total);

        vente.setNumero(numerotation.prochainNumero("COM"));
        return repository.save(vente);
    }

    /** Annulation par un gerant : les articles reviennent en stock, le ticket reste consultable. */
    public VenteDto.Ticket annuler(Long id, String motif, UtilisateurPrincipal auteur) {
        Vente vente = charger(id);
        if (vente.getStatut() == Vente.Statut.ANNULEE) {
            throw new BusinessException("La commande " + vente.getNumero() + " est deja annulee");
        }
        vente.setStatut(Vente.Statut.ANNULEE);
        vente.setAnnuleeLe(Instant.now());
        vente.setAnnuleePar(auteur.getNomComplet());
        vente.setMotifAnnulation(vide(motif));
        if (vente.getCommandeId() != null) {
            // Addition d'une table : les plats ont ete servis, seul le reglement est defait.
            evenements.publishEvent(new AdditionAnnulee(vente.getId(), vente.getCommandeId()));
            return VenteDto.Ticket.de(vente);
        }
        for (LigneVente ligne : vente.getLignes()) {
            if (ligne.getArticle().isSuiviStock()) {
                stockService.appliquer(ligne.getArticle(), TypeMouvementStock.RETOUR, ligne.getQuantite(),
                        null, "Annulation de la commande " + vente.getNumero(), vente.getId());
            }
        }
        return VenteDto.Ticket.de(vente);
    }

    @Transactional(readOnly = true)
    public VenteDto.Ticket ticket(Long id) {
        return VenteDto.Ticket.de(charger(id));
    }

    @Transactional(readOnly = true)
    public Page<VenteDto.Resume> lister(Instant debut, Instant fin, Long sessionId, Pageable pageable) {
        Page<Vente> page = sessionId != null
                ? repository.findBySessionIdOrderByDateVenteDesc(sessionId, pageable)
                : repository.periode(debut, fin, pageable);
        return page.map(VenteDto.Resume::de);
    }

    /** Annulation du ticket d'une addition : la commande retrouve ses lignes a payer. */
    public record AdditionAnnulee(Long venteId, Long commandeId) {
    }

    /** Especes : le montant recu couvre le total et la monnaie est calculee. Sinon, paiement exact. */
    private void regler(Vente vente, VenteDto.Reglement demande, BigDecimal total) {
        vente.setModePaiement(demande.modePaiement());
        vente.setReferencePaiement(vide(demande.referencePaiement()));
        if (demande.modePaiement() == ModePaiementPdv.ESPECES) {
            BigDecimal recu = demande.montantRecu() == null ? total : montant(demande.montantRecu());
            if (recu.compareTo(total) < 0) {
                throw new BusinessException("Montant recu insuffisant : il manque "
                        + total.subtract(recu).stripTrailingZeros().toPlainString());
            }
            vente.setMontantRecu(recu);
            vente.setMonnaieRendue(recu.subtract(total));
        } else {
            vente.setMontantRecu(total);
            vente.setMonnaieRendue(BigDecimal.ZERO);
        }
    }

    private Vente charger(Long id) {
        return repository.detail(id).orElseThrow(() -> ResourceNotFoundException.of("Vente", id));
    }

    private static BigDecimal montant(BigDecimal valeur) {
        return valeur == null ? BigDecimal.ZERO.setScale(2) : valeur.setScale(2, RoundingMode.HALF_UP);
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
