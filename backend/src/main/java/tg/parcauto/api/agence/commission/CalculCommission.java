package tg.parcauto.api.agence.commission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * Calcul pur d'une commission, sans base de donnees : choix du bareme le plus
 * precis, puis application du fixe, du pourcentage ou de la tranche. Les
 * montants sont des francs CFA entiers ; un pourcentage s'arrondit au franc le
 * plus proche (la moitie vers le haut).
 */
public final class CalculCommission {

    /** Commission obtenue et explication lisible (« 0,25 % de 100 000 F »). */
    public record Resultat(long commission, String detail) {
    }

    private CalculCommission() {
    }

    /**
     * Bareme applicable : celui du type d'operation et du reseau exacts avant
     * celui du seul type, puis du seul reseau, puis celui du service entier.
     */
    public static Optional<Bareme> choisir(Collection<Bareme> baremes, Long serviceId, Long typeId, Long reseauId,
                                           java.time.LocalDate jour) {
        return baremes.stream()
                .filter(b -> b.getServiceId().equals(serviceId) && b.valableLe(jour))
                .filter(b -> b.getTypeOperationId() == null || b.getTypeOperationId().equals(typeId))
                .filter(b -> b.getReseauId() == null || b.getReseauId().equals(reseauId))
                .max(Comparator.comparingInt(CalculCommission::precision)
                        .thenComparing(Bareme::getDateDebut)
                        .thenComparing(b -> b.getId() == null ? 0L : b.getId()));
    }

    /** 3 : type et reseau ; 2 : type seul ; 1 : reseau seul ; 0 : service entier. */
    static int precision(Bareme b) {
        return (b.getTypeOperationId() != null ? 2 : 0) + (b.getReseauId() != null ? 1 : 0);
    }

    public static Resultat appliquer(Bareme b, long montant) {
        return switch (b.getMode()) {
            case FIXE -> new Resultat(valeur(b.getValeurFixe()), "Montant fixe de " + francs(valeur(b.getValeurFixe())));
            case POURCENTAGE -> {
                long brut = pourcentage(b.getTaux(), montant);
                long borne = brut;
                String detail = taux(b.getTaux()) + " de " + francs(montant);
                if (b.getMinimum() != null && borne < b.getMinimum()) {
                    borne = b.getMinimum();
                    detail += ", releve au minimum de " + francs(borne);
                }
                if (b.getMaximum() != null && borne > b.getMaximum()) {
                    borne = b.getMaximum();
                    detail += ", limite au maximum de " + francs(borne);
                }
                yield new Resultat(borne, detail);
            }
            case TRANCHES -> b.getTranches().stream()
                    .filter(t -> t.contient(montant))
                    .findFirst()
                    .map(t -> t.getMode() == Bareme.Mode.FIXE
                            ? new Resultat(valeur(t.getValeurFixe()), "Tranche " + bornes(t) + " : "
                            + francs(valeur(t.getValeurFixe())))
                            : new Resultat(pourcentage(t.getTaux(), montant), "Tranche " + bornes(t) + " : "
                            + taux(t.getTaux()) + " de " + francs(montant)))
                    .orElse(new Resultat(0, "Aucune tranche ne couvre " + francs(montant)));
        };
    }

    static long pourcentage(BigDecimal taux, long montant) {
        if (taux == null) {
            return 0;
        }
        return BigDecimal.valueOf(montant).multiply(taux).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static long valeur(Long v) {
        return v == null ? 0 : v;
    }

    static String bornes(TrancheBareme t) {
        return t.getMontantMax() == null ? "a partir de " + francs(t.getMontantMin())
                : francs(t.getMontantMin()) + " a " + francs(t.getMontantMax());
    }

    static String taux(BigDecimal taux) {
        DecimalFormat format = new DecimalFormat("0.####", DecimalFormatSymbols.getInstance(Locale.FRANCE));
        return format.format(taux == null ? BigDecimal.ZERO : taux) + " %";
    }

    static String francs(long montant) {
        return tg.parcauto.api.agence.tresorerie.RegistreService.francs(montant);
    }
}
