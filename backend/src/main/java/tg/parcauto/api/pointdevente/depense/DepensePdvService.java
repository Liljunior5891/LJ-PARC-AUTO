package tg.parcauto.api.pointdevente.depense;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.parc.document.StockageFichierService;
import tg.parcauto.api.pointdevente.caisse.CaisseService;
import tg.parcauto.api.pointdevente.caisse.CircuitDepensesCaisse;
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.fournisseur.Fournisseur;
import tg.parcauto.api.pointdevente.fournisseur.FournisseurRepository;
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
import java.util.Locale;
import java.util.Set;

/**
 * Depenses du point de vente : saisie (depuis la caisse, en especes hors
 * caisse, par banque ou mobile money), justificatif, annulation, categories
 * modifiables et depenses recurrentes proposees chaque mois.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DepensePdvService {

    private static final ZoneId FUSEAU = ZoneId.of("Africa/Lome");
    private static final long TAILLE_MAX_JUSTIFICATIF = 15L * 1024 * 1024;
    /** Mois en arriere examines pour les depenses recurrentes restees impayees. */
    private static final int MOIS_EN_ARRIERE = 12;

    /** Liste de base proposee a chaque entreprise ; l'administrateur la complete ensuite. */
    private static final String[][] CATEGORIES_DE_BASE = {
            {"Salaires", "👥"}, {"Loyer", "🏠"}, {"Electricite", "⚡"}, {"Eau", "💧"},
            {"Internet et telephone", "📶"}, {"Transport et carburant", "⛽"}, {"Entretien et reparations", "🔧"},
            {"Fournitures et emballages", "📦"}, {"Impots et taxes", "🏛"}, {"Marketing et publicite", "📣"},
            {"Frais bancaires et mobile money", "🏦"}, {"Securite et gardiennage", "🛡"}, {"Autres depenses", "🧾"}
    };

    private final DepensePdvRepository repository;
    private final CategorieDepensePdvRepository categorieRepository;
    private final DepenseRecurrenteRepository recurrenteRepository;
    private final FournisseurRepository fournisseurRepository;
    private final CaisseService caisseService;
    private final StockageFichierService stockage;
    private final NumerotationService numerotation;
    private final tg.parcauto.api.pointdevente.tresorerie.TresorerieService tresorerie;
    private final CircuitDepensesCaisse circuit;

    // ------------------------------------------------------------------ dto

    public record CategorieRequete(@NotBlank @Size(max = 80) String nom, @Size(max = 16) String icone,
                                   Integer ordre, Boolean actif) {
    }

    public record CategorieReponse(Long id, String nom, String icone, int ordre, boolean actif, long depenses) {
    }

    public record Requete(
            @NotNull Long categorieId,
            @NotBlank @Size(max = 150) String libelle,
            @Size(max = 150) String beneficiaire,
            Long fournisseurId,
            @NotNull @Positive BigDecimal montant,
            LocalDate dateDepense,
            @NotNull ModeSortie mode,
            /** Session de caisse ouverte, obligatoire pour le mode CAISSE. */
            Long sessionId,
            @Size(max = 80) String reference,
            @Size(max = 500) String note,
            /** Modele recurrent et mois regle (AAAA-MM), pour payer un loyer ou un salaire du mois. */
            Long recurrenteId,
            @Size(max = 7) String periode) {
    }

    /** Ce qui peut changer apres coup ; le montant et le moyen de paiement, eux, passent par une annulation. */
    public record Modification(
            @NotNull Long categorieId,
            @NotBlank @Size(max = 150) String libelle,
            @Size(max = 150) String beneficiaire,
            Long fournisseurId,
            LocalDate dateDepense,
            @Size(max = 80) String reference,
            @Size(max = 500) String note) {
    }

    public record Reponse(Long id, String numero, Long categorieId, String categorieNom, String categorieIcone,
                          String libelle, String beneficiaire, Long fournisseurId, String fournisseurNom,
                          BigDecimal montant, LocalDate dateDepense, ModeSortie mode, String modeLibelle,
                          String reference, String sessionNumero, String caisseNom, Long recurrenteId,
                          String periode, String note, boolean justificatif, String justificatifNom,
                          String auteurNom, Instant creeLe, DepensePdv.Statut statut, Instant annuleeLe,
                          String annuleePar, String motifAnnulation,
                          /** Circuit de validation des depenses de caisse. */
                          String viseePar, Instant viseeLe, String approuveePar, Instant approuveeLe,
                          String refuseePar, Instant refuseeLe, String motifRefus) {

        static Reponse de(DepensePdv d) {
            return new Reponse(d.getId(), d.getNumero(), d.getCategorie().getId(), d.getCategorie().getNom(),
                    d.getCategorie().getIcone(), d.getLibelle(), d.getBeneficiaire(),
                    d.getFournisseur() == null ? null : d.getFournisseur().getId(),
                    d.getFournisseur() == null ? null : d.getFournisseur().getNom(),
                    d.getMontant(), d.getDateDepense(), d.getMode(), d.getMode().getLibelle(), d.getReference(),
                    d.getSession() == null ? null : d.getSession().getNumero(),
                    d.getSession() == null ? null : d.getSession().getCaisse().getNom(),
                    d.getRecurrente() == null ? null : d.getRecurrente().getId(), d.getPeriode(), d.getNote(),
                    d.getJustificatifChemin() != null, d.getJustificatifNom(), d.getAuteurNom(), d.getCreeLe(),
                    d.getStatut(), d.getAnnuleeLe(), d.getAnnuleePar(), d.getMotifAnnulation(),
                    d.getViseePar(), d.getViseeLe(), d.getApprouveePar(), d.getApprouveeLe(),
                    d.getRefuseePar(), d.getRefuseeLe(), d.getMotifRefus());
        }
    }

    public record ParCategorie(Long categorieId, String categorie, String icone, BigDecimal total, long nombre) {
    }

    public record ParMode(ModeSortie mode, String libelle, BigDecimal total, long nombre) {
    }

    public record Synthese(LocalDate debut, LocalDate fin, BigDecimal total, long nombre,
                           List<ParCategorie> parCategorie, List<ParMode> parMode) {
    }

    public record RecurrenteRequete(
            @NotBlank @Size(max = 150) String libelle,
            @NotNull Long categorieId,
            @Size(max = 150) String beneficiaire,
            @NotNull @Positive BigDecimal montant,
            @Min(1) @Max(28) Integer jourEcheance,
            ModeSortie mode,
            /** Premier mois du (AAAA-MM). */
            @NotBlank @Size(min = 7, max = 7) String debut,
            /** Dernier mois du (AAAA-MM), vide si sans fin. */
            @Size(max = 7) String fin,
            Boolean actif,
            @Size(max = 255) String note) {
    }

    public record RecurrenteReponse(Long id, String libelle, Long categorieId, String categorieNom,
                                    String categorieIcone, String beneficiaire, BigDecimal montant, int jourEcheance,
                                    ModeSortie mode, String debut, String fin, boolean actif, String note,
                                    long paiements) {
    }

    /** Mois d'une depense recurrente encore a payer. */
    public record APayer(Long recurrenteId, String libelle, Long categorieId, String categorieNom,
                         String categorieIcone, String beneficiaire, BigDecimal montant, ModeSortie mode,
                         String periode, String periodeLibelle, LocalDate echeance, boolean enRetard) {
    }

    public record MotifAnnulation(@NotBlank @Size(max = 255) String motif) {
    }

    public record MotifRefus(@NotBlank @Size(max = 255) String motif) {
    }

    // ------------------------------------------------------------ categories

    /** Categories de l'entreprise ; la liste de base est creee a la premiere consultation. */
    public List<CategorieReponse> categories() {
        List<CategorieDepensePdv> liste = categorieRepository.findAllByOrderByOrdreAscNomAsc();
        if (liste.isEmpty()) {
            int ordre = 0;
            for (String[] base : CATEGORIES_DE_BASE) {
                CategorieDepensePdv c = new CategorieDepensePdv();
                c.setNom(base[0]);
                c.setIcone(base[1]);
                c.setOrdre(ordre += 10);
                categorieRepository.save(c);
            }
            categorieRepository.flush();
            liste = categorieRepository.findAllByOrderByOrdreAscNomAsc();
        }
        return liste.stream().map(c -> new CategorieReponse(c.getId(), c.getNom(), c.getIcone(), c.getOrdre(),
                c.isActif(), repository.countByCategorieId(c.getId()))).toList();
    }

    public CategorieReponse creerCategorie(CategorieRequete r) {
        if (!categorieRepository.parNom(r.nom()).isEmpty()) {
            throw new BusinessException("La categorie " + r.nom().trim() + " existe deja");
        }
        CategorieDepensePdv c = new CategorieDepensePdv();
        appliquer(c, r);
        if (r.ordre() == null) {
            c.setOrdre(categorieRepository.findAllByOrderByOrdreAscNomAsc().stream()
                    .mapToInt(CategorieDepensePdv::getOrdre).max().orElse(0) + 10);
        }
        categorieRepository.saveAndFlush(c);
        return new CategorieReponse(c.getId(), c.getNom(), c.getIcone(), c.getOrdre(), c.isActif(), 0);
    }

    public CategorieReponse modifierCategorie(Long id, CategorieRequete r) {
        CategorieDepensePdv c = categorieRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Categorie de depense", id));
        if (categorieRepository.parNom(r.nom()).stream().anyMatch(o -> !o.getId().equals(id))) {
            throw new BusinessException("Une autre categorie s'appelle deja " + r.nom().trim());
        }
        appliquer(c, r);
        categorieRepository.flush();
        return new CategorieReponse(c.getId(), c.getNom(), c.getIcone(), c.getOrdre(), c.isActif(),
                repository.countByCategorieId(id));
    }

    /** Suppression d'une categorie jamais utilisee ; sinon, on la desactive. */
    public void supprimerCategorie(Long id) {
        CategorieDepensePdv c = categorieRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Categorie de depense", id));
        if (repository.countByCategorieId(id) > 0
                || recurrenteRepository.toutes().stream().anyMatch(r -> r.getCategorie().getId().equals(id))) {
            throw new BusinessException("La categorie " + c.getNom() + " est deja utilisee : desactivez-la plutot");
        }
        categorieRepository.delete(c);
    }

    // -------------------------------------------------------------- depenses

    @Transactional(readOnly = true)
    public Page<Reponse> rechercher(LocalDate debut, LocalDate fin, Long categorieId, ModeSortie mode,
                                    DepensePdv.Statut statut, String q, int page, int size) {
        LocalDate[] p = periode(debut, fin);
        return repository.rechercher(p[0], p[1], categorieId, mode, statut, motif(q),
                PageRequest.of(page, Math.max(1, Math.min(size, 200)))).map(Reponse::de);
    }

    @Transactional(readOnly = true)
    public Synthese synthese(LocalDate debut, LocalDate fin) {
        LocalDate[] p = periode(debut, fin);
        List<ParCategorie> categories = parCategorie(p[0], p[1]);
        List<ParMode> modes = repository.parMode(p[0], p[1]).stream()
                .map(l -> new ParMode((ModeSortie) l[0], ((ModeSortie) l[0]).getLibelle(), (BigDecimal) l[1], (Long) l[2]))
                .toList();
        BigDecimal total = categories.stream().map(ParCategorie::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        long nombre = categories.stream().mapToLong(ParCategorie::nombre).sum();
        return new Synthese(p[0], p[1], total, nombre, categories, modes);
    }

    @Transactional(readOnly = true)
    public List<ParCategorie> parCategorie(LocalDate debut, LocalDate fin) {
        return repository.parCategorie(debut, fin).stream()
                .map(l -> new ParCategorie((Long) l[0], (String) l[1], (String) l[2], (BigDecimal) l[3], (Long) l[4]))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal total(LocalDate debut, LocalDate fin) {
        return repository.totalPeriode(debut, fin);
    }

    @Transactional(readOnly = true)
    public Reponse parId(Long id) {
        return Reponse.de(charger(id));
    }

    public Reponse creer(Requete r, UtilisateurPrincipal auteur) {
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        LocalDate date = r.dateDepense() == null ? aujourdhui : r.dateDepense();
        if (date.isAfter(aujourdhui)) {
            throw new BusinessException("La date de la depense ne peut pas etre dans le futur");
        }
        CategorieDepensePdv categorie = categorieActive(r.categorieId());
        DepensePdv d = new DepensePdv();
        d.setNumero(numerotation.prochainNumero("DEP"));
        d.setCategorie(categorie);
        d.setLibelle(r.libelle().trim());
        d.setBeneficiaire(vide(r.beneficiaire()));
        d.setFournisseur(fournisseur(r.fournisseurId()));
        d.setMontant(r.montant().setScale(2, RoundingMode.HALF_UP));
        d.setDateDepense(date);
        d.setMode(r.mode());
        d.setReference(vide(r.reference()));
        d.setNote(vide(r.note()));
        d.setAuteurNom(auteur.getNomComplet());
        if (r.recurrenteId() != null) {
            DepenseRecurrente modele = recurrenteRepository.findById(r.recurrenteId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Depense recurrente", r.recurrenteId()));
            String periode = r.periode() == null || r.periode().isBlank()
                    ? YearMonth.from(date).toString() : YearMonth.parse(r.periode().trim()).toString();
            if (repository.moisDejaPaye(modele.getId(), periode)) {
                throw new BusinessException(modele.getLibelle() + " est deja paye pour " + libelleMois(YearMonth.parse(periode)));
            }
            d.setRecurrente(modele);
            d.setPeriode(periode);
        }
        if (!r.mode().propose()) {
            throw new BusinessException("Choisissez la caisse, le coffre ou la banque");
        }
        if (r.mode() == ModeSortie.COFFRE) {
            d.setCoffreId(tresorerie.exigerCoffre(auteur, d.getMontant()));
        }
        if (r.mode() == ModeSortie.CAISSE) {
            if (r.sessionId() == null) {
                throw new BusinessException("Choisissez la caisse ouverte d'ou sortent les especes");
            }
            CircuitDepensesCaisse.Etape etape = circuit.etapeInitiale(auteur);
            if (etape == CircuitDepensesCaisse.Etape.DIRECTE) {
                MouvementCaisse sortie = caisseService.sortie(r.sessionId(), d.getMontant(), motifSortie(d), auteur);
                d.setSession(sortie.getSession());
                d.setMouvementCaisse(sortie);
            } else {
                // Rien ne sort du tiroir avant l'approbation de l'administrateur.
                d.setSession(caisseService.controlerSortie(r.sessionId(), d.getMontant(), auteur));
                if (etape == CircuitDepensesCaisse.Etape.ADMIN) {
                    d.setStatut(DepensePdv.Statut.ATTENTE_ADMIN);
                    d.setViseePar(auteur.getNomComplet());
                    d.setViseeLe(Instant.now());
                } else {
                    d.setStatut(DepensePdv.Statut.ATTENTE_GERANT);
                }
            }
        }
        return Reponse.de(repository.saveAndFlush(d));
    }

    public Reponse modifier(Long id, Modification r) {
        DepensePdv d = chargerValide(id);
        LocalDate date = r.dateDepense() == null ? d.getDateDepense() : r.dateDepense();
        if (date.isAfter(LocalDate.now(FUSEAU))) {
            throw new BusinessException("La date de la depense ne peut pas etre dans le futur");
        }
        if (!d.getCategorie().getId().equals(r.categorieId())) {
            d.setCategorie(categorieActive(r.categorieId()));
        }
        d.setLibelle(r.libelle().trim());
        d.setBeneficiaire(vide(r.beneficiaire()));
        d.setFournisseur(fournisseur(r.fournisseurId()));
        d.setDateDepense(date);
        d.setReference(vide(r.reference()));
        d.setNote(vide(r.note()));
        repository.flush();
        return Reponse.de(d);
    }

    // ---------------------------------------------------- circuit de validation

    /** Depenses de caisse qui attendent une validation de cet utilisateur (gerant : son visa ; administrateur : tout). */
    @Transactional(readOnly = true)
    public List<Reponse> aValider(UtilisateurPrincipal u) {
        List<DepensePdv.Statut> statuts = CircuitDepensesCaisse.administrateur(u)
                ? List.of(DepensePdv.Statut.ATTENTE_GERANT, DepensePdv.Statut.ATTENTE_ADMIN)
                : CircuitDepensesCaisse.gerant(u) ? List.of(DepensePdv.Statut.ATTENTE_GERANT) : List.of();
        return statuts.isEmpty() ? List.of() : repository.enAttente(statuts).stream().map(Reponse::de).toList();
    }

    /**
     * Validation : le gerant vise une depense en attente de son visa ; l'administrateur
     * l'approuve (a l'une ou l'autre etape), ce qui fait sortir les especes de la caisse.
     */
    public Reponse valider(Long id, UtilisateurPrincipal u) {
        DepensePdv d = charger(id);
        if (!d.getStatut().enAttente()) {
            throw new BusinessException("La depense " + d.getNumero() + " n'attend plus de validation");
        }
        Instant maintenant = Instant.now();
        if (!CircuitDepensesCaisse.administrateur(u)) {
            CircuitDepensesCaisse.exigerVisa(u);
            if (d.getStatut() != DepensePdv.Statut.ATTENTE_GERANT) {
                throw new BusinessException("La depense " + d.getNumero() + " attend la validation de l'administrateur");
            }
            d.setStatut(DepensePdv.Statut.ATTENTE_ADMIN);
            d.setViseePar(u.getNomComplet());
            d.setViseeLe(maintenant);
            repository.flush();
            return Reponse.de(d);
        }
        if (d.getViseePar() == null) {
            d.setViseePar(u.getNomComplet());
            d.setViseeLe(maintenant);
        }
        // Les especes sortent maintenant : la depense compte a la date de l'approbation.
        d.setDateDepense(LocalDate.now(FUSEAU));
        MouvementCaisse sortie = caisseService.sortieApprouvee(d.getSession(), d.getMontant(), motifSortie(d), u);
        d.setSession(sortie.getSession());
        d.setMouvementCaisse(sortie);
        d.setStatut(DepensePdv.Statut.VALIDE);
        d.setApprouveePar(u.getNomComplet());
        d.setApprouveeLe(maintenant);
        repository.flush();
        return Reponse.de(d);
    }

    /** Refus : le gerant a l'etape de son visa, l'administrateur a toute etape. Rien n'est sorti de la caisse. */
    public Reponse refuser(Long id, String motif, UtilisateurPrincipal u) {
        DepensePdv d = charger(id);
        if (!d.getStatut().enAttente()) {
            throw new BusinessException("La depense " + d.getNumero() + " n'attend plus de validation");
        }
        if (!CircuitDepensesCaisse.administrateur(u)) {
            CircuitDepensesCaisse.exigerVisa(u);
            if (d.getStatut() != DepensePdv.Statut.ATTENTE_GERANT) {
                throw new BusinessException("La depense " + d.getNumero() + " attend la decision de l'administrateur");
            }
        }
        d.setStatut(DepensePdv.Statut.REFUSEE);
        d.setRefuseePar(u.getNomComplet());
        d.setRefuseeLe(Instant.now());
        d.setMotifRefus(motif.trim());
        repository.flush();
        return Reponse.de(d);
    }

    private static String motifSortie(DepensePdv d) {
        return tronquer("Depense " + d.getNumero() + " - " + d.getCategorie().getNom() + " : " + d.getLibelle());
    }

    /** Annulation : la depense ne compte plus ; payee depuis la caisse, sa sortie est retiree de la session. */
    public Reponse annuler(Long id, String motif, UtilisateurPrincipal auteur) {
        DepensePdv d = chargerValide(id);
        MouvementCaisse sortie = d.getMouvementCaisse();
        if (sortie != null) {
            d.setMouvementCaisse(null);
            repository.flush();
            caisseService.retirerSortie(sortie, "une depense");
        }
        d.setStatut(DepensePdv.Statut.ANNULEE);
        d.setAnnuleeLe(Instant.now());
        d.setAnnuleePar(auteur.getNomComplet());
        d.setMotifAnnulation(motif.trim());
        repository.flush();
        return Reponse.de(d);
    }

    // ---------------------------------------------------------- justificatif

    public Reponse joindre(Long id, MultipartFile fichier) {
        DepensePdv d = charger(id);
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException("Fichier vide");
        }
        if (fichier.getSize() > TAILLE_MAX_JUSTIFICATIF) {
            throw new BusinessException("Le justificatif depasse 15 Mo");
        }
        String ancien = d.getJustificatifChemin();
        d.setJustificatifChemin(stockage.enregistrer(fichier, "pdv-depenses/" + d.getId()));
        d.setJustificatifNom(fichier.getOriginalFilename() == null ? "justificatif" : fichier.getOriginalFilename());
        d.setJustificatifType(fichier.getContentType());
        stockage.supprimer(ancien);
        return Reponse.de(d);
    }

    @Transactional(readOnly = true)
    public Resource justificatif(Long id) {
        DepensePdv d = charger(id);
        if (d.getJustificatifChemin() == null) {
            throw new ResourceNotFoundException("Aucun justificatif pour la depense " + d.getNumero());
        }
        return stockage.charger(d.getJustificatifChemin());
    }

    public Reponse retirerJustificatif(Long id) {
        DepensePdv d = charger(id);
        stockage.supprimer(d.getJustificatifChemin());
        d.setJustificatifChemin(null);
        d.setJustificatifNom(null);
        d.setJustificatifType(null);
        return Reponse.de(d);
    }

    // ---------------------------------------------------------- recurrentes

    @Transactional(readOnly = true)
    public List<RecurrenteReponse> recurrentes() {
        return recurrenteRepository.toutes().stream().map(this::recurrente).toList();
    }

    public RecurrenteReponse creerRecurrente(RecurrenteRequete r) {
        DepenseRecurrente m = new DepenseRecurrente();
        appliquer(m, r);
        return recurrente(recurrenteRepository.saveAndFlush(m));
    }

    public RecurrenteReponse modifierRecurrente(Long id, RecurrenteRequete r) {
        DepenseRecurrente m = recurrenteRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Depense recurrente", id));
        appliquer(m, r);
        recurrenteRepository.flush();
        return recurrente(m);
    }

    public void supprimerRecurrente(Long id) {
        DepenseRecurrente m = recurrenteRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Depense recurrente", id));
        if (repository.countByRecurrenteId(id) > 0) {
            throw new BusinessException("Des paiements sont rattaches a " + m.getLibelle() + " : desactivez-la plutot");
        }
        recurrenteRepository.delete(m);
    }

    /** Mois dus et non payes des depenses recurrentes actives, jusqu'au mois en cours, les plus anciens d'abord. */
    @Transactional(readOnly = true)
    public List<APayer> aPayer() {
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        YearMonth courant = YearMonth.from(aujourdhui);
        Set<String> payes = new HashSet<>();
        for (Object[] l : repository.moisPayes()) {
            payes.add(l[0] + "/" + l[1]);
        }
        List<APayer> dus = new ArrayList<>();
        for (DepenseRecurrente m : recurrenteRepository.actives()) {
            YearMonth premier = YearMonth.from(m.getDebut());
            YearMonth borne = courant.minusMonths(MOIS_EN_ARRIERE - 1L);
            if (premier.isBefore(borne)) {
                premier = borne;
            }
            YearMonth dernier = m.getFin() == null ? courant : YearMonth.from(m.getFin());
            if (dernier.isAfter(courant)) {
                dernier = courant;
            }
            for (YearMonth mois = premier; !mois.isAfter(dernier); mois = mois.plusMonths(1)) {
                if (payes.contains(m.getId() + "/" + mois)) {
                    continue;
                }
                LocalDate echeance = mois.atDay(Math.min(m.getJourEcheance(), mois.lengthOfMonth()));
                dus.add(new APayer(m.getId(), m.getLibelle(), m.getCategorie().getId(), m.getCategorie().getNom(),
                        m.getCategorie().getIcone(), m.getBeneficiaire(), m.getMontant(), m.getMode(),
                        mois.toString(), libelleMois(mois), echeance, echeance.isBefore(aujourdhui)));
            }
        }
        dus.sort(java.util.Comparator.comparing(APayer::echeance).thenComparing(APayer::libelle));
        return dus;
    }

    // ------------------------------------------------------------- internes

    private RecurrenteReponse recurrente(DepenseRecurrente m) {
        return new RecurrenteReponse(m.getId(), m.getLibelle(), m.getCategorie().getId(), m.getCategorie().getNom(),
                m.getCategorie().getIcone(), m.getBeneficiaire(), m.getMontant(), m.getJourEcheance(), m.getMode(),
                YearMonth.from(m.getDebut()).toString(), m.getFin() == null ? null : YearMonth.from(m.getFin()).toString(),
                m.isActif(), m.getNote(), repository.countByRecurrenteId(m.getId()));
    }

    private void appliquer(DepenseRecurrente m, RecurrenteRequete r) {
        YearMonth debut = mois(r.debut(), "premier mois");
        YearMonth fin = r.fin() == null || r.fin().isBlank() ? null : mois(r.fin(), "dernier mois");
        if (fin != null && fin.isBefore(debut)) {
            throw new BusinessException("Le dernier mois precede le premier");
        }
        if (m.getCategorie() == null || !m.getCategorie().getId().equals(r.categorieId())) {
            m.setCategorie(categorieActive(r.categorieId()));
        }
        m.setLibelle(r.libelle().trim());
        m.setBeneficiaire(vide(r.beneficiaire()));
        m.setMontant(r.montant().setScale(2, RoundingMode.HALF_UP));
        m.setJourEcheance(r.jourEcheance() == null ? 1 : r.jourEcheance());
        m.setMode(r.mode());
        m.setDebut(debut.atDay(1));
        m.setFin(fin == null ? null : fin.atDay(1));
        m.setActif(r.actif() == null || r.actif());
        m.setNote(vide(r.note()));
    }

    private static void appliquer(CategorieDepensePdv c, CategorieRequete r) {
        c.setNom(r.nom().trim());
        c.setIcone(vide(r.icone()));
        if (r.ordre() != null) {
            c.setOrdre(r.ordre());
        }
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
    }

    private CategorieDepensePdv categorieActive(Long id) {
        CategorieDepensePdv c = categorieRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Categorie de depense", id));
        if (!c.isActif()) {
            throw new BusinessException("La categorie " + c.getNom() + " est desactivee");
        }
        return c;
    }

    private Fournisseur fournisseur(Long id) {
        return id == null ? null : fournisseurRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Fournisseur", id));
    }

    private DepensePdv charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depense", id));
    }

    private DepensePdv chargerValide(Long id) {
        DepensePdv d = charger(id);
        if (d.getStatut() == DepensePdv.Statut.ANNULEE) {
            throw new BusinessException("La depense " + d.getNumero() + " est annulee");
        }
        if (d.getStatut() == DepensePdv.Statut.REFUSEE) {
            throw new BusinessException("La depense " + d.getNumero() + " a ete refusee");
        }
        return d;
    }

    /** Periode par defaut : le mois en cours. */
    private static LocalDate[] periode(LocalDate debut, LocalDate fin) {
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        LocalDate d = debut == null ? aujourdhui.withDayOfMonth(1) : debut;
        LocalDate f = fin == null ? aujourdhui : fin;
        if (f.isBefore(d)) {
            throw new BusinessException("La fin de la periode precede son debut");
        }
        return new LocalDate[]{d, f};
    }

    private static YearMonth mois(String valeur, String champ) {
        try {
            return YearMonth.parse(valeur.trim());
        } catch (RuntimeException e) {
            throw new BusinessException("Le " + champ + " doit etre au format AAAA-MM");
        }
    }

    private static final String[] MOIS = {"janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout",
            "septembre", "octobre", "novembre", "decembre"};

    static String libelleMois(YearMonth mois) {
        return MOIS[mois.getMonthValue() - 1] + " " + mois.getYear();
    }

    private static String motif(String q) {
        return q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String tronquer(String v) {
        return v.length() <= 255 ? v : v.substring(0, 255);
    }
}
