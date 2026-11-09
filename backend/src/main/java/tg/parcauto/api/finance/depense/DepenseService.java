package tg.parcauto.api.finance.depense;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.exploitation.location.LocationService;
import tg.parcauto.api.exploitation.mission.MissionService;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionRepository;
import tg.parcauto.api.parc.camion.CamionService;
import tg.parcauto.api.parc.chauffeur.ChauffeurService;
import tg.parcauto.api.parc.document.StockageFichierService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Depenses du parc : depenses des vehicules et charges generales de
 * l'entreprise. Avec la validation activee (reglage), une depense saisie par le
 * gestionnaire attend l'administrateur avant de compter. Une depense ne se
 * supprime plus : elle s'annule avec un motif. Une depense a payer est la dette
 * envers son fournisseur, soldee par ses reglements.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DepenseService {

    private static final long TAILLE_MAX_JUSTIFICATIF = 15L * 1024 * 1024;

    private final DepenseRepository repository;
    private final CamionService camionService;
    private final CamionRepository camionRepository;
    private final ChauffeurService chauffeurService;
    private final MissionService missionService;
    private final LocationService locationService;
    private final NumerotationService numerotation;
    private final CategorieChargeRepository categoriesCharge;
    private final FournisseurParcRepository fournisseurs;
    private final ReglementFournisseurParcRepository reglements;
    private final DepenseRecurrenteParcRepository recurrentes;
    private final ReglageParcRepository reglages;
    private final StockageFichierService stockage;

    // ---------------------------------------------------------------- lecture

    @Transactional(readOnly = true)
    public Page<DepenseDto.Reponse> rechercher(String q, CategorieDepense categorie,
                                               SousCategorieDepense sousCategorie, Long camionId,
                                               Long chauffeurId, LocalDate debut, LocalDate fin,
                                               Pageable pageable) {
        // Usage interne (rapports) : seules les depenses validees.
        return rechercher(q, categorie, sousCategorie, camionId, chauffeurId, debut, fin, StatutDepense.VALIDEE, null, null, pageable);
    }

    /** Recherche de l'ecran : statut (nul = tous), type VEHICULE ou GENERALE, categorie de charge. */
    @Transactional(readOnly = true)
    public Page<DepenseDto.Reponse> rechercher(String q, CategorieDepense categorie,
                                               SousCategorieDepense sousCategorie, Long camionId,
                                               Long chauffeurId, LocalDate debut, LocalDate fin,
                                               StatutDepense statut, String type, Long categorieChargeId,
                                               Pageable pageable) {
        Page<Depense> page = repository.findAll(DepenseSpecifications.rechercher(q, categorie, sousCategorie, camionId,
                chauffeurId, debut, fin, statut == null ? List.of() : List.of(statut), type, categorieChargeId), pageable);
        Map<Long, BigDecimal> regles = regles(page.getContent().stream().filter(Depense::isAPayer).map(Depense::getId).toList());
        return page.map(d -> DepenseDto.Reponse.de(d, regles.get(d.getId())));
    }

    @Transactional(readOnly = true)
    public DepenseDto.Reponse parId(Long id) {
        return reponse(charger(id));
    }

    /** Reponses aux questions du chapitre 17, au niveau des categories (depenses validees). */
    @Transactional(readOnly = true)
    public List<DepenseDto.LigneCategorie> repartitionParCategorie(LocalDate debut, LocalDate fin,
                                                                   Long camionId) {
        var lignes = camionId == null
                ? repository.totauxParCategorie(debut, fin)
                : repository.totauxParCategoriePourCamion(camionId, debut, fin);
        return lignes.stream()
                .map(l -> new DepenseDto.LigneCategorie(l.getCategorie(), l.getCategorie().getLibelle(),
                        l.getTotal(), l.getNombre()))
                .sorted((a, b) -> b.total().compareTo(a.total()))
                .toList();
    }

    /** Detail par nature pour un camion : vidange, pneus, peage, assurance... */
    @Transactional(readOnly = true)
    public List<DepenseDto.LigneNature> repartitionParNature(Long camionId, LocalDate debut, LocalDate fin) {
        return repository.totauxParNaturePourCamion(camionId, debut, fin).stream()
                .map(l -> new DepenseDto.LigneNature(l.getSousCategorie(), l.getSousCategorie().getLibelle(),
                        l.getSousCategorie().getCategorie().getLibelle(), l.getTotal(), l.getNombre()))
                .sorted((a, b) -> b.total().compareTo(a.total()))
                .toList();
    }

    /** Charges generales validees de la periode, par categorie modifiable. */
    @Transactional(readOnly = true)
    public List<DepenseDto.LigneCharge> repartitionCharges(LocalDate debut, LocalDate fin) {
        return repository.chargesParCategorie(debut, fin).stream()
                .map(l -> new DepenseDto.LigneCharge((Long) l[0], (String) l[1], (String) l[2], (BigDecimal) l[3], (Long) l[4]))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal totalChargesGenerales(LocalDate debut, LocalDate fin) {
        return repository.totalChargesGenerales(debut, fin);
    }

    // ---------------------------------------------------------------- saisie

    public DepenseDto.Reponse creer(DepenseDto.Requete requete) {
        Depense depense = new Depense();
        depense.setNumero(numerotation.prochainNumero("DEP"));
        appliquer(depense, requete);
        if (requete.recurrenteId() != null) {
            DepenseRecurrenteParc modele = recurrentes.findById(requete.recurrenteId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Depense recurrente", requete.recurrenteId()));
            String periode = requete.periode() == null || requete.periode().isBlank()
                    ? YearMonth.from(requete.dateDepense()).toString() : YearMonth.parse(requete.periode().trim()).toString();
            if (repository.moisDejaPaye(modele.getId(), periode)) {
                throw new BusinessException(modele.getLibelle() + " est deja payee pour " + periode);
            }
            depense.setRecurrente(modele);
            depense.setPeriode(periode);
        }
        UtilisateurPrincipal auteur = courant();
        depense.setAuteurNom(auteur == null ? null : auteur.getNomComplet());
        depense.setStatut(validationRequise() ? StatutDepense.ATTENTE_ADMIN : StatutDepense.VALIDEE);
        if (depense.getStatut() == StatutDepense.VALIDEE && auteur != null && administrateur()) {
            depense.setApprouveePar(auteur.getNomComplet());
            depense.setApprouveeLe(Instant.now());
        }
        Depense enregistree = repository.save(depense);
        BigDecimal acompte = requete.acompte();
        if (enregistree.isAPayer() && acompte != null && acompte.signum() > 0) {
            if (acompte.compareTo(enregistree.getMontant()) > 0) {
                throw new BusinessException("L'acompte depasse le montant de la depense");
            }
            ReglementFournisseurParc r = new ReglementFournisseurParc();
            r.setNumero(numerotation.prochainNumero("RGF"));
            r.setDepense(enregistree);
            r.setMontant(acompte.setScale(2, RoundingMode.HALF_UP));
            r.setDateReglement(enregistree.getDateDepense());
            r.setModePaiement(enregistree.getModePaiement());
            r.setNote("Acompte verse a la saisie");
            r.setAuteurNom(auteur == null ? "Systeme" : auteur.getNomComplet());
            reglements.save(r);
        }
        mettreAJourCompteurCamion(enregistree);
        return reponse(enregistree);
    }

    /**
     * Correction d'une depense active. Avec la validation activee, une depense
     * deja validee que le gestionnaire corrige repart chez l'administrateur.
     */
    public DepenseDto.Reponse modifier(Long id, DepenseDto.Requete requete) {
        Depense depense = chargerActive(id);
        appliquer(depense, requete);
        BigDecimal regle = depense.isAPayer() ? reglements.regle(id) : BigDecimal.ZERO;
        if (!depense.isAPayer() && reglements.regle(id).signum() > 0) {
            throw new BusinessException("Des reglements sont enregistres : la depense reste a payer");
        }
        if (regle.compareTo(depense.getMontant()) > 0) {
            throw new BusinessException("Le montant ne peut pas etre inferieur au total deja regle ("
                    + regle.setScale(0, RoundingMode.HALF_UP).toPlainString() + " F)");
        }
        if (depense.getStatut() == StatutDepense.VALIDEE && validationRequise()) {
            depense.setStatut(StatutDepense.ATTENTE_ADMIN);
            depense.setApprouveePar(null);
            depense.setApprouveeLe(null);
        }
        return reponse(repository.save(depense));
    }

    /** Annulation avec motif : la depense reste visible mais ne compte plus. */
    public DepenseDto.Reponse annuler(Long id, String motif) {
        Depense depense = chargerActive(id);
        if (reglements.regle(id).signum() > 0) {
            throw new BusinessException("Des reglements sont enregistres sur " + depense.getNumero() + " : annulez-les d'abord");
        }
        UtilisateurPrincipal auteur = courant();
        depense.setStatut(StatutDepense.ANNULEE);
        depense.setAnnuleePar(auteur == null ? null : auteur.getNomComplet());
        depense.setAnnuleeLe(Instant.now());
        depense.setMotifAnnulation(motif.trim());
        return reponse(depense);
    }

    // ---------------------------------------------------- validation (admin)

    @Transactional(readOnly = true)
    public List<DepenseDto.Reponse> aValider() {
        List<Depense> liste = repository.enAttente();
        Map<Long, BigDecimal> regles = regles(liste.stream().filter(Depense::isAPayer).map(Depense::getId).toList());
        return liste.stream().map(d -> DepenseDto.Reponse.de(d, regles.get(d.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public long nombreAValider() {
        return repository.countByStatut(StatutDepense.ATTENTE_ADMIN);
    }

    /** Approbation de l'administrateur : la depense compte dans les resultats. */
    public DepenseDto.Reponse valider(Long id) {
        Depense depense = enAttente(id);
        UtilisateurPrincipal admin = courant();
        depense.setStatut(StatutDepense.VALIDEE);
        depense.setApprouveePar(admin == null ? null : admin.getNomComplet());
        depense.setApprouveeLe(Instant.now());
        return reponse(depense);
    }

    /** Refus de l'administrateur : un acompte saisi avec la depense est annule avec elle. */
    public DepenseDto.Reponse refuser(Long id, String motif) {
        Depense depense = enAttente(id);
        UtilisateurPrincipal admin = courant();
        String nom = admin == null ? null : admin.getNomComplet();
        for (ReglementFournisseurParc r : reglements.findByDepenseIdOrderByDateReglementAscIdAsc(id)) {
            if (r.getStatut() == ReglementFournisseurParc.Statut.VALIDE) {
                r.setStatut(ReglementFournisseurParc.Statut.ANNULE);
                r.setAnnuleLe(Instant.now());
                r.setAnnulePar(nom);
                r.setMotifAnnulation("Depense refusee : " + motif.trim());
            }
        }
        depense.setStatut(StatutDepense.REFUSEE);
        depense.setRefuseePar(nom);
        depense.setRefuseeLe(Instant.now());
        depense.setMotifRefus(motif.trim());
        return reponse(depense);
    }

    // ---------------------------------------------------------- justificatif

    public DepenseDto.Reponse joindre(Long id, MultipartFile fichier) {
        Depense d = charger(id);
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException("Fichier vide");
        }
        if (fichier.getSize() > TAILLE_MAX_JUSTIFICATIF) {
            throw new BusinessException("Le justificatif depasse 15 Mo");
        }
        String ancien = d.getJustificatifChemin();
        d.setJustificatifChemin(stockage.enregistrer(fichier, "parc-depenses/" + d.getId()));
        d.setJustificatifNom(fichier.getOriginalFilename() == null ? "justificatif" : fichier.getOriginalFilename());
        d.setJustificatifType(fichier.getContentType());
        if (ancien != null) {
            stockage.supprimer(ancien);
        }
        return reponse(d);
    }

    @Transactional(readOnly = true)
    public Resource justificatif(Long id) {
        Depense d = charger(id);
        if (d.getJustificatifChemin() == null) {
            throw new ResourceNotFoundException("Aucun justificatif pour la depense " + d.getNumero());
        }
        return stockage.charger(d.getJustificatifChemin());
    }

    @Transactional(readOnly = true)
    public String typeJustificatif(Long id) {
        return charger(id).getJustificatifType();
    }

    @Transactional(readOnly = true)
    public String nomJustificatif(Long id) {
        return charger(id).getJustificatifNom();
    }

    public DepenseDto.Reponse retirerJustificatif(Long id) {
        Depense d = charger(id);
        if (d.getJustificatifChemin() != null) {
            stockage.supprimer(d.getJustificatifChemin());
        }
        d.setJustificatifChemin(null);
        d.setJustificatifNom(null);
        d.setJustificatifType(null);
        return reponse(d);
    }

    // ---------------------------------------------------------------- outils

    @Transactional(readOnly = true)
    public Depense charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depense", id));
    }

    /** Reponse avec le total regle d'une depense a payer. */
    DepenseDto.Reponse reponse(Depense d) {
        return DepenseDto.Reponse.de(d, d.isAPayer() ? reglements.regle(d.getId()) : null);
    }

    Map<Long, BigDecimal> regles(Collection<Long> ids) {
        Map<Long, BigDecimal> resultat = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] l : reglements.regles(ids)) {
                resultat.put((Long) l[0], (BigDecimal) l[1]);
            }
        }
        return resultat;
    }

    /** La validation est activee et l'utilisateur n'est pas administrateur. */
    boolean validationRequise() {
        return reglages.findFirstByOrderByIdAsc().map(ReglageParc::isValidationDepenses).orElse(false) && !administrateur();
    }

    static boolean administrateur() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    static UtilisateurPrincipal courant() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UtilisateurPrincipal u ? u : null;
    }

    private Depense chargerActive(Long id) {
        Depense d = charger(id);
        if (!d.getStatut().active()) {
            throw new BusinessException("La depense " + d.getNumero() + " est " + d.getStatut().getLibelle().toLowerCase());
        }
        return d;
    }

    private Depense enAttente(Long id) {
        Depense d = charger(id);
        if (d.getStatut() != StatutDepense.ATTENTE_ADMIN) {
            throw new BusinessException("La depense " + d.getNumero() + " n'attend pas de validation");
        }
        return d;
    }

    /** Un releve de compteur saisi lors d'un plein fait avancer le kilometrage du camion. */
    private void mettreAJourCompteurCamion(Depense depense) {
        Long releve = depense.getKilometrage();
        Camion camion = depense.getCamion();
        if (camion != null && releve != null && releve > camion.getKilometrageActuel()) {
            camion.setKilometrageActuel(releve);
            camionRepository.save(camion);
        }
    }

    private void appliquer(Depense d, DepenseDto.Requete r) {
        if (r.dateDepense().isAfter(LocalDate.now().plusDays(1))) {
            throw new BusinessException("La date de la depense ne peut pas etre dans le futur");
        }
        if (r.categorieChargeId() != null) {
            // Charge generale : pas de vehicule, ni de chauffeur, mission ou plein.
            if (r.camionId() != null) {
                throw new BusinessException("Une charge generale ne porte pas sur un vehicule");
            }
            CategorieCharge categorie = categoriesCharge.findById(r.categorieChargeId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Categorie de charge", r.categorieChargeId()));
            if (!categorie.isActif() && (d.getCategorieCharge() == null || !d.getCategorieCharge().getId().equals(categorie.getId()))) {
                throw new BusinessException("La categorie " + categorie.getNom() + " est desactivee");
            }
            d.setCamion(null);
            d.setChauffeur(null);
            d.setMission(null);
            d.setLocation(null);
            d.setCategorieCharge(categorie);
            d.setSousCategorie(SousCategorieDepense.CHARGE_GENERALE);
            d.setKilometrage(null);
            d.setStation(null);
            d.setQuantiteLitres(null);
            d.setPrixLitre(null);
            d.setAChargeClient(false);
        } else {
            if (r.camionId() == null) {
                throw new BusinessException("Choisissez le vehicule, ou la categorie pour une charge generale");
            }
            if (r.sousCategorie() == null || r.sousCategorie() == SousCategorieDepense.CHARGE_GENERALE) {
                throw new BusinessException("Choisissez la nature de la depense du vehicule");
            }
            Camion camion = camionService.charger(r.camionId());
            d.setCamion(camion);
            d.setCategorieCharge(null);
            d.setChauffeur(r.chauffeurId() == null ? null : chauffeurService.charger(r.chauffeurId()));
            d.setMission(r.missionId() == null ? null : missionService.charger(r.missionId()));
            d.setLocation(r.locationId() == null ? null : locationService.charger(r.locationId()));
            d.setSousCategorie(r.sousCategorie());
            d.setKilometrage(r.kilometrage());
            d.setStation(r.station());
            d.setQuantiteLitres(r.quantiteLitres());
            d.setPrixLitre(r.prixLitre());
            d.setAChargeClient(Boolean.TRUE.equals(r.aChargeClient()));
            if (r.sousCategorie().estCarburant()) {
                if (r.quantiteLitres() == null) {
                    throw new BusinessException("La quantite en litres est obligatoire pour un plein de carburant");
                }
                if (r.kilometrage() == null) {
                    throw new BusinessException(
                            "Le releve du compteur est obligatoire pour un plein : il conditionne le calcul de consommation");
                }
            }
            if (d.getMission() != null && !d.getMission().getCamion().getId().equals(camion.getId())) {
                throw new BusinessException("La mission " + d.getMission().getNumero()
                        + " porte sur un autre vehicule que celui de la depense");
            }
        }
        d.synchroniserCategorie();
        d.setLibelle(r.libelle().trim());
        d.setMontant(r.montant());
        d.setDateDepense(r.dateDepense());
        d.setModePaiement(r.modePaiement());
        d.setReference(r.reference());
        d.setJustificatif(r.justificatif());
        d.setObservations(r.observations());

        FournisseurParc fournisseur = fournisseur(r.fournisseurId(), r.fournisseur());
        d.setFournisseurParc(fournisseur);
        d.setFournisseur(fournisseur != null ? fournisseur.getNom() : vide(r.fournisseur()));

        boolean aPayer = Boolean.TRUE.equals(r.aPayer());
        if (aPayer && fournisseur == null) {
            throw new BusinessException("Une depense a payer est une dette : choisissez le fournisseur");
        }
        d.setAPayer(aPayer);
        d.setEcheance(!aPayer ? null : r.echeance() != null ? r.echeance()
                : r.dateDepense().plusDays(fournisseur.getDelaiPaiementJours()));
    }

    /** Fiche choisie, ou retrouvee (creee au besoin) a partir du nom saisi. */
    private FournisseurParc fournisseur(Long id, String nom) {
        if (id != null) {
            return fournisseurs.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Fournisseur", id));
        }
        String n = vide(nom);
        if (n == null) {
            return null;
        }
        return fournisseurs.findFirstByNomIgnoreCase(n).orElseGet(() -> {
            FournisseurParc f = new FournisseurParc();
            f.setNom(n.length() <= 180 ? n : n.substring(0, 180));
            return fournisseurs.save(f);
        });
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
