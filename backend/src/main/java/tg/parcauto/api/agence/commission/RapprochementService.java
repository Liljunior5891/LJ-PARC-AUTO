package tg.parcauto.api.agence.commission;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.catalogue.Reseau;
import tg.parcauto.api.agence.catalogue.ServiceFinancier;
import tg.parcauto.api.agence.catalogue.TypeOperation;
import tg.parcauto.api.agence.cloture.VerrouJournee;
import tg.parcauto.api.agence.operation.TransactionAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgenceRepository;
import tg.parcauto.api.agence.tresorerie.Ecriture;
import tg.parcauto.api.agence.tresorerie.NatureEcriture;
import tg.parcauto.api.agence.tresorerie.RegistreService;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rapprochement des commissions avec les versements des operateurs.
 * <p>
 * Pour un service a versement differe, la commission d'une transaction est
 * acquise (elle compte dans le chiffre d'affaires) mais reste en attente. Quand
 * l'operateur verse, on enregistre le montant recu et le compte credite, et l'on
 * marque les commissions couvertes, une a une ou toutes celles d'une periode.
 * L'ecart entre l'attendu et le verse est conserve. Un versement annule remet
 * ses commissions en attente.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RapprochementService {

    private final VersementCommissionRepository versements;
    private final PerimetreAgence perimetre;
    private final CompteAgenceRepository comptes;
    private final RegistreService registre;
    private final VerrouJournee verrou;
    private final NumerotationService numerotation;
    private final AuditAgenceService audit;

    @PersistenceContext
    private EntityManager em;

    /** Ce que l'agence attend de chaque operateur. */
    public record AttenteOperateur(Long serviceId, String service, boolean differe, long nombre, long montant, LocalDate plusAncienne) {
    }

    public record CommissionAttente(Long transactionId, String reference, Instant date, LocalDate journee, String reseau,
                                    String operation, long montant, long commission, String caissierNom) {
    }

    public record Versement(@NotNull Long agenceId, @NotNull Long serviceId,
                            /** Commissions choisies une a une ; vide : toutes celles en attente de la periode. */
                            List<Long> transactionIds, LocalDate du, LocalDate au,
                            @NotNull @PositiveOrZero Long montantVerse, @NotNull Long compteId,
                            @Size(max = 80) String referenceOperateur, @Size(max = 500) String observations) {
    }

    public record VersementDto(Long id, String reference, Long agenceId, String agenceNom, Long serviceId, String service,
                               String referenceOperateur, Instant dateVersement, long montantAttendu, long montantVerse, long ecart,
                               int nombreCommissions, String compteNom, String observations, VersementCommission.Statut statut,
                               String auteurNom, String motifAnnulation) {
    }

    // ------------------------------------------------------------------ lecture

    /** Par operateur : nombre et montant des commissions en attente, et la plus ancienne. */
    @Transactional(readOnly = true)
    public List<AttenteOperateur> attentes(Long agenceId) {
        Set<Long> agences = agences(agenceId);
        Map<Long, Object[]> parService = em.createQuery("""
                        select t.serviceId, count(t), coalesce(sum(t.commission), 0), min(t.journee) from TransactionAgence t
                        where t.agenceId in :agences and t.statut = :validee and t.commissionStatut = :acquise
                        group by t.serviceId
                        """, Object[].class)
                .setParameter("agences", agences.isEmpty() ? Set.of(-1L) : agences)
                .setParameter("validee", TransactionAgence.Statut.VALIDEE)
                .setParameter("acquise", TransactionAgence.StatutCommission.ACQUISE)
                .getResultList().stream().collect(Collectors.toMap(r -> (Long) r[0], r -> r));
        return em.createQuery("select s from ServiceFinancier s order by s.ordre, s.nom", ServiceFinancier.class).getResultList().stream()
                .filter(s -> s.isActif() || parService.containsKey(s.getId()))
                .map(s -> {
                    Object[] r = parService.get(s.getId());
                    return new AttenteOperateur(s.getId(), s.getNom(), s.isCommissionDifferee(), r == null ? 0 : n(r[1]), r == null ? 0 : n(r[2]),
                            r == null ? null : (LocalDate) r[3]);
                })
                .toList();
    }

    /** Commissions en attente d'un operateur, des plus anciennes aux plus recentes. */
    @Transactional(readOnly = true)
    public Page<CommissionAttente> enAttente(Long agenceId, Long serviceId, LocalDate du, LocalDate au, Pageable pageable) {
        Agence agence = perimetre.exiger(agenceId);
        String filtre = """
                from TransactionAgence t where t.agenceId = :agence and t.serviceId = :service and t.statut = :validee
                  and t.commissionStatut = :acquise and t.journee >= :du and t.journee <= :au""";
        LocalDate debut = du != null ? du : LocalDate.of(2000, 1, 1);
        LocalDate fin = au != null ? au : LocalDate.of(2999, 12, 31);
        long total = em.createQuery("select count(t) " + filtre, Long.class)
                .setParameter("agence", agence.getId()).setParameter("service", serviceId)
                .setParameter("validee", TransactionAgence.Statut.VALIDEE).setParameter("acquise", TransactionAgence.StatutCommission.ACQUISE)
                .setParameter("du", debut).setParameter("au", fin).getSingleResult();
        List<TransactionAgence> page = em.createQuery("select t " + filtre + " order by t.dateOperation, t.id", TransactionAgence.class)
                .setParameter("agence", agence.getId()).setParameter("service", serviceId)
                .setParameter("validee", TransactionAgence.Statut.VALIDEE).setParameter("acquise", TransactionAgence.StatutCommission.ACQUISE)
                .setParameter("du", debut).setParameter("au", fin)
                .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize()).getResultList();
        Map<Long, String> reseaux = em.createQuery("select r from Reseau r", Reseau.class).getResultList().stream()
                .collect(Collectors.toMap(Reseau::getId, Reseau::getNom));
        Map<Long, String> types = em.createQuery("select t from TypeOperation t", TypeOperation.class).getResultList().stream()
                .collect(Collectors.toMap(TypeOperation::getId, TypeOperation::getLibelle));
        return new PageImpl<>(page.stream().map(t -> new CommissionAttente(t.getId(), t.getReference(), t.getDateOperation(), t.getJournee(),
                t.getReseauId() == null ? null : reseaux.get(t.getReseauId()), types.get(t.getTypeOperationId()), t.getMontant(),
                t.getCommission(), t.getCaissierNom())).toList(), pageable, total);
    }

    @Transactional(readOnly = true)
    public Page<VersementDto> historique(Long agenceId, Long serviceId, Pageable pageable) {
        Set<Long> agences = agences(agenceId);
        Set<Long> ids = agences.isEmpty() ? Set.of(-1L) : agences;
        Contexte ctx = contexte();
        return (serviceId == null ? versements.findByAgenceIdInOrderByDateVersementDescIdDesc(ids, pageable)
                : versements.findByAgenceIdInAndServiceIdOrderByDateVersementDescIdDesc(ids, serviceId, pageable)).map(v -> versDto(v, ctx));
    }

    // --------------------------------------------------------------- ecriture

    /**
     * Enregistre le versement d'un operateur : le montant recu entre au compte
     * choisi, les commissions couvertes passent a « creditee », l'ecart est garde.
     */
    public VersementDto enregistrer(Versement v, String auteur) {
        Agence agence = perimetre.exiger(v.agenceId());
        ServiceFinancier service = em.find(ServiceFinancier.class, v.serviceId());
        if (service == null) {
            throw ResourceNotFoundException.of("Service", v.serviceId());
        }
        CompteAgence compte = comptes.findById(v.compteId()).orElseThrow(() -> ResourceNotFoundException.of("Compte", v.compteId()));
        if (!compte.getAgenceId().equals(agence.getId()) || !compte.isActif()) {
            throw new BusinessException("Le compte " + compte.getLibelle() + " ne peut pas recevoir ce versement");
        }
        List<TransactionAgence> couvertes;
        if (v.transactionIds() != null && !v.transactionIds().isEmpty()) {
            couvertes = em.createQuery("select t from TransactionAgence t where t.id in :ids order by t.id", TransactionAgence.class)
                    .setParameter("ids", v.transactionIds()).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            if (couvertes.size() != v.transactionIds().stream().distinct().count()) {
                throw new BusinessException("Une des commissions choisies est introuvable");
            }
        } else {
            couvertes = em.createQuery("""
                            select t from TransactionAgence t where t.agenceId = :agence and t.serviceId = :service and t.statut = :validee
                              and t.commissionStatut = :acquise and t.journee >= :du and t.journee <= :au order by t.id
                            """, TransactionAgence.class)
                    .setParameter("agence", agence.getId()).setParameter("service", service.getId())
                    .setParameter("validee", TransactionAgence.Statut.VALIDEE).setParameter("acquise", TransactionAgence.StatutCommission.ACQUISE)
                    .setParameter("du", v.du() != null ? v.du() : LocalDate.of(2000, 1, 1))
                    .setParameter("au", v.au() != null ? v.au() : LocalDate.of(2999, 12, 31))
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        }
        if (couvertes.isEmpty()) {
            throw new BusinessException("Aucune commission en attente a rapprocher pour " + service.getNom());
        }
        for (TransactionAgence t : couvertes) {
            em.refresh(t);
            if (!t.getAgenceId().equals(agence.getId()) || !t.getServiceId().equals(service.getId())) {
                throw new BusinessException("La transaction " + t.getReference() + " n'est pas une operation " + service.getNom() + " de cette agence");
            }
            if (t.getStatut() != TransactionAgence.Statut.VALIDEE || t.getCommissionStatut() != TransactionAgence.StatutCommission.ACQUISE) {
                throw new BusinessException("La commission de " + t.getReference() + " n'est plus en attente");
            }
        }
        long attendu = couvertes.stream().mapToLong(TransactionAgence::getCommission).sum();
        Instant maintenant = Instant.now();
        VersementCommission vc = new VersementCommission();
        vc.setAgenceId(agence.getId());
        vc.setServiceId(service.getId());
        vc.setReference(numerotation.prochainNumero("VCO"));
        vc.setReferenceOperateur(texte(v.referenceOperateur()));
        vc.setDateVersement(maintenant);
        vc.setJournee(registre.journee(maintenant));
        vc.setMontantAttendu(attendu);
        vc.setMontantVerse(v.montantVerse());
        vc.setEcart(v.montantVerse() - attendu);
        vc.setNombreCommissions(couvertes.size());
        vc.setCompteId(compte.getId());
        vc.setObservations(texte(v.observations()));
        vc.setAuteurNom(auteur);
        vc = versements.save(vc);
        if (v.montantVerse() > 0) {
            Ecriture e = registre.enregistrer(new RegistreService.Entete(agence.getId(), NatureEcriture.COMMISSION,
                    vc.getReference() + " - Commissions " + service.getNom() + " (" + couvertes.size() + " operation(s))", null,
                    "VERSEMENT_COMMISSION", vc.getId()),
                    List.of(new RegistreService.Mouvement(compte.getId(), v.montantVerse(), service.getId(), null)));
            vc.setEcritureId(e.getId());
        }
        for (TransactionAgence t : couvertes) {
            t.setCommissionStatut(TransactionAgence.StatutCommission.CREDITEE);
            t.setVersementId(vc.getId());
        }
        audit.tracer(agence.getId(), "RAPPROCHEMENT", "VERSEMENT_COMMISSION", vc.getId(), "Versement " + service.getNom() + " "
                + vc.getReference() + " : attendu " + RegistreService.francs(attendu) + ", verse " + RegistreService.francs(v.montantVerse())
                + ", ecart " + RegistreService.francs(vc.getEcart()), null, couvertes.stream().map(TransactionAgence::getReference).toList());
        return versDto(vc, contexte());
    }

    /** Annule un versement : l'argent ressort du compte, ses commissions repassent en attente. */
    public VersementDto annuler(Long id, String motif, String auteur) {
        VersementCommission vc = versements.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Versement", id));
        perimetre.exiger(vc.getAgenceId());
        if (vc.getStatut() != VersementCommission.Statut.VALIDE) {
            throw new BusinessException("Le versement " + vc.getReference() + " est deja annule");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif de l'annulation est obligatoire");
        }
        verrou.exigerOuverte(vc.getAgenceId(), vc.getJournee(), "l'annulation de " + vc.getReference());
        if (vc.getEcritureId() != null) {
            vc.setAnnulationEcritureId(registre.contrePasser(vc.getEcritureId(), motif.trim()).getId());
        }
        em.createQuery("select t from TransactionAgence t where t.versementId = :v", TransactionAgence.class).setParameter("v", vc.getId())
                .getResultList().forEach(t -> {
                    t.setCommissionStatut(TransactionAgence.StatutCommission.ACQUISE);
                    t.setVersementId(null);
                });
        vc.setStatut(VersementCommission.Statut.ANNULE);
        vc.setMotifAnnulation(motif.trim());
        audit.tracer(vc.getAgenceId(), "ANNULATION", "VERSEMENT_COMMISSION", vc.getId(), "Versement " + vc.getReference() + " annule par "
                + auteur + " (" + motif.trim() + ") : " + vc.getNombreCommissions() + " commission(s) de nouveau en attente", "VALIDE", "ANNULE");
        return versDto(vc, contexte());
    }

    // ------------------------------------------------ tableau de bord et alertes

    /** Commissions en attente aujourd'hui (toutes dates confondues). */
    @Transactional(readOnly = true)
    public long totalEnAttente(Set<Long> agences) {
        if (agences.isEmpty()) {
            return 0;
        }
        return n(em.createQuery("select coalesce(sum(t.commission), 0) from TransactionAgence t where t.agenceId in :agences"
                        + " and t.statut = :validee and t.commissionStatut = :acquise")
                .setParameter("agences", agences).setParameter("validee", TransactionAgence.Statut.VALIDEE)
                .setParameter("acquise", TransactionAgence.StatutCommission.ACQUISE).getSingleResult());
    }

    /** Commissions de la periode deja creditees (recues). */
    @Transactional(readOnly = true)
    public long encaissees(Set<Long> agences, LocalDate du, LocalDate au) {
        if (agences.isEmpty()) {
            return 0;
        }
        return n(em.createQuery("select coalesce(sum(t.commission), 0) from TransactionAgence t where t.agenceId in :agences"
                        + " and t.statut = :validee and t.commissionStatut = :creditee and t.journee >= :du and t.journee <= :au")
                .setParameter("agences", agences).setParameter("validee", TransactionAgence.Statut.VALIDEE)
                .setParameter("creditee", TransactionAgence.StatutCommission.CREDITEE).setParameter("du", du).setParameter("au", au)
                .getSingleResult());
    }

    /** [agenceId, serviceId, nombre, montant, plus ancienne journee] des commissions en attente depuis avant la limite. */
    @Transactional(readOnly = true)
    public List<Object[]> anciennes(Set<Long> agences, LocalDate avant) {
        if (agences.isEmpty()) {
            return List.of();
        }
        return em.createQuery("""
                        select t.agenceId, t.serviceId, count(t), coalesce(sum(t.commission), 0), min(t.journee) from TransactionAgence t
                        where t.agenceId in :agences and t.statut = :validee and t.commissionStatut = :acquise and t.journee < :avant
                        group by t.agenceId, t.serviceId
                        """, Object[].class)
                .setParameter("agences", agences).setParameter("validee", TransactionAgence.Statut.VALIDEE)
                .setParameter("acquise", TransactionAgence.StatutCommission.ACQUISE).setParameter("avant", avant).getResultList();
    }

    // ------------------------------------------------------------------ outils

    private Set<Long> agences(Long agenceId) {
        return agenceId != null ? Set.of(perimetre.exiger(agenceId).getId())
                : perimetre.visibles().stream().map(Agence::getId).collect(Collectors.toSet());
    }

    private record Contexte(Map<Long, String> agences, Map<Long, String> services, Map<Long, String> comptes) {
    }

    private Contexte contexte() {
        List<Agence> visibles = perimetre.visibles();
        return new Contexte(visibles.stream().collect(Collectors.toMap(Agence::getId, Agence::getNom)),
                em.createQuery("select s from ServiceFinancier s", ServiceFinancier.class).getResultList().stream()
                        .collect(Collectors.toMap(ServiceFinancier::getId, ServiceFinancier::getNom)),
                comptes.findByAgenceIdIn(visibles.stream().map(Agence::getId).toList()).stream()
                        .collect(Collectors.toMap(CompteAgence::getId, CompteAgence::getLibelle)));
    }

    private static VersementDto versDto(VersementCommission v, Contexte ctx) {
        return new VersementDto(v.getId(), v.getReference(), v.getAgenceId(), ctx.agences().get(v.getAgenceId()), v.getServiceId(),
                ctx.services().get(v.getServiceId()), v.getReferenceOperateur(), v.getDateVersement(), v.getMontantAttendu(),
                v.getMontantVerse(), v.getEcart(), v.getNombreCommissions(), ctx.comptes().get(v.getCompteId()), v.getObservations(),
                v.getStatut(), v.getAuteurNom(), v.getMotifAnnulation());
    }

    private static String texte(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static long n(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
