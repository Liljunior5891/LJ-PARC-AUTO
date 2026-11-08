package tg.parcauto.api.agence.commission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.InitialisationAgence;
import tg.parcauto.api.agence.catalogue.Reseau;
import tg.parcauto.api.agence.catalogue.ReseauRepository;
import tg.parcauto.api.agence.catalogue.ServiceFinancier;
import tg.parcauto.api.agence.catalogue.ServiceFinancierRepository;
import tg.parcauto.api.agence.catalogue.TypeOperation;
import tg.parcauto.api.agence.catalogue.TypeOperationRepository;
import tg.parcauto.api.agence.tresorerie.JourneeCaisse;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Baremes de commission : creation, versions successives, fin de validite,
 * simulation, et calcul de la commission proposee a la saisie.
 * <p>
 * Modifier un bareme deja en vigueur cree une nouvelle version a partir de la
 * date d'effet choisie (aujourd'hui au plus tot) et clot l'ancienne la veille :
 * l'historique reste consultable et le passe n'est jamais recalcule. Un bareme
 * qui n'a pas encore commence se corrige sur place.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BaremeService {

    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BaremeRepository repository;
    private final ServiceFinancierRepository services;
    private final TypeOperationRepository types;
    private final ReseauRepository reseaux;
    private final InitialisationAgence initialisation;
    private final AuditAgenceService audit;

    public record Tranche(@NotNull Long montantMin, Long montantMax, @NotNull Bareme.Mode mode, Long valeurFixe,
                          BigDecimal taux) {
    }

    public record Requete(
            @NotNull Long serviceId,
            Long typeOperationId,
            Long reseauId,
            @NotBlank @Size(max = 120) String libelle,
            @NotNull Bareme.Mode mode,
            Long valeurFixe,
            BigDecimal taux,
            Long minimum,
            Long maximum,
            List<@Valid Tranche> tranches,
            Bareme.CompteCredit compteCredit,
            LocalDate dateDebut,
            LocalDate dateFin,
            /** Modification : date a partir de laquelle la nouvelle version s'applique (aujourd'hui par defaut). */
            LocalDate dateEffet) {
    }

    public enum Statut { EN_COURS, A_VENIR, TERMINE, DESACTIVE }

    public record BaremeDto(Long id, Long serviceId, String serviceNom, Long typeOperationId, String typeLibelle,
                            Long reseauId, String reseauNom, String libelle, Bareme.Mode mode, Long valeurFixe,
                            BigDecimal taux, Long minimum, Long maximum, List<Tranche> tranches,
                            Bareme.CompteCredit compteCredit, LocalDate dateDebut, LocalDate dateFin, boolean actif,
                            boolean exemple, Long precedentId, Statut statut, String resume, String portee) {
    }

    /** Commission retenue pour une operation ; baremeId nul si aucun bareme ne s'applique. */
    public record Commission(Long baremeId, String baremeLibelle, long commission, String detail,
                             Bareme.CompteCredit compteCredit, boolean exemple) {
    }

    /** Ligne du tableau d'exemples du cahier des charges : commission attendue et calculee. */
    public record Controle(String service, String operation, String reseau, long montant, long attendue,
                           Long calculee, String bareme, String detail) {
    }

    // ---------------------------------------------------------------- lecture

    /** Baremes, en cours et a venir ; avec l'historique, aussi les versions terminees. */
    @Transactional(readOnly = true)
    public List<BaremeDto> lister(Long serviceId, boolean historique) {
        Contexte ctx = contexte();
        LocalDate aujourdhui = aujourdhui();
        return (serviceId == null ? repository.findAllByOrderByServiceIdAscDateDebutDescIdDesc()
                : repository.findByServiceIdOrderByDateDebutDescIdDesc(serviceId)).stream()
                .map(b -> versDto(b, ctx, aujourdhui))
                .filter(b -> historique || b.statut() == Statut.EN_COURS || b.statut() == Statut.A_VENIR)
                .sorted(Comparator.comparing((BaremeDto b) -> ctx.ordreService(b.serviceId()))
                        .thenComparing(b -> b.typeLibelle() == null ? "" : b.typeLibelle())
                        .thenComparing(b -> b.reseauNom() == null ? "" : b.reseauNom())
                        .thenComparing(BaremeDto::dateDebut, Comparator.reverseOrder()))
                .toList();
    }

    /** Toutes les versions d'un bareme, de la plus recente a la plus ancienne. */
    @Transactional(readOnly = true)
    public List<BaremeDto> historique(Long id) {
        Contexte ctx = contexte();
        LocalDate aujourdhui = aujourdhui();
        Bareme courant = charger(id);
        // Remonte a la version la plus recente, puis redescend la chaine.
        for (List<Bareme> suivantes = repository.findByPrecedentId(courant.getId()); !suivantes.isEmpty();
             suivantes = repository.findByPrecedentId(courant.getId())) {
            courant = suivantes.get(0);
        }
        List<BaremeDto> versions = new ArrayList<>();
        for (Bareme b = courant; b != null; b = b.getPrecedentId() == null ? null : repository.findById(b.getPrecedentId()).orElse(null)) {
            versions.add(versDto(b, ctx, aujourdhui));
        }
        return versions;
    }

    // --------------------------------------------------------------- ecriture

    public BaremeDto creer(Requete r) {
        initialisation.assurer();
        Bareme b = new Bareme();
        b.setServiceId(r.serviceId());
        b.setTypeOperationId(r.typeOperationId());
        b.setReseauId(r.reseauId());
        verifierPortee(b);
        appliquer(b, r);
        b.setDateDebut(r.dateDebut() != null ? r.dateDebut() : aujourdhui());
        b.setDateFin(r.dateFin());
        verifierPeriode(b);
        verifierChevauchement(b, null);
        b = repository.save(b);
        BaremeDto dto = versDto(b, contexte(), aujourdhui());
        audit.tracer(null, "CREATION", "BAREME", b.getId(), "Bareme " + b.getLibelle() + " cree (" + dto.resume() + ")",
                null, dto);
        return dto;
    }

    public BaremeDto modifier(Long id, Requete r) {
        Bareme ancien = charger(id);
        LocalDate aujourdhui = aujourdhui();
        Contexte ctx = contexte();
        BaremeDto avant = versDto(ancien, ctx, aujourdhui);
        if (avant.statut() == Statut.TERMINE || avant.statut() == Statut.DESACTIVE) {
            throw new BusinessException("Ce bareme n'est plus en vigueur : creez-en un nouveau");
        }
        if (!repository.findByPrecedentId(id).isEmpty()) {
            throw new BusinessException("Une version plus recente de ce bareme existe : modifiez-la");
        }
        LocalDate effet = r.dateEffet() != null ? r.dateEffet() : aujourdhui;
        if (effet.isBefore(aujourdhui)) {
            throw new BusinessException("Un bareme ne se change pas dans le passe : choisissez une date d'effet a partir d'aujourd'hui");
        }

        // Commence aujourd'hui ou plus tard : correction sur place (l'audit garde l'avant et l'apres).
        if (!ancien.getDateDebut().isBefore(aujourdhui)) {
            appliquer(ancien, r);
            ancien.setDateDebut(r.dateDebut() != null && !r.dateDebut().isBefore(aujourdhui) ? r.dateDebut() : ancien.getDateDebut());
            ancien.setDateFin(r.dateFin());
            verifierPeriode(ancien);
            verifierChevauchement(ancien, ancien.getId());
            BaremeDto apres = versDto(ancien, ctx, aujourdhui);
            audit.tracer(null, "MODIFICATION", "BAREME", id, "Bareme " + ancien.getLibelle() + " corrige avant son entree en vigueur",
                    avant, apres);
            return apres;
        }

        // En vigueur depuis avant aujourd'hui : nouvelle version a partir de la date d'effet,
        // l'ancienne s'arrete la veille (la date d'effet est forcement posterieure a son debut).
        if (ancien.getDateFin() != null && effet.isAfter(ancien.getDateFin())) {
            throw new BusinessException("La date d'effet tombe apres la fin de ce bareme (" + ancien.getDateFin().format(JJ_MM_AAAA)
                    + ") : creez plutot un nouveau bareme");
        }
        ancien.setDateFin(effet.minusDays(1));
        Bareme nouveau = new Bareme();
        nouveau.setServiceId(ancien.getServiceId());
        nouveau.setTypeOperationId(ancien.getTypeOperationId());
        nouveau.setReseauId(ancien.getReseauId());
        nouveau.setPrecedentId(ancien.getId());
        appliquer(nouveau, r);
        nouveau.setDateDebut(effet);
        nouveau.setDateFin(r.dateFin());
        verifierPeriode(nouveau);
        verifierChevauchement(nouveau, ancien.getId());
        nouveau = repository.save(nouveau);
        BaremeDto apres = versDto(nouveau, ctx, aujourdhui);
        audit.tracer(null, "MODIFICATION", "BAREME", nouveau.getId(), "Bareme " + nouveau.getLibelle()
                + " : nouvelle version a partir du " + effet.format(JJ_MM_AAAA) + " (" + apres.resume() + ")", avant, apres);
        return apres;
    }

    /**
     * Arrete un bareme le jour donne (aujourd'hui par defaut). Un bareme qui
     * n'a pas encore commence est simplement desactive.
     */
    public BaremeDto terminer(Long id, LocalDate dateFin) {
        Bareme b = charger(id);
        LocalDate aujourdhui = aujourdhui();
        Contexte ctx = contexte();
        BaremeDto avant = versDto(b, ctx, aujourdhui);
        LocalDate fin = dateFin != null ? dateFin : aujourdhui;
        if (fin.isBefore(aujourdhui.minusDays(1))) {
            throw new BusinessException("La fin d'un bareme ne se place pas dans le passe");
        }
        if (fin.isBefore(b.getDateDebut())) {
            b.setActif(false);
        } else {
            b.setDateFin(fin);
        }
        BaremeDto apres = versDto(b, ctx, aujourdhui);
        audit.tracer(null, "FIN", "BAREME", id, "Bareme " + b.getLibelle()
                + (b.isActif() ? " arrete le " + fin.format(JJ_MM_AAAA) : " desactive avant son entree en vigueur"), avant, apres);
        return apres;
    }

    // ------------------------------------------------------------------ calcul

    /** Commission proposee pour une operation du jour ; zero si aucun bareme ne s'applique. */
    @Transactional(readOnly = true)
    public Commission commission(Long serviceId, Long typeId, Long reseauId, long montant, LocalDate jour) {
        LocalDate date = jour != null ? jour : aujourdhui();
        Optional<Bareme> bareme = CalculCommission.choisir(repository.findByServiceIdOrderByDateDebutDescIdDesc(serviceId),
                serviceId, typeId, reseauId, date);
        if (bareme.isEmpty()) {
            return new Commission(null, null, 0, "Aucun bareme pour cette operation", Bareme.CompteCredit.COMMISSIONS, false);
        }
        Bareme b = bareme.get();
        CalculCommission.Resultat r = CalculCommission.appliquer(b, montant);
        return new Commission(b.getId(), b.getLibelle(), r.commission(), r.detail(), b.getCompteCredit(), b.isExemple());
    }

    /** Les exemples du cahier des charges, compares a ce que donnent les baremes actuels. */
    @Transactional(readOnly = true)
    public List<Controle> controle() {
        Contexte ctx = contexte();
        List<Controle> lignes = new ArrayList<>();
        for (ExemplesCahier.Cas cas : ExemplesCahier.CAS) {
            ServiceFinancier s = ctx.serviceParCode(cas.service());
            if (s == null) {
                continue;
            }
            TypeOperation t = cas.type() == null ? null : ctx.type(s.getId(), cas.type());
            Reseau res = cas.reseau() == null ? null : ctx.reseau(s.getId(), cas.reseau());
            Commission c = commission(s.getId(), t == null ? null : t.getId(), res == null ? null : res.getId(), cas.montant(), null);
            lignes.add(new Controle(s.getNom(), t == null ? "—" : t.getLibelle(), res == null ? null : res.getNom(),
                    cas.montant(), cas.attendue(), c.baremeId() == null ? null : c.commission(), c.baremeLibelle(), c.detail()));
        }
        return lignes;
    }

    /**
     * Cree les baremes d'exemple du cahier des charges (Keaz Spot, MoneyGram,
     * Western Union, KORI), marques « exemple » : a remplacer par les baremes
     * reels. Un bareme deja present sur la meme portee n'est pas double.
     */
    public List<BaremeDto> chargerExemples() {
        initialisation.assurer();
        Contexte ctx = contexte();
        List<BaremeDto> crees = new ArrayList<>();
        for (ExemplesCahier.Modele m : ExemplesCahier.MODELES) {
            ServiceFinancier s = ctx.serviceParCode(m.service());
            if (s == null) {
                continue;
            }
            TypeOperation t = m.type() == null ? null : ctx.type(s.getId(), m.type());
            Reseau res = m.reseau() == null ? null : ctx.reseau(s.getId(), m.reseau());
            if ((m.type() != null && t == null) || (m.reseau() != null && res == null)) {
                continue;
            }
            Bareme b = new Bareme();
            b.setServiceId(s.getId());
            b.setTypeOperationId(t == null ? null : t.getId());
            b.setReseauId(res == null ? null : res.getId());
            b.setLibelle(m.libelle());
            b.setMode(m.mode());
            b.setTaux(m.taux());
            m.tranches().forEach(x -> b.getTranches().add(
                    new TrancheBareme(x.getMontantMin(), x.getMontantMax(), x.getMode(), x.getValeurFixe(), x.getTaux())));
            b.setDateDebut(aujourdhui());
            b.setExemple(true);
            boolean dejaCouvert = repository.findByServiceIdOrderByDateDebutDescIdDesc(s.getId()).stream()
                    .anyMatch(x -> memePortee(x, b) && chevauche(x, b));
            if (dejaCouvert) {
                continue;
            }
            Bareme enregistre = repository.save(b);
            crees.add(versDto(enregistre, ctx, aujourdhui()));
        }
        if (!crees.isEmpty()) {
            audit.tracer(null, "CREATION", "BAREME", null, crees.size() + " bareme(s) d'exemple du cahier des charges charge(s)",
                    null, crees.stream().map(BaremeDto::libelle).toList());
        }
        return crees;
    }

    // ------------------------------------------------------------- controles

    private void appliquer(Bareme b, Requete r) {
        b.setLibelle(r.libelle().trim());
        b.setMode(r.mode());
        b.setCompteCredit(r.compteCredit() != null ? r.compteCredit() : Bareme.CompteCredit.COMMISSIONS);
        b.setValeurFixe(null);
        b.setTaux(null);
        b.setMinimum(null);
        b.setMaximum(null);
        b.getTranches().clear();
        b.setExemple(false);
        switch (r.mode()) {
            case FIXE -> {
                if (r.valeurFixe() == null || r.valeurFixe() < 0) {
                    throw new BusinessException("Indiquez le montant fixe de la commission");
                }
                b.setValeurFixe(r.valeurFixe());
            }
            case POURCENTAGE -> {
                verifierTaux(r.taux(), "le bareme");
                if (r.minimum() != null && r.minimum() < 0 || r.maximum() != null && r.maximum() < 0) {
                    throw new BusinessException("Le minimum et le maximum ne peuvent pas etre negatifs");
                }
                if (r.minimum() != null && r.maximum() != null && r.minimum() > r.maximum()) {
                    throw new BusinessException("Le minimum depasse le maximum");
                }
                b.setTaux(r.taux());
                b.setMinimum(r.minimum());
                b.setMaximum(r.maximum());
            }
            case TRANCHES -> b.getTranches().addAll(tranches(r.tranches()));
        }
    }

    private List<TrancheBareme> tranches(List<Tranche> demandees) {
        if (demandees == null || demandees.isEmpty()) {
            throw new BusinessException("Ajoutez au moins une tranche");
        }
        List<Tranche> triees = demandees.stream().sorted(Comparator.comparing(Tranche::montantMin)).toList();
        List<TrancheBareme> resultat = new ArrayList<>();
        Long finPrecedente = null;
        for (int i = 0; i < triees.size(); i++) {
            Tranche t = triees.get(i);
            String nom = "La tranche " + (i + 1);
            if (t.montantMin() < 0) {
                throw new BusinessException(nom + " commence sous zero");
            }
            if (t.montantMax() != null && t.montantMax() < t.montantMin()) {
                throw new BusinessException(nom + " finit avant de commencer");
            }
            if (i > 0) {
                if (finPrecedente == null) {
                    throw new BusinessException("La tranche " + i + " est sans limite haute : elle doit etre la derniere");
                }
                if (t.montantMin() <= finPrecedente) {
                    throw new BusinessException(nom + " chevauche la precedente (elle doit commencer apres "
                            + CalculCommission.francs(finPrecedente) + ")");
                }
            }
            if (t.mode() == Bareme.Mode.FIXE) {
                if (t.valeurFixe() == null || t.valeurFixe() < 0) {
                    throw new BusinessException(nom + " : indiquez son montant fixe");
                }
            } else if (t.mode() == Bareme.Mode.POURCENTAGE) {
                verifierTaux(t.taux(), nom.toLowerCase());
            } else {
                throw new BusinessException(nom + " : une tranche est un montant fixe ou un pourcentage");
            }
            resultat.add(new TrancheBareme(t.montantMin(), t.montantMax(), t.mode(),
                    t.mode() == Bareme.Mode.FIXE ? t.valeurFixe() : null, t.mode() == Bareme.Mode.POURCENTAGE ? t.taux() : null));
            finPrecedente = t.montantMax();
        }
        return resultat;
    }

    private static void verifierTaux(BigDecimal taux, String objet) {
        if (taux == null || taux.signum() <= 0 || taux.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Le pourcentage de " + objet + " doit etre compris entre 0 et 100");
        }
        if (taux.scale() > 4 && taux.stripTrailingZeros().scale() > 4) {
            throw new BusinessException("Le pourcentage accepte au plus 4 decimales");
        }
    }

    private void verifierPortee(Bareme b) {
        services.findById(b.getServiceId()).orElseThrow(() -> ResourceNotFoundException.of("Service", b.getServiceId()));
        if (b.getTypeOperationId() != null) {
            TypeOperation t = types.findById(b.getTypeOperationId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Type d'operation", b.getTypeOperationId()));
            if (!t.getService().getId().equals(b.getServiceId())) {
                throw new BusinessException("L'operation " + t.getLibelle() + " n'appartient pas a ce service");
            }
        }
        if (b.getReseauId() != null) {
            Reseau r = reseaux.findById(b.getReseauId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Reseau", b.getReseauId()));
            if (!r.getService().getId().equals(b.getServiceId())) {
                throw new BusinessException("Le reseau " + r.getNom() + " n'appartient pas a ce service");
            }
        }
    }

    private static void verifierPeriode(Bareme b) {
        if (b.getDateFin() != null && b.getDateFin().isBefore(b.getDateDebut())) {
            throw new BusinessException("La date de fin precede la date de debut");
        }
    }

    /** Deux baremes de meme portee ne peuvent pas valoir le meme jour : on ne saurait lequel appliquer. */
    private void verifierChevauchement(Bareme b, Long sauf) {
        repository.findByServiceIdOrderByDateDebutDescIdDesc(b.getServiceId()).stream()
                .filter(x -> !x.getId().equals(sauf) && !x.getId().equals(b.getId()))
                .filter(x -> memePortee(x, b) && chevauche(x, b))
                .findFirst()
                .ifPresent(x -> {
                    throw new BusinessException("Le bareme « " + x.getLibelle() + " » couvre deja cette operation du "
                            + x.getDateDebut().format(JJ_MM_AAAA)
                            + (x.getDateFin() == null ? " sans date de fin" : " au " + x.getDateFin().format(JJ_MM_AAAA))
                            + " : modifiez-le ou arretez-le");
                });
    }

    private static boolean memePortee(Bareme a, Bareme b) {
        return Objects.equals(a.getTypeOperationId(), b.getTypeOperationId()) && Objects.equals(a.getReseauId(), b.getReseauId());
    }

    private static boolean chevauche(Bareme a, Bareme b) {
        if (!a.isActif() || !b.isActif()) {
            return false;
        }
        LocalDate finA = a.getDateFin() == null ? LocalDate.MAX : a.getDateFin();
        LocalDate finB = b.getDateFin() == null ? LocalDate.MAX : b.getDateFin();
        return !a.getDateDebut().isAfter(finB) && !b.getDateDebut().isAfter(finA);
    }

    // ---------------------------------------------------------------- outils

    static LocalDate aujourdhui() {
        return LocalDate.now(JourneeCaisse.FUSEAU);
    }

    private Bareme charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Bareme", id));
    }

    /** Catalogue charge une fois pour nommer services, operations et reseaux. */
    private Contexte contexte() {
        return new Contexte(services.findAll(), types.findAll(), reseaux.findAll());
    }

    private record Contexte(Map<Long, ServiceFinancier> services, Map<Long, TypeOperation> types, Map<Long, Reseau> reseaux) {

        Contexte(List<ServiceFinancier> s, List<TypeOperation> t, List<Reseau> r) {
            this(s.stream().collect(Collectors.toMap(ServiceFinancier::getId, Function.identity())),
                    t.stream().collect(Collectors.toMap(TypeOperation::getId, Function.identity())),
                    r.stream().collect(Collectors.toMap(Reseau::getId, Function.identity())));
        }

        int ordreService(Long id) {
            ServiceFinancier s = services.get(id);
            return s == null ? 0 : s.getOrdre();
        }

        ServiceFinancier serviceParCode(String code) {
            return services.values().stream().filter(s -> s.getCode().equals(code)).findFirst().orElse(null);
        }

        TypeOperation type(Long serviceId, String code) {
            return types.values().stream().filter(t -> t.getService().getId().equals(serviceId) && t.getCode().equals(code))
                    .findFirst().orElse(null);
        }

        Reseau reseau(Long serviceId, String code) {
            return reseaux.values().stream().filter(r -> r.getService().getId().equals(serviceId) && r.getCode().equals(code))
                    .findFirst().orElse(null);
        }
    }

    private static BaremeDto versDto(Bareme b, Contexte ctx, LocalDate aujourdhui) {
        ServiceFinancier s = ctx.services().get(b.getServiceId());
        TypeOperation t = b.getTypeOperationId() == null ? null : ctx.types().get(b.getTypeOperationId());
        Reseau r = b.getReseauId() == null ? null : ctx.reseaux().get(b.getReseauId());
        Statut statut = !b.isActif() ? Statut.DESACTIVE
                : b.getDateFin() != null && b.getDateFin().isBefore(aujourdhui) ? Statut.TERMINE
                : b.getDateDebut().isAfter(aujourdhui) ? Statut.A_VENIR : Statut.EN_COURS;
        String portee = (t == null ? "Toutes les operations" : t.getLibelle()) + (r == null ? "" : " · " + r.getNom());
        return new BaremeDto(b.getId(), b.getServiceId(), s == null ? null : s.getNom(), b.getTypeOperationId(),
                t == null ? null : t.getLibelle(), b.getReseauId(), r == null ? null : r.getNom(), b.getLibelle(), b.getMode(),
                b.getValeurFixe(), b.getTaux(), b.getMinimum(), b.getMaximum(),
                b.getTranches().stream().map(x -> new Tranche(x.getMontantMin(), x.getMontantMax(), x.getMode(), x.getValeurFixe(), x.getTaux())).toList(),
                b.getCompteCredit(), b.getDateDebut(), b.getDateFin(), b.isActif(), b.isExemple(), b.getPrecedentId(),
                statut, resume(b), portee);
    }

    static String resume(Bareme b) {
        return switch (b.getMode()) {
            case FIXE -> CalculCommission.francs(b.getValeurFixe() == null ? 0 : b.getValeurFixe()) + " par operation";
            case POURCENTAGE -> CalculCommission.taux(b.getTaux())
                    + (b.getMinimum() != null ? ", min " + CalculCommission.francs(b.getMinimum()) : "")
                    + (b.getMaximum() != null ? ", max " + CalculCommission.francs(b.getMaximum()) : "");
            case TRANCHES -> b.getTranches().size() + " tranche(s)";
        };
    }
}
