package tg.parcauto.api.agence.mouvement;

import jakarta.persistence.criteria.Predicate;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.DroitsAgence;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.PermissionAgence;
import tg.parcauto.api.agence.ReglageAgence;
import tg.parcauto.api.agence.ReglageAgenceRepository;
import tg.parcauto.api.agence.tresorerie.CompteAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgenceRepository;
import tg.parcauto.api.agence.tresorerie.Ecriture;
import tg.parcauto.api.agence.tresorerie.NatureEcriture;
import tg.parcauto.api.agence.tresorerie.RegistreService;
import tg.parcauto.api.agence.tresorerie.TypeCompte;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.parc.document.StockageFichierService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mouvements entre comptes d'une agence : approvisionnement d'un float,
 * transfert interne, versement ou retrait bancaire, credit de commission,
 * regularisation. Ils ne creent ni recette ni depense.
 * <p>
 * Un mouvement au-dela du seuil regle attend la validation d'une autre
 * personne habilitee ; une regularisation l'attend toujours. L'administrateur
 * valide lui-meme. Un mouvement en attente ne touche aucun solde ; un mouvement
 * valide se corrige par son annulation (ecriture inverse).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MouvementService {

    private final MouvementAgenceRepository repository;
    private final PerimetreAgence perimetre;
    private final DroitsAgence droits;
    private final CompteAgenceRepository comptes;
    private final RegistreService registre;
    private final ReglageAgenceRepository reglages;
    private final NumerotationService numerotation;
    private final StockageFichierService stockage;
    private final AuditAgenceService audit;
    private final tg.parcauto.api.agence.cloture.VerrouJournee verrou;

    public enum Sens { AUGMENTER, DIMINUER }

    public record Requete(
            @NotNull Long agenceId,
            @NotNull MouvementAgence.Type type,
            Long compteSourceId,
            Long compteDestinationId,
            /** Regularisation : le compte corrige et le sens de la correction. */
            Long compteId,
            Sens sens,
            @NotNull @Positive Long montant,
            @NotBlank @Size(max = 255) String motif,
            @Size(max = 1000) String justification) {
    }

    public record MouvementDto(Long id, String reference, Long agenceId, String agenceNom, MouvementAgence.Type type,
                               String typeLibelle, Long compteSourceId, String compteSource, Long compteDestinationId,
                               String compteDestination, long montant, String motif, String justification, String pieceNom,
                               MouvementAgence.Statut statut, Instant dateMouvement, String auteurNom, Instant valideLe,
                               String valideParNom, String motifRejet, String motifAnnulation, boolean validable,
                               boolean annulable) {
    }

    public record Filtres(Long agenceId, MouvementAgence.Statut statut, MouvementAgence.Type type, LocalDate du, LocalDate au) {
    }

    // ---------------------------------------------------------------- saisie

    public MouvementDto creer(Requete r, UtilisateurPrincipal auteur) {
        Agence agence = perimetre.exiger(r.agenceId());
        MouvementAgence m = new MouvementAgence();
        m.setAgenceId(agence.getId());
        m.setType(r.type());
        m.setMontant(r.montant());
        m.setMotif(r.motif().trim());
        m.setJustification(r.justification() == null || r.justification().isBlank() ? null : r.justification().trim());
        if (r.type() == MouvementAgence.Type.REGULARISATION) {
            if (r.compteId() == null || r.sens() == null) {
                throw new BusinessException("Choisissez le compte a regulariser et le sens de la correction");
            }
            CompteAgence c = compte(agence, r.compteId());
            if (r.sens() == Sens.AUGMENTER) {
                m.setCompteDestinationId(c.getId());
            } else {
                m.setCompteSourceId(c.getId());
            }
        } else {
            if (r.compteSourceId() == null || r.compteDestinationId() == null) {
                throw new BusinessException("Choisissez le compte de depart et le compte d'arrivee");
            }
            if (r.compteSourceId().equals(r.compteDestinationId())) {
                throw new BusinessException("Le compte de depart et le compte d'arrivee doivent etre differents");
            }
            CompteAgence source = compte(agence, r.compteSourceId());
            CompteAgence destination = compte(agence, r.compteDestinationId());
            verifierType(r.type(), source, destination);
            m.setCompteSourceId(source.getId());
            m.setCompteDestinationId(destination.getId());
        }
        m.setReference(numerotation.prochainNumero("MVT"));
        m.setDateMouvement(Instant.now());
        m.setAuteurId(auteur.getId());
        m.setAuteurNom(auteur.getNomComplet());

        ReglageAgence reglage = reglages.findFirstByOrderByIdAsc().orElseGet(ReglageAgence::new);
        boolean aValider = !DroitsAgence.estAdministrateur()
                && (r.type() == MouvementAgence.Type.REGULARISATION || r.montant() > reglage.getSeuilValidationMouvement());
        m.setStatut(MouvementAgence.Statut.EN_ATTENTE);
        m = repository.save(m);
        audit.tracer(agence.getId(), "CREATION", "MOUVEMENT", m.getId(), m.getType().getLibelle() + " " + m.getReference()
                + " de " + RegistreService.francs(m.getMontant()) + (aValider ? " (en attente de validation)" : ""), null, r);
        if (!aValider) {
            appliquer(m, auteur);
        }
        return versDto(m, contexte());
    }

    /** Valide un mouvement en attente : il s'ecrit au registre. L'auteur ne valide pas son propre mouvement. */
    public MouvementDto valider(Long id, UtilisateurPrincipal validateur) {
        MouvementAgence m = enAttente(id);
        if (m.getAuteurId().equals(validateur.getId()) && !DroitsAgence.estAdministrateur()) {
            throw new AccessDeniedException("Un mouvement se valide par une autre personne que son auteur");
        }
        appliquer(m, validateur);
        audit.tracer(m.getAgenceId(), "VALIDATION", "MOUVEMENT", m.getId(), "Mouvement " + m.getReference() + " valide",
                "EN_ATTENTE", "VALIDE");
        return versDto(m, contexte());
    }

    public MouvementDto rejeter(Long id, String motif, UtilisateurPrincipal validateur) {
        MouvementAgence m = enAttente(id);
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif du rejet est obligatoire");
        }
        m.setStatut(MouvementAgence.Statut.REJETE);
        m.setMotifRejet(motif.trim());
        m.setValideLe(Instant.now());
        m.setValideParId(validateur.getId());
        m.setValideParNom(validateur.getNomComplet());
        audit.tracer(m.getAgenceId(), "REJET", "MOUVEMENT", m.getId(), "Mouvement " + m.getReference() + " rejete (" + motif.trim() + ")",
                "EN_ATTENTE", "REJETE");
        return versDto(m, contexte());
    }

    /** Annule un mouvement valide par l'ecriture inverse. */
    public MouvementDto annuler(Long id, String motif, UtilisateurPrincipal auteur) {
        MouvementAgence m = charger(id);
        perimetre.exiger(m.getAgenceId());
        if (m.getStatut() != MouvementAgence.Statut.VALIDE) {
            throw new BusinessException("Seul un mouvement valide s'annule (celui-ci est " + m.getStatut().name().toLowerCase().replace('_', ' ') + ")");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif de l'annulation est obligatoire");
        }
        verrou.exigerOuverte(m.getAgenceId(), registre.journee(m.getValideLe() != null ? m.getValideLe() : m.getDateMouvement()),
                "l'annulation de " + m.getReference());
        Ecriture inverse = registre.contrePasser(m.getEcritureId(), motif.trim());
        m.setStatut(MouvementAgence.Statut.ANNULE);
        m.setAnnulationEcritureId(inverse.getId());
        m.setMotifAnnulation(motif.trim());
        audit.tracer(m.getAgenceId(), "ANNULATION", "MOUVEMENT", m.getId(), "Mouvement " + m.getReference() + " annule ("
                + motif.trim() + ") par " + auteur.getNomComplet(), "VALIDE", "ANNULE");
        return versDto(m, contexte());
    }

    // ---------------------------------------------------------- piece jointe

    public MouvementDto joindre(Long id, MultipartFile fichier) {
        MouvementAgence m = charger(id);
        perimetre.exiger(m.getAgenceId());
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException("Choisissez un fichier");
        }
        if (fichier.getSize() > 15L * 1024 * 1024) {
            throw new BusinessException("La piece justificative depasse 15 Mo");
        }
        String ancien = m.getPieceChemin();
        m.setPieceChemin(stockage.enregistrer(fichier, "agences-mouvements/" + m.getId()));
        m.setPieceNom(fichier.getOriginalFilename() == null ? "piece" : fichier.getOriginalFilename());
        m.setPieceType(fichier.getContentType());
        if (ancien != null) {
            stockage.supprimer(ancien);
        }
        return versDto(m, contexte());
    }

    @Transactional(readOnly = true)
    public Resource piece(Long id) {
        MouvementAgence m = charger(id);
        perimetre.exiger(m.getAgenceId());
        if (m.getPieceChemin() == null) {
            throw new ResourceNotFoundException("Aucune piece pour le mouvement " + m.getReference());
        }
        return stockage.charger(m.getPieceChemin());
    }

    @Transactional(readOnly = true)
    public MouvementDto detail(Long id) {
        MouvementAgence m = charger(id);
        perimetre.exiger(m.getAgenceId());
        return versDto(m, contexte());
    }

    // ------------------------------------------------------------- historique

    @Transactional(readOnly = true)
    public Page<MouvementDto> lister(Filtres f, Pageable pageable) {
        Set<Long> agences = f.agenceId() != null ? Set.of(perimetre.exiger(f.agenceId()).getId())
                : perimetre.visibles().stream().map(Agence::getId).collect(Collectors.toSet());
        Specification<MouvementAgence> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(agences.isEmpty() ? cb.disjunction() : root.get("agenceId").in(agences));
            if (f.statut() != null) {
                p.add(cb.equal(root.get("statut"), f.statut()));
            }
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.du() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("dateMouvement"), f.du().atStartOfDay(tg.parcauto.api.agence.tresorerie.JourneeCaisse.FUSEAU).toInstant()));
            }
            if (f.au() != null) {
                p.add(cb.lessThan(root.get("dateMouvement"), f.au().plusDays(1).atStartOfDay(tg.parcauto.api.agence.tresorerie.JourneeCaisse.FUSEAU).toInstant()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Pageable trie = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "dateMouvement").and(Sort.by(Sort.Direction.DESC, "id")));
        Contexte ctx = contexte();
        return repository.findAll(spec, trie).map(m -> versDto(m, ctx));
    }

    @Transactional(readOnly = true)
    public long enAttente() {
        Set<Long> agences = perimetre.visibles().stream().map(Agence::getId).collect(Collectors.toSet());
        if (agences.isEmpty()) {
            return 0;
        }
        return repository.count((root, q, cb) -> cb.and(root.get("agenceId").in(agences),
                cb.equal(root.get("statut"), MouvementAgence.Statut.EN_ATTENTE)));
    }

    // ------------------------------------------------------------------ outils

    private void appliquer(MouvementAgence m, UtilisateurPrincipal validateur) {
        List<RegistreService.Mouvement> lignes = new ArrayList<>();
        if (m.getCompteSourceId() != null) {
            lignes.add(RegistreService.Mouvement.de(m.getCompteSourceId(), -m.getMontant()));
        }
        if (m.getCompteDestinationId() != null) {
            lignes.add(RegistreService.Mouvement.de(m.getCompteDestinationId(), m.getMontant()));
        }
        NatureEcriture nature = m.getType() == MouvementAgence.Type.REGULARISATION ? NatureEcriture.REGULARISATION : NatureEcriture.MOUVEMENT;
        Ecriture e = registre.enregistrer(new RegistreService.Entete(m.getAgenceId(), nature,
                m.getReference() + " - " + m.getType().getLibelle() + " : " + m.getMotif(), m.getJustification(), "MOUVEMENT", m.getId()), lignes);
        m.setEcritureId(e.getId());
        m.setStatut(MouvementAgence.Statut.VALIDE);
        m.setValideLe(Instant.now());
        m.setValideParId(validateur.getId());
        m.setValideParNom(validateur.getNomComplet());
    }

    /** Coherence minimale du type avec les comptes choisis. */
    private static void verifierType(MouvementAgence.Type type, CompteAgence source, CompteAgence destination) {
        switch (type) {
            case VERSEMENT_BANCAIRE -> {
                if (destination.getType() != TypeCompte.BANQUE) {
                    throw new BusinessException("Un versement bancaire arrive sur un compte bancaire");
                }
            }
            case RETRAIT_BANCAIRE -> {
                if (source.getType() != TypeCompte.BANQUE) {
                    throw new BusinessException("Un retrait bancaire part d'un compte bancaire");
                }
            }
            case CREDIT_COMMISSION -> {
                if (source.getType() != TypeCompte.COMMISSIONS && destination.getType() != TypeCompte.COMMISSIONS) {
                    throw new BusinessException("Un credit de commission part du compte des commissions ou y arrive");
                }
            }
            case APPROVISIONNEMENT -> {
                if (destination.getType().getFamille() != TypeCompte.Famille.VIRTUEL && destination.getType().getFamille() != TypeCompte.Famille.CASH) {
                    throw new BusinessException("Un approvisionnement alimente un float, un compte de recharge ou une caisse");
                }
            }
            default -> {
            }
        }
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

    private MouvementAgence enAttente(Long id) {
        MouvementAgence m = charger(id);
        perimetre.exiger(m.getAgenceId());
        if (m.getStatut() != MouvementAgence.Statut.EN_ATTENTE) {
            throw new BusinessException("Le mouvement " + m.getReference() + " n'est plus en attente");
        }
        return m;
    }

    private MouvementAgence charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Mouvement", id));
    }

    private record Contexte(Map<Long, String> agences, Map<Long, String> comptes, boolean validation, Long moi, boolean admin) {
    }

    private Contexte contexte() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Long moi = auth != null && auth.getPrincipal() instanceof UtilisateurPrincipal u ? u.getId() : null;
        List<Agence> visibles = perimetre.visibles();
        return new Contexte(visibles.stream().collect(Collectors.toMap(Agence::getId, Agence::getNom)),
                comptes.findByAgenceIdIn(visibles.stream().map(Agence::getId).toList()).stream()
                        .collect(Collectors.toMap(CompteAgence::getId, CompteAgence::getLibelle)),
                droits.courants().contains(PermissionAgence.MOUVEMENT_VALIDER), moi, DroitsAgence.estAdministrateur());
    }

    private static MouvementDto versDto(MouvementAgence m, Contexte ctx) {
        boolean validable = ctx.validation() && m.getStatut() == MouvementAgence.Statut.EN_ATTENTE
                && (ctx.admin() || !m.getAuteurId().equals(ctx.moi()));
        boolean annulable = ctx.validation() && m.getStatut() == MouvementAgence.Statut.VALIDE;
        return new MouvementDto(m.getId(), m.getReference(), m.getAgenceId(), ctx.agences().get(m.getAgenceId()), m.getType(),
                m.getType().getLibelle(), m.getCompteSourceId(), m.getCompteSourceId() == null ? null : ctx.comptes().get(m.getCompteSourceId()),
                m.getCompteDestinationId(), m.getCompteDestinationId() == null ? null : ctx.comptes().get(m.getCompteDestinationId()),
                m.getMontant(), m.getMotif(), m.getJustification(), m.getPieceNom(), m.getStatut(), m.getDateMouvement(),
                m.getAuteurNom(), m.getValideLe(), m.getValideParNom(), m.getMotifRejet(), m.getMotifAnnulation(), validable, annulable);
    }
}
