package tg.parcauto.api.marketplace.paiement;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.marketplace.CommandeEnLigne;

import java.util.Optional;

/**
 * Point d'entree du paiement en ligne. Sans fournisseur configure, le mobile
 * money n'est simplement pas propose aux clients : seules restent les
 * commandes payees au retrait ou a la livraison.
 */
@Service
public class PaiementService {

    private final Optional<FournisseurPaiement> fournisseur;

    public PaiementService(ObjectProvider<FournisseurPaiement> fournisseurs) {
        this.fournisseur = Optional.ofNullable(fournisseurs.getIfAvailable());
    }

    public boolean disponible() {
        return fournisseur.isPresent();
    }

    public boolean simule() {
        return fournisseur.map(FournisseurPaiement::simule).orElse(false);
    }

    /** Lance le paiement d'une commande et note sa reference ; renvoie l'adresse de paiement. */
    public String initier(CommandeEnLigne commande) {
        FournisseurPaiement f = fournisseur.orElseThrow(() ->
                new BusinessException("Le paiement en ligne n'est pas disponible pour le moment"));
        FournisseurPaiement.Initiation initiation = f.initier(commande);
        // Relance : l'ancienne transaction peut encore aboutir, sa reference reste reconnue.
        String ancienne = commande.getReferencePaiement();
        if (ancienne != null && !ancienne.equals(initiation.reference())) {
            String liste = commande.getReferencesPrecedentes() == null ? ancienne
                    : commande.getReferencesPrecedentes() + "," + ancienne;
            commande.setReferencesPrecedentes(liste.length() > 1000 ? liste.substring(liste.length() - 1000) : liste);
        }
        commande.setReferencePaiement(initiation.reference());
        return initiation.urlPaiement();
    }

    /**
     * Paiement confirme par le fournisseur : la reference doit etre celle de la
     * transaction lancee pour cette commande.
     */
    public void confirmer(CommandeEnLigne commande, String reference) {
        boolean courante = reference != null && reference.equals(commande.getReferencePaiement());
        boolean precedente = reference != null && commande.getReferencesPrecedentes() != null
                && java.util.Arrays.asList(commande.getReferencesPrecedentes().split(",")).contains(reference);
        if (!courante && !precedente) {
            throw new BusinessException("Reference de paiement inconnue pour cette commande");
        }
        if (commande.isPayeeEnLigne()) {
            // Deja payee : une seconde transaction confirmee est un double paiement a rembourser.
            org.slf4j.LoggerFactory.getLogger(PaiementService.class).warn(
                    "Commande {} deja payee en ligne : la transaction {} est un double paiement a rembourser",
                    commande.getNumero(), reference);
            return;
        }
        commande.setStatutPaiement(CommandeEnLigne.StatutPaiement.PAYEE);
        commande.setPayeeEnLigne(true);
    }
}
