package tg.parcauto.api.pointdevente.caisse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.personnel.PersonnelService;
import tg.parcauto.api.pointdevente.restauration.Commande;
import tg.parcauto.api.pointdevente.restauration.CommandeRepository;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sessions de caisse et controle des especes.
 * <p>
 * Un gerant ouvre la session d'une caisse en comptant le fond par coupure ;
 * le personnel affecte a la caisse encaisse ensuite dessus et peut enregistrer
 * des entrees et sorties d'especes motivees. A la fermeture, le gerant compte
 * le tiroir sans voir le montant attendu : l'ecart n'apparait qu'apres le
 * comptage et doit etre justifie s'il n'est pas nul. Une session ne se ferme
 * pas tant qu'une commande qui s'y rattache reste a regler.
 * <p>
 * Especes attendues = fond + ventes en especes + entrees - sorties. Les autres
 * moyens de paiement ne passent pas par le tiroir.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CaisseService {

    private final SessionCaisseRepository repository;
    private final MouvementCaisseRepository mouvementRepository;
    private final VenteRepository venteRepository;
    private final tg.parcauto.api.pointdevente.materiaux.ReglementClientRepository reglementRepository;
    private final CommandeRepository commandeRepository;
    private final NumerotationService numerotation;
    private final GestionCaisseService gestionCaisses;
    private final SessionTravailService sessionsTravail;
    private final tg.parcauto.api.pointdevente.materiaux.SoldeResteRepository soldeRepository;
    private final tg.parcauto.api.pointdevente.tresorerie.RemiseCaisseRepository remiseRepository;
    private final SortieAttenteRepository sortiesAttente;
    private final CircuitDepensesCaisse circuit;

    /** Quantite comptee d'une valeur de billet ou de piece. */
    public record LigneBillettage(@NotNull BigDecimal valeur, @NotNull @PositiveOrZero Integer quantite) {
    }

    public record Ouverture(@NotNull Long caisseId, @NotNull List<@Valid LigneBillettage> billettage) {
    }

    public record Comptage(@NotNull List<@Valid LigneBillettage> billettage) {
    }

    public record Cloture(@NotNull List<@Valid LigneBillettage> billettage,
                          @Size(max = 2000) String justification,
                          @Size(max = 2000) String observations) {
    }

    public record Mouvement(@NotNull MouvementCaisse.Type type, @NotNull @Positive BigDecimal montant,
                            @NotBlank @Size(max = 255) String motif) {
    }

    /** Resultat du comptage a l'aveugle, avant la fermeture. */
    public record Controle(BigDecimal montantCompte, BigDecimal especesAttendues, BigDecimal ecart,
                           List<String> commandesEnCours) {
    }

    public record TotalMode(ModePaiementPdv mode, String libelle, BigDecimal total, long nombre) {
    }

    public record CoupureDto(BigDecimal valeur, String nature, int quantite, BigDecimal montant) {
    }

    public record MouvementDto(Long id, MouvementCaisse.Type type, BigDecimal montant, String motif,
                               String auteurNom, Instant date) {
    }

    /** Sortie demandee a la caisse, en attente du gerant ou de l'administrateur. */
    public record SortieAttenteDto(Long id, String numero, BigDecimal montant, String motif, String auteurNom,
                                   Instant demandeeLe, SortieAttente.Statut statut, String viseePar) {
        public static SortieAttenteDto de(SortieAttente s) {
            return new SortieAttenteDto(s.getId(), s.getNumero(), s.getMontant(), s.getMotif(), s.getAuteurNom(),
                    s.getDemandeeLe(), s.getStatut(), s.getViseePar());
        }
    }

    public record Reponse(
            Long id, String numero, Long caisseId, String caisseNom,
            /** Gerant qui a ouvert la session. */
            Long caissierId, String caissierNom, SessionCaisse.Statut statut,
            Instant ouverteLe, Instant fermeeLe, String fermeeParNom, BigDecimal fondInitial,
            BigDecimal montantCompte, BigDecimal montantTheorique, BigDecimal ecart,
            String justificationEcart, String observations,
            /** Totaux en cours, recalcules a chaque lecture tant que la session est ouverte. */
            BigDecimal chiffreAffaires, long nombreVentes, long ventesAnnulees,
            BigDecimal entrees, BigDecimal sorties, BigDecimal especesAttendues, List<TotalMode> parMode,
            List<MouvementDto> mouvements, List<CoupureDto> billettageOuverture, List<CoupureDto> billettageFermeture,
            int commandesEnCours,
            /** Part des ventes de la session laissee a credit (quincaillerie et materiaux). */
            BigDecimal creditAccorde,
            /** Reglements de clients a credit encaisses sur la session, deja compris dans parMode. */
            BigDecimal reglementsRecus,
            /** Rendu a des clients qui ont solde leur reste, deja deduit de parMode. */
            BigDecimal remboursements,
            /** Sorties demandees sur la session et pas encore approuvees : elles ne pesent pas sur le tiroir. */
            List<SortieAttenteDto> sortiesEnAttente) {
    }

    // ------------------------------------------------------------ ouverture

    /** Un gerant ouvre la session d'une caisse, avec le fond compte par coupure. */
    public Reponse ouvrir(Ouverture ouverture, UtilisateurPrincipal gerant) {
        Caisse caisse = gestionCaisses.aOuvrir(ouverture.caisseId(), gerant);
        repository.findByCaisseIdAndStatut(caisse.getId(), SessionCaisse.Statut.OUVERTE).ifPresent(s -> {
            throw new BusinessException("La caisse " + caisse.getNom() + " est deja ouverte (session " + s.getNumero() + ")");
        });
        SessionCaisse session = new SessionCaisse();
        session.setCaisse(caisse);
        session.setNumero(numerotation.prochainNumero("CAI"));
        session.setCaissierId(gerant.getId());
        session.setCaissierNom(gerant.getNomComplet());
        session.setOuverteLe(Instant.now());
        List<Coupure> fond = coupures(ouverture.billettage(), Coupure.Moment.OUVERTURE);
        session.getBillettage().addAll(fond);
        session.setFondInitial(total(fond));
        return versDto(repository.save(session));
    }

    // -------------------------------------------------------- session de travail

    /** Sessions ouvertes sur lesquelles l'utilisateur peut encaisser. */
    @Transactional(readOnly = true)
    public List<Reponse> ouvertesPour(UtilisateurPrincipal utilisateur) {
        return sessionsTravail.ouvertesPour(utilisateur).stream().map(this::versDto).toList();
    }

    /** La session choisie par le poste, ou la seule ouverte a l'utilisateur. */
    @Transactional(readOnly = true)
    public Optional<Reponse> courante(UtilisateurPrincipal utilisateur, Long sessionId) {
        return sessionsTravail.courante(utilisateur, sessionId).map(this::versDto);
    }

    /**
     * Entree ou sortie d'especes : par un gerant ou par le personnel de la caisse, motif obligatoire.
     * Avec la validation des depenses de caisse, une sortie devient une demande : rien ne quitte le
     * tiroir avant l'approbation de l'administrateur.
     */
    public Reponse mouvement(Long id, Mouvement demande, UtilisateurPrincipal auteur) {
        if (demande.type() == MouvementCaisse.Type.SORTIE) {
            CircuitDepensesCaisse.Etape etape = circuit.etapeInitiale(auteur);
            if (etape != CircuitDepensesCaisse.Etape.DIRECTE) {
                SessionCaisse session = controlerMouvement(id, demande.type(), demande.montant(), auteur);
                SortieAttente s = new SortieAttente();
                s.setNumero(numerotation.prochainNumero("SOR"));
                s.setSession(session);
                s.setMontant(demande.montant());
                s.setMotif(demande.motif().trim());
                s.setAuteurId(auteur.getId());
                s.setAuteurNom(auteur.getNomComplet());
                s.setDemandeeLe(Instant.now());
                if (etape == CircuitDepensesCaisse.Etape.ADMIN) {
                    s.setStatut(SortieAttente.Statut.ATTENTE_ADMIN);
                    s.setViseePar(auteur.getNomComplet());
                    s.setViseeLe(s.getDemandeeLe());
                } else {
                    s.setStatut(SortieAttente.Statut.ATTENTE_GERANT);
                }
                sortiesAttente.save(s);
                return versDto(session);
            }
        }
        MouvementCaisse m = enregistrerMouvement(id, demande.type(), demande.montant(), demande.motif(), auteur);
        return versDto(m.getSession());
    }

    /** Verifie qu'une sortie demandee est possible (caisse ouverte et affectee, especes suffisantes), sans rien sortir. */
    @Transactional(readOnly = true)
    public SessionCaisse controlerSortie(Long sessionId, BigDecimal montant, UtilisateurPrincipal auteur) {
        return controlerMouvement(sessionId, MouvementCaisse.Type.SORTIE, montant, auteur);
    }

    /**
     * Sortie approuvee par l'administrateur (depense ou sortie demandee) : passee sur la session
     * d'origine si elle est ouverte, sinon sur la session ouverte de la meme caisse.
     */
    public MouvementCaisse sortieApprouvee(SessionCaisse origine, BigDecimal montant, String motif, UtilisateurPrincipal admin) {
        SessionCaisse session = circuit.sessionPourSortie(origine);
        return enregistrerMouvement(session.getId(), MouvementCaisse.Type.SORTIE, montant,
                motif.length() <= 255 ? motif : motif.substring(0, 255), admin);
    }

    /**
     * Sortie d'especes pour payer une depense ou un fournisseur : elle pese sur
     * les especes attendues de la session, comme une sortie saisie a la caisse.
     */
    public MouvementCaisse sortie(Long sessionId, BigDecimal montant, String motif, UtilisateurPrincipal auteur) {
        return enregistrerMouvement(sessionId, MouvementCaisse.Type.SORTIE, montant, motif, auteur);
    }

    /** Especes attendues dans le tiroir d'une session ouverte (fond, ventes, entrees, sorties). */
    @Transactional(readOnly = true)
    public BigDecimal especesDuTiroir(Long sessionId) {
        SessionCaisse session = chargerOuverte(sessionId);
        return especesAttendues(session, totaux(sessionId), mouvements(sessionId));
    }

    /** Session ouverte, pour le circuit des remises. */
    @Transactional(readOnly = true)
    public SessionCaisse sessionOuverte(Long sessionId) {
        return chargerOuverte(sessionId);
    }

    /**
     * Remise validee par le gerant : les especes recomptees quittent le tiroir.
     * Pas de plafond ici : un excedent compte physiquement sort aussi du tiroir.
     */
    public MouvementCaisse retraitRemise(Long sessionId, BigDecimal montant, String motif, UtilisateurPrincipal gerant) {
        SessionCaisse session = chargerOuverte(sessionId);
        MouvementCaisse m = new MouvementCaisse();
        m.setSession(session);
        m.setType(MouvementCaisse.Type.SORTIE);
        m.setMontant(montant);
        m.setMotif(motif.length() <= 255 ? motif : motif.substring(0, 255));
        m.setAuteurId(gerant.getId());
        m.setAuteurNom(gerant.getNomComplet());
        m.setDateMouvement(Instant.now());
        return mouvementRepository.save(m);
    }

    /** Retire la sortie d'une depense ou d'un reglement annule, tant que la session est ouverte. */
    public void retirerSortie(MouvementCaisse mouvement, String piece) {
        SessionCaisse session = mouvement.getSession();
        if (session.getStatut() != SessionCaisse.Statut.OUVERTE) {
            throw new BusinessException("La session " + session.getNumero() + " est fermee : " + piece
                    + " paye(e) depuis cette caisse ne peut plus etre annule(e)");
        }
        mouvementRepository.delete(mouvement);
    }

    private MouvementCaisse enregistrerMouvement(Long id, MouvementCaisse.Type type, BigDecimal montant, String motif,
                                                 UtilisateurPrincipal auteur) {
        SessionCaisse session = controlerMouvement(id, type, montant, auteur);
        MouvementCaisse m = new MouvementCaisse();
        m.setSession(session);
        m.setType(type);
        m.setMontant(montant);
        m.setMotif(motif.trim());
        m.setAuteurId(auteur.getId());
        m.setAuteurNom(auteur.getNomComplet());
        m.setDateMouvement(Instant.now());
        return mouvementRepository.save(m);
    }

    /** Caisse ouverte et affectee ; une sortie ne depasse pas les especes attendues du tiroir. */
    private SessionCaisse controlerMouvement(Long id, MouvementCaisse.Type type, BigDecimal montant,
                                             UtilisateurPrincipal auteur) {
        SessionCaisse session = chargerOuverte(id);
        if (!PersonnelService.gestionnaire(auteur) && !session.getCaisse().getUtilisateurIds().contains(auteur.getId())) {
            throw new BusinessException("La caisse " + session.getCaisse().getNom() + " ne vous est pas affectee");
        }
        if (type == MouvementCaisse.Type.SORTIE) {
            BigDecimal disponible = especesAttendues(session, totaux(session.getId()), mouvements(session.getId()));
            if (montant.compareTo(disponible) > 0) {
                throw new BusinessException("Sortie impossible : il n'y a que " + entier(disponible)
                        + " F d'especes attendues dans le tiroir de " + session.getCaisse().getNom());
            }
        }
        return session;
    }

    // ------------------------------------------------------------ fermeture

    /**
     * Comptage a l'aveugle : le gerant saisit le billettage, le serveur revele
     * alors les especes attendues et l'ecart. Rien n'est enregistre.
     */
    @Transactional(readOnly = true)
    public Controle controler(Long id, Comptage comptage) {
        SessionCaisse session = chargerOuverte(id);
        BigDecimal compte = total(coupures(comptage.billettage(), Coupure.Moment.FERMETURE));
        BigDecimal attendu = especesAttendues(session, totaux(id), mouvements(id));
        return new Controle(compte, attendu, compte.subtract(attendu),
                enCours(session).stream().map(Commande::repere).toList());
    }

    /** Fermeture par un gerant : plus de commande a regler, et un ecart non nul justifie. */
    public Reponse fermer(Long id, Cloture cloture, UtilisateurPrincipal gerant) {
        SessionCaisse session = chargerOuverte(id);
        List<Commande> enCours = enCours(session);
        if (!enCours.isEmpty()) {
            throw new BusinessException(enCours.size() + " commande(s) en cours non reglee(s) : "
                    + enCours.stream().map(Commande::repere).limit(8).collect(Collectors.joining(", "))
                    + ". Encaissez-les ou annulez-les avant de fermer la caisse");
        }
        if (remiseRepository.existsBySessionIdAndStatut(id, tg.parcauto.api.pointdevente.tresorerie.RemiseCaisse.Statut.EN_ATTENTE)) {
            throw new BusinessException("Une remise de recette attend la validation du gerant : "
                    + "validez-la ou refusez-la avant de fermer la caisse");
        }
        List<Coupure> comptees = coupures(cloture.billettage(), Coupure.Moment.FERMETURE);
        BigDecimal compte = total(comptees);
        BigDecimal attendu = especesAttendues(session, totaux(id), mouvements(id));
        BigDecimal ecart = compte.subtract(attendu);
        String justification = vide(cloture.justification());
        if (ecart.signum() != 0 && justification == null) {
            throw new BusinessException("Ecart de " + entier(ecart) + " F entre les especes comptees ("
                    + entier(compte) + " F) et attendues (" + entier(attendu) + " F) : justifiez-le pour fermer la caisse");
        }
        session.getBillettage().addAll(comptees);
        session.setStatut(SessionCaisse.Statut.FERMEE);
        session.setFermeeLe(Instant.now());
        session.setFermeeParId(gerant.getId());
        session.setFermeeParNom(gerant.getNomComplet());
        session.setMontantCompte(compte);
        session.setMontantTheorique(attendu);
        session.setEcart(ecart);
        session.setJustificationEcart(ecart.signum() == 0 ? null : justification);
        session.setObservations(vide(cloture.observations()));
        return versDto(session);
    }

    public record VenteProduit(String produit, BigDecimal quantite, BigDecimal total, long commandes) {
    }

    /** Ventes validees de la session regroupees par produit. */
    @Transactional(readOnly = true)
    public List<VenteProduit> parProduit(Long id) {
        charger(id);
        return venteRepository.parProduitSession(id).stream()
                .map(l -> new VenteProduit((String) l[0], (BigDecimal) l[1], (BigDecimal) l[2], (Long) l[3]))
                .toList();
    }

    @Transactional(readOnly = true)
    public Reponse rapport(Long id) {
        return versDto(charger(id));
    }

    /** Historique des sessions, de toutes les caisses ou d'une seule. */
    @Transactional(readOnly = true)
    public Page<Reponse> lister(Long caisseId, Pageable pageable) {
        Page<SessionCaisse> page = caisseId == null
                ? repository.findAllByOrderByOuverteLeDesc(pageable)
                : repository.findByCaisseIdOrderByOuverteLeDesc(caisseId, pageable);
        return page.map(this::versDto);
    }

    // -------------------------------------------------------------- interne

    /**
     * Commandes qui retiennent la session : les siennes, et celles sans session
     * quand c'est la derniere caisse ouverte (personne d'autre ne les reglera).
     */
    private List<Commande> enCours(SessionCaisse session) {
        boolean derniere = repository.countByStatut(SessionCaisse.Statut.OUVERTE) <= 1;
        return commandeRepository.enCoursPourSession(session.getId(), derniere);
    }

    /** Billettage controle : valeurs du franc CFA, chacune une seule fois ; les lignes a zero sont omises. */
    private static List<Coupure> coupures(List<LigneBillettage> lignes, Coupure.Moment moment) {
        Set<BigDecimal> vues = new HashSet<>();
        List<Coupure> coupures = new ArrayList<>();
        for (LigneBillettage ligne : lignes) {
            BigDecimal valeur = Coupure.VALEURS.stream().filter(v -> v.compareTo(ligne.valeur()) == 0).findFirst()
                    .orElseThrow(() -> new BusinessException("Coupure inconnue : " + entier(ligne.valeur()) + " F"));
            if (!vues.add(valeur)) {
                throw new BusinessException("La coupure de " + entier(valeur) + " F figure deux fois");
            }
            if (ligne.quantite() > 0) {
                coupures.add(new Coupure(moment, valeur, ligne.quantite()));
            }
        }
        return coupures;
    }

    private static BigDecimal total(List<Coupure> coupures) {
        return coupures.stream().map(Coupure::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Encaissements de la session par moyen de paiement : ce qui est entre a la
     * vente (total, ou acompte d'une vente a credit) et les reglements de
     * clients recus ensuite, moins ce qui a ete rembourse a des clients.
     */
    private Map<ModePaiementPdv, TotalMode> totaux(Long sessionId) {
        Map<ModePaiementPdv, TotalMode> totaux = new EnumMap<>(ModePaiementPdv.class);
        List<Object[]> lignes = new ArrayList<>(venteRepository.totauxSession(sessionId));
        lignes.addAll(reglementRepository.totauxSession(sessionId));
        for (Object[] r : soldeRepository.remboursementsSession(sessionId)) {
            lignes.add(new Object[]{r[0], ((BigDecimal) r[1]).negate(), 0L});
        }
        for (Object[] ligne : lignes) {
            ModePaiementPdv mode = (ModePaiementPdv) ligne[0];
            TotalMode avant = totaux.get(mode);
            BigDecimal total = (BigDecimal) ligne[1];
            long nombre = (Long) ligne[2];
            totaux.put(mode, avant == null ? new TotalMode(mode, mode.getLibelle(), total, nombre)
                    : new TotalMode(mode, mode.getLibelle(), avant.total().add(total), avant.nombre() + nombre));
        }
        return totaux;
    }

    private List<MouvementCaisse> mouvements(Long sessionId) {
        return mouvementRepository.findBySessionIdOrderByDateMouvementAscIdAsc(sessionId);
    }

    private static BigDecimal especesAttendues(SessionCaisse session, Map<ModePaiementPdv, TotalMode> totaux,
                                               List<MouvementCaisse> mouvements) {
        TotalMode especes = totaux.get(ModePaiementPdv.ESPECES);
        BigDecimal net = mouvements.stream().map(MouvementCaisse::effet).reduce(BigDecimal.ZERO, BigDecimal::add);
        return session.getFondInitial().add(especes == null ? BigDecimal.ZERO : especes.total()).add(net);
    }

    private Reponse versDto(SessionCaisse s) {
        Map<ModePaiementPdv, TotalMode> totaux = totaux(s.getId());
        List<MouvementCaisse> mouvements = mouvements(s.getId());
        List<Object[]> chiffres = venteRepository.chiffreSession(s.getId());
        BigDecimal chiffre = chiffres.isEmpty() || chiffres.get(0)[0] == null ? BigDecimal.ZERO : (BigDecimal) chiffres.get(0)[0];
        BigDecimal credit = chiffres.isEmpty() || chiffres.get(0)[1] == null ? BigDecimal.ZERO : (BigDecimal) chiffres.get(0)[1];
        BigDecimal reglements = reglementRepository.totauxSession(s.getId()).stream()
                .map(l -> (BigDecimal) l[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        long nombre = venteRepository.totauxSession(s.getId()).stream().mapToLong(l -> (Long) l[2]).sum();
        BigDecimal entrees = somme(mouvements, MouvementCaisse.Type.ENTREE);
        BigDecimal sorties = somme(mouvements, MouvementCaisse.Type.SORTIE);
        return new Reponse(
                s.getId(), s.getNumero(), s.getCaisse().getId(), s.getCaisse().getNom(),
                s.getCaissierId(), s.getCaissierNom(), s.getStatut(),
                s.getOuverteLe(), s.getFermeeLe(), s.getFermeeParNom(), s.getFondInitial(),
                s.getMontantCompte(), s.getMontantTheorique(), s.getEcart(),
                s.getJustificationEcart(), s.getObservations(),
                chiffre, nombre, venteRepository.annuleesSession(s.getId()),
                entrees, sorties, especesAttendues(s, totaux, mouvements), List.copyOf(totaux.values()),
                mouvements.stream().map(m -> new MouvementDto(m.getId(), m.getType(), m.getMontant(), m.getMotif(),
                        m.getAuteurNom(), m.getDateMouvement())).toList(),
                billettage(s, Coupure.Moment.OUVERTURE), billettage(s, Coupure.Moment.FERMETURE),
                s.ouverte() ? enCours(s).size() : 0, credit, reglements,
                soldeRepository.remboursementsSession(s.getId()).stream()
                        .map(l -> (BigDecimal) l[1]).reduce(BigDecimal.ZERO, BigDecimal::add),
                sortiesAttente.findBySessionIdOrderByDemandeeLeAscIdAsc(s.getId()).stream()
                        .filter(a -> a.getStatut().enAttente()).map(SortieAttenteDto::de).toList());
    }

    private static List<CoupureDto> billettage(SessionCaisse s, Coupure.Moment moment) {
        return s.billettage(moment).stream()
                .sorted((a, b) -> b.getValeur().compareTo(a.getValeur()))
                .map(c -> new CoupureDto(c.getValeur(), Coupure.nature(c.getValeur()), c.getQuantite(), c.montant()))
                .toList();
    }

    private static BigDecimal somme(List<MouvementCaisse> mouvements, MouvementCaisse.Type type) {
        return mouvements.stream().filter(m -> m.getType() == type).map(MouvementCaisse::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SessionCaisse chargerOuverte(Long id) {
        SessionCaisse session = charger(id);
        if (!session.ouverte()) {
            throw new BusinessException("La session " + session.getNumero() + " est deja fermee");
        }
        return session;
    }

    private SessionCaisse charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Session de caisse", id));
    }

    private static String entier(BigDecimal montant) {
        return montant.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
