package tg.parcauto.api.agence.tresorerie;

/**
 * Types de compte d'une agence et la famille dans laquelle chacun compte : le
 * cash (caisse generale, caisses des caissiers et petites caisses de depenses), le
 * virtuel (float de chaque service, compte de recharge), la banque, les
 * commissions et les fonds d'exploitation.
 */
public enum TypeCompte {

    CAISSE_GENERALE("Caisse generale", Famille.CASH, false),
    CAISSE_CAISSIER("Caisse de caissier", Famille.CASH, false),
    /** Especes mises par l'administrateur a la disposition d'un caissier, pour ses seules depenses. */
    PETITE_CAISSE("Petite caisse de depenses", Famille.CASH, true),
    FLOAT("Float", Famille.VIRTUEL, false),
    RECHARGE("Compte de recharge", Famille.VIRTUEL, false),
    COMMISSIONS("Commissions", Famille.COMMISSIONS, false),
    BANQUE("Compte bancaire", Famille.BANQUE, true),
    FONDS_EXPLOITATION("Fonds d'exploitation", Famille.FONDS, true);

    public enum Famille { CASH, VIRTUEL, BANQUE, COMMISSIONS, FONDS }

    private final String libelle;
    private final Famille famille;
    /** Cree a la main par l'administrateur ; les autres comptes se creent seuls. */
    private final boolean manuel;

    TypeCompte(String libelle, Famille famille, boolean manuel) {
        this.libelle = libelle;
        this.famille = famille;
        this.manuel = manuel;
    }

    public String getLibelle() {
        return libelle;
    }

    public Famille getFamille() {
        return famille;
    }

    public boolean isManuel() {
        return manuel;
    }
}
