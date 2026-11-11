package tg.parcauto.api.finance.depense;

import jakarta.validation.constraints.Email;
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
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.finance.commun.ModePaiement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fournisseurs du parc et leurs dettes : une depense a payer est la dette,
 * soldee par des reglements partiels ou totaux. Le solde du d'un fournisseur
 * est la somme des restes de ses depenses a payer validees.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FournisseurParcService {

    private final FournisseurParcRepository repository;
    private final DepenseRepository depenses;
    private final ReglementFournisseurParcRepository reglements;
    private final DepenseService depenseService;
    private final NumerotationService numerotation;

    public record Requete(@NotBlank @Size(max = 180) String nom, @Size(max = 80) String activite,
                          @Size(max = 150) String contact, @Size(max = 40) String telephone,
                          @Email @Size(max = 150) String email, @Size(max = 255) String adresse,
                          @Size(max = 60) String nif, @Min(0) @Max(365) Integer delaiPaiementJours,
                          @Size(max = 500) String note, Boolean actif) {
    }

    public record Reponse(Long id, String nom, String activite, String contact, String telephone, String email,
                          String adresse, String nif, int delaiPaiementJours, String note, boolean actif,
                          /** Total des depenses validees, et la dette encore due (dont en retard). */
                          BigDecimal achats, BigDecimal soldeDu, BigDecimal enRetard, long dettesOuvertes) {
    }

    public record ReglementRequete(@NotNull Long depenseId, @NotNull @Positive BigDecimal montant,
                                   LocalDate dateReglement, ModePaiement modePaiement,
                                   @Size(max = 80) String reference, @Size(max = 255) String note) {
    }

    public record ReglementReponse(Long id, String numero, Long depenseId, String depenseNumero, String depenseLibelle,
                                   BigDecimal montant, LocalDate dateReglement, ModePaiement modePaiement,
                                   String reference, String note, String auteurNom,
                                   ReglementFournisseurParc.Statut statut, Instant annuleLe, String annulePar,
                                   String motifAnnulation) {
    }

    public record Fiche(Reponse fournisseur, List<DepenseDto.Reponse> dettes, List<DepenseDto.Reponse> depenses,
                        List<ReglementReponse> reglements) {
    }

    // ---------------------------------------------------------------- fiches

    @Transactional(readOnly = true)
    public List<Reponse> lister() {
        Map<Long, BigDecimal> achats = new HashMap<>();
        for (Object[] l : depenses.achatsParFournisseur()) {
            achats.put((Long) l[0], (BigDecimal) l[1]);
        }
        Map<Long, BigDecimal[]> dus = dus(null);
        return repository.findAllByOrderByNomAsc().stream().map(f -> versDto(f, achats, dus)).toList();
    }

    @Transactional(readOnly = true)
    public Fiche fiche(Long id) {
        FournisseurParc f = charger(id);
        List<Depense> liste = depenses.duFournisseur(id);
        Map<Long, BigDecimal> regles = depenseService.regles(liste.stream().filter(Depense::isAPayer).map(Depense::getId).toList());
        List<DepenseDto.Reponse> toutes = liste.stream().map(d -> DepenseDto.Reponse.de(d, regles.get(d.getId()))).toList();
        List<DepenseDto.Reponse> dettes = toutes.stream()
                .filter(d -> d.aPayer() && d.statut() == StatutDepense.VALIDEE && d.resteDu().signum() > 0).toList();
        BigDecimal achats = toutes.stream().filter(d -> d.statut() == StatutDepense.VALIDEE)
                .map(DepenseDto.Reponse::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, BigDecimal[]> dus = dus(id);
        return new Fiche(versDto(f, Map.of(id, achats), dus), dettes, toutes,
                reglements.duFournisseur(id).stream().map(FournisseurParcService::versDto).toList());
    }

    public Reponse enregistrer(Long id, Requete r) {
        String nom = r.nom().trim();
        if (repository.existsByNomIgnoreCaseAndIdNot(nom, id == null ? -1L : id)) {
            throw new BusinessException("Le fournisseur " + nom + " existe deja");
        }
        FournisseurParc f = id == null ? new FournisseurParc() : charger(id);
        f.setNom(nom);
        f.setActivite(vide(r.activite()));
        f.setContact(vide(r.contact()));
        f.setTelephone(vide(r.telephone()));
        f.setEmail(vide(r.email()));
        f.setAdresse(vide(r.adresse()));
        f.setNif(vide(r.nif()));
        f.setDelaiPaiementJours(r.delaiPaiementJours() == null ? 0 : r.delaiPaiementJours());
        f.setNote(vide(r.note()));
        if (r.actif() != null) {
            f.setActif(r.actif());
        }
        f = repository.saveAndFlush(f);
        return versDto(f, Map.of(), Map.of());
    }

    /** Suppression d'une fiche jamais utilisee ; sinon, on la desactive. */
    public void supprimer(Long id) {
        FournisseurParc f = charger(id);
        if (depenses.countByFournisseurParcId(id) > 0) {
            throw new BusinessException(f.getNom() + " a deja des depenses : desactivez la fiche plutot");
        }
        repository.delete(f);
    }

    // ---------------------------------------------------------------- dettes

    /** Dettes ouvertes (depenses a payer validees non soldees), d'un fournisseur ou de tous. */
    @Transactional(readOnly = true)
    public List<DepenseDto.Reponse> dettes(Long fournisseurId) {
        List<Depense> liste = depenses.aPayer(fournisseurId);
        Map<Long, BigDecimal> regles = depenseService.regles(liste.stream().map(Depense::getId).toList());
        return liste.stream().map(d -> DepenseDto.Reponse.de(d, regles.get(d.getId())))
                .filter(d -> d.resteDu().signum() > 0).toList();
    }

    /** Reglement d'une dette validee, dans la limite du reste du. */
    public ReglementReponse regler(ReglementRequete r) {
        depenseService.charger(r.depenseId());
        // Deux reglements simultanes de la meme dette passent l'un apres l'autre : le second voit le premier.
        Depense d = depenses.verrouiller(r.depenseId())
                .orElseThrow(() -> ResourceNotFoundException.of("Depense", r.depenseId()));
        if (!d.isAPayer()) {
            throw new BusinessException("La depense " + d.getNumero() + " n'est pas a payer");
        }
        if (d.getStatut() != StatutDepense.VALIDEE) {
            throw new BusinessException("La depense " + d.getNumero() + " doit etre validee avant d'etre reglee");
        }
        BigDecimal reste = d.getMontant().subtract(reglements.regle(d.getId()));
        BigDecimal montant = r.montant().setScale(2, RoundingMode.HALF_UP);
        if (montant.compareTo(reste) > 0) {
            throw new BusinessException("Le reglement depasse le reste du (" + reste.setScale(0, RoundingMode.HALF_UP).toPlainString() + " F)");
        }
        LocalDate date = r.dateReglement() == null ? LocalDate.now() : r.dateReglement();
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException("La date du reglement ne peut pas etre dans le futur");
        }
        ReglementFournisseurParc reglement = new ReglementFournisseurParc();
        reglement.setNumero(numerotation.prochainNumero("RGF"));
        reglement.setDepense(d);
        reglement.setMontant(montant);
        reglement.setDateReglement(date);
        reglement.setModePaiement(r.modePaiement());
        reglement.setReference(vide(r.reference()));
        reglement.setNote(vide(r.note()));
        var auteur = DepenseService.courant();
        reglement.setAuteurNom(auteur == null ? "Systeme" : auteur.getNomComplet());
        return versDto(reglements.save(reglement));
    }

    public ReglementReponse annulerReglement(Long id, String motif) {
        // Avec la validation des depenses, rouvrir une dette reglee revient a l'administrateur.
        if (depenseService.validationRequise()) {
            throw new BusinessException("La validation des depenses est activee : seul l'administrateur annule un"
                    + " reglement");
        }
        ReglementFournisseurParc r = reglements.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Reglement", id));
        if (r.getStatut() == ReglementFournisseurParc.Statut.ANNULE) {
            throw new BusinessException("Le reglement " + r.getNumero() + " est deja annule");
        }
        var auteur = DepenseService.courant();
        r.setStatut(ReglementFournisseurParc.Statut.ANNULE);
        r.setAnnuleLe(Instant.now());
        r.setAnnulePar(auteur == null ? null : auteur.getNomComplet());
        r.setMotifAnnulation(motif.trim());
        return versDto(r);
    }

    // ---------------------------------------------------------------- outils

    /** [solde du, en retard, nombre] par fournisseur, sur les dettes validees. */
    private Map<Long, BigDecimal[]> dus(Long fournisseurId) {
        Map<Long, BigDecimal[]> resultat = new HashMap<>();
        for (DepenseDto.Reponse d : dettes(fournisseurId)) {
            BigDecimal[] t = resultat.computeIfAbsent(d.fournisseurId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = t[0].add(d.resteDu());
            if (d.enRetard()) {
                t[1] = t[1].add(d.resteDu());
            }
            t[2] = t[2].add(BigDecimal.ONE);
        }
        return resultat;
    }

    private static Reponse versDto(FournisseurParc f, Map<Long, BigDecimal> achats, Map<Long, BigDecimal[]> dus) {
        BigDecimal[] t = dus.getOrDefault(f.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        return new Reponse(f.getId(), f.getNom(), f.getActivite(), f.getContact(), f.getTelephone(), f.getEmail(),
                f.getAdresse(), f.getNif(), f.getDelaiPaiementJours(), f.getNote(), f.isActif(),
                achats.getOrDefault(f.getId(), BigDecimal.ZERO), t[0], t[1], t[2].longValue());
    }

    private static ReglementReponse versDto(ReglementFournisseurParc r) {
        return new ReglementReponse(r.getId(), r.getNumero(), r.getDepense().getId(), r.getDepense().getNumero(),
                r.getDepense().getLibelle(), r.getMontant(), r.getDateReglement(), r.getModePaiement(), r.getReference(),
                r.getNote(), r.getAuteurNom(), r.getStatut(), r.getAnnuleLe(), r.getAnnulePar(), r.getMotifAnnulation());
    }

    private FournisseurParc charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Fournisseur", id));
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
