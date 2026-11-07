package tg.parcauto.api.pointdevente.tresorerie;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.pointdevente.caisse.CaisseService;
import tg.parcauto.api.pointdevente.caisse.Coupure;
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.personnel.PersonnelService;
import tg.parcauto.api.pointdevente.reglage.ReglagePdvService;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.UtilisateurPrincipal;
import tg.parcauto.api.security.UtilisateurRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Circuit des especes, quand l'option est activee : le caissier remet sa recette
 * au gerant (billettage), le gerant la recompte et la valide, l'argent passe du
 * tiroir a son coffre ; le gerant degage ensuite son coffre vers
 * l'administrateur, qui recompte et valide : l'argent arrive sur le compte de
 * l'entreprise. Seules les especes suivent ce circuit.
 * <p>
 * Une seule somme circule a chaque etape : celle recomptee par qui la recoit.
 * Ce qu'il manquerait par rapport a la declaration reste donc a la charge de
 * l'etape precedente (le tiroir, puis le coffre), ou il apparait en ecart.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TresorerieService implements ReglagePdvService.ControleOptions {

    private final RemiseCaisseRepository remises;
    private final DegagementCoffreRepository degagements;
    private final CaisseService caisseService;
    private final ReglagePdvService reglages;
    private final NumerotationService numerotation;
    private final UtilisateurRepository utilisateurs;

    // ------------------------------------------------------------------ dto

    public record Ligne(@NotNull BigDecimal valeur, @NotNull @PositiveOrZero Integer quantite) {
    }

    public record Envoi(@NotNull List<@Valid Ligne> billettage, @Size(max = 2000) String justification) {
    }

    public record Validation(@NotNull List<@Valid Ligne> billettage, @Size(max = 2000) String commentaire) {
    }

    public record Refus(@NotBlank @Size(max = 2000) String motif) {
    }

    public record CoupureDto(BigDecimal valeur, int quantite, BigDecimal montant) {
    }

    public record RemiseDto(Long id, String numero, Long sessionId, String sessionNumero, String caisseNom,
                            Long caissierId, String caissierNom, BigDecimal montantAttendu, BigDecimal montantDeclare,
                            BigDecimal montantValide, BigDecimal ecartDeclare, BigDecimal ecartVerification,
                            String justification, RemiseCaisse.Statut statut, Instant envoyeeLe, String traiteeParNom,
                            Instant traiteeLe, String commentaire, String destination,
                            List<CoupureDto> declare, List<CoupureDto> verifie) {
    }

    public record DegagementDto(Long id, String numero, Long gerantId, String gerantNom, BigDecimal montantAttendu,
                                BigDecimal montantDeclare, BigDecimal montantValide, BigDecimal ecartDeclare,
                                BigDecimal ecartVerification, String justification, DegagementCoffre.Statut statut,
                                Instant envoyeLe, String traiteParNom, Instant traiteLe, String commentaire,
                                List<CoupureDto> declare, List<CoupureDto> verifie) {
    }

    /** Ce que voit le caissier sur sa session : la somme a remettre et sa remise en cours. */
    public record EtatSession(Long sessionId, String sessionNumero, String caisseNom, BigDecimal fondInitial,
                              BigDecimal especesTiroir, BigDecimal aRemettre, RemiseDto enAttente,
                              List<RemiseDto> remises) {
    }

    public record MouvementCoffre(Instant date, String piece, String libelle, BigDecimal entree, BigDecimal sortie) {
    }

    public record Coffre(Long gerantId, String gerantNom, BigDecimal solde, DegagementDto degagementEnAttente,
                         List<MouvementCoffre> mouvements) {
    }

    public record SoldeCoffre(Long gerantId, String gerantNom, BigDecimal solde) {
    }

    public record CompteEntreprise(BigDecimal solde, BigDecimal parDegagements, BigDecimal parRemisesDirectes,
                                   List<SoldeCoffre> coffres, List<MouvementCoffre> entrees) {
    }

    /** Compteurs des validations en attente, pour les pastilles du menu. */
    public record EnAttente(long remises, long degagements) {
    }

    // ------------------------------------------------------- remise caissier

    @Transactional(readOnly = true)
    public EtatSession etatSession(Long sessionId, UtilisateurPrincipal utilisateur) {
        reglages.exigerCircuit();
        SessionCaisse session = sessionAutorisee(sessionId, utilisateur);
        BigDecimal tiroir = caisseService.especesDuTiroir(sessionId);
        List<RemiseDto> liste = remises.deLaSession(sessionId).stream().map(this::dto).toList();
        return new EtatSession(session.getId(), session.getNumero(), session.getCaisse().getNom(),
                session.getFondInitial(), tiroir, aRemettre(session, tiroir),
                liste.stream().filter(r -> r.statut() == RemiseCaisse.Statut.EN_ATTENTE).findFirst().orElse(null),
                liste);
    }

    /** Le caissier envoie le billettage de sa recette au gerant ; rien ne bouge avant la validation. */
    public RemiseDto envoyerRemise(Long sessionId, Envoi envoi, UtilisateurPrincipal caissier) {
        reglages.exigerCircuit();
        SessionCaisse session = sessionAutorisee(sessionId, caissier);
        if (remises.existsBySessionIdAndStatut(sessionId, RemiseCaisse.Statut.EN_ATTENTE)) {
            throw new BusinessException("Une remise de " + session.getCaisse().getNom()
                    + " attend deja la validation du gerant");
        }
        List<CoupureTresorerie> coupures = coupures(envoi.billettage(), CoupureTresorerie.Moment.DECLARE);
        BigDecimal declare = total(coupures);
        if (declare.signum() <= 0) {
            throw new BusinessException("Comptez les billets et les pieces a remettre");
        }
        BigDecimal attendu = aRemettre(session, caisseService.especesDuTiroir(sessionId));
        String justification = exigerJustification(declare.subtract(attendu), envoi.justification(),
                "Ecart avec la somme attendue : justifiez-le");
        RemiseCaisse r = new RemiseCaisse();
        r.setNumero(numerotation.prochainNumero("REM"));
        r.setSession(session);
        r.setCaissierId(caissier.getId());
        r.setCaissierNom(caissier.getNomComplet());
        r.setMontantAttendu(attendu);
        r.setMontantDeclare(declare);
        r.setJustification(justification);
        r.setEnvoyeeLe(Instant.now());
        r.getBillettage().addAll(coupures);
        return dto(remises.save(r));
    }

    // ------------------------------------------------------- validation gerant

    @Transactional(readOnly = true)
    public List<RemiseDto> remisesEnAttente() {
        reglages.exigerCircuit();
        return remises.parStatut(RemiseCaisse.Statut.EN_ATTENTE).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public Page<RemiseDto> historiqueRemises(int page) {
        return remises.historique(PageRequest.of(page, 30)).map(this::dto);
    }

    /**
     * Le gerant recompte et valide : la somme recomptee quitte le tiroir et entre
     * dans son coffre. Validee par l'administrateur, elle va au compte de l'entreprise.
     */
    public RemiseDto validerRemise(Long id, Validation validation, UtilisateurPrincipal valideur) {
        reglages.exigerCircuit();
        RemiseCaisse r = remiseEnAttente(id);
        List<CoupureTresorerie> verifie = coupures(validation.billettage(), CoupureTresorerie.Moment.VERIFIE);
        BigDecimal valide = total(verifie);
        if (valide.signum() <= 0) {
            throw new BusinessException("Le recompte est vide : refusez plutot la remise");
        }
        String commentaire = exigerJustification(valide.subtract(r.getMontantDeclare()), validation.commentaire(),
                "Le recompte differe du billettage du caissier : indiquez l'ecart constate");
        boolean entreprise = estAdministrateur(valideur);
        String destination = entreprise ? "compte de l'entreprise" : "coffre de " + valideur.getNomComplet();
        MouvementCaisse sortie = caisseService.retraitRemise(r.getSession().getId(), valide,
                "Remise " + r.getNumero() + " de " + r.getCaissierNom() + " vers le " + destination, valideur);
        r.getBillettage().addAll(verifie);
        r.setMontantValide(valide);
        r.setStatut(RemiseCaisse.Statut.VALIDEE);
        r.setTraiteeParId(valideur.getId());
        r.setTraiteeParNom(valideur.getNomComplet());
        r.setTraiteeLe(Instant.now());
        r.setCommentaire(commentaire);
        r.setCoffreId(entreprise ? null : valideur.getId());
        r.setMouvementCaisse(sortie);
        return dto(r);
    }

    /** Refus : rien ne bouge, le caissier recompte et renvoie. */
    public RemiseDto refuserRemise(Long id, String motif, UtilisateurPrincipal valideur) {
        RemiseCaisse r = remiseEnAttente(id);
        r.setStatut(RemiseCaisse.Statut.REFUSEE);
        r.setTraiteeParId(valideur.getId());
        r.setTraiteeParNom(valideur.getNomComplet());
        r.setTraiteeLe(Instant.now());
        r.setCommentaire(motif.trim());
        return dto(r);
    }

    // ------------------------------------------------------------- coffre

    @Transactional(readOnly = true)
    public Coffre coffre(UtilisateurPrincipal gerant) {
        reglages.exigerCircuit();
        return coffreDe(gerant.getId(), gerant.getNomComplet());
    }

    /** Le gerant degage son coffre vers l'administrateur, avec son billettage. */
    public DegagementDto envoyerDegagement(Envoi envoi, UtilisateurPrincipal gerant) {
        reglages.exigerCircuit();
        if (estAdministrateur(gerant)) {
            throw new BusinessException("L'administrateur n'a pas de coffre a degager : ses remises vont au compte de l'entreprise");
        }
        if (degagements.findFirstByGerantIdAndStatut(gerant.getId(), DegagementCoffre.Statut.EN_ATTENTE).isPresent()) {
            throw new BusinessException("Un degagement attend deja la validation de l'administrateur");
        }
        List<CoupureTresorerie> coupures = coupures(envoi.billettage(), CoupureTresorerie.Moment.DECLARE);
        BigDecimal declare = total(coupures);
        if (declare.signum() <= 0) {
            throw new BusinessException("Comptez les billets et les pieces a degager");
        }
        BigDecimal solde = solde(gerant.getId());
        String justification = exigerJustification(declare.subtract(solde), envoi.justification(),
                "Le billettage differe du solde du coffre : justifiez l'ecart");
        DegagementCoffre d = new DegagementCoffre();
        d.setNumero(numerotation.prochainNumero("DEG"));
        d.setGerantId(gerant.getId());
        d.setGerantNom(gerant.getNomComplet());
        d.setMontantAttendu(solde);
        d.setMontantDeclare(declare);
        d.setJustification(justification);
        d.setEnvoyeLe(Instant.now());
        d.getBillettage().addAll(coupures);
        return dto(degagements.save(d));
    }

    // ----------------------------------------------------- administrateur

    @Transactional(readOnly = true)
    public List<DegagementDto> degagementsEnAttente() {
        reglages.exigerCircuit();
        return degagements.findByStatutOrderByEnvoyeLeAsc(DegagementCoffre.Statut.EN_ATTENTE).stream()
                .map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public Page<DegagementDto> historiqueDegagements(int page) {
        return degagements.findAllByOrderByEnvoyeLeDesc(PageRequest.of(page, 30)).map(this::dto);
    }

    public DegagementDto validerDegagement(Long id, Validation validation, UtilisateurPrincipal admin) {
        reglages.exigerCircuit();
        DegagementCoffre d = degagementEnAttente(id);
        List<CoupureTresorerie> verifie = coupures(validation.billettage(), CoupureTresorerie.Moment.VERIFIE);
        BigDecimal valide = total(verifie);
        if (valide.signum() <= 0) {
            throw new BusinessException("Le recompte est vide : refusez plutot le degagement");
        }
        String commentaire = exigerJustification(valide.subtract(d.getMontantDeclare()), validation.commentaire(),
                "Le recompte differe du billettage du gerant : indiquez l'ecart constate");
        d.getBillettage().addAll(verifie);
        d.setMontantValide(valide);
        d.setStatut(DegagementCoffre.Statut.VALIDE);
        d.setTraiteParId(admin.getId());
        d.setTraiteParNom(admin.getNomComplet());
        d.setTraiteLe(Instant.now());
        d.setCommentaire(commentaire);
        return dto(d);
    }

    public DegagementDto refuserDegagement(Long id, String motif, UtilisateurPrincipal admin) {
        DegagementCoffre d = degagementEnAttente(id);
        d.setStatut(DegagementCoffre.Statut.REFUSE);
        d.setTraiteParId(admin.getId());
        d.setTraiteParNom(admin.getNomComplet());
        d.setTraiteLe(Instant.now());
        d.setCommentaire(motif.trim());
        return dto(d);
    }

    /** Ce qui est arrive sur le compte de l'entreprise, et ce qui dort encore dans les coffres. */
    @Transactional(readOnly = true)
    public CompteEntreprise compteEntreprise() {
        reglages.exigerCircuit();
        BigDecimal parDegagements = degagements.totalValide();
        BigDecimal parRemises = remises.totalVerseEntreprise();
        List<MouvementCoffre> entrees = new ArrayList<>();
        for (DegagementCoffre d : degagements.findByStatutOrderByTraiteLeDesc(DegagementCoffre.Statut.VALIDE,
                PageRequest.of(0, 100))) {
            entrees.add(new MouvementCoffre(d.getTraiteLe(), d.getNumero(), "Degagement du coffre de " + d.getGerantNom(),
                    d.getMontantValide(), null));
        }
        for (RemiseCaisse r : remises.verseesEntreprise(PageRequest.of(0, 100))) {
            entrees.add(new MouvementCoffre(r.getTraiteeLe(), r.getNumero(), "Remise de " + r.getCaissierNom()
                    + " (" + r.getSession().getCaisse().getNom() + ")", r.getMontantValide(), null));
        }
        entrees.sort(Comparator.comparing(MouvementCoffre::date).reversed());
        List<SoldeCoffre> coffres = gerants().stream()
                .map(g -> new SoldeCoffre((Long) g[0], (String) g[1], solde((Long) g[0])))
                .sorted(Comparator.comparing(SoldeCoffre::solde).reversed())
                .toList();
        return new CompteEntreprise(parDegagements.add(parRemises), parDegagements, parRemises, coffres,
                entrees.size() > 100 ? entrees.subList(0, 100) : entrees);
    }

    @Transactional(readOnly = true)
    public EnAttente enAttente() {
        if (!reglages.lire().circuitCaisseActif()) {
            return new EnAttente(0, 0);
        }
        return new EnAttente(remises.countByStatut(RemiseCaisse.Statut.EN_ATTENTE),
                degagements.countByStatut(DegagementCoffre.Statut.EN_ATTENTE));
    }

    @Override
    @Transactional(readOnly = true)
    public String blocageArretCircuit() {
        if (remises.countByStatut(RemiseCaisse.Statut.EN_ATTENTE) > 0) {
            return "des remises attendent la validation du gerant";
        }
        if (degagements.countByStatut(DegagementCoffre.Statut.EN_ATTENTE) > 0) {
            return "des degagements attendent la validation de l'administrateur";
        }
        for (Object[] g : gerants()) {
            if (solde((Long) g[0]).signum() != 0) {
                return "le coffre de " + g[1] + " n'est pas vide (" + entier(solde((Long) g[0])) + " F) : degagez-le d'abord";
            }
        }
        return null;
    }

    // ------------------------------------------------------------ internes

    private Coffre coffreDe(Long gerantId, String nom) {
        List<MouvementCoffre> mouvements = new ArrayList<>();
        for (RemiseCaisse r : remises.recuesParCoffre(gerantId, PageRequest.of(0, 100))) {
            mouvements.add(new MouvementCoffre(r.getTraiteeLe(), r.getNumero(), "Remise de " + r.getCaissierNom()
                    + " (" + r.getSession().getCaisse().getNom() + ")", r.getMontantValide(), null));
        }
        for (DegagementCoffre d : degagements.findByGerantIdOrderByEnvoyeLeDesc(gerantId, PageRequest.of(0, 100))) {
            if (d.getStatut() == DegagementCoffre.Statut.VALIDE) {
                mouvements.add(new MouvementCoffre(d.getTraiteLe(), d.getNumero(),
                        "Degagement vers l'entreprise, valide par " + d.getTraiteParNom(), null, d.getMontantValide()));
            }
        }
        mouvements.sort(Comparator.comparing(MouvementCoffre::date).reversed());
        return new Coffre(gerantId, nom, solde(gerantId),
                degagements.findFirstByGerantIdAndStatut(gerantId, DegagementCoffre.Statut.EN_ATTENTE).map(this::dto)
                        .orElse(null),
                mouvements);
    }

    private BigDecimal solde(Long gerantId) {
        return remises.recuParCoffre(gerantId).subtract(degagements.sortiParCoffre(gerantId));
    }

    /** [id, nom] des gerants qui ont un coffre : ceux qui ont deja recu des remises, et les comptes GERANT actifs. */
    private List<Object[]> gerants() {
        List<Object[]> liste = new ArrayList<>();
        Set<Long> vus = new HashSet<>();
        for (Object[] c : remises.coffres()) {
            if (vus.add((Long) c[0])) {
                liste.add(c);
            }
        }
        utilisateurs.findByEntrepriseIdOrderByNomAscPrenomAsc(ContexteEntreprise.exiger()).stream()
                .filter(u -> u.isActif() && u.getRoles().contains(Role.GERANT) && !u.getRoles().contains(Role.ADMIN))
                .filter(u -> vus.add(u.getId()))
                .forEach(u -> liste.add(new Object[]{u.getId(), u.nomComplet()}));
        return liste;
    }

    /** A remettre : les especes du tiroir, le fond de caisse y restant. */
    private static BigDecimal aRemettre(SessionCaisse session, BigDecimal tiroir) {
        return tiroir.subtract(session.getFondInitial()).max(BigDecimal.ZERO);
    }

    private SessionCaisse sessionAutorisee(Long sessionId, UtilisateurPrincipal utilisateur) {
        SessionCaisse session = caisseService.sessionOuverte(sessionId);
        if (!PersonnelService.gestionnaire(utilisateur)
                && !session.getCaisse().getUtilisateurIds().contains(utilisateur.getId())) {
            throw new BusinessException("La caisse " + session.getCaisse().getNom() + " ne vous est pas affectee");
        }
        return session;
    }

    private RemiseCaisse remiseEnAttente(Long id) {
        RemiseCaisse r = remises.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Remise", id));
        if (r.getStatut() != RemiseCaisse.Statut.EN_ATTENTE) {
            throw new BusinessException("La remise " + r.getNumero() + " a deja ete traitee");
        }
        return r;
    }

    private DegagementCoffre degagementEnAttente(Long id) {
        DegagementCoffre d = degagements.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Degagement", id));
        if (d.getStatut() != DegagementCoffre.Statut.EN_ATTENTE) {
            throw new BusinessException("Le degagement " + d.getNumero() + " a deja ete traite");
        }
        return d;
    }

    /** Un ecart au-dela de la tolerance reglee doit etre explique. */
    private String exigerJustification(BigDecimal ecart, String texte, String message) {
        String propre = texte == null || texte.isBlank() ? null : texte.trim();
        if (ecart.abs().compareTo(reglages.lire().ecartTolere()) > 0 && propre == null) {
            throw new BusinessException(message + " (" + entier(ecart) + " F)");
        }
        return propre;
    }

    private static boolean estAdministrateur(UtilisateurPrincipal u) {
        return u.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.ADMIN.authority()));
    }

    private static List<CoupureTresorerie> coupures(List<Ligne> lignes, CoupureTresorerie.Moment moment) {
        Set<BigDecimal> vues = new HashSet<>();
        List<CoupureTresorerie> resultat = new ArrayList<>();
        for (Ligne l : lignes) {
            BigDecimal valeur = Coupure.VALEURS.stream().filter(v -> v.compareTo(l.valeur()) == 0).findFirst()
                    .orElseThrow(() -> new BusinessException("Coupure inconnue : " + entier(l.valeur()) + " F"));
            if (!vues.add(valeur)) {
                throw new BusinessException("La coupure de " + entier(valeur) + " F figure deux fois");
            }
            if (l.quantite() > 0) {
                resultat.add(new CoupureTresorerie(moment, valeur, l.quantite()));
            }
        }
        return resultat;
    }

    private static BigDecimal total(List<CoupureTresorerie> coupures) {
        return coupures.stream().map(CoupureTresorerie::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<CoupureDto> coupuresDto(List<CoupureTresorerie> toutes, CoupureTresorerie.Moment moment) {
        return toutes.stream().filter(c -> c.getMoment() == moment)
                .sorted(Comparator.comparing(CoupureTresorerie::getValeur).reversed())
                .map(c -> new CoupureDto(c.getValeur(), c.getQuantite(), c.montant())).toList();
    }

    private RemiseDto dto(RemiseCaisse r) {
        String destination = r.getStatut() != RemiseCaisse.Statut.VALIDEE ? null
                : r.getCoffreId() == null ? "Compte de l'entreprise" : "Coffre de " + r.getTraiteeParNom();
        return new RemiseDto(r.getId(), r.getNumero(), r.getSession().getId(), r.getSession().getNumero(),
                r.getSession().getCaisse().getNom(), r.getCaissierId(), r.getCaissierNom(), r.getMontantAttendu(),
                r.getMontantDeclare(), r.getMontantValide(), r.getMontantDeclare().subtract(r.getMontantAttendu()),
                r.getMontantValide() == null ? null : r.getMontantValide().subtract(r.getMontantDeclare()),
                r.getJustification(), r.getStatut(), r.getEnvoyeeLe(), r.getTraiteeParNom(), r.getTraiteeLe(),
                r.getCommentaire(), destination, coupuresDto(r.getBillettage(), CoupureTresorerie.Moment.DECLARE),
                coupuresDto(r.getBillettage(), CoupureTresorerie.Moment.VERIFIE));
    }

    private DegagementDto dto(DegagementCoffre d) {
        return new DegagementDto(d.getId(), d.getNumero(), d.getGerantId(), d.getGerantNom(), d.getMontantAttendu(),
                d.getMontantDeclare(), d.getMontantValide(), d.getMontantDeclare().subtract(d.getMontantAttendu()),
                d.getMontantValide() == null ? null : d.getMontantValide().subtract(d.getMontantDeclare()),
                d.getJustification(), d.getStatut(), d.getEnvoyeLe(), d.getTraiteParNom(), d.getTraiteLe(),
                d.getCommentaire(), coupuresDto(d.getBillettage(), CoupureTresorerie.Moment.DECLARE),
                coupuresDto(d.getBillettage(), CoupureTresorerie.Moment.VERIFIE));
    }

    private static String entier(BigDecimal v) {
        return String.format(Locale.FRANCE, "%,d", v.setScale(0, RoundingMode.HALF_UP).longValue()).replace(' ', ' ');
    }
}
