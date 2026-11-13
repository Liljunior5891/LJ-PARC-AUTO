package tg.parcauto.api.pointdevente.reserve;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.pointdevente.depense.DepensePdv;
import tg.parcauto.api.pointdevente.depense.DepensePdvRepository;
import tg.parcauto.api.pointdevente.depense.ModeSortie;
import tg.parcauto.api.pointdevente.reglage.ReglagePdvService;
import tg.parcauto.api.pointdevente.vente.MargeVentes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fonds de reserve du point de vente, compte separe du compte de l'entreprise.
 * <p>
 * Chaque mois, il recoit le pourcentage alors en vigueur du benefice net :
 * ventes validees - cout d'achat des articles vendus - depenses valides, avant
 * les parts des associes. Les depenses payees par le fonds lui-meme sont exclues
 * de cette base : elles sont deja financees par la reserve. Un mois en perte
 * reprend une partie des versements, au plus ce qui avait ete verse : le cumul
 * ne descend jamais sous zero.
 * <p>
 * Le versement a lieu a la fin du mois : il est alors fige ({@link MoisFondsReserve})
 * et une correction ulterieure ne le change plus. Tant que le mois court, son
 * montant n'est qu'une estimation : il ne quitte pas encore le compte de
 * l'entreprise et ne peut pas etre depense ; une perte estimee reduit en revanche
 * deja le disponible, puisqu'elle sera reprise a la cloture.
 * <p>
 * Solde = versements des mois termines - depenses payees par le fonds ;
 * disponible = solde - depenses en attente - perte estimee du mois en cours.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FondsReserveService {

    private static final ZoneId FUSEAU = ZoneId.of("Africa/Lome");
    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final List<DepensePdv.Statut> VALIDES = List.of(DepensePdv.Statut.VALIDE);
    private static final List<DepensePdv.Statut> EN_ATTENTE =
            List.of(DepensePdv.Statut.ATTENTE_GERANT, DepensePdv.Statut.ATTENTE_ADMIN);
    private static final String[] MOIS = {"janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout",
            "septembre", "octobre", "novembre", "decembre"};

    private final PeriodeFondsReserveRepository periodes;
    private final MoisFondsReserveRepository moisClos;
    private final ClotureFondsReserve cloture;
    private final DepensePdvRepository depenses;
    private final MargeVentes marges;
    private final ReglagePdvService reglages;

    /**
     * Versement d'un mois (ou de la partie d'un mois couverte par une periode) :
     * {@code dotation} est le pourcentage du benefice net, {@code verse} ce qui a
     * reellement bouge (une reprise ne depasse pas le cumul deja verse).
     * {@code enCours} : le mois n'est pas termine, le montant est une estimation.
     */
    public record Dotation(String mois, String libelle, LocalDate debut, LocalDate fin, BigDecimal pourcent,
                           BigDecimal chiffreAffaires, BigDecimal coutAchats, BigDecimal depenses,
                           BigDecimal beneficeNet, BigDecimal dotation, BigDecimal verse, boolean enCours) {

        /** Libelle d'ecriture, le meme dans le journal de banque et celui du fonds. */
        public String ecriture() {
            String taux = pourcent.stripTrailingZeros().toPlainString() + " %";
            if (verse.signum() < 0) {
                return "Reprise du fonds de reserve - " + libelle + " : perte de " + entier(beneficeNet.negate())
                        + " F (" + taux + ")";
            }
            return "Dotation au fonds de reserve - " + libelle + " : " + taux + " de " + entier(beneficeNet)
                    + " F de benefice net" + (verse.compareTo(dotation) != 0 ? ", limitee a ce qui avait ete verse" : "");
        }
    }

    /** Depense payee (ou a payer) par le fonds. */
    public record Sortie(Long id, String numero, LocalDate date, String categorie, String libelle,
                         String beneficiaire, BigDecimal montant, DepensePdv.Statut statut, String auteurNom) {
    }

    public record Periode(LocalDate debut, LocalDate fin, BigDecimal pourcent) {
    }

    /**
     * {@code totalDotations} : versements des mois termines (sortis du compte de
     * l'entreprise) ; {@code moisEnCours} : estimation du mois en cours, versee a sa fin.
     */
    public record Etat(boolean actif, BigDecimal pourcent, LocalDate depuis, BigDecimal totalDotations,
                       BigDecimal totalUtilise, BigDecimal enAttente, BigDecimal solde, BigDecimal moisEnCours,
                       BigDecimal disponible, List<Periode> periodes, List<Dotation> dotations, List<Sortie> sorties) {
    }

    /** Calcul complet : chaque ligne, la somme des mois clos et l'estimation du mois en cours. */
    private record Calcul(List<Dotation> lignes, BigDecimal verseClos, BigDecimal estimation) {
        static final Calcul VIDE = new Calcul(List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Etat complet du fonds, pour son ecran. */
    public Etat etat() {
        ReglagePdvService.Reglages r = reglages.lire();
        List<PeriodeFondsReserve> liste = periodes.findAllByOrderByDebutAscIdAsc();
        Calcul c = calcul(liste, true);
        BigDecimal utilise = depenses.totalPar(ModeSortie.RESERVE, VALIDES);
        BigDecimal attente = depenses.totalPar(ModeSortie.RESERVE, EN_ATTENTE);
        List<Sortie> sorties = new ArrayList<>();
        List<DepensePdv.Statut> statuts = new ArrayList<>(EN_ATTENTE);
        statuts.add(DepensePdv.Statut.VALIDE);
        for (DepensePdv d : depenses.parModeEtStatuts(ModeSortie.RESERVE, statuts, PageRequest.of(0, 200))) {
            sorties.add(new Sortie(d.getId(), d.getNumero(), d.getDateDepense(), d.getCategorie().getNom(),
                    d.getLibelle(), d.getBeneficiaire(), d.getMontant(), d.getStatut(), d.getAuteurNom()));
        }
        List<Dotation> recentes = new ArrayList<>(c.lignes());
        Collections.reverse(recentes);
        return new Etat(r.fondsReserveActif(), r.fondsReservePourcent(),
                liste.isEmpty() ? null : liste.get(0).getDebut(), c.verseClos(), utilise, attente,
                c.verseClos().subtract(utilise), c.estimation(), disponible(c, utilise, attente),
                liste.stream().map(p -> new Periode(p.getDebut(), p.getFin(), p.getPourcent())).toList(),
                recentes, sorties);
    }

    /** Le fonds a deja ete active : son journal a un sens. */
    public boolean aExiste() {
        return reglages.lire().fondsReserveActif() || periodes.count() > 0;
    }

    /** Versements des mois termines, du plus ancien au plus recent (journaux de banque et du fonds). */
    public List<Dotation> versementsClos() {
        return calcul(periodes.findAllByOrderByDebutAscIdAsc(), true).lignes().stream()
                .filter(d -> !d.enCours() && d.verse().signum() != 0).toList();
    }

    /** Total verse au fonds par les mois termines : ce qui a quitte le compte de l'entreprise. */
    public BigDecimal totalDotations() {
        return calcul(periodes.findAllByOrderByDebutAscIdAsc(), true).verseClos();
    }

    /**
     * Refuse une depense par le fonds au-dela de ce qui est disponible. A
     * l'approbation, la depense elle-meme est encore comptee en attente : son
     * montant est alors rendu au disponible. Le reglage reste verrouille jusqu'a
     * la fin de la transaction de l'appelant : deux depenses simultanees passent
     * l'une apres l'autre et ne depassent pas a elles deux le disponible.
     */
    @Transactional
    public void exigerDisponible(BigDecimal montant, BigDecimal dejaEnAttente) {
        reglages.verrouillerFondsReserve();
        Calcul c = calcul(periodes.findAllByOrderByDebutAscIdAsc(), true);
        BigDecimal disponible = disponible(c, depenses.totalPar(ModeSortie.RESERVE, VALIDES),
                depenses.totalPar(ModeSortie.RESERVE, EN_ATTENTE))
                .add(dejaEnAttente == null ? BigDecimal.ZERO : dejaEnAttente);
        if (montant.compareTo(disponible) > 0) {
            throw new BusinessException("Le fonds de reserve ne dispose que de " + entier(disponible.max(BigDecimal.ZERO))
                    + " F (le versement du mois en cours n'arrive qu'a la fin du mois) : la depense de "
                    + entier(montant) + " F le depasse");
        }
    }

    /** Montant entier lisible (12 500), pour les messages et les journaux. */
    public static String entier(BigDecimal v) {
        return String.format(java.util.Locale.FRENCH, "%,.0f", v).replace(' ', ' ').replace(' ', ' ');
    }

    // ------------------------------------------------------------- internes

    /** La seule regle du disponible, pour l'ecran comme pour le controle. */
    private static BigDecimal disponible(Calcul c, BigDecimal utilise, BigDecimal attente) {
        return c.verseClos().subtract(utilise).subtract(attente).add(c.estimation().min(BigDecimal.ZERO));
    }

    /** Tranche d'un mois couverte par une periode. */
    private record Tranche(LocalDate debut, LocalDate fin, BigDecimal pourcent, boolean enCours) {
    }

    /**
     * Lignes de chaque mois : les mois clos sont relus tels qu'ils ont ete figes,
     * ceux qui viennent de se terminer sont calcules puis figes, le mois en cours
     * est estime. Seule la periode non figee est relue dans les ventes et les depenses.
     */
    private Calcul calcul(List<PeriodeFondsReserve> liste, boolean reessai) {
        if (liste.isEmpty()) {
            return Calcul.VIDE;
        }
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        List<Tranche> tranches = tranches(liste, aujourdhui);
        if (tranches.isEmpty()) {
            return Calcul.VIDE;
        }
        Map<LocalDate, MoisFondsReserve> figes = new HashMap<>();
        for (MoisFondsReserve m : moisClos.findAllByOrderByDebutAsc()) {
            figes.put(m.getDebut(), m);
        }
        LocalDate premierACalculer = tranches.stream().filter(t -> t.enCours() || !figes.containsKey(t.debut()))
                .map(Tranche::debut).findFirst().orElse(null);
        Map<LocalDate, MargeVentes.Marge> ventesParJour = new HashMap<>();
        Map<LocalDate, BigDecimal> depensesParJour = new HashMap<>();
        if (premierACalculer != null) {
            ventesParJour.putAll(marges.parJour(premierACalculer.atStartOfDay(FUSEAU).toInstant(),
                    aujourdhui.plusDays(1).atStartOfDay(FUSEAU).toInstant()));
            for (Object[] l : depenses.totauxParJourHors(premierACalculer, aujourdhui, ModeSortie.RESERVE)) {
                depensesParJour.put((LocalDate) l[0], (BigDecimal) l[1]);
            }
        }
        List<Dotation> lignes = new ArrayList<>();
        List<MoisFondsReserve> aFiger = new ArrayList<>();
        BigDecimal cumul = BigDecimal.ZERO;
        BigDecimal verseClos = BigDecimal.ZERO;
        BigDecimal estimation = BigDecimal.ZERO;
        for (Tranche t : tranches) {
            MoisFondsReserve fige = t.enCours() ? null : figes.get(t.debut());
            Dotation d;
            if (fige != null) {
                d = ligne(t, fige.getChiffreAffaires(), fige.getCoutAchats(), fige.getDepenses(), fige.getBeneficeNet(),
                        fige.getDotation(), fige.getVerse());
            } else {
                MargeVentes.Marge marge = MargeVentes.Marge.NULLE;
                BigDecimal frais = BigDecimal.ZERO;
                for (LocalDate j = t.debut(); !j.isAfter(t.fin()); j = j.plusDays(1)) {
                    marge = marge.plus(ventesParJour.getOrDefault(j, MargeVentes.Marge.NULLE));
                    frais = frais.add(depensesParJour.getOrDefault(j, BigDecimal.ZERO));
                }
                BigDecimal net = marge.brute().subtract(frais);
                BigDecimal dotation = net.multiply(t.pourcent()).divide(CENT, 2, RoundingMode.HALF_UP);
                BigDecimal verse = cumul.add(dotation).max(BigDecimal.ZERO).subtract(cumul);
                d = ligne(t, marge.chiffreAffaires(), marge.coutAchats(), frais, net, dotation, verse);
                if (!t.enCours()) {
                    aFiger.add(enregistrement(d));
                }
            }
            cumul = cumul.add(d.verse());
            if (t.enCours()) {
                estimation = estimation.add(d.verse());
            } else {
                verseClos = verseClos.add(d.verse());
            }
            lignes.add(d);
        }
        if (!aFiger.isEmpty()) {
            try {
                cloture.figer(aFiger);
            } catch (DataIntegrityViolationException e) {
                // Une autre lecture vient de figer les memes mois : on relit ce qu'elle a enregistre.
                if (!reessai) {
                    throw e;
                }
                return calcul(liste, false);
            }
        }
        return new Calcul(lignes, verseClos, estimation);
    }

    /** Une tranche par mois de chaque periode, jusqu'a aujourd'hui. */
    private static List<Tranche> tranches(List<PeriodeFondsReserve> liste, LocalDate aujourdhui) {
        YearMonth moisCourant = YearMonth.from(aujourdhui);
        List<Tranche> tranches = new ArrayList<>();
        for (PeriodeFondsReserve p : liste) {
            LocalDate finPeriode = p.getFin() == null || p.getFin().isAfter(aujourdhui) ? aujourdhui : p.getFin();
            LocalDate jour = p.getDebut();
            while (!jour.isAfter(finPeriode)) {
                YearMonth mois = YearMonth.from(jour);
                LocalDate finMois = mois.atEndOfMonth().isBefore(finPeriode) ? mois.atEndOfMonth() : finPeriode;
                tranches.add(new Tranche(jour, finMois, p.getPourcent(), !mois.isBefore(moisCourant)));
                jour = finMois.plusDays(1);
            }
        }
        return tranches;
    }

    private static Dotation ligne(Tranche t, BigDecimal chiffre, BigDecimal cout, BigDecimal frais, BigDecimal net,
                                  BigDecimal dotation, BigDecimal verse) {
        YearMonth mois = YearMonth.from(t.debut());
        return new Dotation(mois.toString(), MOIS[mois.getMonthValue() - 1] + " " + mois.getYear(), t.debut(), t.fin(),
                t.pourcent(), chiffre, cout, frais, net, dotation, verse, t.enCours());
    }

    private static MoisFondsReserve enregistrement(Dotation d) {
        MoisFondsReserve m = new MoisFondsReserve();
        m.setDebut(d.debut());
        m.setFin(d.fin());
        m.setPourcent(d.pourcent());
        m.setChiffreAffaires(d.chiffreAffaires());
        m.setCoutAchats(d.coutAchats());
        m.setDepenses(d.depenses());
        m.setBeneficeNet(d.beneficeNet());
        m.setDotation(d.dotation());
        m.setVerse(d.verse());
        return m;
    }
}
