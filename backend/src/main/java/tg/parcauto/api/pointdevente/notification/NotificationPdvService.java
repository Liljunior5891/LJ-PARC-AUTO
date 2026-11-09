package tg.parcauto.api.pointdevente.notification;

import tg.parcauto.api.marketplace.CommandeEnLigne;
import tg.parcauto.api.marketplace.CommandeEnLigneRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.pointdevente.caisse.SessionTravailService;
import tg.parcauto.api.pointdevente.catalogue.PostePreparation;
import tg.parcauto.api.pointdevente.lieu.BonRetrait;
import tg.parcauto.api.pointdevente.lieu.BonRetraitRepository;
import tg.parcauto.api.pointdevente.lieu.LieuStockService;
import tg.parcauto.api.pointdevente.lieu.RemiseHorsLigne;
import tg.parcauto.api.pointdevente.lieu.RemiseHorsLigneRepository;
import tg.parcauto.api.pointdevente.lieu.RetraitService;
import tg.parcauto.api.pointdevente.lieu.Transfert;
import tg.parcauto.api.pointdevente.lieu.TransfertRepository;
import tg.parcauto.api.pointdevente.personnel.PersonnelService;
import tg.parcauto.api.pointdevente.restauration.Commande;
import tg.parcauto.api.pointdevente.restauration.CommandeRepository;
import tg.parcauto.api.pointdevente.restauration.EcranPreparationRepository;
import tg.parcauto.api.pointdevente.restauration.EcranPreparationService;
import tg.parcauto.api.pointdevente.restauration.LigneCommande;
import tg.parcauto.api.pointdevente.restauration.PreparationService;
import tg.parcauto.api.pointdevente.restauration.RestaurationDto;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Ce qui attend chaque poste, a l'instant : le navigateur interroge
 * regulierement et signale, par un son et un message, chaque evenement qu'il
 * n'a pas encore vu. Un evenement porte une cle stable : elle change quand il
 * se renouvelle (nouvel article propose, nouvel appel, nouveau plat pret).
 * <ul>
 *   <li>caisse (gerant, caissier) : commandes transmises par les serveurs ou passees depuis le code QR
 *   a valider, additions demandees ;</li>
 *   <li>serveur : appels et plats prets de ses tables (ceux sans serveur vont a tous) ;</li>
 *   <li>preparateur : nouveaux bons sur ses ecrans de preparation ;</li>
 *   <li>personnel de caisse : ouverture d'une session sur sa caisse.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPdvService {

    public enum Type { VALIDATION, APPEL, ADDITION, PRET, BON, CAISSE, RETRAIT, TRANSFERT }

    public record Evenement(String cle, Type type, String titre, String message, String lien, Instant date) {
    }

    private final CommandeRepository commandeRepository;
    private final PreparationService preparationService;
    private final EcranPreparationService ecranService;
    private final EcranPreparationRepository ecranRepository;
    private final SessionTravailService sessionsTravail;
    private final CommandeEnLigneRepository commandesEnLigne;
    private final LieuStockService lieux;
    private final BonRetraitRepository bonsRetrait;
    private final TransfertRepository transferts;
    private final RemiseHorsLigneRepository remises;
    private final tg.parcauto.api.pointdevente.depense.DepensePdvRepository depenses;
    private final tg.parcauto.api.pointdevente.caisse.SortieAttenteRepository sortiesAttente;

    public List<Evenement> pour(UtilisateurPrincipal u) {
        List<Evenement> evenements = new ArrayList<>();
        boolean caisse = aRole(u, Role.ADMIN, Role.GERANT, Role.CAISSIER);
        boolean serveur = aRole(u, Role.SERVEUR);
        if (caisse || serveur) {
            for (Commande c : commandeRepository.ouvertes()) {
                salle(c, u, caisse, serveur, evenements);
            }
            // Caisse boutique : les commandes en ligne arrivent a la caisse, sans commande de salle.
            if (caisse) {
                commandesEnLigne.aTraiterEnCaisse().stream()
                        .filter(c -> c.getStatut() == CommandeEnLigne.Statut.NOUVELLE)
                        .forEach(c -> evenements.add(new Evenement("en-ligne-" + c.getId(), Type.VALIDATION,
                                "Nouvelle commande en ligne",
                                c.getNumero() + " · " + c.getClientNom() + " · " + c.getModeRemise().getLibelle(),
                                "/pdv/caisse?enLigne=" + c.getId(), c.getDateCommande())));
            }
            if (!PersonnelService.gestionnaire(u)) {
                sessionsTravail.ouvertesPour(u).forEach(s -> evenements.add(new Evenement("caisse-" + s.getId(),
                        Type.CAISSE, "Caisse ouverte", s.getCaisse().getNom() + " : vous pouvez encaisser",
                        null, s.getOuverteLe())));
            }
        }
        if (aRole(u, Role.PREPARATEUR)) {
            preparation(u, evenements);
        }
        depensesDeCaisse(u, evenements);
        if (aRole(u, Role.ADMIN, Role.GERANT, Role.MAGASINIER) && lieux.actif()) {
            magasins(u, evenements);
        }
        evenements.sort(Comparator.comparing(Evenement::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return evenements;
    }

    /**
     * Validation des depenses de caisse : le gerant voit ce qui attend son visa,
     * l'administrateur tout ce qui attend ; le demandeur apprend la decision du jour.
     */
    private void depensesDeCaisse(UtilisateurPrincipal u, List<Evenement> sortie) {
        boolean admin = aRole(u, Role.ADMIN);
        if (admin || aRole(u, Role.GERANT)) {
            var statutsDepense = admin
                    ? List.of(tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.ATTENTE_GERANT,
                              tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.ATTENTE_ADMIN)
                    : List.of(tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.ATTENTE_GERANT);
            depenses.enAttente(statutsDepense).forEach(d -> sortie.add(new Evenement(
                    "depense-attente-" + d.getId() + "-" + d.getStatut(), Type.VALIDATION, "Depense de caisse a valider",
                    d.getNumero() + " · " + entier(d.getMontant()) + " F · " + d.getLibelle() + " · " + d.getAuteurNom(),
                    "/pdv/validations-caisse", d.getCreeLe())));
            var statutsSortie = admin
                    ? List.of(tg.parcauto.api.pointdevente.caisse.SortieAttente.Statut.ATTENTE_GERANT,
                              tg.parcauto.api.pointdevente.caisse.SortieAttente.Statut.ATTENTE_ADMIN)
                    : List.of(tg.parcauto.api.pointdevente.caisse.SortieAttente.Statut.ATTENTE_GERANT);
            sortiesAttente.parStatuts(statutsSortie).forEach(s -> sortie.add(new Evenement(
                    "sortie-attente-" + s.getId() + "-" + s.getStatut(), Type.VALIDATION, "Sortie de caisse a valider",
                    s.getNumero() + " · " + entier(s.getMontant()) + " F · " + s.getSession().getCaisse().getNom()
                            + " · " + s.getAuteurNom(),
                    "/pdv/validations-caisse", s.getDemandeeLe())));
        }
        for (var s : sortiesAttente.trancheesPour(u.getId(), Instant.now().minus(1, ChronoUnit.DAYS))) {
            boolean refusee = s.getStatut() == tg.parcauto.api.pointdevente.caisse.SortieAttente.Statut.REFUSEE;
            sortie.add(new Evenement("sortie-" + s.getStatut() + "-" + s.getId(), Type.CAISSE,
                    refusee ? "Sortie de caisse refusee" : "Sortie de caisse approuvee",
                    s.getNumero() + " · " + entier(s.getMontant()) + " F"
                            + (refusee ? " · " + s.getMotifRefus() : " · les especes peuvent sortir"),
                    null, refusee ? s.getRefuseeLe() : s.getApprouveeLe()));
        }
    }

    private static String entier(BigDecimal montant) {
        return montant.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Magasins et entrepots : le magasinier est prevenu de ce qui arrive sur ses lieux ;
     * le gerant, de ce qui attend sa decision et des bons non retires depuis plus de 30 jours.
     */
    private void magasins(UtilisateurPrincipal u, List<Evenement> sortie) {
        if (aRole(u, Role.ADMIN, Role.GERANT)) {
            Instant avant = Instant.now().minus(RetraitService.JOURS_ALERTE, ChronoUnit.DAYS);
            for (BonRetrait b : bonsRetrait.anciens(avant)) {
                sortie.add(new Evenement("retrait-ancien-" + b.getId(), Type.RETRAIT, "Bon non retire depuis plus de 30 jours",
                        b.getNumero() + " · " + b.getLieu().getNom(), "/pdv/retraits?q=" + b.getNumero(), b.getDateEmission()));
            }
            for (Transfert t : transferts.parStatuts(List.of(Transfert.Statut.DEMANDE, Transfert.Statut.ECART))) {
                boolean ecart = t.getStatut() == Transfert.Statut.ECART;
                sortie.add(new Evenement("transfert-" + t.getStatut() + "-" + t.getId(), Type.TRANSFERT,
                        ecart ? "Ecart sur un transfert" : t.getSource() == null ? "Demande de transfert a traiter" : "Transfert a valider",
                        t.getNumero() + " · " + (t.getSource() == null ? "depart a choisir" : t.getSource().getNom())
                                + " vers " + t.getDestination().getNom(),
                        "/pdv/transferts", ecart ? t.getRecuLe() : t.getDemandeLe()));
            }
            for (RemiseHorsLigne r : remises.findByStatutOrderByFaiteLeDesc(RemiseHorsLigne.Statut.CONFLIT)) {
                sortie.add(new Evenement("conflit-" + r.getId(), Type.RETRAIT, "Remise sans connexion a verifier",
                        r.getAuteur() + " · " + r.getDetail(), "/pdv/retraits", r.getFaiteLe()));
            }
        }
        if (aRole(u, Role.MAGASINIER)) {
            List<Long> miens = lieux.idsAccessibles(u);
            if (miens.isEmpty()) {
                return;
            }
            for (BonRetrait b : bonsRetrait.aServir(miens)) {
                sortie.add(new Evenement("retrait-" + b.getId(), Type.RETRAIT, "Bon a servir",
                        b.getNumero() + " · " + b.getLieu().getNom(), "/pdv/retraits?q=" + b.getNumero(), b.getDateEmission()));
            }
            for (Transfert t : transferts.parStatuts(List.of(Transfert.Statut.VALIDE, Transfert.Statut.EXPEDIE))) {
                boolean depart = t.getStatut() == Transfert.Statut.VALIDE;
                if (miens.contains((depart ? t.getSource() : t.getDestination()).getId())) {
                    sortie.add(new Evenement("transfert-" + t.getStatut() + "-" + t.getId(), Type.TRANSFERT,
                            depart ? "Transfert a expedier" : "Transfert a receptionner",
                            t.getNumero() + " · " + t.getSource().getNom() + " vers " + t.getDestination().getNom(),
                            "/pdv/transferts", depart ? t.getValideLe() : t.getExpedieLe()));
                }
            }
        }
    }

    /** Evenements d'une commande de table pour l'utilisateur. */
    private static void salle(Commande c, UtilisateurPrincipal u, boolean caisse, boolean serveur, List<Evenement> sortie) {
        // Le serveur de la table ; une commande sans serveur (passee par le client) concerne tous les serveurs.
        boolean saTable = Objects.equals(c.getServeurId(), u.getId()) || (serveur && c.getServeurId() == null);
        boolean siennePret = Objects.equals(c.getServeurId(), u.getId());
        String lien = "/pdv/commandes/" + c.getId();

        List<LigneCommande> proposees = lignes(c, LigneCommande.Statut.PROPOSEE);
        // Seule la caisse envoie en preparation : elle seule est appelee a valider.
        if (!proposees.isEmpty() && caisse) {
            long derniere = proposees.stream().mapToLong(LigneCommande::getId).max().orElse(0);
            boolean duServeur = proposees.stream().anyMatch(l -> l.getOrigine() == LigneCommande.Origine.SERVEUR);
            String titre = c.isEnLigne() ? "Commande en ligne a valider"
                    : duServeur ? "Commande de " + c.getServeurNom() + " a valider" : "Commande client a valider";
            String origine = c.isEnLigne() ? "commande(s) dans la boutique en ligne"
                    : duServeur ? "transmis par le serveur" : "commande(s) depuis le code QR";
            sortie.add(new Evenement("validation-" + c.getId() + "-" + derniere, Type.VALIDATION, titre,
                    c.repere() + " : " + proposees.size() + " article(s) " + origine,
                    lien, c.getAppelLe() != null ? c.getAppelLe() : c.getOuverteLe()));
        }

        if (c.getAppelMotif() != null && c.getAppelLe() != null) {
            boolean addition = c.getAppelMotif() == Commande.Appel.ADDITION;
            if (saTable || (addition && caisse)) {
                sortie.add(new Evenement("appel-" + c.getId() + "-" + c.getAppelLe().toEpochMilli(),
                        addition ? Type.ADDITION : Type.APPEL,
                        addition ? "Addition demandee" : "Un client appelle",
                        c.repere() + (addition ? " demande l'addition" : " demande un serveur"), lien, c.getAppelLe()));
            }
        }

        List<LigneCommande> pretes = lignes(c, LigneCommande.Statut.PRETE);
        // Plat pret : le serveur de la table ; sans serveur, la caisse s'en charge.
        if (!pretes.isEmpty() && (siennePret || (c.getServeurId() == null && caisse))) {
            Instant derniere = pretes.stream().map(LigneCommande::getPreteLe).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(c.getOuverteLe());
            sortie.add(new Evenement("pret-" + c.getId() + "-" + derniere.toEpochMilli(), Type.PRET,
                    "Pret a servir", c.repere() + " : " + resume(pretes), lien, derniere));
        }
    }

    /** Nouveaux bons (rien encore commence) des ecrans du preparateur, ou des postes sans ecran. */
    private void preparation(UtilisateurPrincipal u, List<Evenement> sortie) {
        if (ecranRepository.count() == 0) {
            for (PostePreparation poste : PostePreparation.values()) {
                nouveaux(preparationService.bons(poste), poste.getLibelle(), "/pdv/cuisine", sortie);
            }
            return;
        }
        for (EcranPreparationService.Reponse ecran : ecranService.accessibles(u)) {
            nouveaux(preparationService.bonsEcran(ecran.id(), u), ecran.nom(), "/pdv/cuisine?ecran=" + ecran.id(), sortie);
        }
    }

    private static void nouveaux(List<RestaurationDto.Bon> bons, String ecran, String lien, List<Evenement> sortie) {
        for (RestaurationDto.Bon bon : bons) {
            if (bon.statut() == LigneCommande.Statut.A_PREPARER) {
                sortie.add(new Evenement("bon-" + bon.commandeId() + "-" + bon.envoi() + "-" + ecran, Type.BON,
                        "Nouveau bon · " + ecran,
                        bon.repere() + " : " + bon.lignes().stream()
                                .map(l -> quantite(l.quantite()) + " " + l.libelle()).collect(Collectors.joining(", ")),
                        lien, bon.envoyeLe()));
            }
        }
    }

    private static List<LigneCommande> lignes(Commande c, LigneCommande.Statut statut) {
        return c.getLignes().stream().filter(l -> l.getStatut() == statut).toList();
    }

    private static String resume(List<LigneCommande> lignes) {
        String texte = lignes.stream().limit(4).map(l -> quantite(l.getQuantite()) + " " + l.getLibelle())
                .collect(Collectors.joining(", "));
        return lignes.size() > 4 ? texte + "..." : texte;
    }

    private static String quantite(BigDecimal quantite) {
        return quantite.stripTrailingZeros().toPlainString() + " ×";
    }

    private static boolean aRole(UtilisateurPrincipal u, Role... roles) {
        for (Role role : roles) {
            if (u.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role.authority()))) {
                return true;
            }
        }
        return false;
    }
}
