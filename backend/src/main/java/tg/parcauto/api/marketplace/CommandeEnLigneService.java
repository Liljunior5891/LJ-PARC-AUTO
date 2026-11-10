package tg.parcauto.api.marketplace;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.exploitation.client.Client;
import tg.parcauto.api.marketplace.CommandeEnLigne.Statut;
import tg.parcauto.api.marketplace.paiement.PaiementService;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.pointdevente.caisse.SessionTravailService;
import tg.parcauto.api.pointdevente.restauration.Commande;
import tg.parcauto.api.pointdevente.restauration.CommandeRepository;
import tg.parcauto.api.pointdevente.restauration.CommandeService;
import tg.parcauto.api.pointdevente.restauration.LigneCommande;
import tg.parcauto.api.pointdevente.restauration.RestaurationDto;
import tg.parcauto.api.pointdevente.vente.VenteService;
import org.springframework.context.event.EventListener;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.stock.TypeMouvementStock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Commandes en ligne : passees par un client sans compte depuis la boutique
 * publique, puis traitees par l'entreprise (confirmation, preparation,
 * livraison ou retrait).
 * <p>
 * Sans point de vente, le stock est reserve des la commande (un produit libre
 * voit sa quantite baisser) et rendu si elle est annulee.
 * <p>
 * Avec le point de vente, la commande est confiee a la caisse et suit le
 * circuit d'une commande sur place : elle n'est acceptee que si une caisse
 * est ouverte ; au restaurant, elle devient une commande de salle a valider,
 * qui part sur les ecrans de preparation puis s'encaisse ; en boutique, la
 * caisse l'accepte puis l'encaisse a la remise. Le stock sort alors avec le
 * point de vente, et la vente est comptee comme vente en ligne.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommandeEnLigneService {

    /** Etapes suivantes permises depuis chaque etat. */
    static final String MESSAGE_FERMETURE =
            "La boutique ne prend pas de commande pour le moment : la caisse est fermee. Reessayez a son ouverture.";

    private static final Map<Statut, Set<Statut>> TRANSITIONS = Map.of(
            Statut.NOUVELLE, EnumSet.of(Statut.CONFIRMEE, Statut.ANNULEE),
            Statut.CONFIRMEE, EnumSet.of(Statut.PRETE, Statut.EN_LIVRAISON, Statut.LIVREE, Statut.ANNULEE),
            Statut.PRETE, EnumSet.of(Statut.EN_LIVRAISON, Statut.LIVREE, Statut.ANNULEE),
            Statut.EN_LIVRAISON, EnumSet.of(Statut.LIVREE, Statut.ANNULEE),
            Statut.LIVREE, EnumSet.noneOf(Statut.class),
            Statut.ANNULEE, EnumSet.noneOf(Statut.class));

    private final CommandeEnLigneRepository repository;
    private final OffreRepository offreRepository;
    private final OffreService offreService;
    private final BoutiqueService boutiqueService;
    private final ClientEnLigneService clientEnLigne;
    private final ArticleRepository articleRepository;
    private final StockService stockService;
    private final PaiementService paiementService;
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;
    private final NumerotationService numerotation;
    private final ModuleService moduleService;
    private final SessionTravailService sessionsTravail;
    private final CommandeService commandeSalleService;
    private final CommandeRepository commandeSalleRepository;
    private final tg.parcauto.api.pointdevente.lieu.LieuStockService lieux;

    // --------------------------------------------------------- Cote client

    /** Commande passee depuis la boutique publique ; renvoie le suivi et, s'il y a lieu, l'adresse de paiement. */
    public MarketplaceDto.Suivi commander(MarketplaceDto.DemandeCommande d) {
        Boutique boutique = boutiqueService.publiee();
        if (d.modeRemise() == CommandeEnLigne.ModeRemise.RETRAIT && !boutique.isRetraitActif()) {
            throw new BusinessException("Cette boutique ne propose pas le retrait sur place");
        }
        if (d.modeRemise() == CommandeEnLigne.ModeRemise.LIVRAISON) {
            if (!boutique.isLivraisonActive()) {
                throw new BusinessException("Cette boutique ne livre pas");
            }
            if (d.adresseLivraison() == null || d.adresseLivraison().isBlank()) {
                throw new BusinessException("Indiquez l'adresse de livraison");
            }
        }
        if (d.modePaiement() == CommandeEnLigne.ModePaiement.MOBILE_MONEY && !boutiqueService.mobileMoneyOuvert(boutique)) {
            throw new BusinessException("Le paiement mobile money n'est pas disponible dans cette boutique");
        }
        if (d.modePaiement() == CommandeEnLigne.ModePaiement.A_LA_REMISE && !boutique.isPaiementRemise()) {
            throw new BusinessException("Cette boutique demande le paiement en ligne");
        }

        Long entreprise = ContexteEntreprise.exiger();
        boolean enCaisse = moduleService.estActif(entreprise, ModuleApplicatif.POINT_DE_VENTE);
        boolean restaurant = enCaisse
                && "RESTAURATION".equals(moduleService.variante(entreprise, ModuleApplicatif.POINT_DE_VENTE));
        // Memes regles que la vitrine : avec le point de vente ou la gestion de parc, pas d'offre libre.
        boolean libresMasquees = enCaisse || moduleService.estActif(entreprise, ModuleApplicatif.GESTION_PARC);
        if (enCaisse && !sessionsTravail.uneCaisseOuverte()) {
            throw new BusinessException(MESSAGE_FERMETURE);
        }

        // Une meme offre demandee deux fois ne fait qu'une ligne.
        Map<Long, Integer> quantites = new LinkedHashMap<>();
        d.lignes().forEach(l -> quantites.merge(l.offreId(), l.quantite(), Integer::sum));

        CommandeEnLigne commande = new CommandeEnLigne();
        commande.setNumero(numerotation.prochainNumero("CMD"));
        commande.setJeton(ClientEnLigneService.nouveauJeton());
        commande.setDateCommande(Instant.now());
        commande.setClientNom(MarketplaceDto.nomComplet(d.nom(), d.prenoms()));
        commande.setClientNomFamille(d.nom().trim());
        commande.setClientPrenoms(MarketplaceDto.prenoms(d.prenoms()));
        commande.setClientTelephone(d.telephone().trim());
        commande.setClientEmail(vide(d.email()));
        commande.setModeRemise(d.modeRemise());
        commande.setAdresseLivraison(d.modeRemise() == CommandeEnLigne.ModeRemise.LIVRAISON
                ? d.adresseLivraison().trim() : null);
        commande.setModePaiement(d.modePaiement());
        commande.setNote(vide(d.note()));
        commande.setEnCaisse(enCaisse);

        BigDecimal sousTotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> demande : quantites.entrySet()) {
            Offre offre = offreRepository.findById(demande.getKey())
                    .filter(Offre::isPubliee)
                    .orElseThrow(() -> new BusinessException("Un produit du panier n'est plus en vente"));
            if (offre.getType() == Offre.Type.VEHICULE) {
                throw new BusinessException("Un vehicule se loue par une demande de location, pas par le panier");
            }
            if (libresMasquees && offre.getType() == Offre.Type.LIBRE) {
                throw new BusinessException(offre.getTitre() + " n'est plus en vente");
            }
            if (!offreService.disponible(offre)) {
                throw new BusinessException(offre.getTitre() + " n'est plus disponible");
            }
            int quantite = demande.getValue();
            BigDecimal prix = offreService.prix(offre);
            LigneCommandeEnLigne ligne = new LigneCommandeEnLigne();
            ligne.setOffreId(offre.getId());
            ligne.setLibelle(offre.getTitre());
            ligne.setPrixUnitaire(prix);
            ligne.setQuantite(quantite);
            ligne.setTotal(prix.multiply(BigDecimal.valueOf(quantite)));
            reserver(offre, ligne, commande.getNumero(), enCaisse);
            commande.ajouter(ligne);
            sousTotal = sousTotal.add(ligne.getTotal());
        }
        BigDecimal frais = d.modeRemise() == CommandeEnLigne.ModeRemise.LIVRAISON && boutique.getFraisLivraison() != null
                ? boutique.getFraisLivraison() : BigDecimal.ZERO;
        commande.setSousTotal(sousTotal);
        commande.setFraisLivraison(frais);
        commande.setTotal(sousTotal.add(frais));

        Client client = clientEnLigne.trouverOuCreer(d.nom(), d.prenoms(), d.telephone(), d.email());
        commande.setClientId(client.getId());

        String urlPaiement = null;
        if (d.modePaiement() == CommandeEnLigne.ModePaiement.MOBILE_MONEY) {
            urlPaiement = paiementService.initier(commande);
        }
        repository.save(commande);
        // Au restaurant, une commande a payer en ligne n'arrive en salle (et en cuisine) qu'une fois payee.
        if (restaurant && d.modePaiement() != CommandeEnLigne.ModePaiement.MOBILE_MONEY) {
            ouvrirEnSalle(commande);
        }
        return suivi(boutique, commande, urlPaiement);
    }

    /** Restaurant : la commande en ligne devient une commande de salle, a valider puis a preparer. */
    private void ouvrirEnSalle(CommandeEnLigne commande) {
        Commande salle = commandeSalleService.commandeEnLigne("En ligne · " + commande.getClientNom(),
                noteSalle(commande),
                commande.getLignes().stream().map(l -> new RestaurationDto.AjoutLigne(l.getArticleId(),
                        BigDecimal.valueOf(l.getQuantite()), null)).toList(),
                commande.getId());
        commande.setPdvCommandeId(salle.getId());
    }

    /** Paiement en ligne confirme : la commande d'un restaurant part alors en salle. */
    private void apresPaiement(CommandeEnLigne commande) {
        if (commande.getPdvCommandeId() != null || commande.getStatut().terminal()) {
            return;
        }
        Long entreprise = ContexteEntreprise.exiger();
        boolean restaurant = moduleService.estActif(entreprise, ModuleApplicatif.POINT_DE_VENTE)
                && "RESTAURATION".equals(moduleService.variante(entreprise, ModuleApplicatif.POINT_DE_VENTE));
        if (restaurant && commande.getStatutPaiement() == CommandeEnLigne.StatutPaiement.PAYEE) {
            ouvrirEnSalle(commande);
        }
    }

    /** Ce que la caisse et la cuisine doivent savoir de la commande en ligne. */
    private static String noteSalle(CommandeEnLigne c) {
        StringBuilder note = new StringBuilder("Commande en ligne ").append(c.getNumero())
                .append(" — ").append(c.getClientNom()).append(", ").append(c.getClientTelephone())
                .append(". ").append(c.getModeRemise().getLibelle());
        if (c.getAdresseLivraison() != null) {
            note.append(" : ").append(c.getAdresseLivraison());
        }
        note.append(". ").append(c.getModePaiement().getLibelle()).append('.');
        if (c.getNote() != null) {
            note.append(" Note du client : ").append(c.getNote());
        }
        return note.length() > 500 ? note.substring(0, 500) : note.toString();
    }

    public MarketplaceDto.Suivi suivi(String jeton) {
        CommandeEnLigne commande = parJeton(jeton);
        synchroniser(List.of(commande));
        String url = attendPaiement(commande) && commande.getReferencePaiement() != null
                && paiementService.simule() ? "/suivi/" + jeton + "?paiement=simulation" : null;
        return suivi(boutiqueService.courante(), commande, url);
    }

    /** Relance le paiement en ligne d'une commande qui l'attend encore. */
    public MarketplaceDto.Suivi relancerPaiement(String jeton) {
        limiteur.exiger("relance:" + jeton, 5, java.time.Duration.ofMinutes(15),
                "Trop de relances du paiement : patientez quelques minutes, un paiement deja fait sera pris en compte");
        CommandeEnLigne commande = parJeton(jeton);
        if (!attendPaiement(commande)) {
            throw new BusinessException("Cette commande n'attend pas de paiement en ligne");
        }
        String url = paiementService.initier(commande);
        repository.save(commande);
        return suivi(boutiqueService.courante(), commande, url);
    }

    /** Paiement de demonstration : le client confirme lui-meme (fournisseur « simulation » seulement). */
    public MarketplaceDto.Suivi confirmerPaiementSimule(String jeton) {
        if (!paiementService.simule()) {
            throw new BusinessException("Le paiement de demonstration n'est pas active");
        }
        CommandeEnLigne commande = parJeton(jeton);
        if (!attendPaiement(commande)) {
            throw new BusinessException("Cette commande n'attend pas de paiement en ligne");
        }
        paiementService.confirmer(commande, commande.getReferencePaiement());
        apresPaiement(commande);
        return suivi(boutiqueService.courante(), repository.save(commande), null);
    }

    // ---------------------------------------------------- Espace de gestion

    public Page<MarketplaceDto.CommandeReponse> lister(String filtre, Pageable pageable) {
        synchroniser(repository.findByStatutInOrderByDateCommandeDesc(
                EnumSet.of(Statut.NOUVELLE, Statut.CONFIRMEE, Statut.PRETE, Statut.EN_LIVRAISON),
                Pageable.unpaged()).getContent());
        Page<CommandeEnLigne> page = switch (filtre == null ? "" : filtre) {
            case "en-cours" -> repository.findByStatutInOrderByDateCommandeDesc(
                    EnumSet.of(Statut.NOUVELLE, Statut.CONFIRMEE, Statut.PRETE, Statut.EN_LIVRAISON), pageable);
            case "terminees" -> repository.findByStatutInOrderByDateCommandeDesc(
                    EnumSet.of(Statut.LIVREE, Statut.ANNULEE), pageable);
            default -> repository.findAllByOrderByDateCommandeDesc(pageable);
        };
        return page.map(MarketplaceDto.CommandeReponse::de);
    }

    public MarketplaceDto.CommandeReponse detail(Long id) {
        CommandeEnLigne commande = charger(id);
        synchroniser(List.of(commande));
        return MarketplaceDto.CommandeReponse.de(commande);
    }

    /** Commandes en ligne qui attendent la caisse boutique : a accepter, a preparer, a encaisser. */
    @Transactional(readOnly = true)
    public List<MarketplaceDto.CommandeReponse> aTraiterEnCaisse() {
        return repository.aTraiterEnCaisse().stream().map(MarketplaceDto.CommandeReponse::de).toList();
    }

    /**
     * Ticket encaisse a la caisse boutique pour une commande en ligne : elle est
     * remise et reglee. Une commande deja close fait echouer l'encaissement.
     */
    @EventListener
    public void venteEncaissee(VenteService.VenteEnLigneEncaissee evenement) {
        CommandeEnLigne commande = charger(evenement.commandeEnLigneId());
        if (commande.getStatut().terminal()) {
            throw new BusinessException("La commande en ligne " + commande.getNumero() + " est deja "
                    + commande.getStatut().getLibelle().toLowerCase());
        }
        // Le ticket reprend les articles et les prix de la commande : un ecart veut dire un panier modifie.
        if (evenement.totalBrut() != null && evenement.totalBrut().compareTo(commande.getSousTotal()) != 0) {
            throw new BusinessException("Le ticket (" + evenement.totalBrut().stripTrailingZeros().toPlainString()
                    + " F) ne correspond pas a la commande en ligne " + commande.getNumero() + " ("
                    + commande.getSousTotal().stripTrailingZeros().toPlainString()
                    + " F hors livraison) : encaissez exactement les articles commandes");
        }
        commande.setStatut(Statut.LIVREE);
        commande.setStatutPaiement(CommandeEnLigne.StatutPaiement.PAYEE);
        commande.setVenteId(evenement.venteId());
        repository.save(commande);
    }

    /**
     * Le ticket qui reglait la commande est annule : elle n'est plus ni remise
     * ni payee. Elle redevient prete, a encaisser de nouveau ou a annuler.
     */
    @EventListener
    public void venteAnnulee(VenteService.VenteAnnulee evenement) {
        repository.findFirstByVenteId(evenement.venteId()).ifPresent(commande -> {
            commande.setStatut(Statut.PRETE);
            // Payee en ligne : l'argent est deja recu, l'annulation du ticket ne le fait pas redemander.
            commande.setStatutPaiement(commande.isPayeeEnLigne()
                    ? CommandeEnLigne.StatutPaiement.PAYEE : CommandeEnLigne.StatutPaiement.EN_ATTENTE);
            commande.setVenteId(null);
        });
    }

    /**
     * Restaurant : l'etat d'une commande en ligne suit celui de la commande de
     * salle qui la porte (validation, preparation, encaissement, refus).
     */
    private void synchroniser(List<CommandeEnLigne> commandes) {
        List<CommandeEnLigne> liees = commandes.stream()
                .filter(c -> c.getPdvCommandeId() != null && !c.getStatut().terminal()).toList();
        if (liees.isEmpty()) {
            return;
        }
        Map<Long, Commande> salles = commandeSalleRepository
                .findAllById(liees.stream().map(CommandeEnLigne::getPdvCommandeId).toList()).stream()
                .collect(Collectors.toMap(Commande::getId, Function.identity()));
        for (CommandeEnLigne c : liees) {
            Commande salle = salles.get(c.getPdvCommandeId());
            if (salle == null) {
                continue;
            }
            Statut cible = statutDe(salle, c.getStatut());
            if (cible == Statut.LIVREE) {
                c.setStatutPaiement(CommandeEnLigne.StatutPaiement.PAYEE);
            }
            if (cible == Statut.ANNULEE && c.getMotifAnnulation() == null) {
                c.setMotifAnnulation("Commande refusee par l'etablissement");
            }
            if (cible != c.getStatut()) {
                c.setStatut(cible);
                repository.save(c);
            }
        }
    }

    private static Statut statutDe(Commande salle, Statut actuel) {
        if (salle.getStatut() == Commande.Statut.ANNULEE) {
            return Statut.ANNULEE;
        }
        if (salle.getStatut() == Commande.Statut.ENCAISSEE) {
            return Statut.LIVREE;
        }
        List<LigneCommande> lignes = salle.getLignes().stream()
                .filter(l -> l.getStatut() != LigneCommande.Statut.ANNULEE).toList();
        if (lignes.isEmpty()) {
            return actuel;
        }
        if (lignes.stream().anyMatch(l -> l.getStatut() == LigneCommande.Statut.PROPOSEE)) {
            return Statut.NOUVELLE;
        }
        boolean toutPret = lignes.stream().allMatch(l -> l.getStatut() == LigneCommande.Statut.PRETE
                || l.getStatut() == LigneCommande.Statut.SERVIE);
        return toutPret ? Statut.PRETE : Statut.CONFIRMEE;
    }

    public MarketplaceDto.CommandeReponse changerStatut(Long id, MarketplaceDto.ChangementStatut changement) {
        CommandeEnLigne commande = charger(id);
        Statut cible = changement.statut();
        if (commande.getPdvCommandeId() != null) {
            throw new BusinessException("Cette commande se traite a la caisse, comme une commande sur place : "
                    + "validez-la, servez-la et encaissez-la depuis l'ecran des commandes");
        }
        if (commande.isEnCaisse() && cible == Statut.LIVREE) {
            throw new BusinessException("Encaissez cette commande a la caisse pour la remettre au client");
        }
        if (!TRANSITIONS.get(commande.getStatut()).contains(cible)) {
            throw new BusinessException("Une commande « " + commande.getStatut().getLibelle()
                    + " » ne peut pas passer a « " + cible.getLibelle() + " »");
        }
        if (cible == Statut.EN_LIVRAISON && commande.getModeRemise() != CommandeEnLigne.ModeRemise.LIVRAISON) {
            throw new BusinessException("Cette commande est a retirer sur place, pas a livrer");
        }
        if (cible == Statut.ANNULEE) {
            liberer(commande);
            commande.setMotifAnnulation(vide(changement.motif()));
        }
        // Remise en main propre d'une commande payable a la remise : elle est reglee a ce moment-la.
        if (cible == Statut.LIVREE && commande.getModePaiement() == CommandeEnLigne.ModePaiement.A_LA_REMISE) {
            commande.setStatutPaiement(CommandeEnLigne.StatutPaiement.PAYEE);
        }
        commande.setStatut(cible);
        return MarketplaceDto.CommandeReponse.de(repository.save(commande));
    }

    /** Paiement constate a la main (mobile money recu hors plateforme, especes a l'avance...). */
    public MarketplaceDto.CommandeReponse marquerPayee(Long id) {
        CommandeEnLigne commande = charger(id);
        if (commande.getStatut() == Statut.ANNULEE) {
            throw new BusinessException("Cette commande est annulee");
        }
        commande.setStatutPaiement(CommandeEnLigne.StatutPaiement.PAYEE);
        return MarketplaceDto.CommandeReponse.de(repository.save(commande));
    }

    @Transactional(readOnly = true)
    public long compter(Statut statut) {
        return repository.countByStatut(statut);
    }

    @Transactional(readOnly = true)
    public long enCours() {
        return repository.countByStatut(Statut.CONFIRMEE) + repository.countByStatut(Statut.PRETE)
                + repository.countByStatut(Statut.EN_LIVRAISON);
    }

    // ------------------------------------------------------------- Interne

    private void reserver(Offre offre, LigneCommandeEnLigne ligne, String numero, boolean enCaisse) {
        int quantite = ligne.getQuantite();
        if (offre.getType() == Offre.Type.ARTICLE) {
            Article article = offreService.article(offre);
            if (article == null) {
                throw new BusinessException(offre.getTitre() + " n'est plus disponible");
            }
            ligne.setArticleId(article.getId());
            if (enCaisse) {
                // Le stock sortira avec le point de vente (envoi en cuisine ou encaissement) : on verifie seulement.
                BigDecimal disponible = lieux.disponibleEnLigne(article);
                if (article.isSuiviStock() && disponible.compareTo(BigDecimal.valueOf(quantite)) < 0) {
                    throw new BusinessException("Stock insuffisant pour " + offre.getTitre() + " : "
                            + disponible.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " disponible(s)");
                }
            } else if (article.isSuiviStock()) {
                stockService.appliquer(article, lieux.lieuEnLigne(article, BigDecimal.valueOf(quantite)),
                        TypeMouvementStock.VENTE, BigDecimal.valueOf(quantite),
                        article.getPrixAchat(), "Commande en ligne " + numero, null);
            }
        } else if (offre.getStock() != null) {
            // Deux clients sur la derniere unite : un seul passe.
            if (offreRepository.retirerStock(offre.getId(), quantite) == 0) {
                Integer reste = offreRepository.findById(offre.getId()).map(Offre::getStock).orElse(0);
                throw new BusinessException("Stock insuffisant pour " + offre.getTitre() + " : "
                        + Math.max(0, reste == null ? 0 : reste) + " disponible(s)");
            }
        }
    }

    /** Annulation : ce qui avait ete reserve retourne en stock (rien n'a ete reserve pour la caisse). */
    private void liberer(CommandeEnLigne commande) {
        if (commande.isEnCaisse()) {
            return;
        }
        for (LigneCommandeEnLigne ligne : commande.getLignes()) {
            if (ligne.getArticleId() != null) {
                articleRepository.findById(ligne.getArticleId())
                        .filter(Article::isSuiviStock)
                        .ifPresent(article -> stockService.appliquer(article,
                                stockService.lieuDuMouvement(article, "Commande en ligne " + commande.getNumero()),
                                TypeMouvementStock.RETOUR,
                                BigDecimal.valueOf(ligne.getQuantite()), article.getPrixAchat(),
                                "Annulation de la commande en ligne " + commande.getNumero(), null));
            } else if (ligne.getOffreId() != null) {
                offreRepository.findById(ligne.getOffreId())
                        .filter(o -> o.getType() == Offre.Type.LIBRE && o.getStock() != null)
                        .ifPresent(o -> o.setStock(o.getStock() + ligne.getQuantite()));
            }
        }
    }

    private boolean attendPaiement(CommandeEnLigne c) {
        return c.getModePaiement() == CommandeEnLigne.ModePaiement.MOBILE_MONEY
                && c.getStatutPaiement() != CommandeEnLigne.StatutPaiement.PAYEE
                && c.getStatut() != Statut.ANNULEE;
    }

    private MarketplaceDto.Suivi suivi(Boutique boutique, CommandeEnLigne commande, String urlPaiement) {
        return new MarketplaceDto.Suivi("COMMANDE", boutique.getNom(), boutique.getSlug(), boutique.getTelephone(),
                boutique.getWhatsapp(), boutique.getMessageConfirmation(), MarketplaceDto.CommandeReponse.de(commande),
                null, urlPaiement, paiementService.simule());
    }

    private CommandeEnLigne parJeton(String jeton) {
        return repository.findByJeton(jeton).orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
    }

    private CommandeEnLigne charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Commande", id));
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
