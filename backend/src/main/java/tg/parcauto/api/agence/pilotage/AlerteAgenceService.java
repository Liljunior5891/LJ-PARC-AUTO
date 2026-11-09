package tg.parcauto.api.agence.pilotage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.ReglageAgence;
import tg.parcauto.api.agence.ReglageAgenceRepository;
import tg.parcauto.api.agence.caisse.SessionCaisseAgence;
import tg.parcauto.api.agence.carte.CarteService;
import tg.parcauto.api.agence.cloture.ClotureAgence;
import tg.parcauto.api.agence.cloture.ClotureService;
import tg.parcauto.api.agence.credit.CreditService;
import tg.parcauto.api.agence.mouvement.MouvementService;
import tg.parcauto.api.agence.operation.TransactionAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgenceRepository;
import tg.parcauto.api.agence.tresorerie.JourneeCaisse;
import tg.parcauto.api.agence.tresorerie.RegistreService;
import tg.parcauto.api.agence.tresorerie.TypeCompte;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Alertes des agences, affichees dans l'application : cash ou virtuel sous le
 * seuil regle du compte, ecarts de caisse, doublons confirmes, stock de cartes
 * faible, echeances de credit, journees non cloturees, mouvements a valider.
 * Il n'y a ni transaction en attente ni commission a crediter plus tard : les
 * transactions sont validees des la saisie et les commissions creditees aussitot.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlerteAgenceService {

    private static final DateTimeFormatter JJ_MM = DateTimeFormatter.ofPattern("dd/MM");

    private final PerimetreAgence perimetre;
    private final CompteAgenceRepository comptes;
    private final ReglageAgenceRepository reglages;
    private final CarteService cartes;
    private final CreditService credits;
    private final ClotureService clotures;
    private final MouvementService mouvements;
    private final tg.parcauto.api.agence.depense.DepenseAgenceService depenses;
    private final tg.parcauto.api.agence.commission.RapprochementService rapprochement;

    @PersistenceContext
    private EntityManager em;

    public enum Niveau { CRITIQUE, ATTENTION, INFO }

    public record Alerte(Niveau niveau, String categorie, String agence, String message, String lien) {
    }

    public List<Alerte> alertes(Long agenceId) {
        List<Agence> visibles = agenceId != null ? List.of(perimetre.exiger(agenceId)) : perimetre.visibles();
        Map<Long, String> noms = visibles.stream().collect(Collectors.toMap(Agence::getId, Agence::getNom));
        Set<Long> agences = noms.keySet();
        List<Alerte> alertes = new ArrayList<>();
        if (agences.isEmpty()) {
            return alertes;
        }
        ReglageAgence reglage = reglages.findFirstByOrderByIdAsc().orElseGet(ReglageAgence::new);
        Instant ilYASeptJours = Instant.now().minus(7, ChronoUnit.DAYS);

        // Cash et virtuel sous le seuil du compte.
        for (CompteAgence c : comptes.findByAgenceIdIn(agences)) {
            if (c.isActif() && c.getSeuilAlerte() != null && c.getSolde() < c.getSeuilAlerte()) {
                boolean cash = c.getType().getFamille() == TypeCompte.Famille.CASH;
                alertes.add(new Alerte(Niveau.CRITIQUE, cash ? "Cash insuffisant" : "Virtuel insuffisant", noms.get(c.getAgenceId()),
                        c.getLibelle() + " : " + RegistreService.francs(c.getSolde()) + ", sous le seuil de " + RegistreService.francs(c.getSeuilAlerte()),
                        "/agences/mouvements"));
            }
        }

        // Ecarts de caisse des 7 derniers jours (fermetures de caisse et clotures).
        em.createQuery("select s from SessionCaisseAgence s where s.agenceId in :agences and s.statut = :fermee and s.fermeeLe >= :depuis and s.ecart <> 0",
                        SessionCaisseAgence.class)
                .setParameter("agences", agences).setParameter("fermee", SessionCaisseAgence.Statut.FERMEE).setParameter("depuis", ilYASeptJours)
                .getResultList()
                .forEach(s -> alertes.add(new Alerte(Niveau.ATTENTION, "Ecart de caisse", noms.get(s.getAgenceId()),
                        "Caisse de " + s.getCaissierNom() + " (" + s.getNumero() + ") : ecart de " + RegistreService.francs(s.getEcart())
                                + (s.getJustification() != null ? " — " + s.getJustification() : ""), "/agences/caisses")));
        em.createQuery("select c from ClotureAgence c where c.agenceId in :agences and c.journee >= :depuis", ClotureAgence.class)
                .setParameter("agences", agences).setParameter("depuis", LocalDate.now(JourneeCaisse.FUSEAU).minusDays(7))
                .getResultList().stream()
                .filter(c -> c.getLignes().stream().anyMatch(l -> l.getEcart() != null && l.getEcart() != 0))
                .forEach(c -> alertes.add(new Alerte(Niveau.ATTENTION, "Ecart de caisse", noms.get(c.getAgenceId()),
                        "Cloture du " + c.getJournee().format(JJ_MM) + " : ecart total de " + RegistreService.francs(
                                c.getLignes().stream().filter(l -> l.getEcart() != null).mapToLong(l -> l.getEcart()).sum()), "/agences/cloture")));

        // Doublons enregistres malgre l'avertissement.
        em.createQuery("select t from TransactionAgence t where t.agenceId in :agences and t.doublonConfirme = true and t.dateOperation >= :depuis"
                        + " and t.statut = :validee order by t.dateOperation desc", TransactionAgence.class)
                .setParameter("agences", agences).setParameter("depuis", ilYASeptJours).setParameter("validee", TransactionAgence.Statut.VALIDEE)
                .setMaxResults(20).getResultList()
                .forEach(t -> alertes.add(new Alerte(Niveau.INFO, "Doublon confirme", noms.get(t.getAgenceId()),
                        t.getReference() + " (" + RegistreService.francs(t.getMontant()) + ") enregistree malgre l'avertissement de doublon, par "
                                + t.getCaissierNom(), "/agences/transactions")));

        // Commissions acquises que l'operateur n'a toujours pas versees.
        Map<Long, String> services = em.createQuery("select s from ServiceFinancier s", tg.parcauto.api.agence.catalogue.ServiceFinancier.class)
                .getResultList().stream().collect(Collectors.toMap(s -> s.getId(), s -> s.getNom()));
        rapprochement.anciennes(agences, LocalDate.now(JourneeCaisse.FUSEAU).minusDays(reglage.getJoursAlerteCommission()))
                .forEach(r -> alertes.add(new Alerte(Niveau.ATTENTION, "Commission non creditee", noms.get((Long) r[0]),
                        services.get((Long) r[1]) + " : " + ((Number) r[2]).longValue() + " commission(s), "
                                + RegistreService.francs(((Number) r[3]).longValue()) + ", en attente depuis le "
                                + ((LocalDate) r[4]).format(JJ_MM), "/agences/rapprochement")));

        // Stock de cartes faible.
        cartes.alertesStock(agences).forEach(m -> alertes.add(new Alerte(Niveau.ATTENTION, "Stock faible", null, m, "/agences/cartes")));

        // Echeances de credit proches ou depassees.
        credits.echeancesProches(agences, reglage.getJoursAlerteEcheance()).forEach(c -> alertes.add(new Alerte(
                c.enRetard() ? Niveau.CRITIQUE : Niveau.ATTENTION, "Echeance de credit", c.agenceNom(),
                c.numero() + " (" + c.tiersNom() + ") : " + RegistreService.francs(c.montantProchaineEcheance())
                        + (c.enRetard() ? " en retard depuis le " : " a regler le ") + c.prochaineEcheance().format(JJ_MM), "/agences/credits")));

        // Journees avec activite non cloturees.
        clotures.aFaire(agenceId).forEach(a -> alertes.add(new Alerte(Niveau.ATTENTION, "Cloture non effectuee", a.agenceNom(),
                "Journee du " + a.journee().format(JJ_MM) + " pas encore cloturee (" + a.statut().toLowerCase().replace('_', ' ') + ")",
                "/agences/cloture")));

        long enAttente = mouvements.enAttente();
        if (enAttente > 0) {
            alertes.add(new Alerte(Niveau.INFO, "Mouvement a valider", null, enAttente + " mouvement(s) en attente de validation",
                    "/agences/mouvements"));
        }
        long depensesAValider = depenses.nombreAValider(java.util.Set.copyOf(agences));
        if (depensesAValider > 0) {
            alertes.add(new Alerte(Niveau.INFO, "Depense a valider", null, depensesAValider
                    + " depense(s) payee(s) par la caisse en attente de votre validation", "/agences/depenses?aValider=1"));
        }
        alertes.sort(Comparator.comparing(Alerte::niveau));
        return alertes;
    }
}
