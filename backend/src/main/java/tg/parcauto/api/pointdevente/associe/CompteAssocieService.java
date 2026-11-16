package tg.parcauto.api.pointdevente.associe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.parc.document.StockageFichierService;
import tg.parcauto.api.pointdevente.reglage.ReglagePdvService;
import tg.parcauto.api.pointdevente.reserve.FondsReserveService;
import tg.parcauto.api.pointdevente.tresorerie.TresorerieService;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sous-compte des associes.
 * <p>
 * A la fin de chaque mois, la part de chaque associe (marge brute de ses produits,
 * selon ses rattachements) est figee : c'est ce que l'entreprise lui doit. Le mois
 * en cours n'est qu'une estimation, une vente pouvant encore etre annulee.
 * <p>
 * Le gerant verse cette part par un depot en banque, depuis son coffre ou le
 * compte de l'entreprise, bordereau joint ; il ne verse pas plus que ce qui reste
 * du sur les mois clos. L'associe valide le depot, qui credite alors son
 * sous-compte, ou le refuse avec un motif : le gerant le corrige et le soumet de
 * nouveau, ou l'annule. Le sous-compte ne bouge qu'a la validation.
 * <p>
 * Depuis son sous-compte, l'associe apporte au fonds de reserve commun.
 * <ul>
 *   <li>reste a verser = parts des mois clos - depots engages (non annules) ;</li>
 *   <li>solde du sous-compte = depots valides - apports au fonds.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompteAssocieService {

    private static final ZoneId FUSEAU = ZoneId.of("Africa/Lome");
    private static final long TAILLE_MAX_PIECE = 15L * 1024 * 1024;
    private static final Set<DepotAssocie.Statut> ENGAGES =
            Set.of(DepotAssocie.Statut.EN_ATTENTE, DepotAssocie.Statut.REFUSE, DepotAssocie.Statut.VALIDE);
    private static final Set<DepotAssocie.Statut> A_TRAITER =
            Set.of(DepotAssocie.Statut.EN_ATTENTE, DepotAssocie.Statut.REFUSE);
    private static final String[] MOIS = {"janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout",
            "septembre", "octobre", "novembre", "decembre"};

    private final AssocieService associes;
    private final PartMensuelleRepository parts;
    private final ClotureAssociesRepository clotures;
    private final ClotureParts cloture;
    private final DepotAssocieRepository depots;
    private final FondsReserveService fonds;
    private final ReglagePdvService reglages;
    private final TresorerieService tresorerie;
    private final NumerotationService numerotation;
    private final StockageFichierService stockage;
    private final VenteRepository ventes;

    // ------------------------------------------------------------------ dto

    public record Mois(String mois, String libelle, BigDecimal chiffreAffaires, BigDecimal marge, BigDecimal part,
                       boolean enCours) {
    }

    public record Depot(Long id, String numero, Long associeId, String associeNom, BigDecimal montant,
                        DepotAssocie.Source source, String banque, String reference, LocalDate dateDepot,
                        String periode, String note, boolean piece, String pieceNom, DepotAssocie.Statut statut,
                        String motifRefus, Long auteurId, String auteurNom, Instant soumisLe, Instant traiteLe) {

        static Depot de(DepotAssocie d) {
            return new Depot(d.getId(), d.getNumero(), d.getAssocieId(), d.getAssocieNom(), d.getMontant(),
                    d.getSource(), d.getBanque(), d.getReference(), d.getDateDepot(), d.getPeriode(), d.getNote(),
                    d.getPieceChemin() != null, d.getPieceNom(), d.getStatut(), d.getMotifRefus(), d.getAuteurId(),
                    d.getAuteurNom(), d.getSoumisLe(), d.getTraiteLe());
        }
    }

    /**
     * Compte d'un associe. {@code partsCloses} : parts figees des mois termines ;
     * {@code estimation} : part du mois en cours ; {@code verse} : depots valides ;
     * {@code enCours} : depots en attente ou refuses (a corriger).
     */
    public record Compte(Long associeId, String nom, BigDecimal partsCloses, BigDecimal estimation,
                         BigDecimal verse, BigDecimal enCours, BigDecimal resteAVerser, BigDecimal apportsFonds,
                         BigDecimal solde, long depotsAValider, List<Mois> mois, List<Depot> depots,
                         List<FondsReserveService.Apport> apports) {
    }

    /** Une ligne par associe, pour le gerant et l'administrateur. */
    public record Ligne(Long associeId, String nom, String email, boolean actif, BigDecimal partsCloses,
                        BigDecimal estimation, BigDecimal verse, BigDecimal enCours, BigDecimal resteAVerser,
                        BigDecimal solde) {
    }

    public record Synthese(List<Ligne> associes, List<Depot> depots) {
    }

    public record DepotRequete(@NotNull Long associeId, @NotNull @Positive BigDecimal montant,
                               @NotNull DepotAssocie.Source source, @Size(max = 100) String banque,
                               @NotBlank @Size(max = 80) String reference, @NotNull LocalDate dateDepot,
                               @Size(max = 100) String periode, @Size(max = 2000) String note) {
    }

    public record Refus(@NotBlank @Size(max = 2000) String motif) {
    }

    public record ApportRequete(@NotNull @Positive BigDecimal montant, @Size(max = 500) String note) {
    }

    /** Le fonds vu par un associe : solde, ses propres apports et toutes les depenses (total et detail). */
    public record FondsAssocie(boolean actif, BigDecimal solde, BigDecimal disponible, BigDecimal totalApports,
                               BigDecimal mesApports, BigDecimal totalUtilise, BigDecimal enAttente,
                               List<FondsReserveService.Apport> apports, List<FondsReserveService.Sortie> depenses) {
    }

    // ------------------------------------------------------------- lecture

    /** Le compte de l'associe connecte. */
    @Transactional(readOnly = true)
    public Compte monCompte(UtilisateurPrincipal associe) {
        reglages.exigerAssocies();
        return compte(associes.compteAssocie(associe.getId()));
    }

    /** Le compte d'un associe, pour le gerant et l'administrateur. */
    @Transactional(readOnly = true)
    public Compte compteDe(Long associeId) {
        reglages.exigerAssocies();
        return compte(associes.compteAssocie(associeId));
    }

    /** Tous les associes et les derniers depots. */
    @Transactional(readOnly = true)
    public Synthese synthese() {
        reglages.exigerAssocies();
        Etat e = etat();
        List<Ligne> lignes = new ArrayList<>();
        for (Utilisateur u : associes.comptesAssocies()) {
            BigDecimal closes = e.closes(u.getId());
            BigDecimal engage = depots.total(u.getId(), ENGAGES);
            BigDecimal valide = depots.total(u.getId(), Set.of(DepotAssocie.Statut.VALIDE));
            lignes.add(new Ligne(u.getId(), u.nomComplet(), u.getEmail(), u.isActif(), closes,
                    e.estimation(u.getId()), valide, engage.subtract(valide), closes.subtract(engage),
                    valide.subtract(fonds.totalApportsDe(u.getId()))));
        }
        return new Synthese(lignes, depots.findAllByOrderBySoumisLeDesc(PageRequest.of(0, 200)).stream()
                .map(Depot::de).toList());
    }

    /** Depots qui attendent la decision de l'associe connecte (pastille du menu). */
    @Transactional(readOnly = true)
    public long aValider(UtilisateurPrincipal associe) {
        if (!reglages.lire().associesActif()) {
            return 0;
        }
        return depots.countByAssocieIdAndStatut(associe.getId(), DepotAssocie.Statut.EN_ATTENTE);
    }

    /** Le fonds de reserve vu par un associe. */
    @Transactional(readOnly = true)
    public FondsAssocie fonds(UtilisateurPrincipal associe) {
        reglages.exigerAssocies();
        FondsReserveService.Etat e = fonds.etat();
        return new FondsAssocie(e.actif(), e.solde(), e.disponible(), e.totalApports(),
                fonds.totalApportsDe(associe.getId()), e.totalUtilise(), e.enAttente(),
                fonds.apportsDe(associe.getId()), e.sorties());
    }

    // ------------------------------------------------------------- depots

    /** Le gerant (ou l'administrateur) saisit le depot ; le bordereau est joint ensuite. */
    public Depot creerDepot(DepotRequete r, UtilisateurPrincipal auteur) {
        reglages.exigerAssocies();
        reglages.verrouiller();
        Utilisateur associe = associes.compteAssocie(r.associeId());
        DepotAssocie d = new DepotAssocie();
        d.setNumero(numerotation.prochainNumero("VAS"));
        d.setAssocieId(associe.getId());
        d.setAssocieNom(associe.nomComplet());
        appliquer(d, r, auteur, BigDecimal.ZERO);
        return Depot.de(depots.save(d));
    }

    /** Correction d'un depot en attente ou refuse : il repart vers l'associe. */
    public Depot corriger(Long id, DepotRequete r, UtilisateurPrincipal auteur) {
        reglages.exigerAssocies();
        reglages.verrouiller();
        DepotAssocie d = modifiable(id, auteur);
        if (!d.getAssocieId().equals(r.associeId())) {
            throw new BusinessException("Un depot ne change pas d'associe : annulez-le et saisissez-en un autre");
        }
        appliquer(d, r, auteur, d.getMontant());
        d.setStatut(DepotAssocie.Statut.EN_ATTENTE);
        d.setMotifRefus(null);
        d.setTraiteLe(null);
        return Depot.de(d);
    }

    public Depot annuler(Long id, UtilisateurPrincipal auteur) {
        DepotAssocie d = modifiable(id, auteur);
        d.setStatut(DepotAssocie.Statut.ANNULE);
        d.setTraiteLe(Instant.now());
        return Depot.de(d);
    }

    public Depot joindrePiece(Long id, MultipartFile fichier, UtilisateurPrincipal auteur) {
        DepotAssocie d = modifiable(id, auteur);
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException("Fichier vide");
        }
        if (fichier.getSize() > TAILLE_MAX_PIECE) {
            throw new BusinessException("La piece depasse 15 Mo");
        }
        String ancien = d.getPieceChemin();
        d.setPieceChemin(stockage.enregistrer(fichier, "pdv-associes/" + d.getId()));
        d.setPieceNom(fichier.getOriginalFilename() == null ? "bordereau" : fichier.getOriginalFilename());
        d.setPieceType(fichier.getContentType());
        stockage.supprimer(ancien);
        return Depot.de(d);
    }

    /** L'associe confirme avoir recu le depot : son sous-compte est credite. */
    public Depot valider(Long id, UtilisateurPrincipal associe) {
        reglages.exigerAssocies();
        DepotAssocie d = attenteDe(id, associe);
        if (d.getPieceChemin() == null) {
            throw new BusinessException("Le bordereau n'est pas encore joint : le depot ne peut pas etre valide");
        }
        d.setStatut(DepotAssocie.Statut.VALIDE);
        d.setTraiteLe(Instant.now());
        return Depot.de(d);
    }

    /** L'associe conteste le depot : rien n'est credite, le gerant corrige ou annule. */
    public Depot refuser(Long id, String motif, UtilisateurPrincipal associe) {
        reglages.exigerAssocies();
        DepotAssocie d = attenteDe(id, associe);
        d.setStatut(DepotAssocie.Statut.REFUSE);
        d.setMotifRefus(motif.trim());
        d.setTraiteLe(Instant.now());
        return Depot.de(d);
    }

    /** Bordereau d'un depot : l'associe ne voit que les siens. */
    @Transactional(readOnly = true)
    public DepotAssocie depotAvecPiece(Long id, UtilisateurPrincipal lecteur) {
        DepotAssocie d = depots.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depot", id));
        if (!gestionnaire(lecteur) && !d.getAssocieId().equals(lecteur.getId())) {
            throw ResourceNotFoundException.of("Depot", id);
        }
        if (d.getPieceChemin() == null) {
            throw new ResourceNotFoundException("Aucun bordereau joint au depot " + d.getNumero());
        }
        return d;
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource piece(DepotAssocie d) {
        return stockage.charger(d.getPieceChemin());
    }

    // -------------------------------------------------------- apport au fonds

    /** L'associe apporte au fonds de reserve depuis son sous-compte, dans la limite de son solde. */
    public FondsReserveService.Apport apporter(ApportRequete r, UtilisateurPrincipal associe) {
        reglages.exigerAssocies();
        Utilisateur compte = associes.compteAssocie(associe.getId());
        // Verrou du reglage (et fonds actif exige) : deux apports simultanes ne depassent pas le solde.
        reglages.verrouillerFondsReserve();
        BigDecimal montant = r.montant().setScale(2, RoundingMode.HALF_UP);
        BigDecimal solde = depots.total(compte.getId(), Set.of(DepotAssocie.Statut.VALIDE))
                .subtract(fonds.totalApportsDe(compte.getId()));
        if (montant.compareTo(solde) > 0) {
            throw new BusinessException("Votre sous-compte ne contient que " + FondsReserveService.entier(solde.max(BigDecimal.ZERO))
                    + " F : l'apport de " + FondsReserveService.entier(montant) + " F le depasse");
        }
        return fonds.apportAssocie(compte.getId(), compte.nomComplet(), montant, r.note(), associe);
    }

    // ------------------------------------------------------------ internes

    private Compte compte(Utilisateur u) {
        Etat e = etat();
        BigDecimal closes = e.closes(u.getId());
        BigDecimal engage = depots.total(u.getId(), ENGAGES);
        BigDecimal valide = depots.total(u.getId(), Set.of(DepotAssocie.Statut.VALIDE));
        BigDecimal apports = fonds.totalApportsDe(u.getId());
        List<Mois> mois = new ArrayList<>();
        for (PartMensuelle p : e.figees()) {
            if (p.getAssocieId().equals(u.getId())) {
                mois.add(new Mois(YearMonth.from(p.getMois()).toString(), libelle(p.getMois()), p.getChiffreAffaires(),
                        p.getMarge(), p.getPart(), false));
            }
        }
        AssocieService.BeneficeAssocie courant = e.moisEnCours().get(u.getId());
        if (courant != null) {
            LocalDate debut = YearMonth.now(FUSEAU).atDay(1);
            mois.add(new Mois(YearMonth.from(debut).toString(), libelle(debut), courant.chiffreAffaires(),
                    courant.marge(), courant.part(), true));
        }
        List<Depot> liste = depots.findByAssocieIdOrderBySoumisLeDesc(u.getId()).stream()
                .filter(d -> d.getStatut() != DepotAssocie.Statut.ANNULE).map(Depot::de).toList();
        return new Compte(u.getId(), u.nomComplet(), closes, e.estimation(u.getId()), valide, engage.subtract(valide),
                closes.subtract(engage), apports, valide.subtract(apports),
                liste.stream().filter(d -> d.statut() == DepotAssocie.Statut.EN_ATTENTE).count(),
                mois, liste, fonds.apportsDe(u.getId()));
    }

    /** Parts figees de tous les mois clos, et estimation du mois en cours par associe. */
    private record Etat(List<PartMensuelle> figees, Map<Long, AssocieService.BeneficeAssocie> moisEnCours) {

        BigDecimal closes(Long associeId) {
            return figees.stream().filter(p -> p.getAssocieId().equals(associeId)).map(PartMensuelle::getPart)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal estimation(Long associeId) {
            AssocieService.BeneficeAssocie b = moisEnCours.get(associeId);
            return b == null ? BigDecimal.ZERO : b.part();
        }
    }

    private Etat etat() {
        figerMoisTermines(true);
        YearMonth courant = YearMonth.now(FUSEAU);
        AssocieService.Benefices b = associes.benefices(debut(courant), debut(courant.plusMonths(1)));
        return new Etat(parts.findAllByOrderByMoisAsc(), b.associes().stream()
                .collect(Collectors.toMap(AssocieService.BeneficeAssocie::associeId, x -> x, (x, y) -> x)));
    }

    /**
     * Fige les mois termines qui ne le sont pas encore, depuis le mois de la
     * premiere vente : la part de chaque associe, avec ses rattachements du moment.
     */
    void figerMoisTermines(boolean reessai) {
        Instant premiere = ventes.premiereVente();
        if (premiere == null) {
            return;
        }
        YearMonth courant = YearMonth.now(FUSEAU);
        Set<LocalDate> deja = new HashSet<>();
        clotures.findAllByOrderByMoisAsc().forEach(c -> deja.add(c.getMois()));
        List<ClotureAssocies> nouveaux = new ArrayList<>();
        List<PartMensuelle> lignes = new ArrayList<>();
        for (YearMonth m = YearMonth.from(premiere.atZone(FUSEAU)); m.isBefore(courant); m = m.plusMonths(1)) {
            if (deja.contains(m.atDay(1))) {
                continue;
            }
            ClotureAssocies c = new ClotureAssocies();
            c.setMois(m.atDay(1));
            nouveaux.add(c);
            for (AssocieService.BeneficeAssocie a : associes.benefices(debut(m), debut(m.plusMonths(1))).associes()) {
                if (a.part().signum() == 0 && a.chiffreAffaires().signum() == 0) {
                    continue;
                }
                PartMensuelle p = new PartMensuelle();
                p.setAssocieId(a.associeId());
                p.setMois(m.atDay(1));
                p.setChiffreAffaires(a.chiffreAffaires());
                p.setMarge(a.marge());
                p.setPart(a.part());
                lignes.add(p);
            }
        }
        if (nouveaux.isEmpty()) {
            return;
        }
        try {
            cloture.figer(nouveaux, lignes);
        } catch (DataIntegrityViolationException e) {
            // Une autre lecture vient de figer les memes mois : on reprend ce qui reste.
            if (!reessai) {
                throw e;
            }
            figerMoisTermines(false);
        }
    }

    /**
     * Controles communs a la saisie et a la correction : montant dans la limite
     * du reste a verser (hors ce depot), coffre du gerant suffisant, bordereau.
     */
    private void appliquer(DepotAssocie d, DepotRequete r, UtilisateurPrincipal auteur, BigDecimal dejaEngage) {
        BigDecimal montant = r.montant().setScale(2, RoundingMode.HALF_UP);
        figerMoisTermines(true);
        BigDecimal closes = parts.findAllByOrderByMoisAsc().stream()
                .filter(p -> p.getAssocieId().equals(d.getAssocieId())).map(PartMensuelle::getPart)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reste = closes.subtract(depots.total(d.getAssocieId(), ENGAGES)).add(dejaEngage);
        if (montant.compareTo(reste) > 0) {
            throw new BusinessException("Il ne reste a verser a " + d.getAssocieNom() + " que "
                    + FondsReserveService.entier(reste.max(BigDecimal.ZERO))
                    + " F sur les mois termines (le mois en cours n'est verse qu'a sa fin)");
        }
        if (r.dateDepot().isAfter(LocalDate.now(FUSEAU))) {
            throw new BusinessException("La date du bordereau ne peut pas etre dans le futur");
        }
        if (r.source() == DepotAssocie.Source.COFFRE) {
            // Le montant deja engage par ce depot sur le meme coffre lui est rendu le temps du controle.
            BigDecimal rendu = d.getSource() == DepotAssocie.Source.COFFRE && auteur.getId().equals(d.getCoffreId())
                    ? dejaEngage : BigDecimal.ZERO;
            d.setCoffreId(tresorerie.exigerCoffre(auteur, montant.subtract(rendu)));
        } else {
            d.setCoffreId(null);
        }
        d.setMontant(montant);
        d.setSource(r.source());
        d.setBanque(propre(r.banque()));
        d.setReference(r.reference().trim());
        d.setDateDepot(r.dateDepot());
        d.setPeriode(propre(r.periode()));
        d.setNote(propre(r.note()));
        d.setAuteurId(auteur.getId());
        d.setAuteurNom(auteur.getNomComplet());
        d.setSoumisLe(Instant.now());
    }

    /** Depot que le gerant peut encore modifier : en attente ou refuse ; un gerant ne touche qu'aux siens. */
    private DepotAssocie modifiable(Long id, UtilisateurPrincipal auteur) {
        DepotAssocie d = depots.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depot", id));
        if (!A_TRAITER.contains(d.getStatut())) {
            throw new BusinessException("Le depot " + d.getNumero() + " est " + (d.getStatut() == DepotAssocie.Statut.VALIDE
                    ? "deja valide par l'associe" : "annule") + " : il ne peut plus etre modifie");
        }
        if (!administrateur(auteur) && !d.getAuteurId().equals(auteur.getId())) {
            throw new BusinessException("Seul l'auteur du depot ou un administrateur peut le modifier");
        }
        return d;
    }

    /** Depot en attente adresse a l'associe connecte. */
    private DepotAssocie attenteDe(Long id, UtilisateurPrincipal associe) {
        DepotAssocie d = depots.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depot", id));
        if (!d.getAssocieId().equals(associe.getId())) {
            throw ResourceNotFoundException.of("Depot", id);
        }
        if (d.getStatut() != DepotAssocie.Statut.EN_ATTENTE) {
            throw new BusinessException("Le depot " + d.getNumero() + " n'attend pas votre decision");
        }
        return d;
    }

    private static boolean administrateur(UtilisateurPrincipal u) {
        return u.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.ADMIN.authority()));
    }

    private static boolean gestionnaire(UtilisateurPrincipal u) {
        return u.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.ADMIN.authority())
                || a.getAuthority().equals(Role.GERANT.authority()));
    }

    private static Instant debut(YearMonth m) {
        return m.atDay(1).atStartOfDay(FUSEAU).toInstant();
    }

    private static String libelle(LocalDate jour) {
        return MOIS[jour.getMonthValue() - 1] + " " + jour.getYear();
    }

    private static String propre(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
