package tg.parcauto.api.agence.depense;

import jakarta.persistence.criteria.Predicate;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.DroitsAgence;
import tg.parcauto.api.agence.InitialisationAgence;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.PermissionAgence;
import tg.parcauto.api.agence.ReglageAgence;
import tg.parcauto.api.agence.ReglageAgenceRepository;
import tg.parcauto.api.agence.caisse.SessionCaisseService;
import tg.parcauto.api.agence.cloture.VerrouJournee;
import tg.parcauto.api.agence.tresorerie.CompteAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgenceRepository;
import tg.parcauto.api.agence.tresorerie.Ecriture;
import tg.parcauto.api.agence.tresorerie.NatureEcriture;
import tg.parcauto.api.agence.tresorerie.RegistreService;
import tg.parcauto.api.agence.tresorerie.TypeCompte;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.common.SpecificationUtils;
import tg.parcauto.api.parc.document.StockageFichierService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Depenses des agences : chaque depense debite un compte de l'agence par le
 * registre (bloquee si le solde ne suffit pas) et compte comme charge. Le
 * caissier paie depuis sa caisse ouverte, dans la limite du plafond regle.
 * Les charges recurrentes se reglent une fois par mois ; la generation du mois
 * ne cree jamais de doublon.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DepenseAgenceService {

    /** Categories du cahier des charges, posees a la premiere utilisation. */
    private static final List<String> CATEGORIES = List.of("Loyer", "Eau", "Electricite", "Transport", "Internet", "Salaires",
            "Fournitures", "Maintenance", "Frais bancaires", "Frais financiers", "Autres charges");

    private final DepenseAgenceRepository repository;
    private final CategorieDepenseAgenceRepository categories;
    private final ChargeRecurrenteRepository recurrentes;
    private final PerimetreAgence perimetre;
    private final DroitsAgence droits;
    private final CompteAgenceRepository comptes;
    private final RegistreService registre;
    private final SessionCaisseService sessions;
    private final ReglageAgenceRepository reglages;
    private final VerrouJournee verrou;
    private final NumerotationService numerotation;
    private final StockageFichierService stockage;
    private final InitialisationAgence initialisation;
    private final AuditAgenceService audit;
    private final tg.parcauto.api.agence.caisse.SessionCaisseAgenceRepository sessionsRepository;

    public record CategorieRequete(@NotBlank @Size(max = 80) String nom, Integer ordre, Boolean actif) {
    }

    public record CategorieDto(Long id, String nom, int ordre, boolean actif) {
    }

    public record Requete(@NotNull Long agenceId, @NotNull Long categorieId, @NotBlank @Size(max = 255) String libelle,
                          @NotNull @Positive Long montant, Long compteId, @Size(max = 150) String beneficiaire,
                          @Size(max = 80) String reference, @Size(max = 500) String observations,
                          /** Charge recurrente reglee, et mois concerne (AAAA-MM, le mois courant par defaut). */
                          Long recurrenteId, @Size(max = 7) String periode) {
    }

    public record DepenseDto(Long id, String numero, Long agenceId, String agenceNom, Long categorieId, String categorieNom,
                             String libelle, long montant, LocalDate journee, Instant dateDepense, Long compteId,
                             String compteNom, String beneficiaire, String reference, Long recurrenteId, String periode,
                             String observations, String justificatifNom, DepenseAgence.Statut statut, String auteurNom,
                             Instant annuleeLe, String annuleeParNom, String motifAnnulation, boolean annulable,
                             /** Circuit de validation des depenses de caisse. */
                             String viseeParNom, Instant viseeLe, String approuveeParNom, Instant approuveeLe,
                             String refuseeParNom, Instant refuseeLe, String motifRefus,
                             /** L'utilisateur peut viser (superviseur) ou approuver (administrateur) cette depense. */
                             boolean validable) {
    }

    public record Filtres(Long agenceId, LocalDate du, LocalDate au, Long categorieId, DepenseAgence.Statut statut, String q) {
    }

    public record TotalCategorie(Long categorieId, String categorie, long total, long nombre) {
    }

    public record TotalMois(String mois, String libelle, long total) {
    }

    /** Suivi mensuel : total du mois, repartition par categorie, comparaison avec les mois precedents. */
    public record Synthese(String mois, long total, List<TotalCategorie> parCategorie, List<TotalMois> mois6) {
    }

    public record RecurrenteRequete(@NotNull Long agenceId, @NotNull Long categorieId, @NotBlank @Size(max = 120) String libelle,
                                    @NotNull @Positive Long montant, @Min(1) @Max(28) Integer jourEcheance, Boolean actif) {
    }

    public record RecurrenteDto(Long id, Long agenceId, Long categorieId, String categorieNom, String libelle, long montant,
                                int jourEcheance, boolean actif, boolean regleeCeMois) {
    }

    public record Generation(String mois, List<DepenseDto> creees, List<String> dejaReglees) {
    }

    // ------------------------------------------------------------ categories

    public List<CategorieDto> categories() {
        initialisation.assurer();
        if (categories.count() == 0) {
            int ordre = 0;
            for (String nom : CATEGORIES) {
                CategorieDepenseAgence c = new CategorieDepenseAgence();
                c.setNom(nom);
                c.setOrdre(ordre += 10);
                categories.save(c);
            }
        }
        return categories.findAllByOrderByOrdreAscNomAsc().stream().map(c -> new CategorieDto(c.getId(), c.getNom(), c.getOrdre(), c.isActif())).toList();
    }

    public CategorieDto enregistrerCategorie(Long id, CategorieRequete r) {
        String nom = r.nom().trim();
        if (id == null ? categories.existsByNomIgnoreCase(nom) : categories.existsByNomIgnoreCaseAndIdNot(nom, id)) {
            throw new BusinessException("La categorie " + nom + " existe deja");
        }
        CategorieDepenseAgence c = id == null ? new CategorieDepenseAgence()
                : categories.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Categorie", id));
        c.setNom(nom);
        if (r.ordre() != null) {
            c.setOrdre(r.ordre());
        } else if (id == null) {
            c.setOrdre(categories.findAll().stream().mapToInt(CategorieDepenseAgence::getOrdre).max().orElse(0) + 10);
        }
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
        c = categories.save(c);
        return new CategorieDto(c.getId(), c.getNom(), c.getOrdre(), c.isActif());
    }

    // -------------------------------------------------------------- depenses

    public DepenseDto creer(Requete r, UtilisateurPrincipal auteur) {
        Agence agence = perimetre.exiger(r.agenceId());
        CategorieDepenseAgence categorie = categories.findById(r.categorieId())
                .orElseThrow(() -> ResourceNotFoundException.of("Categorie", r.categorieId()));
        if (!categorie.isActif()) {
            throw new BusinessException("La categorie " + categorie.getNom() + " est desactivee");
        }
        CompteAgence compte = compteDebite(agence, r.compteId(), r.montant(), auteur);
        DepenseAgence d = new DepenseAgence();
        if (r.recurrenteId() != null) {
            ChargeRecurrente modele = recurrente(r.recurrenteId(), agence);
            String periode = r.periode() == null || r.periode().isBlank()
                    ? YearMonth.from(registre.journee(Instant.now())).toString() : YearMonth.parse(r.periode().trim()).toString();
            if (repository.dejaReglee(modele.getId(), periode)) {
                throw new BusinessException(modele.getLibelle() + " est deja reglee pour " + libelleMois(YearMonth.parse(periode)));
            }
            d.setRecurrenteId(modele.getId());
            d.setPeriode(periode);
        }
        return enregistrer(d, agence, categorie, r.libelle().trim(), r.montant(), compte, r.beneficiaire(), r.reference(),
                r.observations(), auteur);
    }

    private DepenseDto enregistrer(DepenseAgence d, Agence agence, CategorieDepenseAgence categorie, String libelle, long montant,
                                   CompteAgence compte, String beneficiaire, String reference, String observations,
                                   UtilisateurPrincipal auteur) {
        Instant maintenant = Instant.now();
        d.setAgenceId(agence.getId());
        d.setNumero(numerotation.prochainNumero("DPA"));
        d.setCategorieId(categorie.getId());
        d.setLibelle(libelle);
        d.setMontant(montant);
        d.setJournee(registre.journee(maintenant));
        d.setDateDepense(maintenant);
        d.setCompteId(compte.getId());
        d.setBeneficiaire(texte(beneficiaire));
        d.setReference(texte(reference));
        d.setObservations(texte(observations));
        d.setAuteurId(auteur.getId());
        d.setAuteurNom(auteur.getNomComplet());
        DepenseAgence.Statut attente = etapeInitiale(compte);
        if (attente == null) {
            d = repository.save(d);
            ecrire(d, compte.getId(), categorie.getNom());
            return versDto(d, contexte());
        }
        // Rien n'est ecrit au registre avant l'approbation de l'administrateur.
        long disponible = compte.getSolde() + compte.getDecouvertAutorise();
        if (montant > disponible) {
            throw new BusinessException(compte.getLibelle() + " insuffisant : il manque " + RegistreService.francs(montant - disponible)
                    + " (solde disponible " + RegistreService.francs(disponible) + ")");
        }
        d.setStatut(attente);
        if (attente == DepenseAgence.Statut.ATTENTE_ADMIN) {
            d.setViseeParNom(auteur.getNomComplet());
            d.setViseeLe(maintenant);
        }
        d = repository.save(d);
        audit.tracer(agence.getId(), "CREATION", "DEPENSE", d.getId(), "Depense " + d.getNumero() + " de "
                + RegistreService.francs(montant) + " en attente de validation (" + libelleAttente(attente) + ")", null, attente.name());
        return versDto(d, contexte());
    }

    private void ecrire(DepenseAgence d, Long compteId, String categorieNom) {
        Ecriture e = registre.enregistrer(new RegistreService.Entete(d.getAgenceId(), NatureEcriture.DEPENSE,
                d.getNumero() + " - " + categorieNom + " : " + d.getLibelle(), null, "DEPENSE", d.getId()),
                List.of(RegistreService.Mouvement.de(compteId, -d.getMontant())));
        d.setEcritureId(e.getId());
    }

    /**
     * Etape par laquelle commence une depense : nulle (prise en compte tout de suite)
     * sans le reglage, hors caisse ou pour l'administrateur ; le visa du superviseur
     * pour un caissier ; l'administrateur pour une depense saisie par un superviseur.
     */
    private DepenseAgence.Statut etapeInitiale(CompteAgence compte) {
        boolean reglage = reglages.findFirstByOrderByIdAsc().map(ReglageAgence::isValidationDepensesCaisse).orElse(false);
        // Toute depense d'un non-administrateur passe par le circuit, quel que soit le compte : sinon un
        // virement caisse -> banque sous le seuil puis une depense sur la banque echappe au visa.
        if (!reglage || DroitsAgence.estAdministrateur()) {
            return null;
        }
        return caissierSeul() ? DepenseAgence.Statut.ATTENTE_SUPERVISEUR : DepenseAgence.Statut.ATTENTE_ADMIN;
    }

    private static String libelleAttente(DepenseAgence.Statut s) {
        return s == DepenseAgence.Statut.ATTENTE_SUPERVISEUR ? "visa du superviseur" : "approbation de l'administrateur";
    }

    // ---------------------------------------------------- circuit de validation

    /** Depenses qui attendent une validation de cet utilisateur : son visa (superviseur) ou toutes (administrateur). */
    @Transactional(readOnly = true)
    public List<DepenseDto> aValider(Long agenceId) {
        List<DepenseAgence.Statut> statuts = statutsValidables();
        Set<Long> agences = agences(agenceId);
        if (statuts.isEmpty() || agences.isEmpty()) {
            return List.of();
        }
        Contexte ctx = contexte();
        return repository.findByAgenceIdInAndStatutInOrderByDateDepenseAscIdAsc(agences, statuts).stream()
                .map(d -> versDto(d, ctx)).toList();
    }

    /** Nombre de depenses qui attendent une validation de cet utilisateur. */
    @Transactional(readOnly = true)
    public long nombreAValider(Set<Long> agences) {
        List<DepenseAgence.Statut> statuts = statutsValidables();
        return statuts.isEmpty() || agences.isEmpty() ? 0 : repository.countByAgenceIdInAndStatutIn(agences, statuts);
    }

    private List<DepenseAgence.Statut> statutsValidables() {
        if (DroitsAgence.estAdministrateur()) {
            return List.of(DepenseAgence.Statut.ATTENTE_SUPERVISEUR, DepenseAgence.Statut.ATTENTE_ADMIN);
        }
        return droits.courants().contains(PermissionAgence.DEPENSE_VALIDER)
                ? List.of(DepenseAgence.Statut.ATTENTE_SUPERVISEUR) : List.of();
    }

    /**
     * Validation : le superviseur vise ; l'administrateur approuve (a l'une ou l'autre
     * etape) et la depense s'ecrit au registre. Une depense de caissier dont la caisse
     * est fermee entre-temps sort de la caisse generale, ou ses especes ont ete remises.
     */
    public DepenseDto valider(Long id, UtilisateurPrincipal u) {
        DepenseAgence d = enAttente(id);
        Instant maintenant = Instant.now();
        if (!DroitsAgence.estAdministrateur()) {
            droits.exiger(PermissionAgence.DEPENSE_VALIDER);
            if (d.getStatut() != DepenseAgence.Statut.ATTENTE_SUPERVISEUR) {
                throw new BusinessException("La depense " + d.getNumero() + " attend l'approbation de l'administrateur");
            }
            if (d.getAuteurId().equals(u.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("Une depense se vise par une autre personne que son auteur");
            }
            d.setStatut(DepenseAgence.Statut.ATTENTE_ADMIN);
            d.setViseeParNom(u.getNomComplet());
            d.setViseeLe(maintenant);
            audit.tracer(d.getAgenceId(), "VALIDATION", "DEPENSE", d.getId(), "Depense " + d.getNumero() + " visee",
                    "ATTENTE_SUPERVISEUR", "ATTENTE_ADMIN");
            return versDto(d, contexte());
        }
        DepenseAgence.Statut avant = d.getStatut();
        if (d.getViseeParNom() == null) {
            d.setViseeParNom(u.getNomComplet());
            d.setViseeLe(maintenant);
        }
        CompteAgence compte = compteAApprouver(d);
        d.setCompteId(compte.getId());
        d.setJournee(registre.journee(maintenant));
        String categorie = categories.findById(d.getCategorieId()).map(CategorieDepenseAgence::getNom).orElse("Depense");
        ecrire(d, compte.getId(), categorie);
        d.setStatut(DepenseAgence.Statut.VALIDEE);
        d.setApprouveeParNom(u.getNomComplet());
        d.setApprouveeLe(maintenant);
        audit.tracer(d.getAgenceId(), "VALIDATION", "DEPENSE", d.getId(), "Depense " + d.getNumero() + " approuvee, "
                + RegistreService.francs(d.getMontant()) + " sortis de " + compte.getLibelle(), avant.name(), "VALIDEE");
        return versDto(d, contexte());
    }

    public DepenseDto refuser(Long id, String motif, UtilisateurPrincipal u) {
        DepenseAgence d = enAttente(id);
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif du refus est obligatoire");
        }
        if (!DroitsAgence.estAdministrateur()) {
            droits.exiger(PermissionAgence.DEPENSE_VALIDER);
            if (d.getStatut() != DepenseAgence.Statut.ATTENTE_SUPERVISEUR) {
                throw new BusinessException("La depense " + d.getNumero() + " attend la decision de l'administrateur");
            }
        }
        DepenseAgence.Statut avant = d.getStatut();
        d.setStatut(DepenseAgence.Statut.REFUSEE);
        d.setRefuseeParNom(u.getNomComplet());
        d.setRefuseeLe(Instant.now());
        d.setMotifRefus(motif.trim());
        audit.tracer(d.getAgenceId(), "REJET", "DEPENSE", d.getId(), "Depense " + d.getNumero() + " refusee (" + motif.trim() + ")",
                avant.name(), "REFUSEE");
        return versDto(d, contexte());
    }

    /**
     * Compte debite a l'approbation : la caisse du caissier si sa caisse est encore
     * ouverte ; la caisse generale si elle est fermee (ses especes y ont ete remises) ;
     * refus si elle est comptee et en attente de la decision du superviseur.
     */
    private CompteAgence compteAApprouver(DepenseAgence d) {
        CompteAgence compte = comptes.findById(d.getCompteId()).orElseThrow(() -> ResourceNotFoundException.of("Compte", d.getCompteId()));
        if (compte.getType() != TypeCompte.CAISSE_CAISSIER || compte.getUtilisateurId() == null) {
            return compte;
        }
        var session = sessionsRepository.findFirstByCaissierIdAndStatutIn(compte.getUtilisateurId(), SessionCaisseService.EN_COURS)
                .filter(s -> s.getCompteId().equals(compte.getId()));
        if (session.isPresent()) {
            if (session.get().getStatut() == tg.parcauto.api.agence.caisse.SessionCaisseAgence.Statut.SOUMISE) {
                throw new BusinessException("La caisse de " + session.get().getCaissierNom()
                        + " est comptee et attend la decision du superviseur : approuvez apres sa fermeture");
            }
            return compte;
        }
        return comptes.findByAgenceIdIn(List.of(d.getAgenceId())).stream()
                .filter(c -> c.getType() == TypeCompte.CAISSE_GENERALE).findFirst()
                .orElseThrow(() -> new BusinessException("L'agence n'a pas de caisse generale"));
    }

    private DepenseAgence enAttente(Long id) {
        DepenseAgence d = charger(id);
        perimetre.exiger(d.getAgenceId());
        if (!d.getStatut().enAttente()) {
            throw new BusinessException("La depense " + d.getNumero() + " n'attend plus de validation");
        }
        return d;
    }

    /**
     * Compte debite : la caisse ouverte pour un caissier (dans la limite du
     * plafond), le compte choisi sinon.
     */
    private CompteAgence compteDebite(Agence agence, Long compteId, long montant, UtilisateurPrincipal auteur) {
        if (caissierSeul()) {
            ReglageAgence reglage = reglages.findFirstByOrderByIdAsc().orElseGet(ReglageAgence::new);
            if (montant > reglage.getPlafondDepenseCaissier()) {
                throw new BusinessException("Une depense de caissier est limitee a " + RegistreService.francs(reglage.getPlafondDepenseCaissier())
                        + " : demandez a votre superviseur");
            }
            var session = sessions.ouverte(agence.getId(), auteur.getId())
                    .orElseThrow(() -> new BusinessException(sessions.motifSansCaisse(agence.getId(), agence.getNom(), auteur.getId())
                            + " (une depense de caissier se paie depuis sa caisse)"));
            return comptes.findById(session.getCompteId()).orElseThrow();
        }
        if (compteId == null) {
            throw new BusinessException("Choisissez le compte qui paie la depense");
        }
        CompteAgence c = comptes.findById(compteId).orElseThrow(() -> ResourceNotFoundException.of("Compte", compteId));
        if (!c.getAgenceId().equals(agence.getId())) {
            throw new BusinessException("Le compte " + c.getLibelle() + " n'appartient pas a l'agence " + agence.getNom());
        }
        if (!c.isActif()) {
            throw new BusinessException("Le compte " + c.getLibelle() + " est desactive");
        }
        return c;
    }

    public DepenseDto annuler(Long id, String motif, UtilisateurPrincipal auteur) {
        DepenseAgence d = charger(id);
        perimetre.exiger(d.getAgenceId());
        if (d.getStatut() != DepenseAgence.Statut.VALIDEE) {
            throw new BusinessException("La depense " + d.getNumero() + " est deja annulee");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif de l'annulation est obligatoire");
        }
        verrou.exigerOuverte(d.getAgenceId(), d.getJournee(), "l'annulation de " + d.getNumero());
        sessions.exigerCaisseOuverte(d.getCompteId(), d.getApprouveeLe() != null ? d.getApprouveeLe() : d.getDateDepense(),
                "l'annulation de " + d.getNumero());
        Ecriture inverse = registre.contrePasser(d.getEcritureId(), motif.trim());
        d.setStatut(DepenseAgence.Statut.ANNULEE);
        d.setAnnulationEcritureId(inverse.getId());
        d.setAnnuleeLe(Instant.now());
        d.setAnnuleeParNom(auteur.getNomComplet());
        d.setMotifAnnulation(motif.trim());
        audit.tracer(d.getAgenceId(), "ANNULATION", "DEPENSE", d.getId(), "Depense " + d.getNumero() + " annulee (" + motif.trim() + ")",
                "VALIDEE", "ANNULEE");
        return versDto(d, contexte());
    }

    public DepenseDto joindre(Long id, MultipartFile fichier) {
        DepenseAgence d = charger(id);
        perimetre.exiger(d.getAgenceId());
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException("Choisissez un fichier");
        }
        if (fichier.getSize() > 15L * 1024 * 1024) {
            throw new BusinessException("Le justificatif depasse 15 Mo");
        }
        // Justificatif d'une depense validee, refusee ou annulee : fige (preuve de la decision).
        if (d.getJustificatifChemin() != null && d.getStatut() != DepenseAgence.Statut.ATTENTE_SUPERVISEUR
                && d.getStatut() != DepenseAgence.Statut.ATTENTE_ADMIN) {
            throw new BusinessException("Le justificatif de " + d.getNumero() + " a servi a la decision :"
                    + " il ne se remplace plus");
        }
        String ancien = d.getJustificatifChemin();
        d.setJustificatifChemin(stockage.enregistrer(fichier, "agences-depenses/" + d.getId()));
        d.setJustificatifNom(fichier.getOriginalFilename() == null ? "justificatif" : fichier.getOriginalFilename());
        d.setJustificatifType(fichier.getContentType());
        // L'ancienne piece reste sur le disque ; le remplacement est trace.
        audit.tracer(d.getAgenceId(), "PIECE", "DEPENSE", d.getId(), "Justificatif de " + d.getNumero()
                + (ancien == null ? " joint" : " remplace") + " : " + d.getJustificatifNom(), null, null);
        return versDto(d, contexte());
    }

    @Transactional(readOnly = true)
    public Resource justificatif(Long id) {
        DepenseAgence d = charger(id);
        perimetre.exiger(d.getAgenceId());
        if (d.getJustificatifChemin() == null) {
            throw new ResourceNotFoundException("Aucun justificatif pour la depense " + d.getNumero());
        }
        return stockage.charger(d.getJustificatifChemin());
    }

    @Transactional(readOnly = true)
    public DepenseDto detail(Long id) {
        DepenseAgence d = charger(id);
        perimetre.exiger(d.getAgenceId());
        return versDto(d, contexte());
    }

    @Transactional(readOnly = true)
    /** Chiffres de la liste filtree : total, repartition, moyenne, annulees et pieces manquantes. */
    public record Resume(long total, long nombre, long moyenne, long annulees, long montantAnnule, long sansJustificatif,
                         List<TotalCategorie> parCategorie) {
    }

    @Transactional(readOnly = true)
    public Resume resume(Filtres f) {
        List<DepenseAgence> lignes = repository.findAll(specification(f));
        Map<Long, String> noms = categories.findAll().stream()
                .collect(Collectors.toMap(CategorieDepenseAgence::getId, CategorieDepenseAgence::getNom));
        List<DepenseAgence> validees = lignes.stream().filter(d -> d.getStatut() == DepenseAgence.Statut.VALIDEE).toList();
        long total = validees.stream().mapToLong(DepenseAgence::getMontant).sum();
        List<DepenseAgence> annulees = lignes.stream().filter(d -> d.getStatut() == DepenseAgence.Statut.ANNULEE).toList();
        List<TotalCategorie> parCategorie = validees.stream()
                .collect(Collectors.groupingBy(DepenseAgence::getCategorieId)).entrySet().stream()
                .map(e -> new TotalCategorie(e.getKey(), noms.getOrDefault(e.getKey(), "?"),
                        e.getValue().stream().mapToLong(DepenseAgence::getMontant).sum(), e.getValue().size()))
                .sorted(java.util.Comparator.comparingLong(TotalCategorie::total).reversed())
                .toList();
        return new Resume(total, validees.size(), validees.isEmpty() ? 0 : Math.round((double) total / validees.size()),
                annulees.size(), annulees.stream().mapToLong(DepenseAgence::getMontant).sum(),
                validees.stream().filter(d -> d.getJustificatifChemin() == null).count(), parCategorie);
    }

    public Page<DepenseDto> lister(Filtres f, Pageable pageable) {
        Specification<DepenseAgence> spec = specification(f);
        Pageable trie = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "dateDepense").and(Sort.by(Sort.Direction.DESC, "id")));
        Contexte ctx = contexte();
        return repository.findAll(spec, trie).map(d -> versDto(d, ctx));
    }

    /** Depenses visibles selon les filtres ; un caissier ne voit que les siennes. */
    private Specification<DepenseAgence> specification(Filtres f) {
        Set<Long> agences = agences(f.agenceId());
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(agences.isEmpty() ? cb.disjunction() : root.get("agenceId").in(agences));
            if (caissierSeul()) {
                p.add(cb.equal(root.get("auteurId"), moi()));
            }
            if (f.du() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("journee"), f.du()));
            }
            if (f.au() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("journee"), f.au()));
            }
            if (f.categorieId() != null) {
                p.add(cb.equal(root.get("categorieId"), f.categorieId()));
            }
            if (f.statut() != null) {
                p.add(cb.equal(root.get("statut"), f.statut()));
            }
            if (SpecificationUtils.renseigne(f.q())) {
                String motif = SpecificationUtils.motif(f.q());
                p.add(cb.or(cb.like(cb.lower(root.get("numero")), motif), cb.like(cb.lower(root.get("libelle")), motif),
                        cb.like(cb.lower(cb.coalesce(root.get("beneficiaire"), "")), motif)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** Total du mois, par categorie, et les six derniers mois pour comparer. */
    @Transactional(readOnly = true)
    public Synthese synthese(Long agenceId, String mois) {
        Set<Long> agences = agences(agenceId);
        YearMonth m = mois == null || mois.isBlank() ? YearMonth.from(registre.journee(Instant.now())) : YearMonth.parse(mois);
        Map<Long, String> noms = categories.findAll().stream().collect(Collectors.toMap(CategorieDepenseAgence::getId, CategorieDepenseAgence::getNom));
        if (agences.isEmpty()) {
            return new Synthese(m.toString(), 0, List.of(), List.of());
        }
        List<TotalCategorie> parCategorie = repository.parCategorie(agences, m.atDay(1), m.atEndOfMonth()).stream()
                .map(r -> new TotalCategorie((Long) r[0], noms.getOrDefault((Long) r[0], "?"), ((Number) r[1]).longValue(), ((Number) r[2]).longValue()))
                .sorted((a, b) -> Long.compare(b.total(), a.total()))
                .toList();
        List<TotalMois> mois6 = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth x = m.minusMonths(i);
            mois6.add(new TotalMois(x.toString(), libelleMois(x), repository.total(agences, x.atDay(1), x.atEndOfMonth())));
        }
        return new Synthese(m.toString(), parCategorie.stream().mapToLong(TotalCategorie::total).sum(), parCategorie, mois6);
    }

    /** Total des depenses validees, pour le tableau de bord. */
    @Transactional(readOnly = true)
    public long total(Set<Long> agences, LocalDate du, LocalDate au) {
        return agences.isEmpty() ? 0 : repository.total(agences, du, au);
    }

    // ------------------------------------------------------------ recurrentes

    @Transactional(readOnly = true)
    public List<RecurrenteDto> recurrentes(Long agenceId, String mois) {
        Agence agence = perimetre.exiger(agenceId);
        String periode = mois == null || mois.isBlank() ? YearMonth.from(registre.journee(Instant.now())).toString() : YearMonth.parse(mois).toString();
        Map<Long, String> noms = categories.findAll().stream().collect(Collectors.toMap(CategorieDepenseAgence::getId, CategorieDepenseAgence::getNom));
        return recurrentes.findByAgenceIdOrderByLibelleAsc(agence.getId()).stream()
                .map(r -> new RecurrenteDto(r.getId(), r.getAgenceId(), r.getCategorieId(), noms.get(r.getCategorieId()), r.getLibelle(),
                        r.getMontant(), r.getJourEcheance(), r.isActif(), repository.dejaReglee(r.getId(), periode)))
                .toList();
    }

    public RecurrenteDto enregistrerRecurrente(Long id, RecurrenteRequete r) {
        Agence agence = perimetre.exiger(r.agenceId());
        categories.findById(r.categorieId()).orElseThrow(() -> ResourceNotFoundException.of("Categorie", r.categorieId()));
        ChargeRecurrente c = id == null ? new ChargeRecurrente() : recurrente(id, agence);
        if (id == null) {
            c.setAgenceId(agence.getId());
        }
        c.setCategorieId(r.categorieId());
        c.setLibelle(r.libelle().trim());
        c.setMontant(r.montant());
        c.setJourEcheance(r.jourEcheance() == null ? 1 : r.jourEcheance());
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
        c = recurrentes.save(c);
        audit.tracer(agence.getId(), id == null ? "CREATION" : "MODIFICATION", "CHARGE_RECURRENTE", c.getId(),
                "Charge recurrente " + c.getLibelle() + " : " + RegistreService.francs(c.getMontant()) + " par mois", null, r);
        String periode = YearMonth.from(registre.journee(Instant.now())).toString();
        String categorie = categories.findById(c.getCategorieId()).map(CategorieDepenseAgence::getNom).orElse(null);
        return new RecurrenteDto(c.getId(), c.getAgenceId(), c.getCategorieId(), categorie, c.getLibelle(), c.getMontant(),
                c.getJourEcheance(), c.isActif(), repository.dejaReglee(c.getId(), periode));
    }

    /**
     * Regle toutes les charges recurrentes actives du mois qui ne le sont pas
     * encore, depuis le compte choisi. Lancee deux fois, elle ne cree rien de plus.
     */
    public Generation generer(Long agenceId, String mois, Long compteId, UtilisateurPrincipal auteur) {
        Agence agence = perimetre.exiger(agenceId);
        YearMonth m = mois == null || mois.isBlank() ? YearMonth.from(registre.journee(Instant.now())) : YearMonth.parse(mois);
        List<DepenseDto> creees = new ArrayList<>();
        List<String> deja = new ArrayList<>();
        for (ChargeRecurrente r : recurrentes.findByAgenceIdOrderByLibelleAsc(agence.getId())) {
            if (!r.isActif()) {
                continue;
            }
            if (repository.dejaReglee(r.getId(), m.toString())) {
                deja.add(r.getLibelle());
                continue;
            }
            CategorieDepenseAgence categorie = categories.findById(r.getCategorieId()).orElseThrow();
            CompteAgence compte = compteDebite(agence, compteId, r.getMontant(), auteur);
            DepenseAgence d = new DepenseAgence();
            d.setRecurrenteId(r.getId());
            d.setPeriode(m.toString());
            creees.add(enregistrer(d, agence, categorie, r.getLibelle() + " - " + libelleMois(m), r.getMontant(), compte, null, null,
                    "Charge recurrente generee", auteur));
        }
        if (!creees.isEmpty()) {
            audit.tracer(agence.getId(), "GENERATION", "CHARGE_RECURRENTE", null, creees.size() + " charge(s) recurrente(s) de "
                    + libelleMois(m) + " reglee(s), " + RegistreService.francs(creees.stream().mapToLong(DepenseDto::montant).sum()), null,
                    creees.stream().map(DepenseDto::numero).toList());
        }
        return new Generation(m.toString(), creees, deja);
    }

    // ----------------------------------------------------------------- outils

    private ChargeRecurrente recurrente(Long id, Agence agence) {
        ChargeRecurrente r = recurrentes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Charge recurrente", id));
        if (!r.getAgenceId().equals(agence.getId())) {
            throw new BusinessException("La charge " + r.getLibelle() + " n'appartient pas a l'agence " + agence.getNom());
        }
        return r;
    }

    private Set<Long> agences(Long agenceId) {
        return agenceId != null ? Set.of(perimetre.exiger(agenceId).getId())
                : perimetre.visibles().stream().map(Agence::getId).collect(Collectors.toSet());
    }

    static String libelleMois(YearMonth m) {
        String nom = m.getMonth().getDisplayName(TextStyle.FULL, Locale.FRANCE);
        return Character.toUpperCase(nom.charAt(0)) + nom.substring(1) + " " + m.getYear();
    }

    private static boolean caissierSeul() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return true;
        }
        Set<String> roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
        return !roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_SUPERVISEUR_AGENCE");
    }

    private static Long moi() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UtilisateurPrincipal u ? u.getId() : -1L;
    }

    private static String texte(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private DepenseAgence charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depense", id));
    }

    private record Contexte(Map<Long, String> agences, Map<Long, String> categories, Map<Long, String> comptes, boolean annulation,
                            boolean visa, boolean admin, Long moi) {
    }

    private Contexte contexte() {
        List<Agence> visibles = perimetre.visibles();
        return new Contexte(visibles.stream().collect(Collectors.toMap(Agence::getId, Agence::getNom)),
                categories.findAll().stream().collect(Collectors.toMap(CategorieDepenseAgence::getId, CategorieDepenseAgence::getNom)),
                comptes.findByAgenceIdIn(visibles.stream().map(Agence::getId).toList()).stream()
                        .collect(Collectors.toMap(CompteAgence::getId, CompteAgence::getLibelle)),
                droits.courants().contains(PermissionAgence.DEPENSE_ANNULER),
                droits.courants().contains(PermissionAgence.DEPENSE_VALIDER), DroitsAgence.estAdministrateur(), moi());
    }

    private static DepenseDto versDto(DepenseAgence d, Contexte ctx) {
        return new DepenseDto(d.getId(), d.getNumero(), d.getAgenceId(), ctx.agences().get(d.getAgenceId()), d.getCategorieId(),
                ctx.categories().get(d.getCategorieId()), d.getLibelle(), d.getMontant(), d.getJournee(), d.getDateDepense(),
                d.getCompteId(), ctx.comptes().get(d.getCompteId()), d.getBeneficiaire(), d.getReference(), d.getRecurrenteId(),
                d.getPeriode(), d.getObservations(), d.getJustificatifNom(), d.getStatut(), d.getAuteurNom(), d.getAnnuleeLe(),
                d.getAnnuleeParNom(), d.getMotifAnnulation(), ctx.annulation() && d.getStatut() == DepenseAgence.Statut.VALIDEE,
                d.getViseeParNom(), d.getViseeLe(), d.getApprouveeParNom(), d.getApprouveeLe(), d.getRefuseeParNom(),
                d.getRefuseeLe(), d.getMotifRefus(),
                ctx.admin() ? d.getStatut().enAttente()
                        : ctx.visa() && d.getStatut() == DepenseAgence.Statut.ATTENTE_SUPERVISEUR && !d.getAuteurId().equals(ctx.moi()));
    }
}
