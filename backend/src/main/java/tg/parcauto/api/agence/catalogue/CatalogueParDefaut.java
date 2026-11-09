package tg.parcauto.api.agence.catalogue;

import tg.parcauto.api.agence.catalogue.TypeOperation.CompteVirtuel;
import tg.parcauto.api.agence.catalogue.TypeOperation.Nature;

import java.util.List;

/**
 * Services deja prets a l'activation du module : les operateurs courants du
 * Togo et leurs operations, avec les regles de mouvement du cahier des
 * charges. L'administrateur les modifie, les desactive ou en ajoute.
 */
final class CatalogueParDefaut {

    record Type(String code, String libelle, int cash, int virtuel, CompteVirtuel compte, Nature nature) {
    }

    record Service(String code, String nom, String categorie, List<String[]> reseaux, List<Type> types) {
    }

    private static final Type DEPOT = new Type("DEPOT", "Depot", 1, -1, CompteVirtuel.FLOAT, Nature.TRANSIT);
    private static final Type RETRAIT = new Type("RETRAIT", "Retrait", -1, 1, CompteVirtuel.FLOAT, Nature.TRANSIT);
    private static final Type ENVOI = new Type("ENVOI", "Envoi", 1, -1, CompteVirtuel.FLOAT, Nature.TRANSIT);
    private static final Type PAIEMENT = new Type("PAIEMENT", "Paiement de transfert", -1, 1, CompteVirtuel.FLOAT,
            Nature.TRANSIT);

    static final List<Service> SERVICES = List.of(
            new Service("MIXX", "Mixx by Yas", "Mobile Money", List.of(), List.of(DEPOT, RETRAIT)),
            new Service("MOOV", "Moov Money", "Mobile Money", List.of(), List.of(DEPOT, RETRAIT)),
            new Service("XPRESS_CASH", "Xpress Cash", "Transfert d'argent", List.of(), List.of(ENVOI, PAIEMENT)),
            new Service("KEAZ", "Keaz Spot", "Services financiers", List.of(), List.of(DEPOT, RETRAIT)),
            new Service("CORIS", "Coris Money", "Services financiers", List.of(), List.of(DEPOT, RETRAIT)),
            // Transferts geres chacun a part (leur propre float), comme Xpress ou Mixx.
            new Service("WESTERN_UNION", "Western Union", "Transfert d'argent", List.of(), List.of(ENVOI, PAIEMENT)),
            new Service("MONEYGRAM", "MoneyGram", "Transfert d'argent", List.of(), List.of(ENVOI, PAIEMENT)),
            new Service("RIA", "Ria", "Transfert d'argent", List.of(), List.of(ENVOI, PAIEMENT)),
            new Service("KORI", "KORI", "Transfert d'argent", List.of(), List.of(ENVOI, PAIEMENT)),
            new Service("CARTES_XPRESS", "Cartes prepayees Xpress", "Cartes prepayees", List.of(),
                    List.of(new Type("VENTE_CARTE", "Vente de carte", 1, 0, CompteVirtuel.AUCUN, Nature.VENTE_CARTE),
                            new Type("RECHARGE_CARTE", "Recharge de carte", 1, -1, CompteVirtuel.RECHARGE,
                                    Nature.TRANSIT))));

    private CatalogueParDefaut() {
    }
}
