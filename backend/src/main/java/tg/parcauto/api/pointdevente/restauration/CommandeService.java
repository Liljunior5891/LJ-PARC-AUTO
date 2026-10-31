package tg.parcauto.api.pointdevente.restauration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.catalogue.PostePreparation;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.stock.TypeMouvementStock;
import tg.parcauto.api.pointdevente.vente.LigneVente;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteDto;
import tg.parcauto.api.pointdevente.vente.VenteService;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Commandes des tables, de l'ouverture a l'encaissement.
 * <p>
 * Le serveur saisit les articles en brouillon puis les envoie : chaque envoi
 * forme un bon pour la cuisine et un bon pour le bar, et sort les articles du
 * stock (le plat est engage, qu'il soit regle maintenant ou plus tard). Une
 * ligne envoyee ne s'efface plus : elle s'annule, par un gerant, et revient en
 * stock. L'addition se regle en une fois ou par convive ; la commande se
 * ferme quand plus rien ne reste a payer.
 * <p>
 * Le client peut aussi commander depuis le code QR de sa table : selon le
 * reglage de l'etablissement, ses articles attendent la validation d'un
 * serveur (statut propose) ou partent directement en preparation.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommandeService {

    private final CommandeRepository repository;
    private final LigneCommandeRepository ligneRepository;
    private final TableRestaurantRepository tableRepository;
    private final ArticleRepository articleRepository;
    private final StockService stockService;
    private final VenteService venteService;
    private final tg.parcauto.api.pointdevente.caisse.SessionTravailService sessionsTravail;
    private final NumerotationService numerotation;

    public RestaurationDto.CommandeDetail ouvrir(RestaurationDto.Ouverture o, UtilisateurPrincipal serveur) {
        Commande commande = new Commande();
        if (o.tableId() != null) {
            TableRestaurant table = tableRepository.findById(o.tableId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Table", o.tableId()));
            if (!table.isActif()) {
                throw new BusinessException("La table " + table.getNom() + " n'est plus au plan");
            }
            if (repository.existsByTableIdAndStatut(table.getId(), Commande.Statut.OUVERTE)) {
                throw new BusinessException("La table " + table.getNom() + " est deja occupee");
            }
            commande.setTable(table);
        } else if (vide(o.libelle()) == null) {
            throw new BusinessException("Choisissez une table ou donnez un nom a la commande");
        }
        commande.setLibelle(vide(o.libelle()));
        commande.setCouverts(o.couverts());
        commande.setNote(vide(o.note()));
        commande.setServeurId(serveur.getId());
        commande.setServeurNom(serveur.getNomComplet());
        // Rattachee a la session du poste : celle-ci ne fermera pas avant son reglement.
        sessionsTravail.courante(serveur, o.sessionId()).ifPresent(s -> commande.setSessionId(s.getId()));
        commande.setOuverteLe(Instant.now());
        commande.setNumero(numerotation.prochainNumero("CMD"));
        return RestaurationDto.CommandeDetail.de(repository.save(commande));
    }

    @Transactional(readOnly = true)
    public RestaurationDto.CommandeDetail detail(Long id) {
        return RestaurationDto.CommandeDetail.de(charger(id));
    }

    @Transactional(readOnly = true)
    public Page<RestaurationDto.CommandeResume> historique(Commande.Statut statut, Pageable pageable) {
        return repository.findByStatutOrderByOuverteLeDesc(statut, pageable).map(RestaurationDto.CommandeResume::de);
    }

    public RestaurationDto.CommandeDetail modifierInformations(Long id, RestaurationDto.Informations infos) {
        Commande commande = chargerOuverte(id);
        commande.setCouverts(infos.couverts());
        commande.setNote(vide(infos.note()));
        return RestaurationDto.CommandeDetail.de(commande);
    }

    // ------------------------------------------------------------ lignes

    /** Ajout en brouillon ; le meme article sans consigne s'additionne a la ligne en attente. */
    public RestaurationDto.CommandeDetail ajouter(Long id, RestaurationDto.AjoutLigne ajout) {
        Commande commande = chargerOuverte(id);
        Article article = articleRepository.findById(ajout.articleId())
                .orElseThrow(() -> ResourceNotFoundException.of("Article", ajout.articleId()));
        if (!article.isActif()) {
            throw new BusinessException("L'article " + article.getNom() + " n'est plus a la carte");
        }
        String note = vide(ajout.note());
        LigneCommande existante = commande.getLignes().stream()
                .filter(l -> l.getStatut() == LigneCommande.Statut.BROUILLON
                        && l.getOrigine() == LigneCommande.Origine.SERVEUR
                        && l.getArticle().getId().equals(article.getId())
                        && Objects.equals(l.getNote(), note))
                .findFirst()
                .orElse(null);
        if (existante != null) {
            existante.setQuantite(existante.getQuantite().add(ajout.quantite()));
        } else {
            LigneCommande ligne = new LigneCommande();
            ligne.setArticle(article);
            ligne.setLibelle(article.getNom());
            ligne.setPrixUnitaire(article.getPrixVente());
            ligne.setPrixAchat(article.getPrixAchat());
            ligne.setQuantite(ajout.quantite());
            ligne.setNote(note);
            ligne.setPoste(article.getCategorie() == null ? PostePreparation.AUCUN : article.getCategorie().getPoste());
            commande.ajouter(ligne);
        }
        return RestaurationDto.CommandeDetail.de(repository.saveAndFlush(commande));
    }

    public RestaurationDto.CommandeDetail modifierLigne(Long id, Long ligneId, RestaurationDto.ModificationLigne m) {
        Commande commande = chargerOuverte(id);
        LigneCommande ligne = ligne(commande, ligneId);
        if (!ligne.getStatut().enAttente()) {
            throw new BusinessException("Cette ligne est deja partie en preparation");
        }
        ligne.setQuantite(m.quantite());
        ligne.setNote(vide(m.note()));
        return RestaurationDto.CommandeDetail.de(commande);
    }

    /** Retrait d'une ligne encore en brouillon : rien n'est parti en cuisine. */
    public RestaurationDto.CommandeDetail retirerLigne(Long id, Long ligneId) {
        Commande commande = chargerOuverte(id);
        LigneCommande ligne = ligne(commande, ligneId);
        if (!ligne.getStatut().enAttente()) {
            throw new BusinessException("Cette ligne est deja partie en preparation : elle doit etre annulee");
        }
        commande.getLignes().remove(ligne);
        return RestaurationDto.CommandeDetail.de(commande);
    }

    /** Annulation d'une ligne envoyee, par un gerant : l'article revient en stock. */
    public RestaurationDto.CommandeDetail annulerLigne(Long id, Long ligneId, UtilisateurPrincipal auteur) {
        Commande commande = chargerOuverte(id);
        LigneCommande ligne = ligne(commande, ligneId);
        if (ligne.getVente() != null) {
            throw new BusinessException("Cette ligne est deja reglee : annulez la vente correspondante dans l'ecran Ventes");
        }
        if (ligne.getStatut() == LigneCommande.Statut.ANNULEE) {
            return RestaurationDto.CommandeDetail.de(commande);
        }
        if (ligne.getStatut().enAttente()) {
            return retirerLigne(id, ligneId);
        }
        annuler(ligne, commande, auteur);
        return RestaurationDto.CommandeDetail.de(commande);
    }

    // ---------------------------------------------------------- service

    /**
     * Envoie les lignes en brouillon : un nouveau bon par poste. Les articles
     * sans preparation sont prets aussitot. Le stock est sorti ici ; une
     * rupture bloque l'envoi entier.
     */
    public RestaurationDto.CommandeDetail envoyer(Long id) {
        Commande commande = chargerOuverte(id);
        List<LigneCommande> brouillons = commande.getLignes().stream()
                .filter(l -> l.getStatut() == LigneCommande.Statut.BROUILLON)
                .toList();
        if (brouillons.isEmpty()) {
            throw new BusinessException("Aucun article a envoyer");
        }
        envoyerLignes(commande, brouillons);
        return RestaurationDto.CommandeDetail.de(commande);
    }

    // ------------------------------------------------- commande du client

    /**
     * Le serveur accepte la demande du client : les articles proposes partent
     * en preparation, et il prend la table en charge si personne ne l'avait.
     */
    public RestaurationDto.CommandeDetail validerDemandes(Long id, UtilisateurPrincipal serveur) {
        Commande commande = chargerOuverte(id);
        List<LigneCommande> proposees = proposees(commande);
        if (proposees.isEmpty()) {
            throw new BusinessException("Aucune demande du client a valider");
        }
        envoyerLignes(commande, proposees);
        prendreEnCharge(commande, serveur);
        return RestaurationDto.CommandeDetail.de(commande);
    }

    /** Le serveur ecarte la demande du client (commande farfelue, plat indisponible). */
    public RestaurationDto.CommandeDetail refuserDemandes(Long id, UtilisateurPrincipal serveur) {
        Commande commande = chargerOuverte(id);
        commande.getLignes().removeIf(l -> l.getStatut() == LigneCommande.Statut.PROPOSEE);
        prendreEnCharge(commande, serveur);
        return RestaurationDto.CommandeDetail.de(commande);
    }

    /** Appel du client (serveur ou addition) pris en compte : il disparait du plan. */
    public RestaurationDto.CommandeDetail priseEnCompte(Long id, UtilisateurPrincipal serveur) {
        Commande commande = chargerOuverte(id);
        commande.setAppelLe(null);
        commande.setAppelMotif(null);
        prendreEnCharge(commande, serveur);
        return RestaurationDto.CommandeDetail.de(commande);
    }

    /**
     * Commande passee par le client depuis le code QR de sa table : la commande
     * en cours s'il y en a une, sinon une nouvelle. Avec validation, les
     * articles attendent un serveur ; sans, ils partent aussitot en preparation.
     */
    public Commande commandeClient(TableRestaurant table, List<RestaurationDto.AjoutLigne> demandes,
                                   boolean validation) {
        Commande commande = repository.ouvertes().stream()
                .filter(c -> c.getTable() != null && c.getTable().getId().equals(table.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Commande nouvelle = new Commande();
                    nouvelle.setTable(table);
                    nouvelle.setServeurNom(CLIENT_QR);
                    nouvelle.setOuverteLe(Instant.now());
                    nouvelle.setNumero(numerotation.prochainNumero("CMD"));
                    return nouvelle;
                });
        List<LigneCommande> nouvelles = new ArrayList<>();
        for (RestaurationDto.AjoutLigne demande : demandes) {
            Article article = articleRepository.findById(demande.articleId())
                    .filter(Article::isActif)
                    .orElseThrow(() -> new BusinessException("Un article de votre commande n'est plus a la carte"));
            LigneCommande ligne = new LigneCommande();
            ligne.setArticle(article);
            ligne.setLibelle(article.getNom());
            ligne.setPrixUnitaire(article.getPrixVente());
            ligne.setPrixAchat(article.getPrixAchat());
            ligne.setQuantite(demande.quantite());
            ligne.setNote(vide(demande.note()));
            ligne.setPoste(article.getCategorie() == null ? PostePreparation.AUCUN : article.getCategorie().getPoste());
            ligne.setOrigine(LigneCommande.Origine.CLIENT);
            ligne.setStatut(LigneCommande.Statut.PROPOSEE);
            commande.ajouter(ligne);
            nouvelles.add(ligne);
        }
        if (!validation) {
            envoyerLignes(commande, nouvelles);
        }
        return repository.saveAndFlush(commande);
    }

    /** Appel du client depuis sa table ; une commande vide s'ouvre au besoin pour porter l'appel. */
    public Commande appelClient(TableRestaurant table, Commande.Appel motif) {
        Commande commande = repository.ouvertes().stream()
                .filter(c -> c.getTable() != null && c.getTable().getId().equals(table.getId()))
                .findFirst()
                .orElseGet(() -> {
                    if (motif == Commande.Appel.ADDITION) {
                        throw new BusinessException("Aucune commande en cours sur cette table");
                    }
                    Commande nouvelle = new Commande();
                    nouvelle.setTable(table);
                    nouvelle.setServeurNom(CLIENT_QR);
                    nouvelle.setOuverteLe(Instant.now());
                    nouvelle.setNumero(numerotation.prochainNumero("CMD"));
                    return nouvelle;
                });
        commande.setAppelLe(Instant.now());
        commande.setAppelMotif(motif);
        return repository.saveAndFlush(commande);
    }

    static final String CLIENT_QR = "Client (code QR)";

    private static List<LigneCommande> proposees(Commande commande) {
        return commande.getLignes().stream()
                .filter(l -> l.getStatut() == LigneCommande.Statut.PROPOSEE)
                .toList();
    }

    private static void prendreEnCharge(Commande commande, UtilisateurPrincipal serveur) {
        if (commande.getServeurId() == null) {
            commande.setServeurId(serveur.getId());
            commande.setServeurNom(serveur.getNomComplet());
        }
    }

    /**
     * Envoi en preparation : un nouveau bon par poste, les articles sans
     * preparation prets aussitot, le stock sorti. Une rupture bloque l'envoi.
     */
    private void envoyerLignes(Commande commande, List<LigneCommande> lignes) {
        Instant maintenant = Instant.now();
        int envoi = commande.getEnvois() + 1;
        commande.setEnvois(envoi);
        for (LigneCommande ligne : lignes) {
            ligne.setEnvoi(envoi);
            ligne.setEnvoyeeLe(maintenant);
            if (ligne.getPoste() == PostePreparation.AUCUN) {
                ligne.setStatut(LigneCommande.Statut.PRETE);
                ligne.setPreteLe(maintenant);
            } else {
                ligne.setStatut(LigneCommande.Statut.A_PREPARER);
            }
            if (ligne.getArticle().isSuiviStock()) {
                stockService.appliquer(ligne.getArticle(), TypeMouvementStock.VENTE, ligne.getQuantite(),
                        null, "Commande " + commande.getNumero() + " - " + commande.repere(), null);
            }
        }
    }

    /** Le serveur a apporte les plats prets ; sans selection, tout ce qui est pret. */
    public RestaurationDto.CommandeDetail servir(Long id, List<Long> lignes) {
        Commande commande = chargerOuverte(id);
        Instant maintenant = Instant.now();
        for (LigneCommande ligne : commande.getLignes()) {
            boolean choisie = lignes == null || lignes.isEmpty() || lignes.contains(ligne.getId());
            if (choisie && ligne.getStatut() == LigneCommande.Statut.PRETE) {
                ligne.setStatut(LigneCommande.Statut.SERVIE);
                ligne.setServieLe(maintenant);
            }
        }
        return RestaurationDto.CommandeDetail.de(commande);
    }

    public RestaurationDto.CommandeDetail transferer(Long id, Long tableId) {
        Commande commande = chargerOuverte(id);
        TableRestaurant table = tableRepository.findById(tableId)
                .orElseThrow(() -> ResourceNotFoundException.of("Table", tableId));
        if (!table.isActif()) {
            throw new BusinessException("La table " + table.getNom() + " n'est plus au plan");
        }
        if (commande.getTable() != null && commande.getTable().getId().equals(tableId)) {
            return RestaurationDto.CommandeDetail.de(commande);
        }
        if (repository.existsByTableIdAndStatut(tableId, Commande.Statut.OUVERTE)) {
            throw new BusinessException("La table " + table.getNom() + " est deja occupee");
        }
        commande.setTable(table);
        return RestaurationDto.CommandeDetail.de(commande);
    }

    /** Impression de l'addition : la table passe en attente de paiement sur le plan. */
    public RestaurationDto.CommandeDetail addition(Long id) {
        Commande commande = chargerOuverte(id);
        if (commande.getLignes().stream().noneMatch(LigneCommande::aPayer)) {
            throw new BusinessException("Rien a regler sur cette commande");
        }
        commande.setAdditionLe(Instant.now());
        return RestaurationDto.CommandeDetail.de(commande);
    }

    /**
     * Annule toute la commande. Sans ligne envoyee, chacun peut le faire (la
     * table avait ete ouverte par erreur) ; sinon il faut un gerant, et rien ne
     * doit avoir ete regle.
     */
    public RestaurationDto.CommandeDetail annulerCommande(Long id, UtilisateurPrincipal auteur) {
        Commande commande = chargerOuverte(id);
        if (commande.getLignes().stream().anyMatch(l -> l.getVente() != null)) {
            throw new BusinessException("Une partie de l'addition est deja reglee : la commande ne peut plus etre annulee");
        }
        boolean envoyees = commande.getLignes().stream().anyMatch(l -> l.getStatut().facturable());
        if (envoyees && !gestionnaire(auteur)) {
            throw new BusinessException("Des articles sont deja partis en preparation : seul un gerant peut annuler");
        }
        commande.getLignes().removeIf(l -> l.getStatut().enAttente());
        for (LigneCommande ligne : commande.getLignes()) {
            if (ligne.getStatut() != LigneCommande.Statut.ANNULEE) {
                annuler(ligne, commande, auteur);
            }
        }
        commande.setStatut(Commande.Statut.ANNULEE);
        commande.setFermeeLe(Instant.now());
        return RestaurationDto.CommandeDetail.de(commande);
    }

    // ------------------------------------------------------ encaissement

    public RestaurationDto.ResultatEncaissement encaisser(Long id, RestaurationDto.Encaissement demande,
                                                          UtilisateurPrincipal caissier) {
        Commande commande = chargerOuverte(id);
        // Pas de reglement tant que tout n'a pas ete prepare et servi.
        List<LigneCommande> nonServies = commande.getLignes().stream()
                .filter(l -> l.getStatut().facturable() && l.getStatut() != LigneCommande.Statut.SERVIE && l.getVente() == null)
                .toList();
        if (!nonServies.isEmpty()) {
            throw new BusinessException(nonServies.size() + " article(s) pas encore servi(s) ("
                    + nonServies.stream().map(LigneCommande::getLibelle).distinct().limit(4)
                            .collect(java.util.stream.Collectors.joining(", "))
                    + ") : la commande doit etre preparee et servie avant l'encaissement");
        }
        List<LigneCommande> reglees = new ArrayList<>();
        if (demande.lignes() == null || demande.lignes().isEmpty()) {
            if (commande.getLignes().stream().anyMatch(l -> l.getStatut() == LigneCommande.Statut.BROUILLON)) {
                throw new BusinessException("Des articles n'ont pas ete envoyes : envoyez-les ou retirez-les avant l'addition");
            }
            if (!proposees(commande).isEmpty()) {
                throw new BusinessException("Le client a commande depuis sa table : validez ou refusez sa demande avant l'addition");
            }
            commande.getLignes().stream().filter(LigneCommande::aPayer).forEach(reglees::add);
        } else {
            for (RestaurationDto.LignePaiement paiement : demande.lignes()) {
                LigneCommande ligne = ligne(commande, paiement.ligneId());
                if (!ligne.aPayer() || reglees.contains(ligne)) {
                    throw new BusinessException("La ligne " + ligne.getLibelle() + " n'est pas a regler");
                }
                int comparaison = paiement.quantite().compareTo(ligne.getQuantite());
                if (comparaison > 0) {
                    throw new BusinessException("Seulement " + ligne.getQuantite().stripTrailingZeros().toPlainString()
                            + " " + ligne.getLibelle() + " sur l'addition");
                }
                reglees.add(comparaison < 0 ? scinder(commande, ligne, paiement.quantite()) : ligne);
            }
        }
        if (reglees.isEmpty()) {
            throw new BusinessException("Rien a regler sur cette commande");
        }

        List<LigneVente> lignesTicket = reglees.stream().map(CommandeService::versTicket).toList();
        // Au comptoir, le nom de la commande est celui du client, pas une table.
        VenteDto.Reglement reglement = demande.reglement();
        if (commande.getTable() == null && (reglement.clientNom() == null || reglement.clientNom().isBlank())) {
            reglement = new VenteDto.Reglement(reglement.remise(), reglement.modePaiement(), reglement.montantRecu(),
                    reglement.referencePaiement(), commande.repere(), reglement.sessionId());
        }
        Vente vente = venteService.encaisserAddition(lignesTicket, reglement, caissier,
                commande.getId(), commande.getTable() == null ? null : commande.getTable().getNom());
        reglees.forEach(l -> l.setVente(vente));
        if (commande.getSessionId() == null) {
            commande.setSessionId(vente.getSession().getId());
        }

        boolean resteAPayer = commande.getLignes().stream()
                .anyMatch(l -> l.aPayer() || l.getStatut().enAttente());
        if (!resteAPayer) {
            commande.setStatut(Commande.Statut.ENCAISSEE);
            commande.setFermeeLe(Instant.now());
            commande.setAppelLe(null);
            commande.setAppelMotif(null);
        }
        repository.flush();
        return new RestaurationDto.ResultatEncaissement(VenteDto.Ticket.de(vente),
                RestaurationDto.CommandeDetail.de(commande));
    }

    /**
     * Ticket d'addition annule : ses lignes redeviennent a payer. Une commande
     * deja close se rouvre, sans sa table si celle-ci a ete reprise depuis.
     */
    @EventListener
    public void additionAnnulee(VenteService.AdditionAnnulee evenement) {
        List<LigneCommande> lignes = ligneRepository.findByVenteId(evenement.venteId());
        lignes.forEach(l -> l.setVente(null));
        Commande commande = repository.findById(evenement.commandeId()).orElse(null);
        if (commande == null || commande.getStatut() != Commande.Statut.ENCAISSEE) {
            return;
        }
        if (commande.getTable() != null
                && repository.existsByTableIdAndStatut(commande.getTable().getId(), Commande.Statut.OUVERTE)) {
            commande.setLibelle(commande.getTable().getNom() + " (reouverte)");
            commande.setTable(null);
        }
        commande.setStatut(Commande.Statut.OUVERTE);
        commande.setFermeeLe(null);
    }

    // ----------------------------------------------------------- interne

    /** Detache une quantite d'une ligne pour la regler a part ; le reste attend un autre convive. */
    private static LigneCommande scinder(Commande commande, LigneCommande ligne, BigDecimal quantite) {
        ligne.setQuantite(ligne.getQuantite().subtract(quantite));
        LigneCommande part = new LigneCommande();
        part.setArticle(ligne.getArticle());
        part.setLibelle(ligne.getLibelle());
        part.setPrixUnitaire(ligne.getPrixUnitaire());
        part.setPrixAchat(ligne.getPrixAchat());
        part.setQuantite(quantite);
        part.setNote(ligne.getNote());
        part.setPoste(ligne.getPoste());
        part.setStatut(ligne.getStatut());
        part.setEnvoi(ligne.getEnvoi());
        part.setEnvoyeeLe(ligne.getEnvoyeeLe());
        part.setPreteLe(ligne.getPreteLe());
        part.setServieLe(ligne.getServieLe());
        commande.ajouter(part);
        return part;
    }

    private static LigneVente versTicket(LigneCommande l) {
        LigneVente ligne = new LigneVente();
        ligne.setArticle(l.getArticle());
        ligne.setLibelle(l.getNote() == null ? l.getLibelle() : l.getLibelle() + " (" + l.getNote() + ")");
        ligne.setPrixUnitaire(l.getPrixUnitaire());
        ligne.setPrixAchat(l.getPrixAchat());
        ligne.setQuantite(l.getQuantite());
        ligne.setRemise(BigDecimal.ZERO.setScale(2));
        ligne.setTotal(l.total().setScale(2, RoundingMode.HALF_UP));
        return ligne;
    }

    private void annuler(LigneCommande ligne, Commande commande, UtilisateurPrincipal auteur) {
        ligne.setStatut(LigneCommande.Statut.ANNULEE);
        ligne.setAnnuleeLe(Instant.now());
        ligne.setAnnuleePar(auteur.getNomComplet());
        if (ligne.getArticle().isSuiviStock()) {
            stockService.appliquer(ligne.getArticle(), TypeMouvementStock.RETOUR, ligne.getQuantite(),
                    null, "Annulation sur la commande " + commande.getNumero(), null);
        }
    }

    private static boolean gestionnaire(UtilisateurPrincipal p) {
        return p.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals(Role.ADMIN.authority()) || a.getAuthority().equals(Role.GERANT.authority()));
    }

    private static LigneCommande ligne(Commande commande, Long ligneId) {
        return commande.getLignes().stream()
                .filter(l -> l.getId().equals(ligneId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Ligne de commande", ligneId));
    }

    private Commande charger(Long id) {
        return repository.detail(id).orElseThrow(() -> ResourceNotFoundException.of("Commande", id));
    }

    private Commande chargerOuverte(Long id) {
        Commande commande = charger(id);
        if (!commande.ouverte()) {
            throw new BusinessException("La commande " + commande.getNumero() + " est close");
        }
        return commande;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
