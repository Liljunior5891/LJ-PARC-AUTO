package tg.parcauto.api.pointdevente.impression;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.caisse.CaisseRepository;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.Categorie;
import tg.parcauto.api.pointdevente.restauration.Commande;
import tg.parcauto.api.pointdevente.restauration.EcranPreparation;
import tg.parcauto.api.pointdevente.restauration.EcranPreparationRepository;
import tg.parcauto.api.pointdevente.restauration.LigneCommande;
import tg.parcauto.api.pointdevente.vente.LigneVente;
import tg.parcauto.api.pointdevente.vente.Vente;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Impression des bons de preparation : quand la caisse valide, les articles
 * sont repartis par imprimante selon leur categorie (via les postes de
 * preparation) et un bon par imprimante entre dans la file d'impression.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ImpressionService {

    private static final Set<TravailImpression.Statut> A_IMPRIMER =
            EnumSet.of(TravailImpression.Statut.EN_ATTENTE, TravailImpression.Statut.ECHEC);

    private final ImprimanteRepository imprimantes;
    private final TravailImpressionRepository travaux;
    private final EcranPreparationRepository postes;
    private final CaisseRepository caisses;
    private final ObjectMapper json;

    // ------------------------------------------------------------- reglages

    public record ImprimanteRequete(
            @NotBlank @Size(max = 80) String nom,
            Imprimante.Mode mode,
            @Size(max = 64) String adresseIp,
            @Min(1) @Max(65535) Integer port,
            Integer largeurPapier,
            @Min(1) @Max(5) Integer copies,
            Boolean coupe,
            Boolean bip,
            Long caisseId,
            Boolean actif) {
    }

    public record ImprimanteReponse(Long id, String nom, Imprimante.Mode mode, String adresseIp, int port,
                                    int largeurPapier, int copies, boolean coupe, boolean bip,
                                    Long caisseId, String caisseNom, boolean actif,
                                    /** Postes qui envoient leurs bons a cette imprimante. */
                                    List<String> postes,
                                    /** Bons en attente ou en echec. */
                                    long enAttente) {
    }

    @Transactional(readOnly = true)
    public List<ImprimanteReponse> lister() {
        List<EcranPreparation> tous = postes.findAllByOrderByNomAsc();
        List<TravailImpression> attente = travaux.enAttente(A_IMPRIMER);
        return imprimantes.findAllByOrderByNomAsc().stream().map(i -> new ImprimanteReponse(
                i.getId(), i.getNom(), i.getMode(), i.getAdresseIp(), i.getPort(), i.getLargeurPapier(), i.getCopies(),
                i.isCoupe(), i.isBip(),
                i.getCaisse() == null ? null : i.getCaisse().getId(),
                i.getCaisse() == null ? null : i.getCaisse().getNom(),
                i.isActif(),
                tous.stream().filter(p -> p.isActif() && p.getImprimante() != null
                        && p.getImprimante().getId().equals(i.getId())).map(EcranPreparation::getNom).toList(),
                attente.stream().filter(t -> t.getImprimante().getId().equals(i.getId())).count()
        )).toList();
    }

    public ImprimanteReponse creer(ImprimanteRequete r) {
        if (imprimantes.existsByNomIgnoreCase(r.nom().trim())) {
            throw new BusinessException("Une imprimante porte deja le nom " + r.nom().trim());
        }
        Imprimante i = new Imprimante();
        appliquer(i, r);
        Long id = imprimantes.saveAndFlush(i).getId();
        return lister().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    public ImprimanteReponse modifier(Long id, ImprimanteRequete r) {
        Imprimante i = imprimante(id);
        if (imprimantes.existsByNomIgnoreCaseAndIdNot(r.nom().trim(), id)) {
            throw new BusinessException("Une imprimante porte deja le nom " + r.nom().trim());
        }
        appliquer(i, r);
        imprimantes.flush();
        return lister().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    private void appliquer(Imprimante i, ImprimanteRequete r) {
        i.setNom(r.nom().trim());
        if (r.mode() != null) {
            i.setMode(r.mode());
        }
        String ip = r.adresseIp() == null || r.adresseIp().isBlank() ? null : r.adresseIp().trim();
        if (i.getMode() == Imprimante.Mode.AGENT && ip == null) {
            throw new BusinessException("Indiquez l'adresse reseau de l'imprimante : l'agent l'utilise pour imprimer");
        }
        i.setAdresseIp(ip);
        if (r.port() != null) {
            i.setPort(r.port());
        }
        if (r.largeurPapier() != null) {
            if (r.largeurPapier() != 58 && r.largeurPapier() != 80) {
                throw new BusinessException("Largeur de papier : 58 ou 80 mm");
            }
            i.setLargeurPapier(r.largeurPapier());
        }
        if (r.copies() != null) {
            i.setCopies(r.copies());
        }
        if (r.coupe() != null) {
            i.setCoupe(r.coupe());
        }
        if (r.bip() != null) {
            i.setBip(r.bip());
        }
        if (r.actif() != null) {
            i.setActif(r.actif());
        }
        i.setCaisse(r.caisseId() == null ? null : caisses.findById(r.caisseId())
                .orElseThrow(() -> new BusinessException("La caisse choisie n'existe pas")));
    }

    /** Bon d'essai, pour verifier l'imprimante et sa mise en page. */
    public TravailReponse tester(Long id, String auteur) {
        Imprimante i = imprimante(id);
        BonCuisine bon = new BonCuisine(i.getNom(), TravailImpression.Type.TEST, "Essai d'impression", "TEST", null,
                Instant.now(), null, auteur,
                List.of(new BonCuisine.Ligne(BigDecimal.ONE, "Article d'essai", "note de l'article")),
                "Si ce bon est lisible, l'imprimante est prete.", i.getLargeurPapier(), 1);
        return reponse(enregistrer(i, bon, TravailImpression.Type.TEST, "Essai", null, null, null));
    }

    // ------------------------------------------------------------- routage

    /**
     * Categories dont les articles s'impriment sans etre affiches sur aucun
     * ecran : personne ne les fera avancer, ils sont prets des l'impression.
     */
    @Transactional(readOnly = true)
    public Set<Long> categoriesImprimeesSansEcran() {
        Set<Long> imprimees = new HashSet<>();
        Set<Long> affichees = new HashSet<>();
        for (EcranPreparation p : postes.findAll()) {
            if (!p.isActif()) {
                continue;
            }
            Set<Long> ids = new HashSet<>();
            p.getCategories().forEach(c -> ids.add(c.getId()));
            if (p.isAfficherEcran()) {
                affichees.addAll(ids);
            }
            if (p.getImprimante() != null && p.getImprimante().isActif()) {
                imprimees.addAll(ids);
            }
        }
        imprimees.removeAll(affichees);
        return imprimees;
    }

    /** Imprimantes actives qui recoivent chaque categorie. */
    private Map<Long, List<Imprimante>> imprimantesParCategorie() {
        Map<Long, List<Imprimante>> parCategorie = new LinkedHashMap<>();
        for (EcranPreparation p : postes.findAllByOrderByNomAsc()) {
            Imprimante i = p.getImprimante();
            if (!p.isActif() || i == null || !i.isActif()) {
                continue;
            }
            for (Categorie c : p.getCategories()) {
                List<Imprimante> liste = parCategorie.computeIfAbsent(c.getId(), k -> new ArrayList<>());
                if (liste.stream().noneMatch(x -> x.getId().equals(i.getId()))) {
                    liste.add(i);
                }
            }
        }
        return parCategorie;
    }

    /** Repartit des lignes par imprimante ; une ligne de categorie sans imprimante est ignoree. */
    private <T> Map<Imprimante, List<T>> repartir(Collection<T> lignes, Function<T, Article> article) {
        Map<Long, List<Imprimante>> parCategorie = imprimantesParCategorie();
        Map<Imprimante, List<T>> parImprimante = new LinkedHashMap<>();
        if (parCategorie.isEmpty()) {
            return parImprimante;
        }
        for (T ligne : lignes) {
            Categorie c = article.apply(ligne).getCategorie();
            if (c == null) {
                continue;
            }
            for (Imprimante i : parCategorie.getOrDefault(c.getId(), List.of())) {
                parImprimante.computeIfAbsent(i, k -> new ArrayList<>()).add(ligne);
            }
        }
        return parImprimante;
    }

    // ------------------------------------------------------------- bons

    /** Envoi d'une commande valide par la caisse : un bon par imprimante. */
    public void bonsEnvoi(Commande commande, List<LigneCommande> lignes, int envoi, String caissier) {
        repartir(lignes, LigneCommande::getArticle).forEach((imprimante, siennes) -> {
            BonCuisine bon = new BonCuisine(imprimante.getNom(), TravailImpression.Type.BON, commande.repere(),
                    commande.getNumero(), envoi, Instant.now(), commande.getServeurNom(), caissier,
                    siennes.stream().map(l -> new BonCuisine.Ligne(l.getQuantite(), l.getLibelle(), l.getNote())).toList(),
                    envoi == 1 ? commande.getNote() : null, imprimante.getLargeurPapier(), imprimante.getCopies());
            enregistrer(imprimante, bon, TravailImpression.Type.BON,
                    commande.repere() + " · envoi " + envoi, commande.getId(), envoi, null);
        });
    }

    /**
     * Articles deja envoyes puis annules (une ligne, ou toute la commande) : un
     * bon d'annulation par imprimante qui les avait recus.
     */
    public void bonAnnulation(Commande commande, List<LigneCommande> lignes, String auteur) {
        if (lignes.isEmpty()) {
            return;
        }
        repartir(lignes, LigneCommande::getArticle).forEach((imprimante, siennes) -> {
            Integer envoi = siennes.size() == 1 ? siennes.get(0).getEnvoi() : null;
            BonCuisine bon = new BonCuisine(imprimante.getNom(), TravailImpression.Type.ANNULATION, commande.repere(),
                    commande.getNumero(), envoi, Instant.now(), commande.getServeurNom(), auteur,
                    siennes.stream().map(l -> new BonCuisine.Ligne(l.getQuantite(), l.getLibelle(), l.getNote())).toList(),
                    null, imprimante.getLargeurPapier(), imprimante.getCopies());
            enregistrer(imprimante, bon, TravailImpression.Type.ANNULATION,
                    "Annulation · " + commande.repere(), commande.getId(), envoi, null);
        });
    }

    /** Vente directe au comptoir encaissee : un bon par imprimante. */
    public void bonsVente(Vente vente) {
        repartir(vente.getLignes(), LigneVente::getArticle).forEach((imprimante, siennes) -> {
            String repere = vente.getClientNom() != null && !vente.getClientNom().isBlank()
                    ? "Comptoir · " + vente.getClientNom() : "Comptoir";
            BonCuisine bon = new BonCuisine(imprimante.getNom(), TravailImpression.Type.BON, repere,
                    vente.getNumero(), null, Instant.now(), null, vente.getCaissierNom(),
                    siennes.stream().map(l -> new BonCuisine.Ligne(l.getQuantite(), l.getLibelle(), l.getNote())).toList(),
                    null, imprimante.getLargeurPapier(), imprimante.getCopies());
            enregistrer(imprimante, bon, TravailImpression.Type.BON, "Ticket " + vente.getNumero(), null, null,
                    vente.getId());
        });
    }

    /** Nouvel exemplaire d'un bon, marque « DUPLICATA ». */
    public TravailReponse reimprimer(Long id) {
        TravailImpression t = travaux.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Bon", id));
        BonCuisine bon = lire(t).pour(TravailImpression.Type.DUPLICATA);
        return reponse(enregistrer(t.getImprimante(), bon, TravailImpression.Type.DUPLICATA,
                "Duplicata · " + t.getReference(), t.getCommandeId(), t.getEnvoi(), t.getVenteId()));
    }

    private TravailImpression enregistrer(Imprimante imprimante, BonCuisine bon, TravailImpression.Type type,
                                          String reference, Long commandeId, Integer envoi, Long venteId) {
        TravailImpression t = new TravailImpression();
        t.setImprimante(imprimante);
        t.setType(type);
        t.setReference(reference.length() > 80 ? reference.substring(0, 80) : reference);
        t.setCommandeId(commandeId);
        t.setEnvoi(envoi);
        t.setVenteId(venteId);
        try {
            t.setContenu(json.writeValueAsString(bon));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Bon impossible a enregistrer", e);
        }
        return travaux.save(t);
    }

    // ------------------------------------------------------------- impression par le navigateur

    public record TravailReponse(Long id, Long imprimanteId, String imprimanteNom, Imprimante.Mode imprimanteMode,
                                 TravailImpression.Type type,
                                 TravailImpression.Statut statut, String reference, Long commandeId, Integer envoi,
                                 Long venteId, Instant creeLe, Instant imprimeLe, String imprimePar, String erreur,
                                 BonCuisine bon) {
    }

    /** Bons qu'une caisse doit imprimer par le navigateur (nul : toutes les imprimantes). */
    @Transactional(readOnly = true)
    public List<TravailReponse> aImprimer(Long caisseId) {
        return aImprimer(caisseId, null);
    }

    /**
     * Bons a imprimer ; {@code visibles} non nul : seulement ceux des imprimantes communes
     * ou rattachees a ces caisses (caissier et serveur ne voient que leur caisse).
     */
    public List<TravailReponse> aImprimer(Long caisseId, java.util.Set<Long> visibles) {
        if (visibles != null && caisseId != null && !visibles.contains(caisseId)) {
            throw new BusinessException("Cette caisse ne vous est pas affectee");
        }
        return travaux.aImprimer(A_IMPRIMER, caisseId).stream()
                .filter(t -> visible(t, visibles))
                .map(this::reponse).toList();
    }

    private static boolean visible(TravailImpression t, java.util.Set<Long> visibles) {
        return visibles == null || t.getImprimante().getCaisse() == null
                || visibles.contains(t.getImprimante().getCaisse().getId());
    }

    /**
     * La caisse prend les bons pour les imprimer : ils passent imprimes a son
     * nom. Un bon deja pris par une autre caisse n'est pas rendu.
     */
    public List<TravailReponse> prendre(List<Long> ids, String auteur) {
        return prendre(ids, auteur, null);
    }

    public List<TravailReponse> prendre(List<Long> ids, String auteur, java.util.Set<Long> visibles) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<TravailReponse> pris = new ArrayList<>();
        for (TravailImpression t : travaux.verrouiller(ids)) {
            // Un bon d'une autre caisse n'est ni pris ni marque imprime.
            if (!A_IMPRIMER.contains(t.getStatut()) || !visible(t, visibles)) {
                continue;
            }
            t.setStatut(TravailImpression.Statut.IMPRIME);
            t.setTentatives(t.getTentatives() + 1);
            t.setImprimeLe(Instant.now());
            t.setImprimePar(auteur);
            t.setErreur(null);
            pris.add(reponse(t));
        }
        return pris;
    }

    @Transactional(readOnly = true)
    public List<TravailReponse> deLaCommande(Long commandeId) {
        return travaux.deLaCommande(commandeId).stream().map(this::reponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TravailReponse> deLaVente(Long venteId) {
        return travaux.deLaVente(venteId).stream().map(this::reponse).toList();
    }

    private TravailReponse reponse(TravailImpression t) {
        return new TravailReponse(t.getId(), t.getImprimante().getId(), t.getImprimante().getNom(),
                t.getImprimante().getMode(), t.getType(),
                t.getStatut(), t.getReference(), t.getCommandeId(), t.getEnvoi(), t.getVenteId(), t.getCreeLe(),
                t.getImprimeLe(), t.getImprimePar(), t.getErreur(), lire(t));
    }

    private BonCuisine lire(TravailImpression t) {
        try {
            return json.readValue(t.getContenu(), BonCuisine.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Bon " + t.getId() + " illisible", e);
        }
    }

    private Imprimante imprimante(Long id) {
        return imprimantes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Imprimante", id));
    }
}
