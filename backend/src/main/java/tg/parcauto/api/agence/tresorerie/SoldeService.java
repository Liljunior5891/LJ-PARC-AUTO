package tg.parcauto.api.agence.tresorerie;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.catalogue.ServiceFinancier;
import tg.parcauto.api.agence.catalogue.ServiceFinancierRepository;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Soldes et tresorerie des agences, tous calcules a partir du registre.
 * <p>
 * Tresorerie disponible = cash + soldes virtuels + comptes bancaires, hors
 * comptes bloques. Les commissions et les fonds d'exploitation sont montres a
 * part. Les soldes d'ouverture sont des ecritures horodatees, saisies une fois
 * par compte ; ensuite seule une regularisation tracee change un solde.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SoldeService {

    private final PerimetreAgence perimetre;
    private final CompteAgenceRepository comptes;
    private final LigneRegistreRepository lignes;
    private final CompteService compteService;
    private final RegistreService registre;
    private final ServiceFinancierRepository services;
    private final AuditAgenceService audit;

    public record Totaux(long cash, long virtuel, long banque, long tresorerie, long commissions, long fonds) {

        static Totaux de(List<CompteDto> comptes) {
            long cash = 0, virtuel = 0, banque = 0, commissions = 0, fonds = 0;
            for (CompteDto c : comptes) {
                if (c.bloque()) {
                    continue;
                }
                switch (c.famille()) {
                    case CASH -> cash += c.solde();
                    case VIRTUEL -> virtuel += c.solde();
                    case BANQUE -> banque += c.solde();
                    case COMMISSIONS -> commissions += c.solde();
                    case FONDS -> fonds += c.solde();
                }
            }
            return new Totaux(cash, virtuel, banque, cash + virtuel + banque, commissions, fonds);
        }

        static Totaux somme(List<Totaux> liste) {
            return new Totaux(liste.stream().mapToLong(Totaux::cash).sum(), liste.stream().mapToLong(Totaux::virtuel).sum(),
                    liste.stream().mapToLong(Totaux::banque).sum(), liste.stream().mapToLong(Totaux::tresorerie).sum(),
                    liste.stream().mapToLong(Totaux::commissions).sum(), liste.stream().mapToLong(Totaux::fonds).sum());
        }
    }

    public record CompteDto(Long id, Long agenceId, TypeCompte type, String typeLibelle, TypeCompte.Famille famille,
                            String libelle, Long serviceId, String serviceNom, String categorie, boolean serviceActif,
                            String banque, String numeroCompte, long solde, long decouvertAutorise, boolean bloque,
                            boolean actif, boolean manuel, Long seuilAlerte,
                            /** Totaux du registre : ouverture, entrees et sorties hors ouverture. */
                            long ouverture, long entrees, long sorties, long nombreMouvements) {
    }

    public record AgenceSoldes(Long id, String code, String nom, boolean actif, Totaux totaux, List<CompteDto> comptes) {
    }

    public record Etat(LocalDate journee, Totaux totaux, List<AgenceSoldes> agences) {
    }

    public record LigneDto(Long id, Long ecritureId, String numero, NatureEcriture nature, String natureLibelle,
                           Instant date, LocalDate journee, String libelle, String motif, long montant, long soldeApres,
                           String auteurNom, boolean annulation) {
    }

    public record SoldeOuverture(@NotNull Long compteId, @NotNull @PositiveOrZero Long montant) {
    }

    public record Ouverture(@NotNull List<@Valid SoldeOuverture> soldes, @Size(max = 500) String observation) {
    }

    public record RequeteCompte(TypeCompte type, @Size(max = 120) String libelle, @Size(max = 120) String banque,
                                @Size(max = 60) String numeroCompte, @PositiveOrZero Long decouvertAutorise,
                                Boolean bloque, Boolean actif, @PositiveOrZero Long seuilAlerte) {
    }

    public record Verification(Long compteId, String libelle, long soldeEnregistre, long soldeRegistre, long ecart) {
    }

    public record SoldeAuJour(Long compteId, LocalDate jour, long solde) {
    }

    // ----------------------------------------------------------------- lecture

    /** Soldes d'une agence, ou de toutes les agences visibles si aucune n'est demandee. */
    public Etat etat(Long agenceId) {
        List<Agence> agences = agenceId == null ? perimetre.visibles() : List.of(perimetre.exiger(agenceId));
        Map<Long, ServiceFinancier> parService = services.findAll().stream()
                .collect(Collectors.toMap(ServiceFinancier::getId, Function.identity()));
        List<AgenceSoldes> resultats = new ArrayList<>();
        for (Agence agence : agences) {
            List<CompteAgence> liste = compteService.synchroniser(agence);
            Map<Long, Object[]> syntheses = liste.isEmpty() ? Map.of()
                    : lignes.syntheses(liste.stream().map(CompteAgence::getId).toList(), NatureEcriture.OUVERTURE).stream()
                    .collect(Collectors.toMap(s -> (Long) s[0], s -> s));
            List<CompteDto> dtos = liste.stream()
                    .map(c -> versDto(c, parService.get(c.getServiceId()), syntheses.get(c.getId())))
                    .sorted(ordre(parService))
                    .toList();
            resultats.add(new AgenceSoldes(agence.getId(), agence.getCode(), agence.getNom(), agence.isActif(),
                    Totaux.de(dtos.stream().filter(CompteDto::actif).toList()), dtos));
        }
        return new Etat(registre.journee(Instant.now()),
                Totaux.somme(resultats.stream().map(AgenceSoldes::totaux).toList()), resultats);
    }

    /** Lignes du registre d'un compte, de la plus recente a la plus ancienne. */
    @Transactional(readOnly = true)
    public Page<LigneDto> registre(Long compteId, LocalDate du, LocalDate au, Pageable pageable) {
        CompteAgence compte = charger(compteId);
        Page<LigneRegistre> page = du == null && au == null
                ? lignes.findByCompteIdOrderByIdDesc(compte.getId(), pageable)
                : lignes.findByCompteIdAndJourneeBetweenOrderByIdDesc(compte.getId(),
                du == null ? LocalDate.of(2000, 1, 1) : du, au == null ? LocalDate.of(2999, 12, 31) : au, pageable);
        return page.map(l -> {
            Ecriture e = l.getEcriture();
            return new LigneDto(l.getId(), e.getId(), e.getNumero(), e.getNature(), e.getNature().getLibelle(),
                    l.getDateOperation(), l.getJournee(), e.getLibelle(), e.getMotif(), l.getMontant(), l.getSoldeApres(),
                    e.getAuteurNom(), e.getEcritureAnnuleeId() != null);
        });
    }

    /** Solde d'un compte a la fin d'une journee, reconstitue a partir du registre. */
    @Transactional(readOnly = true)
    public SoldeAuJour soldeAu(Long compteId, LocalDate jour) {
        CompteAgence compte = charger(compteId);
        return new SoldeAuJour(compte.getId(), jour, lignes.soldeAu(compte.getId(), jour));
    }

    // ------------------------------------------------------- soldes d'ouverture

    /**
     * Soldes d'ouverture de l'agence, en une seule ecriture horodatee. Un compte
     * qui a deja des mouvements ne recoit plus de solde d'ouverture.
     */
    public Etat ouvrir(Long agenceId, Ouverture demande) {
        Agence agence = perimetre.exiger(agenceId);
        List<SoldeOuverture> retenus = demande.soldes().stream().filter(s -> s.montant() > 0).toList();
        if (retenus.isEmpty()) {
            throw new BusinessException("Saisissez au moins un solde d'ouverture");
        }
        List<Long> ids = retenus.stream().map(SoldeOuverture::compteId).distinct().toList();
        if (ids.size() != retenus.size()) {
            throw new BusinessException("Un compte figure deux fois dans la saisie");
        }
        Map<Long, CompteAgence> verrouilles = registre.verrouiller(ids).stream()
                .collect(Collectors.toMap(CompteAgence::getId, Function.identity()));
        List<RegistreService.Mouvement> mouvements = new ArrayList<>();
        for (SoldeOuverture s : retenus) {
            CompteAgence compte = verrouilles.get(s.compteId());
            if (compte == null || !compte.getAgenceId().equals(agence.getId())) {
                throw new BusinessException("Le compte " + s.compteId() + " n'appartient pas a l'agence " + agence.getNom());
            }
            if (lignes.existsByCompteId(compte.getId())) {
                throw new BusinessException("Le compte " + compte.getLibelle()
                        + " a deja des mouvements : son solde ne se corrige plus que par une regularisation");
            }
            mouvements.add(RegistreService.Mouvement.de(compte.getId(), s.montant()));
        }
        String observation = demande.observation() == null || demande.observation().isBlank() ? null
                : demande.observation().trim();
        Ecriture ecriture = registre.enregistrer(new RegistreService.Entete(agence.getId(), NatureEcriture.OUVERTURE,
                "Soldes d'ouverture - " + agence.getNom(), observation, null, null), mouvements);
        audit.tracer(agence.getId(), "SOLDES_OUVERTURE", "ECRITURE", ecriture.getId(),
                "Soldes d'ouverture de " + agence.getNom() + " (" + ecriture.getNumero() + ")", null,
                retenus.stream().map(s -> Map.of("compte", verrouilles.get(s.compteId()).getLibelle(), "montant", s.montant()))
                        .toList());
        return etat(agence.getId());
    }

    // ------------------------------------------------------------ verification

    /**
     * Compare le solde garde en cache de chaque compte a la somme de son
     * registre ; un ecart est corrige (le registre fait foi) et trace.
     */
    public List<Verification> verifier(Long agenceId) {
        Agence agence = perimetre.exiger(agenceId);
        List<CompteAgence> liste = registre.verrouiller(comptes.findByAgenceIdOrderByIdAsc(agence.getId()).stream()
                .map(CompteAgence::getId).toList());
        if (liste.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> registreParCompte = lignes.syntheses(liste.stream().map(CompteAgence::getId).toList(),
                        NatureEcriture.OUVERTURE).stream()
                .collect(Collectors.toMap(s -> (Long) s[0], s -> ((Number) s[4]).longValue()));
        List<Verification> resultats = new ArrayList<>();
        for (CompteAgence c : liste) {
            long attendu = registreParCompte.getOrDefault(c.getId(), 0L);
            Verification v = new Verification(c.getId(), c.getLibelle(), c.getSolde(), attendu, c.getSolde() - attendu);
            resultats.add(v);
            if (v.ecart() != 0) {
                c.setSolde(attendu);
                audit.tracer(agence.getId(), "CORRECTION_SOLDE", "COMPTE", c.getId(),
                        "Solde de " + c.getLibelle() + " realigne sur le registre", v.soldeEnregistre(), attendu);
            }
        }
        audit.tracer(agence.getId(), "VERIFICATION", "AGENCE", agence.getId(),
                "Verification des soldes de " + agence.getNom() + " : "
                        + resultats.stream().filter(v -> v.ecart() != 0).count() + " ecart(s)", null, null);
        return resultats;
    }

    // ------------------------------------------------------------------ comptes

    /** Compte bancaire ou fonds d'exploitation, crees a la main ; les autres comptes se creent seuls. */
    public CompteDto creerCompte(Long agenceId, RequeteCompte r) {
        Agence agence = perimetre.exiger(agenceId);
        if (r.type() == null || !r.type().isManuel()) {
            throw new BusinessException("Seuls les comptes bancaires et les fonds d'exploitation se creent a la main");
        }
        if (r.libelle() == null || r.libelle().isBlank()) {
            throw new BusinessException("Donnez un nom au compte");
        }
        CompteAgence c = new CompteAgence();
        c.setAgenceId(agence.getId());
        c.setType(r.type());
        appliquer(c, r);
        c = comptes.save(c);
        audit.tracer(agence.getId(), "CREATION", "COMPTE", c.getId(), "Compte " + c.getLibelle() + " cree", null, r);
        return versDto(c, null, null);
    }

    /** Nom et banque (comptes manuels), decouvert tolere, blocage et activation. */
    public CompteDto modifierCompte(Long compteId, RequeteCompte r) {
        CompteAgence c = charger(compteId);
        CompteDto avant = versDto(c, null, null);
        if (Boolean.FALSE.equals(r.actif()) && c.isActif()) {
            if (!c.getType().isManuel()) {
                throw new BusinessException("Le compte " + c.getLibelle() + " est cree par le systeme et ne se desactive pas");
            }
            if (c.getSolde() != 0) {
                throw new BusinessException("Le compte " + c.getLibelle() + " a un solde de "
                        + RegistreService.francs(c.getSolde()) + " : videz-le avant de le desactiver");
            }
        }
        appliquer(c, r);
        CompteDto apres = versDto(c, null, null);
        audit.tracer(c.getAgenceId(), "MODIFICATION", "COMPTE", c.getId(), "Compte " + c.getLibelle() + " modifie",
                avant, apres);
        return apres;
    }

    private void appliquer(CompteAgence c, RequeteCompte r) {
        if (c.getType().isManuel()) {
            if (r.libelle() != null && !r.libelle().isBlank()) {
                c.setLibelle(r.libelle().trim());
            }
            c.setBanque(r.banque() == null || r.banque().isBlank() ? null : r.banque().trim());
            c.setNumeroCompte(r.numeroCompte() == null || r.numeroCompte().isBlank() ? null : r.numeroCompte().trim());
        }
        if (r.decouvertAutorise() != null) {
            c.setDecouvertAutorise(r.decouvertAutorise());
        }
        if (r.bloque() != null) {
            c.setBloque(r.bloque());
        }
        // Le seuil d'alerte s'envoie toujours : vide, il retire l'alerte du compte.
        c.setSeuilAlerte(r.seuilAlerte());
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
    }

    // ---------------------------------------------------------------- outils

    private CompteAgence charger(Long compteId) {
        CompteAgence compte = comptes.findById(compteId).orElseThrow(() -> ResourceNotFoundException.of("Compte", compteId));
        perimetre.exiger(compte.getAgenceId());
        return compte;
    }

    private static Comparator<CompteDto> ordre(Map<Long, ServiceFinancier> services) {
        return Comparator.<CompteDto>comparingInt(c -> c.famille().ordinal())
                .thenComparingInt(c -> c.type().ordinal())
                .thenComparingInt(c -> c.serviceId() == null || !services.containsKey(c.serviceId()) ? 0
                        : services.get(c.serviceId()).getOrdre())
                .thenComparing(CompteDto::libelle);
    }

    private static CompteDto versDto(CompteAgence c, ServiceFinancier service, Object[] synthese) {
        long ouverture = synthese == null ? 0 : ((Number) synthese[1]).longValue();
        long entrees = synthese == null ? 0 : ((Number) synthese[2]).longValue();
        long sorties = synthese == null ? 0 : ((Number) synthese[3]).longValue();
        long nombre = synthese == null ? 0 : ((Number) synthese[5]).longValue();
        return new CompteDto(c.getId(), c.getAgenceId(), c.getType(), c.getType().getLibelle(), c.getType().getFamille(),
                c.getLibelle(), c.getServiceId(), service == null ? null : service.getNom(),
                service == null ? null : service.getCategorie(), service == null || service.isActif(),
                c.getBanque(), c.getNumeroCompte(), c.getSolde(), c.getDecouvertAutorise(), c.isBloque(), c.isActif(),
                c.getType().isManuel(), c.getSeuilAlerte(), ouverture, entrees, sorties, nombre);
    }
}
