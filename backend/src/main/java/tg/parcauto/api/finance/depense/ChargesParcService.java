package tg.parcauto.api.finance.depense;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.finance.commun.ModePaiement;
import tg.parcauto.api.parc.camion.CamionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Charges du parc : categories modifiables des charges generales, depenses
 * recurrentes proposees chaque mois, et le reglage de validation.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChargesParcService {

    /** Mois en arriere examines pour les recurrentes restees impayees. */
    private static final int MOIS_EN_ARRIERE = 12;

    private static final String[][] CATEGORIES_DE_BASE = {
            {"Loyer", "🏠"}, {"Salaires du personnel", "👥"}, {"Electricite", "⚡"}, {"Eau", "💧"},
            {"Internet et telephone", "📶"}, {"Fournitures de bureau", "📦"}, {"Impots et taxes", "🏛"},
            {"Frais bancaires et mobile money", "🏦"}, {"Assurances (hors vehicules)", "🛡"},
            {"Marketing et publicite", "📣"}, {"Securite et gardiennage", "🔒"}, {"Autres charges", "🧾"}
    };

    private final CategorieChargeRepository categories;
    private final DepenseRecurrenteParcRepository recurrentes;
    private final DepenseRepository depenses;
    private final FournisseurParcRepository fournisseurs;
    private final ReglageParcRepository reglages;
    private final CamionService camionService;
    private final DepenseService depenseService;

    // ------------------------------------------------------------------ dto

    public record CategorieRequete(@NotBlank @Size(max = 80) String nom, @Size(max = 16) String icone,
                                   Integer ordre, Boolean actif) {
    }

    public record CategorieReponse(Long id, String nom, String icone, int ordre, boolean actif, long depenses) {
    }

    public record RecurrenteRequete(
            @NotBlank @Size(max = 150) String libelle,
            /** Vehicule et nature, ou categorie de charge generale. */
            Long camionId, SousCategorieDepense sousCategorie, Long categorieChargeId,
            Long fournisseurId,
            @NotNull @Positive BigDecimal montant,
            @Min(1) @Max(28) Integer jourEcheance,
            ModePaiement modePaiement,
            /** Premier mois du (AAAA-MM). */
            @NotBlank @Size(min = 7, max = 7) String debut,
            /** Dernier mois du (AAAA-MM), vide si sans fin. */
            @Size(max = 7) String fin,
            Boolean actif,
            @Size(max = 255) String note) {
    }

    public record RecurrenteReponse(Long id, String libelle, Long camionId, String camionImmatriculation,
                                    SousCategorieDepense sousCategorie, String sousCategorieLibelle,
                                    Long categorieChargeId, String categorieChargeNom, String categorieChargeIcone,
                                    Long fournisseurId, String fournisseurNom, BigDecimal montant, int jourEcheance,
                                    ModePaiement modePaiement, String debut, String fin, boolean actif, String note,
                                    long paiements) {
    }

    /** Mois d'une depense recurrente encore a payer. */
    public record APayer(Long recurrenteId, String libelle, String cible, BigDecimal montant, ModePaiement modePaiement,
                         String fournisseurNom, String periode, String periodeLibelle, LocalDate echeance,
                         boolean enRetard) {
    }

    /** Paiement d'un mois : la depense est creee depuis le modele. */
    public record Paiement(@NotBlank @Size(min = 7, max = 7) String periode, LocalDate dateDepense,
                           @Positive BigDecimal montant, ModePaiement modePaiement, @Size(max = 80) String reference,
                           /** A payer plus tard : dette envers le fournisseur du modele. */
                           Boolean aPayer) {
    }

    public record Reglages(boolean validationDepenses) {
    }

    // ------------------------------------------------------------ reglages

    @Transactional(readOnly = true)
    public Reglages reglages() {
        return new Reglages(reglages.findFirstByOrderByIdAsc().map(ReglageParc::isValidationDepenses).orElse(false));
    }

    public Reglages modifierReglages(Reglages r) {
        ReglageParc reglage = reglages.findFirstByOrderByIdAsc().orElseGet(ReglageParc::new);
        reglage.setValidationDepenses(r.validationDepenses());
        reglages.save(reglage);
        return reglages();
    }

    // ----------------------------------------------------------- categories

    /** Categories de l'entreprise ; la liste de base est creee a la premiere consultation. */
    public List<CategorieReponse> categories() {
        List<CategorieCharge> liste = categories.findAllByOrderByOrdreAscNomAsc();
        if (liste.isEmpty()) {
            int ordre = 0;
            for (String[] base : CATEGORIES_DE_BASE) {
                CategorieCharge c = new CategorieCharge();
                c.setNom(base[0]);
                c.setIcone(base[1]);
                c.setOrdre(ordre += 10);
                categories.save(c);
            }
            categories.flush();
            liste = categories.findAllByOrderByOrdreAscNomAsc();
        }
        return liste.stream().map(this::versDto).toList();
    }

    public CategorieReponse enregistrerCategorie(Long id, CategorieRequete r) {
        String nom = r.nom().trim();
        if (id == null ? categories.existsByNomIgnoreCase(nom) : categories.existsByNomIgnoreCaseAndIdNot(nom, id)) {
            throw new BusinessException("La categorie " + nom + " existe deja");
        }
        CategorieCharge c = id == null ? new CategorieCharge()
                : categories.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Categorie de charge", id));
        c.setNom(nom);
        c.setIcone(r.icone() == null || r.icone().isBlank() ? null : r.icone().trim());
        if (r.ordre() != null) {
            c.setOrdre(r.ordre());
        } else if (id == null) {
            c.setOrdre(categories.findAll().stream().mapToInt(CategorieCharge::getOrdre).max().orElse(0) + 10);
        }
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
        return versDto(categories.saveAndFlush(c));
    }

    /** Suppression d'une categorie jamais utilisee ; sinon, on la desactive. */
    public void supprimerCategorie(Long id) {
        CategorieCharge c = categories.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Categorie de charge", id));
        boolean modele = recurrentes.toutes().stream()
                .anyMatch(r -> r.getCategorieCharge() != null && r.getCategorieCharge().getId().equals(id));
        if (depenses.countByCategorieChargeId(id) > 0 || modele) {
            throw new BusinessException("La categorie " + c.getNom() + " est deja utilisee : desactivez-la plutot");
        }
        categories.delete(c);
    }

    private CategorieReponse versDto(CategorieCharge c) {
        return new CategorieReponse(c.getId(), c.getNom(), c.getIcone(), c.getOrdre(), c.isActif(),
                depenses.countByCategorieChargeId(c.getId()));
    }

    // ---------------------------------------------------------- recurrentes

    @Transactional(readOnly = true)
    public List<RecurrenteReponse> recurrentes() {
        return recurrentes.toutes().stream().map(this::versDto).toList();
    }

    public RecurrenteReponse enregistrerRecurrente(Long id, RecurrenteRequete r) {
        DepenseRecurrenteParc m = id == null ? new DepenseRecurrenteParc()
                : recurrentes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depense recurrente", id));
        YearMonth debut = mois(r.debut(), "premier mois");
        YearMonth fin = r.fin() == null || r.fin().isBlank() ? null : mois(r.fin(), "dernier mois");
        if (fin != null && fin.isBefore(debut)) {
            throw new BusinessException("Le dernier mois precede le premier");
        }
        if (r.categorieChargeId() != null) {
            m.setCategorieCharge(categories.findById(r.categorieChargeId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Categorie de charge", r.categorieChargeId())));
            m.setCamion(null);
            m.setSousCategorie(null);
        } else {
            if (r.camionId() == null || r.sousCategorie() == null) {
                throw new BusinessException("Choisissez le vehicule et la nature, ou une categorie de charge generale");
            }
            if (r.sousCategorie().estCarburant() || r.sousCategorie() == SousCategorieDepense.CHARGE_GENERALE) {
                throw new BusinessException("Un plein de carburant ne se programme pas : il se saisit avec son compteur");
            }
            m.setCamion(camionService.charger(r.camionId()));
            m.setSousCategorie(r.sousCategorie());
            m.setCategorieCharge(null);
        }
        m.setFournisseur(r.fournisseurId() == null ? null : fournisseurs.findById(r.fournisseurId())
                .orElseThrow(() -> ResourceNotFoundException.of("Fournisseur", r.fournisseurId())));
        m.setLibelle(r.libelle().trim());
        m.setMontant(r.montant().setScale(2, RoundingMode.HALF_UP));
        m.setJourEcheance(r.jourEcheance() == null ? 1 : r.jourEcheance());
        m.setModePaiement(r.modePaiement());
        m.setDebut(debut.atDay(1));
        m.setFin(fin == null ? null : fin.atDay(1));
        m.setActif(r.actif() == null || r.actif());
        m.setNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
        return versDto(recurrentes.saveAndFlush(m));
    }

    public void supprimerRecurrente(Long id) {
        DepenseRecurrenteParc m = recurrentes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depense recurrente", id));
        if (depenses.countByRecurrenteId(id) > 0) {
            throw new BusinessException("Des paiements sont rattaches a " + m.getLibelle() + " : desactivez-la plutot");
        }
        recurrentes.delete(m);
    }

    /** Mois dus et non payes des recurrentes actives, jusqu'au mois en cours, les plus anciens d'abord. */
    @Transactional(readOnly = true)
    public List<APayer> aPayer() {
        LocalDate aujourdhui = LocalDate.now();
        YearMonth courant = YearMonth.from(aujourdhui);
        Set<String> payes = new HashSet<>();
        for (Object[] l : depenses.moisPayes()) {
            payes.add(l[0] + "/" + l[1]);
        }
        List<APayer> dus = new ArrayList<>();
        for (DepenseRecurrenteParc m : recurrentes.toutes()) {
            if (!m.isActif()) {
                continue;
            }
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
                dus.add(new APayer(m.getId(), m.getLibelle(), cible(m), m.getMontant(), m.getModePaiement(),
                        m.getFournisseur() == null ? null : m.getFournisseur().getNom(),
                        mois.toString(), libelleMois(mois), echeance, echeance.isBefore(aujourdhui)));
            }
        }
        dus.sort(Comparator.comparing(APayer::echeance).thenComparing(APayer::libelle));
        return dus;
    }

    /** Paie un mois d'une recurrente : la depense est creee depuis le modele (et suit la validation). */
    public DepenseDto.Reponse payer(Long id, Paiement p) {
        DepenseRecurrenteParc m = recurrentes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depense recurrente", id));
        YearMonth mois = mois(p.periode(), "mois");
        boolean aPayer = Boolean.TRUE.equals(p.aPayer());
        if (aPayer && m.getFournisseur() == null) {
            throw new BusinessException("Le modele " + m.getLibelle() + " n'a pas de fournisseur : il ne peut pas rester a payer");
        }
        LocalDate date = p.dateDepense() == null ? LocalDate.now() : p.dateDepense();
        var requete = new DepenseDto.Requete(
                m.getCamion() == null ? null : m.getCamion().getId(), null, null, null,
                m.getSousCategorie(), m.getLibelle() + " - " + libelleMois(mois),
                p.montant() == null ? m.getMontant() : p.montant(), date, null,
                p.modePaiement() == null ? m.getModePaiement() : p.modePaiement(), p.reference(), null,
                null, null, null, null, false, m.getNote(),
                m.getCategorieCharge() == null ? null : m.getCategorieCharge().getId(),
                m.getFournisseur() == null ? null : m.getFournisseur().getId(),
                aPayer, null, null, m.getId(), mois.toString());
        return depenseService.creer(requete);
    }

    private RecurrenteReponse versDto(DepenseRecurrenteParc m) {
        return new RecurrenteReponse(m.getId(), m.getLibelle(),
                m.getCamion() == null ? null : m.getCamion().getId(),
                m.getCamion() == null ? null : m.getCamion().getImmatriculation(),
                m.getSousCategorie(), m.getSousCategorie() == null ? null : m.getSousCategorie().getLibelle(),
                m.getCategorieCharge() == null ? null : m.getCategorieCharge().getId(),
                m.getCategorieCharge() == null ? null : m.getCategorieCharge().getNom(),
                m.getCategorieCharge() == null ? null : m.getCategorieCharge().getIcone(),
                m.getFournisseur() == null ? null : m.getFournisseur().getId(),
                m.getFournisseur() == null ? null : m.getFournisseur().getNom(),
                m.getMontant(), m.getJourEcheance(), m.getModePaiement(),
                YearMonth.from(m.getDebut()).toString(), m.getFin() == null ? null : YearMonth.from(m.getFin()).toString(),
                m.isActif(), m.getNote(), depenses.countByRecurrenteId(m.getId()));
    }

    private static String cible(DepenseRecurrenteParc m) {
        return m.getCamion() != null
                ? m.getCamion().getImmatriculation() + " · " + m.getSousCategorie().getLibelle()
                : (m.getCategorieCharge().getIcone() == null ? "" : m.getCategorieCharge().getIcone() + " ") + m.getCategorieCharge().getNom();
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
}
