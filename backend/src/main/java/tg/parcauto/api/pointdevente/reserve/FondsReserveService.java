package tg.parcauto.api.pointdevente.reserve;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.pointdevente.depense.DepensePdv;
import tg.parcauto.api.pointdevente.depense.DepensePdvRepository;
import tg.parcauto.api.pointdevente.depense.ModeSortie;
import tg.parcauto.api.pointdevente.reglage.ReglagePdvService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fonds de reserve commun du point de vente, compte separe du compte de l'entreprise.
 * <p>
 * Il est alimente par les apports des associes, depuis leur sous-compte, et par
 * les apports directs de l'administrateur, depuis le compte de l'entreprise. Un
 * apport est definitif. Le fonds paie les depenses communes de l'entreprise ;
 * celles du gerant attendent l'administrateur.
 * <p>
 * Solde = apports - depenses payees par le fonds ;
 * disponible = solde - depenses en attente.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FondsReserveService {

    private static final List<DepensePdv.Statut> VALIDES = List.of(DepensePdv.Statut.VALIDE);
    private static final List<DepensePdv.Statut> EN_ATTENTE =
            List.of(DepensePdv.Statut.ATTENTE_GERANT, DepensePdv.Statut.ATTENTE_ADMIN);

    private final ApportFondsRepository apports;
    private final DepensePdvRepository depenses;
    private final ReglagePdvService reglages;
    private final NumerotationService numerotation;

    public record Apport(Long id, String numero, Instant date, ApportFonds.Origine origine, Long associeId,
                         String associeNom, BigDecimal montant, String note, String auteurNom) {

        static Apport de(ApportFonds a) {
            return new Apport(a.getId(), a.getNumero(), a.getDateApport(), a.getOrigine(), a.getAssocieId(),
                    a.getAssocieNom(), a.getMontant(), a.getNote(), a.getAuteurNom());
        }

        /** Libelle d'ecriture, le meme dans le journal de banque et celui du fonds. */
        public String ecriture() {
            return (origine == ApportFonds.Origine.ENTREPRISE ? "Apport de l'entreprise au fonds de reserve"
                    : "Apport de " + associeNom + " au fonds de reserve") + (note == null ? "" : " - " + note);
        }
    }

    /** Cumul des apports d'un contributeur (un associe, ou l'entreprise quand associeId est nul). */
    public record Contributeur(Long associeId, String nom, BigDecimal total) {
    }

    /** Depense payee (ou a payer) par le fonds. */
    public record Sortie(Long id, String numero, LocalDate date, String categorie, String libelle,
                         String beneficiaire, BigDecimal montant, DepensePdv.Statut statut, String auteurNom) {
    }

    public record Etat(boolean actif, BigDecimal totalApports, BigDecimal totalUtilise, BigDecimal enAttente,
                       BigDecimal solde, BigDecimal disponible, List<Contributeur> contributeurs,
                       List<Apport> apports, List<Sortie> sorties) {
    }

    public record ApportDirect(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive BigDecimal montant,
                               @jakarta.validation.constraints.Size(max = 500) String note) {
    }

    /** Etat complet du fonds : apports (du plus recent au plus ancien), cumul par contributeur, depenses. */
    public Etat etat() {
        boolean actif = reglages.lire().fondsReserveActif();
        List<ApportFonds> liste = apports.findAllByOrderByDateApportAsc();
        Map<String, Contributeur> parContributeur = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ApportFonds a : liste) {
            total = total.add(a.getMontant());
            String cle = a.getAssocieId() == null ? "entreprise" : "a" + a.getAssocieId();
            String nom = a.getAssocieId() == null ? "Entreprise" : a.getAssocieNom();
            Contributeur c = parContributeur.getOrDefault(cle, new Contributeur(a.getAssocieId(), nom, BigDecimal.ZERO));
            parContributeur.put(cle, new Contributeur(c.associeId(), nom, c.total().add(a.getMontant())));
        }
        BigDecimal utilise = depenses.totalPar(ModeSortie.RESERVE, VALIDES);
        BigDecimal attente = depenses.totalPar(ModeSortie.RESERVE, EN_ATTENTE);
        List<DepensePdv.Statut> statuts = new ArrayList<>(EN_ATTENTE);
        statuts.add(DepensePdv.Statut.VALIDE);
        List<Sortie> sorties = new ArrayList<>();
        for (DepensePdv d : depenses.parModeEtStatuts(ModeSortie.RESERVE, statuts, PageRequest.of(0, 200))) {
            sorties.add(new Sortie(d.getId(), d.getNumero(), d.getDateDepense(), d.getCategorie().getNom(),
                    d.getLibelle(), d.getBeneficiaire(), d.getMontant(), d.getStatut(), d.getAuteurNom()));
        }
        List<Apport> recents = new ArrayList<>(liste.stream().map(Apport::de).toList());
        recents.sort(Comparator.comparing(Apport::date).reversed());
        List<Contributeur> contributeurs = new ArrayList<>(parContributeur.values());
        contributeurs.sort(Comparator.comparing(Contributeur::total).reversed());
        BigDecimal solde = total.subtract(utilise);
        return new Etat(actif, total, utilise, attente, solde, solde.subtract(attente), contributeurs,
                recents.size() > 200 ? recents.subList(0, 200) : recents, sorties);
    }

    /** Le fonds a deja servi : son journal a un sens. */
    public boolean aExiste() {
        return reglages.lire().fondsReserveActif() || apports.count() > 0;
    }

    /** Tous les apports, du plus ancien au plus recent (journal du fonds). */
    public List<Apport> apports() {
        return apports.findAllByOrderByDateApportAsc().stream().map(Apport::de).toList();
    }

    /** Apports directs de l'entreprise : ils quittent le compte de l'entreprise (journal de banque). */
    public List<Apport> apportsEntreprise() {
        return apports.findByOrigineOrderByDateApportAsc(ApportFonds.Origine.ENTREPRISE).stream().map(Apport::de).toList();
    }

    public BigDecimal totalApportsEntreprise() {
        return apports.totalPar(ApportFonds.Origine.ENTREPRISE);
    }

    /** Apports d'un associe, du plus recent au plus ancien. */
    public List<Apport> apportsDe(Long associeId) {
        return apports.findByAssocieIdOrderByDateApportDesc(associeId).stream().map(Apport::de).toList();
    }

    public BigDecimal totalApportsDe(Long associeId) {
        return apports.totalAssocie(associeId);
    }

    /** L'administrateur verse directement au fonds depuis le compte de l'entreprise. */
    @Transactional
    public Apport apportDirect(ApportDirect demande, UtilisateurPrincipal admin) {
        reglages.verrouillerFondsReserve();
        return Apport.de(enregistrer(ApportFonds.Origine.ENTREPRISE, null, null, demande.montant(), demande.note(), admin));
    }

    /**
     * Apport d'un associe depuis son sous-compte ; l'appelant a deja verifie son
     * solde, sous le verrou du reglage pris ici.
     */
    @Transactional
    public Apport apportAssocie(Long associeId, String associeNom, BigDecimal montant, String note,
                                UtilisateurPrincipal auteur) {
        return Apport.de(enregistrer(ApportFonds.Origine.ASSOCIE, associeId, associeNom, montant, note, auteur));
    }

    /**
     * Refuse une depense par le fonds au-dela de ce qui est disponible. A
     * l'approbation, la depense elle-meme est encore comptee en attente : son
     * montant est alors rendu au disponible. Le reglage reste verrouille jusqu'a
     * la fin de la transaction de l'appelant : deux depenses simultanees passent
     * l'une apres l'autre et ne depassent pas a elles deux le disponible.
     */
    @Transactional
    public void exigerDisponible(BigDecimal montant, BigDecimal dejaEnAttente) {
        reglages.verrouillerFondsReserve();
        BigDecimal disponible = apports.total()
                .subtract(depenses.totalPar(ModeSortie.RESERVE, VALIDES))
                .subtract(depenses.totalPar(ModeSortie.RESERVE, EN_ATTENTE))
                .add(dejaEnAttente == null ? BigDecimal.ZERO : dejaEnAttente);
        if (montant.compareTo(disponible) > 0) {
            throw new BusinessException("Le fonds de reserve ne dispose que de " + entier(disponible.max(BigDecimal.ZERO))
                    + " F : la depense de " + entier(montant) + " F le depasse");
        }
    }

    /** Montant entier lisible (12 500), pour les messages et les journaux. */
    public static String entier(BigDecimal v) {
        return String.format(java.util.Locale.FRENCH, "%,.0f", v).replace(' ', ' ').replace(' ', ' ');
    }

    private ApportFonds enregistrer(ApportFonds.Origine origine, Long associeId, String associeNom, BigDecimal montant,
                                    String note, UtilisateurPrincipal auteur) {
        BigDecimal propre = montant.setScale(2, RoundingMode.HALF_UP);
        if (propre.signum() <= 0) {
            throw new BusinessException("Le montant de l'apport doit etre positif");
        }
        ApportFonds a = new ApportFonds();
        a.setNumero(numerotation.prochainNumero("APF"));
        a.setOrigine(origine);
        a.setAssocieId(associeId);
        a.setAssocieNom(associeNom);
        a.setMontant(propre);
        a.setDateApport(Instant.now());
        a.setNote(note == null || note.isBlank() ? null : note.trim());
        a.setAuteurId(auteur.getId());
        a.setAuteurNom(auteur.getNomComplet());
        return apports.save(a);
    }
}
