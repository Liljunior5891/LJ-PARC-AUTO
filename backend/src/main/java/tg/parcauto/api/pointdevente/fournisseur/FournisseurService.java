package tg.parcauto.api.pointdevente.fournisseur;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseur;
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseurRepository;
import tg.parcauto.api.pointdevente.caisse.CaisseService;
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.depense.ModeSortie;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fournisseurs du point de vente : fiches, dettes nees des receptions,
 * reglements partiels (depuis la caisse, en especes, par banque ou mobile
 * money) et annulation d'un reglement.
 * <p>
 * Une commande recue doit sa valeur recue ; son reste est cette valeur moins
 * ses reglements valides. Rien n'est stocke en double : le solde ne derive pas.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FournisseurService {

    private static final ZoneId FUSEAU = ZoneId.of("Africa/Lome");

    private final FournisseurRepository repository;
    private final ReglementFournisseurRepository reglementRepository;
    private final CommandeFournisseurRepository commandeRepository;
    private final CaisseService caisseService;
    private final NumerotationService numerotation;
    private final tg.parcauto.api.pointdevente.tresorerie.TresorerieService tresorerie;

    // ------------------------------------------------------------------ dto

    public record Requete(
            @NotBlank @Size(max = 150) String nom,
            @Size(max = 120) String personneContact,
            @Size(max = 40) String telephone,
            @Size(max = 40) String telephone2,
            @Email @Size(max = 150) String email,
            @Size(max = 255) String adresse,
            @Size(max = 80) String ville,
            @Size(max = 80) String pays,
            @Size(max = 60) String nif,
            @Size(max = 60) String rccm,
            @Min(0) Integer delaiPaiementJours,
            @Size(max = 4000) String conditions,
            @Size(max = 500) String note) {
    }

    public record Reponse(Long id, String code, String nom, String personneContact, String telephone,
                          String telephone2, String email, String adresse, String ville, String pays, String nif,
                          String rccm, Integer delaiPaiementJours, String conditions, String note, boolean actif,
                          long commandes, BigDecimal du, BigDecimal enRetard, LocalDate prochaineEcheance) {
    }

    /** Commande recue et ce qu'il reste a payer dessus. */
    public record Dette(Long commandeId, String numero, LocalDate dateCommande, Instant recueLe,
                        BigDecimal montantRecu, BigDecimal regle, BigDecimal reste, LocalDate echeance,
                        boolean enRetard) {
    }

    public record ReglementLigne(Long id, String numero, LocalDate dateReglement, Long commandeId,
                                 String commandeNumero, BigDecimal montant, ModeSortie mode, String modeLibelle,
                                 String reference, String sessionNumero, String auteurNom, String note,
                                 ReglementFournisseur.Statut statut, String motifAnnulation) {

        static ReglementLigne de(ReglementFournisseur r) {
            return new ReglementLigne(r.getId(), r.getNumero(), r.getDateReglement(), r.getCommande().getId(),
                    r.getCommande().getNumero(), r.getMontant(), r.getMode(), r.getMode().getLibelle(),
                    r.getReference(), r.getSession() == null ? null : r.getSession().getNumero(), r.getAuteurNom(),
                    r.getNote(), r.getStatut(), r.getMotifAnnulation());
        }
    }

    public record CommandeLigne(Long id, String numero, LocalDate dateCommande, CommandeFournisseur.Statut statut,
                                BigDecimal total, BigDecimal montantRecu, Instant recueLe) {
    }

    public record Compte(Reponse fournisseur, List<Dette> dettes, List<ReglementLigne> reglements,
                         List<CommandeLigne> commandes) {
    }

    public record ReglementRequete(
            @NotNull @Positive BigDecimal montant,
            @NotNull ModeSortie mode,
            /** Session de caisse ouverte, obligatoire pour le mode CAISSE. */
            Long sessionId,
            /** Commande a regler ; sans elle, les plus anciennes echeances passent d'abord. */
            Long commandeId,
            LocalDate dateReglement,
            @Size(max = 80) String reference,
            @Size(max = 255) String note) {
    }

    public record Imputation(String commandeNumero, BigDecimal montant) {
    }

    public record Recu(String numero, LocalDate dateReglement, Long fournisseurId, String fournisseurNom,
                       BigDecimal montant, ModeSortie mode, String modeLibelle, String reference,
                       String sessionNumero, List<Imputation> imputations, BigDecimal resteDu,
                       ReglementFournisseur.Statut statut) {
    }

    public record MotifAnnulation(@NotBlank @Size(max = 255) String motif) {
    }

    /** Situation de paiement d'une commande, pour sa fiche. */
    public record Situation(boolean detteSuivie, BigDecimal montantRecu, BigDecimal regle, BigDecimal reste,
                            LocalDate echeance, boolean enRetard, Long fournisseurId,
                            List<ReglementLigne> reglements) {
    }

    /** Totaux pour le tableau de bord. */
    public record Totaux(BigDecimal du, BigDecimal enRetard, long fournisseursEnRetard) {
    }

    // ---------------------------------------------------------------- fiches

    @Transactional(readOnly = true)
    public Page<Reponse> rechercher(String q, Boolean actif, boolean avecDette, int page, int size) {
        String motif = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Map<Long, BigDecimal[]> dettes = dettesParFiche();
        Map<Long, LocalDate> echeances = prochainesEcheances();
        Map<Long, Long> nombres = nombresDeCommandes();
        List<Reponse> toutes = repository.rechercher(motif, actif).stream()
                .map(f -> reponse(f, dettes, echeances, nombres))
                .filter(r -> !avecDette || r.du().signum() > 0)
                .sorted(avecDette ? Comparator.comparing(Reponse::enRetard).reversed()
                        .thenComparing(Comparator.comparing(Reponse::du).reversed())
                        : Comparator.comparing(r -> r.nom().toLowerCase(Locale.ROOT)))
                .toList();
        int taille = Math.max(1, Math.min(size, 200));
        int debut = Math.min(page * taille, toutes.size());
        return new PageImpl<>(toutes.subList(debut, Math.min(debut + taille, toutes.size())),
                PageRequest.of(page, taille), toutes.size());
    }

    /** Fournisseurs actifs, pour les listes de choix (commande, depense). */
    @Transactional(readOnly = true)
    public List<Reponse> actifs() {
        return repository.findByActifTrueOrderByNomAsc().stream()
                .map(f -> reponse(f, Map.of(), Map.of(), Map.of())).toList();
    }

    @Transactional(readOnly = true)
    public Reponse parId(Long id) {
        return reponse(charger(id));
    }

    public Reponse creer(Requete r) {
        if (!repository.parNom(r.nom()).isEmpty()) {
            throw new BusinessException("Un fournisseur porte deja le nom " + r.nom().trim());
        }
        Fournisseur f = new Fournisseur();
        f.setCode(numerotation.prochainNumero("FRS"));
        appliquer(f, r);
        return reponse(repository.saveAndFlush(f));
    }

    public Reponse modifier(Long id, Requete r) {
        Fournisseur f = charger(id);
        if (repository.parNom(r.nom()).stream().anyMatch(o -> !o.getId().equals(id))) {
            throw new BusinessException("Un autre fournisseur porte deja le nom " + r.nom().trim());
        }
        appliquer(f, r);
        repository.flush();
        return reponse(f);
    }

    /** Archivage logique : commandes et reglements restent consultables, la dette reste payable. */
    public Reponse archiver(Long id, boolean actif) {
        Fournisseur f = charger(id);
        f.setActif(actif);
        return reponse(f);
    }

    /**
     * Fiche correspondant au nom saisi sur une commande : la fiche de ce nom,
     * sinon une fiche creee a la volee (la saisie rapide reste possible).
     */
    public Fournisseur ficheDeCommande(Long fournisseurId, String nom) {
        if (fournisseurId != null) {
            Fournisseur f = charger(fournisseurId);
            if (!f.isActif()) {
                throw new BusinessException("Le fournisseur " + f.getNom() + " est archive");
            }
            return f;
        }
        return repository.premierParNom(nom).orElseGet(() -> {
            Fournisseur f = new Fournisseur();
            f.setCode(numerotation.prochainNumero("FRS"));
            f.setNom(nom.trim());
            return repository.saveAndFlush(f);
        });
    }

    /** A la reception, la valeur recue devient une dette, payable avant l'echeance du fournisseur. */
    public void ouvrirDette(CommandeFournisseur commande) {
        BigDecimal recu = commande.getLignes().stream()
                .filter(l -> l.getQuantiteRecue() != null && l.getCoutUnitaire() != null)
                .map(l -> l.getQuantiteRecue().multiply(l.getCoutUnitaire()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        commande.setMontantRecu(montant(recu));
        commande.setDetteSuivie(true);
        Integer delai = commande.getFiche() == null ? null : commande.getFiche().getDelaiPaiementJours();
        commande.setEcheance(LocalDate.now(FUSEAU).plusDays(delai == null ? 0 : delai));
    }

    // ---------------------------------------------------------------- compte

    @Transactional(readOnly = true)
    public Compte compte(Long id) {
        Fournisseur f = charger(id);
        List<CommandeFournisseur> recues = commandeRepository.dettes(id);
        Map<Long, BigDecimal> regles = regles(recues);
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        List<Dette> dettes = recues.stream()
                .map(c -> dette(c, regles, aujourdhui))
                .filter(d -> d.reste().signum() > 0)
                .toList();
        List<CommandeLigne> commandes = commandeRepository.deLaFiche(id, PageRequest.of(0, 50)).stream()
                .map(c -> new CommandeLigne(c.getId(), c.getNumero(), c.getDateCommande(), c.getStatut(),
                        montant(c.total()), c.getMontantRecu(), c.getRecueLe()))
                .toList();
        return new Compte(reponse(f),
                dettes,
                reglementRepository.duFournisseur(id).stream().map(ReglementLigne::de).toList(),
                commandes);
    }

    @Transactional(readOnly = true)
    public Situation situation(CommandeFournisseur c) {
        List<ReglementLigne> reglements = reglementRepository.deLaCommande(c.getId()).stream()
                .map(ReglementLigne::de).toList();
        if (c.getStatut() != CommandeFournisseur.Statut.RECUE) {
            return new Situation(c.isDetteSuivie(), null, BigDecimal.ZERO, BigDecimal.ZERO, null, false,
                    c.getFiche() == null ? null : c.getFiche().getId(), reglements);
        }
        Dette d = dette(c, regles(List.of(c)), LocalDate.now(FUSEAU));
        return new Situation(c.isDetteSuivie(), d.montantRecu(), d.regle(), c.isDetteSuivie() ? d.reste() : BigDecimal.ZERO,
                c.getEcheance(), c.isDetteSuivie() && d.enRetard(), c.getFiche() == null ? null : c.getFiche().getId(),
                reglements);
    }

    // ------------------------------------------------------------ reglements

    /**
     * Paiement d'un fournisseur : impute sur la commande choisie, ou sur les
     * echeances les plus anciennes. Paye depuis la caisse, il cree une sortie
     * d'especes dans la session ouverte.
     */
    public Recu regler(Long fournisseurId, ReglementRequete demande, UtilisateurPrincipal auteur) {
        Fournisseur f = charger(fournisseurId);
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        List<CommandeFournisseur> recues = commandeRepository.dettes(fournisseurId);
        Map<Long, BigDecimal> regles = regles(recues);
        List<CommandeFournisseur> cibles = new ArrayList<>();
        Map<Long, BigDecimal> restes = new HashMap<>();
        BigDecimal duAvant = BigDecimal.ZERO;
        for (CommandeFournisseur c : recues) {
            BigDecimal reste = dette(c, regles, aujourdhui).reste();
            duAvant = duAvant.add(reste);
            if (reste.signum() > 0 && (demande.commandeId() == null || c.getId().equals(demande.commandeId()))) {
                cibles.add(c);
                restes.put(c.getId(), reste);
            }
        }
        if (cibles.isEmpty()) {
            throw new BusinessException(demande.commandeId() == null
                    ? "Rien a regler : " + f.getNom() + " est entierement paye"
                    : "Cette commande n'a plus rien a regler");
        }
        BigDecimal du = restes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal montant = montant(demande.montant());
        if (montant.compareTo(du) > 0) {
            throw new BusinessException("Le montant depasse ce qui reste du (" + entier(du) + " F)");
        }
        LocalDate date = demande.dateReglement() == null ? aujourdhui : demande.dateReglement();
        if (date.isAfter(aujourdhui)) {
            throw new BusinessException("La date du reglement ne peut pas etre dans le futur");
        }
        if (!demande.mode().propose()) {
            throw new BusinessException("Choisissez la caisse, le coffre ou la banque");
        }
        Long coffre = demande.mode() == ModeSortie.COFFRE ? tresorerie.exigerCoffre(auteur, montant) : null;
        String numero = numerotation.prochainNumero("RF");
        MouvementCaisse sortie = null;
        if (demande.mode() == ModeSortie.CAISSE) {
            if (demande.sessionId() == null) {
                throw new BusinessException("Choisissez la caisse ouverte d'ou sortent les especes");
            }
            sortie = caisseService.sortie(demande.sessionId(), montant,
                    tronquer("Reglement fournisseur " + f.getNom() + " - " + numero), auteur);
        }
        List<Imputation> imputations = new ArrayList<>();
        BigDecimal resteAImputer = montant;
        for (CommandeFournisseur c : cibles) {
            if (resteAImputer.signum() <= 0) {
                break;
            }
            BigDecimal part = resteAImputer.min(restes.get(c.getId()));
            ReglementFournisseur r = new ReglementFournisseur();
            r.setNumero(numero);
            r.setFournisseur(f);
            r.setCommande(c);
            r.setMontant(part);
            r.setMode(demande.mode());
            r.setReference(vide(demande.reference()));
            r.setDateReglement(date);
            r.setSession(sortie == null ? null : sortie.getSession());
            r.setMouvementCaisse(sortie);
            r.setCoffreId(coffre);
            r.setAuteurNom(auteur.getNomComplet());
            r.setNote(vide(demande.note()));
            reglementRepository.save(r);
            imputations.add(new Imputation(c.getNumero(), part));
            resteAImputer = resteAImputer.subtract(part);
        }
        return new Recu(numero, date, f.getId(), f.getNom(), montant, demande.mode(), demande.mode().getLibelle(),
                vide(demande.reference()), sortie == null ? null : sortie.getSession().getNumero(), imputations,
                duAvant.subtract(montant), ReglementFournisseur.Statut.VALIDE);
    }

    /** Annule un paiement : la dette revient ; paye depuis la caisse, sa sortie est retiree de la session. */
    public Recu annuler(String numero, String motif, UtilisateurPrincipal auteur) {
        List<ReglementFournisseur> lignes = reglementRepository.parNumero(numero);
        if (lignes.isEmpty()) {
            throw new ResourceNotFoundException("Reglement introuvable : " + numero);
        }
        ReglementFournisseur premiere = lignes.get(0);
        if (premiere.getStatut() == ReglementFournisseur.Statut.ANNULE) {
            throw new BusinessException("Le reglement " + numero + " est deja annule");
        }
        MouvementCaisse sortie = premiere.getMouvementCaisse();
        if (sortie != null) {
            lignes.forEach(l -> l.setMouvementCaisse(null));
            reglementRepository.flush();
            caisseService.retirerSortie(sortie, "un reglement");
        }
        Instant maintenant = Instant.now();
        BigDecimal total = BigDecimal.ZERO;
        List<Imputation> imputations = new ArrayList<>();
        for (ReglementFournisseur l : lignes) {
            l.setStatut(ReglementFournisseur.Statut.ANNULE);
            l.setAnnuleLe(maintenant);
            l.setAnnulePar(auteur.getNomComplet());
            l.setMotifAnnulation(motif.trim());
            total = total.add(l.getMontant());
            imputations.add(new Imputation(l.getCommande().getNumero(), l.getMontant()));
        }
        reglementRepository.flush();
        Fournisseur f = premiere.getFournisseur();
        return new Recu(numero, premiere.getDateReglement(), f.getId(), f.getNom(), total, premiere.getMode(),
                premiere.getMode().getLibelle(), premiere.getReference(),
                premiere.getSession() == null ? null : premiere.getSession().getNumero(), imputations,
                totalDu(f.getId()), ReglementFournisseur.Statut.ANNULE);
    }

    // ---------------------------------------------------------------- totaux

    @Transactional(readOnly = true)
    public Totaux totaux() {
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        List<CommandeFournisseur> recues = commandeRepository.dettes(null);
        Map<Long, BigDecimal> regles = regles(recues);
        BigDecimal du = BigDecimal.ZERO;
        BigDecimal retard = BigDecimal.ZERO;
        java.util.Set<Long> enRetard = new java.util.HashSet<>();
        for (CommandeFournisseur c : recues) {
            Dette d = dette(c, regles, aujourdhui);
            du = du.add(d.reste());
            if (d.enRetard()) {
                retard = retard.add(d.reste());
                enRetard.add(c.getFiche() == null ? -c.getId() : c.getFiche().getId());
            }
        }
        return new Totaux(du, retard, enRetard.size());
    }

    /** Dettes de chaque commande recue, pour les rapports. */
    @Transactional(readOnly = true)
    public List<Dette> toutesLesDettes() {
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        List<CommandeFournisseur> recues = commandeRepository.dettes(null);
        Map<Long, BigDecimal> regles = regles(recues);
        return recues.stream().map(c -> dette(c, regles, aujourdhui)).filter(d -> d.reste().signum() > 0).toList();
    }

    // ------------------------------------------------------------- internes

    private BigDecimal totalDu(Long fournisseurId) {
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        List<CommandeFournisseur> recues = commandeRepository.dettes(fournisseurId);
        Map<Long, BigDecimal> regles = regles(recues);
        return recues.stream().map(c -> dette(c, regles, aujourdhui).reste()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Situation de paiement des commandes receptionnees parmi celles donnees, par id de commande. */
    @Transactional(readOnly = true)
    public Map<Long, Dette> dettesDe(List<CommandeFournisseur> commandes) {
        List<CommandeFournisseur> recues = commandes.stream()
                .filter(c -> c.getStatut() == CommandeFournisseur.Statut.RECUE).toList();
        Map<Long, BigDecimal> regles = regles(recues);
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        Map<Long, Dette> dettes = new HashMap<>();
        for (CommandeFournisseur c : recues) {
            dettes.put(c.getId(), dette(c, regles, aujourdhui));
        }
        return dettes;
    }

    private Map<Long, BigDecimal> regles(List<CommandeFournisseur> commandes) {
        if (commandes.isEmpty()) {
            return Map.of();
        }
        return reglementRepository.reglesParCommande(commandes.stream().map(CommandeFournisseur::getId).toList())
                .stream().collect(Collectors.toMap(l -> (Long) l[0], l -> (BigDecimal) l[1]));
    }

    private static Dette dette(CommandeFournisseur c, Map<Long, BigDecimal> regles, LocalDate aujourdhui) {
        BigDecimal recu = c.getMontantRecu() == null ? BigDecimal.ZERO : c.getMontantRecu();
        BigDecimal regle = regles.getOrDefault(c.getId(), BigDecimal.ZERO);
        BigDecimal reste = recu.subtract(regle).max(BigDecimal.ZERO);
        boolean retard = reste.signum() > 0 && c.getEcheance() != null && c.getEcheance().isBefore(aujourdhui);
        return new Dette(c.getId(), c.getNumero(), c.getDateCommande(), c.getRecueLe(), recu, regle, reste,
                c.getEcheance(), retard);
    }

    /** [du, en retard] par fiche. */
    private Map<Long, BigDecimal[]> dettesParFiche() {
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        List<CommandeFournisseur> recues = commandeRepository.dettes(null);
        Map<Long, BigDecimal> regles = regles(recues);
        Map<Long, BigDecimal[]> parFiche = new HashMap<>();
        for (CommandeFournisseur c : recues) {
            if (c.getFiche() == null) {
                continue;
            }
            Dette d = dette(c, regles, aujourdhui);
            BigDecimal[] cumul = parFiche.computeIfAbsent(c.getFiche().getId(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            cumul[0] = cumul[0].add(d.reste());
            if (d.enRetard()) {
                cumul[1] = cumul[1].add(d.reste());
            }
        }
        return parFiche;
    }

    private Map<Long, LocalDate> prochainesEcheances() {
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        List<CommandeFournisseur> recues = commandeRepository.dettes(null);
        Map<Long, BigDecimal> regles = regles(recues);
        Map<Long, LocalDate> echeances = new HashMap<>();
        for (CommandeFournisseur c : recues) {
            if (c.getFiche() != null && c.getEcheance() != null && dette(c, regles, aujourdhui).reste().signum() > 0) {
                echeances.merge(c.getFiche().getId(), c.getEcheance(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        return echeances;
    }

    private Map<Long, Long> nombresDeCommandes() {
        return commandeRepository.nombreParFiche().stream()
                .collect(Collectors.toMap(l -> (Long) l[0], l -> (Long) l[1]));
    }

    private Reponse reponse(Fournisseur f) {
        return reponse(f, dettesParFiche(), prochainesEcheances(), nombresDeCommandes());
    }

    private static Reponse reponse(Fournisseur f, Map<Long, BigDecimal[]> dettes, Map<Long, LocalDate> echeances,
                                   Map<Long, Long> nombres) {
        BigDecimal[] d = dettes.getOrDefault(f.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        return new Reponse(f.getId(), f.getCode(), f.getNom(), f.getPersonneContact(), f.getTelephone(),
                f.getTelephone2(), f.getEmail(), f.getAdresse(), f.getVille(), f.getPays(), f.getNif(), f.getRccm(),
                f.getDelaiPaiementJours(), f.getConditions(), f.getNote(), f.isActif(),
                nombres.getOrDefault(f.getId(), 0L), montant(d[0]), montant(d[1]), echeances.get(f.getId()));
    }

    private void appliquer(Fournisseur f, Requete r) {
        f.setNom(r.nom().trim());
        f.setPersonneContact(vide(r.personneContact()));
        f.setTelephone(vide(r.telephone()));
        f.setTelephone2(vide(r.telephone2()));
        f.setEmail(vide(r.email()));
        f.setAdresse(vide(r.adresse()));
        f.setVille(vide(r.ville()));
        f.setPays(vide(r.pays()));
        f.setNif(vide(r.nif()));
        f.setRccm(vide(r.rccm()));
        f.setDelaiPaiementJours(r.delaiPaiementJours());
        f.setConditions(vide(r.conditions()));
        f.setNote(vide(r.note()));
    }

    private Fournisseur charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Fournisseur", id));
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String tronquer(String v) {
        return v.length() <= 255 ? v : v.substring(0, 255);
    }

    private static BigDecimal montant(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String entier(BigDecimal v) {
        return String.format(Locale.FRANCE, "%,d", v.setScale(0, RoundingMode.HALF_UP).longValue());
    }
}
