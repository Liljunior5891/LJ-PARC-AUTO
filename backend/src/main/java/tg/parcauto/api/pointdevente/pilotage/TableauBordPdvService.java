package tg.parcauto.api.pointdevente.pilotage;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseur;
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseurRepository;
import tg.parcauto.api.pointdevente.caisse.CaisseRepository;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisseRepository;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.catalogue.CatalogueDto;
import tg.parcauto.api.pointdevente.restauration.Commande;
import tg.parcauto.api.pointdevente.restauration.CommandeRepository;
import tg.parcauto.api.pointdevente.restauration.LigneCommande;
import tg.parcauto.api.pointdevente.restauration.TableRestaurant;
import tg.parcauto.api.pointdevente.restauration.TableRestaurantRepository;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.VenteDto;
import tg.parcauto.api.pointdevente.vente.VenteRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Indicateurs du point de vente sur une periode, sur le modele du tableau de
 * bord du parc : ce que la vente rapporte, ce qu'elle coute et ce qu'elle
 * degage ; l'activite des caisses (et des tables en restauration), les
 * alertes de stock, la repartition par categorie, l'evolution, les meilleures
 * ventes et l'affluence par heure. Jours et heures sont decoupes dans le
 * fuseau du poste qui consulte.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TableauBordPdvService {

    /** Au-dela, l'evolution passe d'un point par jour a un point par mois. */
    private static final long JOURS_MAX_PAR_JOUR = 62;
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH);
    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("MMM yy", Locale.FRENCH);

    private final VenteRepository venteRepository;
    private final ArticleRepository articleRepository;
    private final SessionCaisseRepository sessionRepository;
    private final CaisseRepository caisseRepository;
    private final CommandeFournisseurRepository commandeFournisseurRepository;
    private final CommandeRepository commandeRepository;
    private final TableRestaurantRepository tableRepository;
    private final ModuleService moduleService;

    public record ParMode(ModePaiementPdv mode, String libelle, BigDecimal total, long nombre) {
    }

    public record MeilleurArticle(Long articleId, String libelle, BigDecimal quantite, BigDecimal chiffreAffaires,
                                  BigDecimal marge) {
    }

    public record Jour(LocalDate date, BigDecimal chiffreAffaires, long tickets) {
    }

    /** Point de la courbe d'evolution : un jour ou un mois. */
    public record Point(String cle, String libelle, BigDecimal chiffreAffaires, BigDecimal cout, BigDecimal marge,
                        long tickets) {
    }

    public record Heure(int heure, BigDecimal chiffreAffaires, long tickets) {
    }

    public record Categorie(String libelle, String couleur, String icone, BigDecimal chiffreAffaires) {
    }

    /** Service en salle, pour un point de vente en mode restaurant. */
    public record Salle(long tablesOccupees, long tables, long commandesOuvertes, long enPreparation,
                        long pretesAServir, long demandesClients) {
    }

    public record TableauBord(
            Instant debut, Instant fin,
            BigDecimal chiffreAffaires, long tickets, BigDecimal panierMoyen, long ticketsAnnules,
            BigDecimal coutVentes, BigDecimal margeBrute, BigDecimal margePourcent, BigDecimal achatsRecus,
            List<ParMode> parMode, List<MeilleurArticle> meilleursArticles,
            List<Jour> parJour, String granularite, List<Point> evolution, List<Heure> parHeure,
            List<Categorie> parCategorie, List<VenteDto.Resume> derniersTickets,
            long articlesActifs, long caissesOuvertes, long caisses,
            long ruptures, long sousSeuil, long commandesFournisseursEnCours, long commandesFournisseursEnRetard,
            List<CatalogueDto.ArticleReponse> stockBas, Salle salle) {
    }

    public TableauBord calculer(Instant debut, Instant fin, ZoneId fuseau) {
        List<ParMode> parMode = new ArrayList<>();
        BigDecimal chiffre = BigDecimal.ZERO;
        long tickets = 0;
        for (Object[] ligne : venteRepository.totauxPeriode(debut, fin)) {
            ModePaiementPdv mode = (ModePaiementPdv) ligne[0];
            BigDecimal total = (BigDecimal) ligne[1];
            long nombre = (Long) ligne[2];
            parMode.add(new ParMode(mode, mode.getLibelle(), total, nombre));
            chiffre = chiffre.add(total);
            tickets += nombre;
        }
        BigDecimal panier = tickets == 0 ? BigDecimal.ZERO
                : chiffre.divide(BigDecimal.valueOf(tickets), 2, RoundingMode.HALF_UP);
        BigDecimal cout = venteRepository.coutAchatPeriode(debut, fin);
        BigDecimal marge = chiffre.subtract(cout);
        BigDecimal margePourcent = chiffre.signum() == 0 ? BigDecimal.ZERO
                : marge.multiply(BigDecimal.valueOf(100)).divide(chiffre, 1, RoundingMode.HALF_UP);

        List<MeilleurArticle> meilleurs = venteRepository.meilleursArticles(debut, fin, PageRequest.of(0, 8)).stream()
                .map(l -> new MeilleurArticle((Long) l[0], (String) l[1], (BigDecimal) l[2], (BigDecimal) l[3],
                        ((BigDecimal) l[3]).subtract((BigDecimal) l[4])))
                .toList();

        List<Object[]> ticketsCout = venteRepository.ticketsAvecCout(debut, fin);
        boolean parJours = Duration.between(debut, fin).toDays() <= JOURS_MAX_PAR_JOUR;
        List<Point> evolution = evolution(ticketsCout, debut, fin, fuseau, parJours);

        List<Categorie> categories = venteRepository.ventesParCategorie(debut, fin).stream()
                .map(l -> new Categorie(l[0] == null ? "Sans categorie" : (String) l[0], (String) l[1],
                        (String) l[2], (BigDecimal) l[3]))
                .toList();

        LocalDate aujourdhui = LocalDate.now(fuseau);
        List<CatalogueDto.ArticleReponse> stockBas = articleRepository.enStockBas().stream()
                .map(CatalogueDto.ArticleReponse::de).toList();

        return new TableauBord(
                debut, fin, chiffre, tickets, panier, venteRepository.annuleesPeriode(debut, fin),
                cout, marge, margePourcent, commandeFournisseurRepository.achatsRecus(debut, fin),
                parMode, meilleurs, parJour(evolution, parJours), parJours ? "JOUR" : "MOIS", evolution,
                parHeure(ticketsCout, fuseau), categories,
                venteRepository.periode(debut, fin, PageRequest.of(0, 6)).map(VenteDto.Resume::de).getContent(),
                articleRepository.countByActifTrue(),
                sessionRepository.countByStatut(SessionCaisse.Statut.OUVERTE),
                caisseRepository.findAllByOrderByNomAsc().stream().filter(c -> c.isActif()).count(),
                // Un produit epuise compte en rupture, pas sous le seuil : les deux compteurs s'additionnent.
                articleRepository.enRupture(), stockBas.stream().filter(a -> a.stockActuel().signum() > 0).count(),
                commandeFournisseurRepository.countByStatut(CommandeFournisseur.Statut.EN_COURS),
                commandeFournisseurRepository.enRetard(aujourdhui),
                stockBas.stream().limit(10).toList(),
                salle());
    }

    /** Un point par jour (ou par mois), periodes creuses comprises : la courbe ne saute pas les jours sans vente. */
    private static List<Point> evolution(List<Object[]> tickets, Instant debut, Instant fin, ZoneId fuseau,
                                         boolean parJours) {
        Map<String, BigDecimal[]> cumuls = new LinkedHashMap<>();
        Map<String, Long> nombres = new LinkedHashMap<>();
        Map<String, String> libelles = new LinkedHashMap<>();
        // Pour « depuis le debut », la serie commence au premier ticket, pas en l'an 2000.
        Instant depart = debut;
        if (!parJours && !tickets.isEmpty()) {
            Instant premier = tickets.stream().map(t -> (Instant) t[0]).min(Instant::compareTo).orElse(debut);
            if (premier.isAfter(depart)) {
                depart = premier;
            }
        }
        ZonedDateTime curseur = depart.atZone(fuseau);
        ZonedDateTime borne = fin.atZone(fuseau);
        if (parJours) {
            for (LocalDate j = curseur.toLocalDate(); j.atStartOfDay(fuseau).isBefore(borne); j = j.plusDays(1)) {
                cumuls.put(j.toString(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                libelles.put(j.toString(), j.format(JOUR));
            }
        } else {
            for (YearMonth m = YearMonth.from(curseur); !m.atDay(1).atStartOfDay(fuseau).isAfter(borne.minusNanos(1));
                 m = m.plusMonths(1)) {
                cumuls.put(m.toString(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                libelles.put(m.toString(), m.atDay(1).format(MOIS));
            }
        }
        for (Object[] t : tickets) {
            ZonedDateTime moment = ((Instant) t[0]).atZone(fuseau);
            String cle = parJours ? moment.toLocalDate().toString() : YearMonth.from(moment).toString();
            BigDecimal[] cumul = cumuls.get(cle);
            if (cumul == null) {
                continue;
            }
            cumul[0] = cumul[0].add((BigDecimal) t[1]);
            cumul[1] = cumul[1].add((BigDecimal) t[2]);
            nombres.merge(cle, 1L, Long::sum);
        }
        return cumuls.entrySet().stream()
                .map(e -> new Point(e.getKey(), libelles.get(e.getKey()), e.getValue()[0], e.getValue()[1],
                        e.getValue()[0].subtract(e.getValue()[1]), nombres.getOrDefault(e.getKey(), 0L)))
                .toList();
    }

    /** Ancienne serie journaliere, conservee pour les ecrans qui la lisent encore. */
    private static List<Jour> parJour(List<Point> evolution, boolean parJours) {
        if (!parJours) {
            return List.of();
        }
        return evolution.stream().map(p -> new Jour(LocalDate.parse(p.cle()), p.chiffreAffaires(), p.tickets())).toList();
    }

    /** Affluence : chiffre d'affaires et tickets par heure de la journee, toutes journees confondues. */
    private static List<Heure> parHeure(List<Object[]> tickets, ZoneId fuseau) {
        BigDecimal[] montants = new BigDecimal[24];
        long[] nombres = new long[24];
        java.util.Arrays.fill(montants, BigDecimal.ZERO);
        for (Object[] t : tickets) {
            int heure = ((Instant) t[0]).atZone(fuseau).getHour();
            montants[heure] = montants[heure].add((BigDecimal) t[1]);
            nombres[heure]++;
        }
        List<Heure> heures = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            heures.add(new Heure(h, montants[h], nombres[h]));
        }
        return heures;
    }

    /** Etat de la salle en direct ; nul pour une boutique. */
    private Salle salle() {
        if (!"RESTAURATION".equals(moduleService.variante(ContexteEntreprise.exiger(), ModuleApplicatif.POINT_DE_VENTE))) {
            return null;
        }
        List<Commande> ouvertes = commandeRepository.ouvertes();
        long tables = tableRepository.toutes().stream().filter(TableRestaurant::isActif).count();
        long occupees = ouvertes.stream().filter(c -> c.getTable() != null).count();
        long enPreparation = 0;
        long pretes = 0;
        long demandes = 0;
        for (Commande c : ouvertes) {
            boolean demande = c.getAppelMotif() != null;
            for (LigneCommande l : c.getLignes()) {
                switch (l.getStatut()) {
                    case A_PREPARER, EN_PREPARATION -> enPreparation++;
                    case PRETE -> pretes++;
                    case PROPOSEE -> demande = true;
                    default -> {
                    }
                }
            }
            if (demande) {
                demandes++;
            }
        }
        return new Salle(occupees, tables, ouvertes.size(), enPreparation, pretes, demandes);
    }
}
