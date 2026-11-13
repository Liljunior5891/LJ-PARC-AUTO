package tg.parcauto.api.pointdevente.reglage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;

import java.math.BigDecimal;

/**
 * Options du point de vente : lues par tous les ecrans du module, modifiees par
 * l'administrateur seul. Sans ligne enregistree, tout est desactive.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReglagePdvService {

    private final ReglagePdvRepository repository;
    private final tg.parcauto.api.pointdevente.reserve.PeriodeFondsReserveRepository periodes;
    private final tg.parcauto.api.pointdevente.depense.DepensePdvRepository depenses;
    /** Fourni par la tresorerie, qui depend elle-meme des reglages : resolu a l'usage. */
    private final org.springframework.beans.factory.ObjectProvider<ControleOptions> controle;

    public record Reglages(boolean associesActif, boolean circuitCaisseActif, BigDecimal ecartTolere,
                           boolean validationDepensesCaisse,
                           /** Minutes sans geste avant le verrouillage de l'ecran de caisse. */
                           int minutesVerrouCaisse,
                           /** Remise maximale d'un caissier, en % du ticket. */
                           BigDecimal remiseMaxCaissierPourcent,
                           /** Fonds de reserve : pourcentage du benefice net mis de cote en continu. */
                           boolean fondsReserveActif, BigDecimal fondsReservePourcent) {

        /** Pour les profils qui n'ont pas a connaitre le fonds de reserve (caissier, serveur, associe...). */
        public Reglages sansFondsReserve() {
            return new Reglages(associesActif, circuitCaisseActif, ecartTolere, validationDepensesCaisse,
                    minutesVerrouCaisse, remiseMaxCaissierPourcent, false, null);
        }
    }

    public record Requete(@NotNull Boolean associesActif, @NotNull Boolean circuitCaisseActif,
                          @NotNull @PositiveOrZero BigDecimal ecartTolere,
                          /** Absent : inchange. */
                          Boolean validationDepensesCaisse,
                          /** Absent : inchange. */
                          @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(120) Integer minutesVerrouCaisse,
                          /** Absent : inchange. */
                          @PositiveOrZero @jakarta.validation.constraints.DecimalMax("100") BigDecimal remiseMaxCaissierPourcent,
                          /** Absent : inchange. */
                          Boolean fondsReserveActif,
                          /** Absent : inchange. */
                          @jakarta.validation.constraints.DecimalMin(value = "0.01", message = "Le pourcentage du fonds de reserve doit etre d'au moins 0,01 %")
                          @jakarta.validation.constraints.DecimalMax("100") BigDecimal fondsReservePourcent) {
    }

    @Transactional(readOnly = true)
    public Reglages lire() {
        return repository.findFirstByOrderByIdAsc()
                .map(r -> new Reglages(r.isAssociesActif(), r.isCircuitCaisseActif(), r.getEcartTolere(),
                        r.isValidationDepensesCaisse(), r.getMinutesVerrouCaisse(), r.getRemiseMaxCaissierPourcent(),
                        r.isFondsReserveActif(), r.getFondsReservePourcent()))
                .orElse(new Reglages(false, false, BigDecimal.ZERO, false, 5, BigDecimal.TEN, false, BigDecimal.TEN));
    }

    public Reglages modifier(Requete r) {
        return modifier(r, "l'administrateur");
    }

    /** {@code auteur} : nom de l'administrateur, inscrit sur les demandes refusees a la desactivation du fonds. */
    @Transactional
    public Reglages modifier(Requete r, String auteur) {
        ReglagePdv reglage = repository.verrouiller().stream().findFirst().orElseGet(ReglagePdv::new);
        if (reglage.isCircuitCaisseActif() && !r.circuitCaisseActif()) {
            ControleOptions c = controle.getIfAvailable();
            String blocage = c == null ? null : c.blocageArretCircuit();
            if (blocage != null) {
                throw new BusinessException("Le circuit de caisse ne peut pas etre desactive : " + blocage);
            }
        }
        reglage.setAssociesActif(r.associesActif());
        reglage.setCircuitCaisseActif(r.circuitCaisseActif());
        reglage.setEcartTolere(r.ecartTolere());
        if (r.validationDepensesCaisse() != null) {
            reglage.setValidationDepensesCaisse(r.validationDepensesCaisse());
        }
        if (r.minutesVerrouCaisse() != null) {
            reglage.setMinutesVerrouCaisse(r.minutesVerrouCaisse());
        }
        if (r.remiseMaxCaissierPourcent() != null) {
            reglage.setRemiseMaxCaissierPourcent(r.remiseMaxCaissierPourcent());
        }
        appliquerFondsReserve(reglage, r, auteur);
        repository.save(reglage);
        return lire();
    }

    /**
     * Toute modification du fonds de reserve prend effet aujourd'hui : la periode
     * en cours s'arrete hier (supprimee si elle commencait aujourd'hui) et, si le
     * fonds reste actif, une nouvelle periode au nouveau taux commence ce jour.
     */
    private void appliquerFondsReserve(ReglagePdv reglage, Requete r, String auteur) {
        boolean actif = r.fondsReserveActif() == null ? reglage.isFondsReserveActif() : r.fondsReserveActif();
        BigDecimal pourcent = r.fondsReservePourcent() == null ? reglage.getFondsReservePourcent()
                : r.fondsReservePourcent().setScale(2, java.math.RoundingMode.HALF_UP);
        boolean tauxChange = pourcent.compareTo(reglage.getFondsReservePourcent()) != 0;
        boolean etatChange = actif != reglage.isFondsReserveActif();
        if (!etatChange && !(actif && tauxChange)) {
            reglage.setFondsReservePourcent(pourcent);
            return;
        }
        if (!actif) {
            // Les demandes encore en attente ne seront plus payees par le fonds : refusees d'office,
            // elles ne bloquent pas la desactivation.
            java.time.Instant maintenant = java.time.Instant.now();
            for (var d : depenses.findByModeAndStatutIn(tg.parcauto.api.pointdevente.depense.ModeSortie.RESERVE,
                    java.util.List.of(tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.ATTENTE_GERANT,
                            tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.ATTENTE_ADMIN))) {
                d.setStatut(tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.REFUSEE);
                d.setRefuseePar(auteur);
                d.setRefuseeLe(maintenant);
                d.setMotifRefus("Fonds de reserve desactive : redemandez la depense par un autre moyen de paiement");
            }
        }
        java.time.LocalDate aujourdhui = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Lome"));
        periodes.findFirstByFinIsNull().ifPresent(p -> {
            if (!p.getDebut().isBefore(aujourdhui)) {
                periodes.delete(p);
            } else {
                p.setFin(aujourdhui.minusDays(1));
            }
            periodes.flush();
        });
        if (actif) {
            tg.parcauto.api.pointdevente.reserve.PeriodeFondsReserve p = new tg.parcauto.api.pointdevente.reserve.PeriodeFondsReserve();
            p.setDebut(aujourdhui);
            p.setPourcent(pourcent);
            periodes.save(p);
        }
        reglage.setFondsReserveActif(actif);
        reglage.setFondsReservePourcent(pourcent);
    }

    /** Les depenses payees par la caisse passent par le gerant puis l'administrateur. */
    @Transactional(readOnly = true)
    public boolean validationDepensesCaisse() {
        return lire().validationDepensesCaisse();
    }

    /** Refuse une action d'une option desactivee. */
    public void exigerAssocies() {
        if (!lire().associesActif()) {
            throw new BusinessException("La gestion des benefices des associes n'est pas activee dans les reglages");
        }
    }

    /**
     * Verrouille le reglage jusqu'a la fin de la transaction de l'appelant, puis
     * exige le fonds actif : deux depenses simultanees ne depassent pas a elles deux
     * le disponible, et le fonds ne change pas pendant la verification.
     */
    @Transactional
    public void verrouillerFondsReserve() {
        boolean actif = repository.verrouiller().stream().findFirst().map(ReglagePdv::isFondsReserveActif).orElse(false);
        if (!actif) {
            throw new BusinessException("Le fonds de reserve n'est pas active dans les reglages");
        }
    }

    public void exigerCircuit() {
        if (!lire().circuitCaisseActif()) {
            throw new BusinessException("Le circuit des remises de caisse n'est pas active dans les reglages");
        }
    }

    /** Ce qui empeche d'arreter une option en cours d'usage ; fourni par le module de tresorerie. */
    public interface ControleOptions {
        /** Raison d'empecher l'arret du circuit (remises ou degagements en attente, coffre non vide), ou nul. */
        String blocageArretCircuit();
    }
}
