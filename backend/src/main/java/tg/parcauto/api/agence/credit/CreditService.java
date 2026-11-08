package tg.parcauto.api.agence.credit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.cloture.VerrouJournee;
import tg.parcauto.api.agence.tresorerie.CompteAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgenceRepository;
import tg.parcauto.api.agence.tresorerie.Ecriture;
import tg.parcauto.api.agence.tresorerie.JourneeCaisse;
import tg.parcauto.api.agence.tresorerie.NatureEcriture;
import tg.parcauto.api.agence.tresorerie.RegistreService;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Credits, dettes, apports et avances des agences, et leurs remboursements.
 * <p>
 * L'argent recu (credit, apport) entre au compte choisi sans etre un revenu ;
 * une avance accordee en sort. Un remboursement distingue le capital, qui
 * reduit le restant du et n'est pas une charge, des interets et frais
 * financiers, seule charge du credit.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CreditService {

    private final CreditAgenceRepository repository;
    private final RemboursementAgenceRepository remboursements;
    private final PerimetreAgence perimetre;
    private final CompteAgenceRepository comptes;
    private final RegistreService registre;
    private final VerrouJournee verrou;
    private final NumerotationService numerotation;
    private final AuditAgenceService audit;

    public record Echeance(@NotNull LocalDate datePrevue, @NotNull @Positive Long montant) {
    }

    public record Requete(@NotNull Long agenceId, @NotNull CreditAgence.Type type, @NotBlank @Size(max = 255) String libelle,
                          @NotBlank @Size(max = 150) String tiersNom, @Size(max = 30) String tiersTelephone,
                          @NotNull @Positive Long montant, LocalDate dateDebut, LocalDate dateEcheance, BigDecimal tauxInteret,
                          Long compteId, Boolean remboursable, @Size(max = 500) String observations,
                          List<@Valid Echeance> echeances) {
    }

    public record DemandeRemboursement(@NotNull @PositiveOrZero Long capital, @PositiveOrZero Long interets, @NotNull Long compteId,
                                       @Size(max = 500) String observations) {
    }

    public record RemboursementDto(Long id, Instant date, LocalDate journee, long capital, long interets, Long compteId,
                                   String compteNom, String observations, RemboursementAgence.Statut statut, String auteurNom,
                                   String motifAnnulation) {
    }

    public record EcheanceDto(LocalDate datePrevue, long montant, boolean passee) {
    }

    public record CreditDto(Long id, String numero, Long agenceId, String agenceNom, CreditAgence.Type type, String typeLibelle,
                            boolean dette, String libelle, String tiersNom, String tiersTelephone, long montantInitial,
                            long capitalRestant, long capitalRembourse, long interetsPayes, LocalDate dateDebut,
                            LocalDate dateEcheance, BigDecimal tauxInteret, Long compteId, String compteNom, boolean remboursable,
                            String observations, CreditAgence.Statut statut, String auteurNom, LocalDate prochaineEcheance,
                            long montantProchaineEcheance, boolean enRetard, List<EcheanceDto> echeances,
                            List<RemboursementDto> remboursements) {
    }

    /** Totaux des credits en cours : ce que l'agence doit et ce qu'on lui doit. */
    public record Totaux(long dettesRestantes, long creancesRestantes, long nombreEnCours) {
    }

    // ------------------------------------------------------------------ saisie

    public CreditDto creer(Requete r, UtilisateurPrincipal auteur) {
        Agence agence = perimetre.exiger(r.agenceId());
        CompteAgence compte = r.compteId() == null ? null : compte(agence, r.compteId());
        if (compte == null && r.type() != CreditAgence.Type.DETTE) {
            throw new BusinessException("Choisissez le compte qui " + (r.type() == CreditAgence.Type.AVANCE ? "verse" : "recoit") + " l'argent");
        }
        if (r.dateEcheance() != null && r.dateDebut() != null && r.dateEcheance().isBefore(r.dateDebut())) {
            throw new BusinessException("L'echeance precede la date de debut");
        }
        CreditAgence c = new CreditAgence();
        c.setAgenceId(agence.getId());
        c.setNumero(numerotation.prochainNumero("CRD"));
        c.setType(r.type());
        c.setLibelle(r.libelle().trim());
        c.setTiersNom(r.tiersNom().trim());
        c.setTiersTelephone(texte(r.tiersTelephone()));
        c.setMontantInitial(r.montant());
        c.setCapitalRestant(r.montant());
        c.setDateDebut(r.dateDebut() != null ? r.dateDebut() : LocalDate.now(JourneeCaisse.FUSEAU));
        c.setDateEcheance(r.dateEcheance());
        c.setTauxInteret(r.tauxInteret());
        c.setCompteId(compte == null ? null : compte.getId());
        c.setRemboursable(r.remboursable() == null || r.remboursable());
        c.setObservations(texte(r.observations()));
        c.setAuteurNom(auteur.getNomComplet());
        if (r.echeances() != null) {
            r.echeances().stream().sorted(Comparator.comparing(Echeance::datePrevue))
                    .forEach(e -> c.getEcheances().add(new EcheanceCredit(e.datePrevue(), e.montant())));
            long totalEcheances = c.getEcheances().stream().mapToLong(EcheanceCredit::getMontant).sum();
            if (totalEcheances > r.montant()) {
                throw new BusinessException("Les echeances (" + RegistreService.francs(totalEcheances) + ") depassent le montant du credit");
            }
        }
        CreditAgence enregistre = repository.save(c);
        if (compte != null) {
            long sens = r.type() == CreditAgence.Type.AVANCE ? -1 : 1;
            Ecriture e = registre.enregistrer(new RegistreService.Entete(agence.getId(), NatureEcriture.CREDIT,
                    enregistre.getNumero() + " - " + r.type().getLibelle() + " : " + enregistre.getLibelle() + " (" + enregistre.getTiersNom() + ")",
                    null, "CREDIT", enregistre.getId()), List.of(RegistreService.Mouvement.de(compte.getId(), sens * r.montant())));
            enregistre.setEcritureId(e.getId());
        }
        audit.tracer(agence.getId(), "CREATION", "CREDIT", enregistre.getId(), r.type().getLibelle() + " " + enregistre.getNumero()
                + " de " + RegistreService.francs(r.montant()) + " (" + enregistre.getTiersNom() + ")", null, r);
        return versDto(enregistre, contexte());
    }

    /** Remboursement : capital et interets ; une avance accordee est remboursee a l'agence. */
    public CreditDto rembourser(Long id, DemandeRemboursement d, UtilisateurPrincipal auteur) {
        CreditAgence c = charger(id);
        Agence agence = perimetre.exiger(c.getAgenceId());
        if (c.getStatut() != CreditAgence.Statut.EN_COURS) {
            throw new BusinessException("Le credit " + c.getNumero() + " n'est plus en cours");
        }
        if (!c.isRemboursable()) {
            throw new BusinessException("Cet apport n'est pas remboursable");
        }
        long capital = d.capital();
        long interets = d.interets() == null ? 0 : d.interets();
        if (!c.estDette() && interets > 0) {
            throw new BusinessException("Une avance accordee se rembourse en capital seulement");
        }
        if (capital + interets <= 0) {
            throw new BusinessException("Indiquez le capital ou les interets rembourses");
        }
        if (capital > c.getCapitalRestant()) {
            throw new BusinessException("Le capital rembourse depasse le restant du (" + RegistreService.francs(c.getCapitalRestant()) + ")");
        }
        CompteAgence compte = compte(agence, d.compteId());
        Instant maintenant = Instant.now();
        RemboursementAgence r = new RemboursementAgence();
        r.setCreditId(c.getId());
        r.setAgenceId(agence.getId());
        r.setDateRemboursement(maintenant);
        r.setJournee(registre.journee(maintenant));
        r.setCapital(capital);
        r.setInterets(interets);
        r.setCompteId(compte.getId());
        r.setObservations(texte(d.observations()));
        r.setAuteurNom(auteur.getNomComplet());
        r = remboursements.save(r);
        long sens = c.estDette() ? -1 : 1;
        Ecriture e = registre.enregistrer(new RegistreService.Entete(agence.getId(), NatureEcriture.CREDIT,
                "Remboursement " + c.getNumero() + " (" + c.getTiersNom() + ") : capital " + RegistreService.francs(capital)
                        + (interets > 0 ? ", interets " + RegistreService.francs(interets) : ""), null, "REMBOURSEMENT", r.getId()),
                List.of(RegistreService.Mouvement.de(compte.getId(), sens * (capital + interets))));
        r.setEcritureId(e.getId());
        c.setCapitalRestant(c.getCapitalRestant() - capital);
        if (c.getCapitalRestant() == 0) {
            c.setStatut(CreditAgence.Statut.SOLDE);
        }
        audit.tracer(agence.getId(), "REMBOURSEMENT", "CREDIT", c.getId(), "Remboursement de " + c.getNumero() + " : capital "
                + RegistreService.francs(capital) + ", interets " + RegistreService.francs(interets) + ", reste "
                + RegistreService.francs(c.getCapitalRestant()), null, d);
        return versDto(c, contexte());
    }

    public CreditDto annulerRemboursement(Long remboursementId, String motif, UtilisateurPrincipal auteur) {
        RemboursementAgence r = remboursements.findById(remboursementId)
                .orElseThrow(() -> ResourceNotFoundException.of("Remboursement", remboursementId));
        CreditAgence c = charger(r.getCreditId());
        perimetre.exiger(c.getAgenceId());
        if (r.getStatut() != RemboursementAgence.Statut.VALIDE) {
            throw new BusinessException("Ce remboursement est deja annule");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif de l'annulation est obligatoire");
        }
        verrou.exigerOuverte(r.getAgenceId(), r.getJournee(), "l'annulation de ce remboursement");
        Ecriture inverse = registre.contrePasser(r.getEcritureId(), motif.trim());
        r.setStatut(RemboursementAgence.Statut.ANNULE);
        r.setAnnulationEcritureId(inverse.getId());
        r.setMotifAnnulation(motif.trim());
        c.setCapitalRestant(c.getCapitalRestant() + r.getCapital());
        c.setStatut(CreditAgence.Statut.EN_COURS);
        audit.tracer(c.getAgenceId(), "ANNULATION", "REMBOURSEMENT", r.getId(), "Remboursement de " + c.getNumero()
                + " annule par " + auteur.getNomComplet() + " (" + motif.trim() + ")", "VALIDE", "ANNULE");
        return versDto(c, contexte());
    }

    // ----------------------------------------------------------------- lecture

    @Transactional(readOnly = true)
    public List<CreditDto> lister(Long agenceId, CreditAgence.Statut statut) {
        Contexte ctx = contexte();
        return repository.findByAgenceIdInOrderByDateDebutDescIdDesc(agences(agenceId)).stream()
                .filter(c -> statut == null || c.getStatut() == statut)
                .map(c -> versDto(c, ctx))
                .toList();
    }

    @Transactional(readOnly = true)
    public CreditDto detail(Long id) {
        CreditAgence c = charger(id);
        perimetre.exiger(c.getAgenceId());
        return versDto(c, contexte());
    }

    @Transactional(readOnly = true)
    public Totaux totaux(Set<Long> agences) {
        List<CreditAgence> enCours = agences.isEmpty() ? List.of() : repository.findByAgenceIdInOrderByDateDebutDescIdDesc(agences).stream()
                .filter(c -> c.getStatut() == CreditAgence.Statut.EN_COURS).toList();
        return new Totaux(enCours.stream().filter(c -> c.estDette() && c.isRemboursable()).mapToLong(CreditAgence::getCapitalRestant).sum(),
                enCours.stream().filter(c -> !c.estDette()).mapToLong(CreditAgence::getCapitalRestant).sum(), enCours.size());
    }

    @Transactional(readOnly = true)
    public long fraisFinanciers(Set<Long> agences, LocalDate du, LocalDate au) {
        return agences.isEmpty() ? 0 : remboursements.fraisFinanciers(agences, du, au);
    }

    /** Credits en cours dont l'echeance (ou la prochaine echeance prevue) tombe dans les jours a venir, ou est depassee. */
    @Transactional(readOnly = true)
    public List<CreditDto> echeancesProches(Set<Long> agences, int jours) {
        LocalDate limite = LocalDate.now(JourneeCaisse.FUSEAU).plusDays(jours);
        Contexte ctx = contexte();
        return agences.isEmpty() ? List.of() : repository.findByAgenceIdInOrderByDateDebutDescIdDesc(agences).stream()
                .filter(c -> c.getStatut() == CreditAgence.Statut.EN_COURS && c.isRemboursable())
                .map(c -> versDto(c, ctx))
                .filter(c -> c.prochaineEcheance() != null && !c.prochaineEcheance().isAfter(limite))
                .toList();
    }

    // ------------------------------------------------------------------ outils

    private Set<Long> agences(Long agenceId) {
        return agenceId != null ? Set.of(perimetre.exiger(agenceId).getId())
                : perimetre.visibles().stream().map(Agence::getId).collect(Collectors.toSet());
    }

    private CompteAgence compte(Agence agence, Long id) {
        CompteAgence c = comptes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Compte", id));
        if (!c.getAgenceId().equals(agence.getId())) {
            throw new BusinessException("Le compte " + c.getLibelle() + " n'appartient pas a l'agence " + agence.getNom());
        }
        if (!c.isActif()) {
            throw new BusinessException("Le compte " + c.getLibelle() + " est desactive");
        }
        return c;
    }

    private CreditAgence charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Credit", id));
    }

    private static String texte(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private record Contexte(Map<Long, String> agences, Map<Long, String> comptes) {
    }

    private Contexte contexte() {
        List<Agence> visibles = perimetre.visibles();
        return new Contexte(visibles.stream().collect(Collectors.toMap(Agence::getId, Agence::getNom)),
                comptes.findByAgenceIdIn(visibles.stream().map(Agence::getId).toList()).stream()
                        .collect(Collectors.toMap(CompteAgence::getId, CompteAgence::getLibelle)));
    }

    /**
     * Prochaine echeance : la premiere echeance prevue qui n'est pas encore
     * couverte par les remboursements de capital cumules, sinon l'echeance finale.
     */
    private CreditDto versDto(CreditAgence c, Contexte ctx) {
        LocalDate aujourdhui = LocalDate.now(JourneeCaisse.FUSEAU);
        List<RemboursementAgence> liste = remboursements.findByCreditIdOrderByDateRemboursementDescIdDesc(c.getId());
        List<RemboursementAgence> valides = liste.stream().filter(r -> r.getStatut() == RemboursementAgence.Statut.VALIDE).toList();
        long capitalRembourse = valides.stream().mapToLong(RemboursementAgence::getCapital).sum();
        long interets = valides.stream().mapToLong(RemboursementAgence::getInterets).sum();
        LocalDate prochaine = null;
        long montantProchaine = 0;
        if (c.getStatut() == CreditAgence.Statut.EN_COURS) {
            long cumul = 0;
            for (EcheanceCredit e : c.getEcheances()) {
                cumul += e.getMontant();
                if (cumul > capitalRembourse) {
                    prochaine = e.getDatePrevue();
                    montantProchaine = Math.min(e.getMontant(), cumul - capitalRembourse);
                    break;
                }
            }
            if (prochaine == null && c.getDateEcheance() != null) {
                prochaine = c.getDateEcheance();
                montantProchaine = c.getCapitalRestant();
            }
        }
        List<EcheanceDto> echeances = new ArrayList<>();
        c.getEcheances().forEach(e -> echeances.add(new EcheanceDto(e.getDatePrevue(), e.getMontant(), e.getDatePrevue().isBefore(aujourdhui))));
        return new CreditDto(c.getId(), c.getNumero(), c.getAgenceId(), ctx.agences().get(c.getAgenceId()), c.getType(),
                c.getType().getLibelle(), c.estDette(), c.getLibelle(), c.getTiersNom(), c.getTiersTelephone(), c.getMontantInitial(),
                c.getCapitalRestant(), capitalRembourse, interets, c.getDateDebut(), c.getDateEcheance(), c.getTauxInteret(),
                c.getCompteId(), c.getCompteId() == null ? null : ctx.comptes().get(c.getCompteId()), c.isRemboursable(),
                c.getObservations(), c.getStatut(), c.getAuteurNom(), prochaine, montantProchaine,
                prochaine != null && prochaine.isBefore(aujourdhui), echeances,
                liste.stream().map(r -> new RemboursementDto(r.getId(), r.getDateRemboursement(), r.getJournee(), r.getCapital(),
                        r.getInterets(), r.getCompteId(), ctx.comptes().get(r.getCompteId()), r.getObservations(), r.getStatut(),
                        r.getAuteurNom(), r.getMotifAnnulation())).toList());
    }
}
