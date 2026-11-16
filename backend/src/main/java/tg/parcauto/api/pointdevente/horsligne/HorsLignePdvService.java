package tg.parcauto.api.pointdevente.horsligne;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.vente.VenteDto;
import tg.parcauto.api.pointdevente.vente.VenteService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ventes de la caisse faites sans connexion. L'appareil les envoie au retour du
 * reseau ; chacune est rejouee dans sa propre transaction, au nom du caissier,
 * au prix encaisse. Une cle unique par vente rend l'envoi rejouable : un
 * renvoi renvoie le resultat deja obtenu. Une vente impossible a enregistrer
 * (session fermee entre-temps, article supprime, remise non permise...) devient
 * un conflit : le gerant la reprend sur une caisse ouverte, ou l'abandonne avec
 * une note (argent rendu, vente refaite autrement).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HorsLignePdvService {

    static final int VENTES_MAX = 100;
    /** Taille gardee d'une vente en conflit ; une vente valide (200 lignes au plus) reste bien en dessous. */
    static final int CONTENU_MAX = 100_000;
    /** Conflits enregistres par caissier et par jour, au plus : au-dela, les ventes restent sur l'appareil. */
    static final int CONFLITS_JOUR_MAX = 300;

    private final VenteHorsLignePdvRepository repository;
    private final VenteService ventes;
    private final PlatformTransactionManager gestionnaire;
    private final ObjectMapper json;
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;
    private final tg.parcauto.api.pointdevente.catalogue.ArticleRepository articles;
    private final tg.parcauto.api.pointdevente.vente.VenteRepository ventesEnregistrees;
    private final jakarta.validation.Validator validateur;

    public record Resultat(String cle, String numeroProvisoire, VenteHorsLignePdv.Statut statut, Long venteId,
                           String numero, String message, String manques, String ecartsPrix) {
    }

    public record VenteDto2(Long id, String cle, String numeroProvisoire, Instant saisieLe, String caissierNom,
                            BigDecimal total, VenteHorsLignePdv.Statut statut, Long venteId, String numero,
                            String message, String manques, String ecartsPrix, String traiteePar, Instant traiteeLe,
                            String note, List<String> articles) {
    }

    public record Reprise(Long sessionId) {
    }

    public record Abandon(@NotBlank @Size(max = 500) String note) {
    }

    // ------------------------------------------------------------- envoi par l'appareil

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<Resultat> synchroniser(List<VenteDto.VenteHorsLigne> envoyees, UtilisateurPrincipal caissier) {
        if (envoyees.size() > VENTES_MAX) {
            throw new BusinessException("Envoyez au plus " + VENTES_MAX + " ventes a la fois");
        }
        limiteur.exiger("pdv-hors-ligne:" + caissier.getId(), 30, Duration.ofMinutes(1),
                "Trop de synchronisations a la suite : patientez une minute");
        TransactionTemplate tx = new TransactionTemplate(gestionnaire);
        List<Resultat> resultats = new ArrayList<>();
        // Chaque vente est controlee a part : une vente mal formee ne bloque pas les autres.
        List<VenteDto.VenteHorsLigne> triees = envoyees.stream().filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(VenteDto.VenteHorsLigne::saisieLe, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        for (VenteDto.VenteHorsLigne v : triees) {
            Resultat r = synchroniser(v, caissier, tx);
            // Sans resultat (erreur passagere), l'appareil garde la vente et la renverra.
            if (r != null) {
                resultats.add(r);
            }
        }
        return resultats;
    }

    private Resultat synchroniser(VenteDto.VenteHorsLigne v, UtilisateurPrincipal caissier, TransactionTemplate tx) {
        String invalide = invalide(v);
        if (v.cle() == null || v.cle().isBlank() || v.cle().length() > 64) {
            // Sans cle exploitable, rien ne peut etre garde : l'appareil retire la vente de sa file.
            return new Resultat(v.cle(), v.numeroProvisoire(), VenteHorsLignePdv.Statut.CONFLIT, null, null,
                    "Vente rejetee : " + (invalide == null ? "cle absente" : invalide), null, null);
        }
        Resultat connu = dejaTraitee(v, tx);
        if (connu != null) {
            return connu;
        }
        if (invalide != null) {
            return conflit(v, caissier, "Vente incomplete : " + invalide, tx);
        }
        try {
            return tx.execute(s -> {
                VenteService.VenteRejouee rejouee = ventes.encaisserHorsLigne(v, caissier, v.sessionId(), false);
                VenteHorsLignePdv trace = trace(v, caissier, VenteHorsLignePdv.Statut.ENREGISTREE, null);
                noter(trace, rejouee);
                return resultat(repository.saveAndFlush(trace));
            });
        } catch (RuntimeException e) {
            if (passagere(e)) {
                // Base momentanement indisponible, verrou ou conflit de version : rien n'est garde,
                // la vente reste dans la file de l'appareil et repart au prochain envoi.
                log.warn("Vente hors ligne {} non rejouee (erreur passagere) : {}", v.numeroProvisoire(), e.toString());
                return null;
            }
            // Un envoi simultane de la meme vente a pu passer avant : son resultat fait foi.
            Resultat entreTemps = dejaTraitee(v, tx);
            return entreTemps != null ? entreTemps : conflit(v, caissier, motif(e), tx);
        }
    }

    /** Erreur technique qui peut disparaitre a l'essai suivant (connexion, verrou, version). */
    static boolean passagere(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof org.springframework.dao.TransientDataAccessException
                    || t instanceof org.springframework.dao.RecoverableDataAccessException
                    || t instanceof org.springframework.dao.DataAccessResourceFailureException
                    || t instanceof org.springframework.transaction.CannotCreateTransactionException
                    || t instanceof java.sql.SQLTransientException
                    || t instanceof java.sql.SQLRecoverableException) {
                return true;
            }
        }
        return false;
    }

    /** Resultat deja connu : trace de synchronisation, ou vente enregistree en ligne avec la meme cle. */
    private Resultat dejaTraitee(VenteDto.VenteHorsLigne v, TransactionTemplate tx) {
        return tx.execute(s -> repository.findByCle(v.cle()).map(HorsLignePdvService::resultat)
                .orElseGet(() -> ventesEnregistrees.findFirstByCleHorsLigne(v.cle())
                        .map(vente -> new Resultat(v.cle(), v.numeroProvisoire(), VenteHorsLignePdv.Statut.ENREGISTREE,
                                vente.getId(), vente.getNumero(), null, null, null))
                        .orElse(null)));
    }

    private Resultat conflit(VenteDto.VenteHorsLigne v, UtilisateurPrincipal caissier, String motif, TransactionTemplate tx) {
        try {
            limiteur.exiger("pdv-hors-ligne-conflits:" + caissier.getId(), CONFLITS_JOUR_MAX, Duration.ofDays(1),
                    "Trop de ventes en conflit aujourd'hui : prevenez le gerant");
        } catch (RuntimeException plafond) {
            log.warn("Plafond de conflits atteint pour {} : vente {} gardee sur l'appareil", caissier.getId(), v.numeroProvisoire());
            return null;
        }
        try {
            return tx.execute(s -> resultat(repository.saveAndFlush(trace(v, caissier, VenteHorsLignePdv.Statut.CONFLIT, motif))));
        } catch (RuntimeException e) {
            Resultat entreTemps = dejaTraitee(v, tx);
            if (entreTemps != null) {
                return entreTemps;
            }
            throw e;
        }
    }

    /** Premiere contrainte non respectee, ou nul. */
    private String invalide(VenteDto.VenteHorsLigne v) {
        return validateur.validate(v).stream().findFirst()
                .map(c -> c.getPropertyPath() + " " + c.getMessage()).orElse(null);
    }

    // ------------------------------------------------------------- suivi par le gerant

    /** Ventes hors ligne des 30 derniers jours (et tous les conflits ouverts). */
    @Transactional(readOnly = true)
    public List<VenteDto2> recentes() {
        List<VenteHorsLignePdv> liste = new ArrayList<>(repository.depuis(Instant.now().minus(Duration.ofDays(30))));
        for (VenteHorsLignePdv c : repository.findByStatutOrderBySaisieLeAsc(VenteHorsLignePdv.Statut.CONFLIT)) {
            if (liste.stream().noneMatch(x -> x.getId().equals(c.getId()))) {
                liste.add(c);
            }
        }
        return liste.stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public long nombreConflits() {
        return repository.countByStatut(VenteHorsLignePdv.Statut.CONFLIT);
    }

    /** Reprise au nom du gerant, sur une caisse ouverte qu'il choisit (le tiroir qui a l'argent). */
    @Transactional
    public VenteDto2 reprendre(Long id, Reprise r, UtilisateurPrincipal gerant) {
        VenteHorsLignePdv trace = conflit(id);
        java.util.Optional<tg.parcauto.api.pointdevente.vente.Vente> deja = ventesEnregistrees.findFirstByCleHorsLigne(trace.getCle());
        if (deja.isPresent()) {
            // La meme vente est arrivee en ligne apres coup (reponse perdue) : aucune seconde vente.
            trace.setVenteId(deja.get().getId());
            trace.setMessage(deja.get().getNumero());
            trace.setNote("Deja enregistree en ligne sous " + deja.get().getNumero() + " : aucune seconde vente.");
            trace.setStatut(VenteHorsLignePdv.Statut.REPRISE);
            trace.setTraiteePar(gerant.getNomComplet());
            trace.setTraiteeLe(Instant.now());
            return dto(trace);
        }
        VenteDto.VenteHorsLigne v = lire(trace);
        VenteService.VenteRejouee rejouee;
        try {
            // Le gerant qui reprend la vente accepte le prix encaisse, meme plus bas que le catalogue.
            rejouee = ventes.encaisserHorsLigne(v, gerant, r == null ? null : r.sessionId(), true);
        } catch (BusinessException | ResourceNotFoundException e) {
            throw new BusinessException("Reprise impossible : " + e.getMessage());
        }
        noter(trace, rejouee);
        trace.setStatut(VenteHorsLignePdv.Statut.REPRISE);
        trace.setTraiteePar(gerant.getNomComplet());
        trace.setTraiteeLe(Instant.now());
        return dto(trace);
    }

    @Transactional
    public VenteDto2 abandonner(Long id, Abandon a, UtilisateurPrincipal gerant) {
        VenteHorsLignePdv trace = conflit(id);
        trace.setStatut(VenteHorsLignePdv.Statut.ABANDONNEE);
        trace.setTraiteePar(gerant.getNomComplet());
        trace.setTraiteeLe(Instant.now());
        trace.setNote(a.note().trim());
        return dto(trace);
    }

    // ------------------------------------------------------------- internes

    private VenteHorsLignePdv conflit(Long id) {
        VenteHorsLignePdv trace = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Vente hors ligne", id));
        if (trace.getStatut() != VenteHorsLignePdv.Statut.CONFLIT) {
            throw new BusinessException("La vente " + trace.getNumeroProvisoire() + " n'est pas en conflit");
        }
        return trace;
    }

    private VenteHorsLignePdv trace(VenteDto.VenteHorsLigne v, UtilisateurPrincipal caissier,
                                   VenteHorsLignePdv.Statut statut, String message) {
        VenteHorsLignePdv t = new VenteHorsLignePdv();
        t.setCle(v.cle());
        t.setNumeroProvisoire(v.numeroProvisoire() == null || v.numeroProvisoire().isBlank() ? "HL-?"
                : tronquer(v.numeroProvisoire(), 40));
        t.setSaisieLe(v.saisieLe() == null ? Instant.now() : v.saisieLe());
        t.setCaissierNom(caissier.getNomComplet());
        t.setSessionId(v.sessionId());
        t.setTotal(total(v));
        String contenu;
        try {
            contenu = json.writeValueAsString(v);
            if (contenu.length() > CONTENU_MAX) {
                // Vente anormalement volumineuse : seul son resume est garde (elle ne pourra qu'etre abandonnee).
                contenu = json.writeValueAsString(java.util.Map.of("cle", v.cle(),
                        "numeroProvisoire", t.getNumeroProvisoire(), "tronquee", true));
                message = (message == null ? "" : message + " ") + "(contenu trop volumineux, non garde)";
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException("Vente " + v.numeroProvisoire() + " illisible");
        }
        t.setContenu(contenu);
        t.setStatut(statut);
        t.setMessage(tronquer(message, 500));
        return t;
    }

    private static void noter(VenteHorsLignePdv trace, VenteService.VenteRejouee rejouee) {
        trace.setVenteId(rejouee.vente().getId());
        trace.setMessage(rejouee.vente().getNumero());
        trace.setManques(rejouee.manques().isEmpty() ? null : tronquer(String.join(" ; ", rejouee.manques()), 1000));
        trace.setEcartsPrix(rejouee.ecartsPrix().isEmpty() ? null : tronquer(String.join(" ; ", rejouee.ecartsPrix()), 1000));
    }

    private VenteDto.VenteHorsLigne lire(VenteHorsLignePdv trace) {
        try {
            return json.readValue(trace.getContenu(), VenteDto.VenteHorsLigne.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Vente " + trace.getNumeroProvisoire() + " illisible");
        }
    }

    /** Total affiche du conflit : lignes au prix encaisse, moins les remises. */
    private static BigDecimal total(VenteDto.VenteHorsLigne v) {
        BigDecimal total = BigDecimal.ZERO;
        for (VenteDto.LigneHorsLigne l : v.lignes() == null ? List.<VenteDto.LigneHorsLigne>of() : v.lignes()) {
            if (l == null || l.prixUnitaire() == null || l.quantite() == null) {
                continue;
            }
            total = total.add(l.prixUnitaire().multiply(l.quantite()))
                    .subtract(l.remise() == null ? BigDecimal.ZERO : l.remise());
        }
        return total.subtract(v.remise() == null ? BigDecimal.ZERO : v.remise()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static Resultat resultat(VenteHorsLignePdv t) {
        boolean enregistree = t.getStatut() == VenteHorsLignePdv.Statut.ENREGISTREE || t.getStatut() == VenteHorsLignePdv.Statut.REPRISE;
        return new Resultat(t.getCle(), t.getNumeroProvisoire(), t.getStatut(), t.getVenteId(),
                enregistree ? t.getMessage() : null, enregistree ? null : t.getMessage(), t.getManques(), t.getEcartsPrix());
    }

    private VenteDto2 dto(VenteHorsLignePdv t) {
        boolean enregistree = t.getStatut() == VenteHorsLignePdv.Statut.ENREGISTREE || t.getStatut() == VenteHorsLignePdv.Statut.REPRISE;
        List<String> articles;
        try {
            List<VenteDto.LigneHorsLigne> lignes = lire(t).lignes();
            java.util.Map<Long, String> noms = new java.util.HashMap<>();
            this.articles.findAllById(lignes.stream().map(VenteDto.LigneHorsLigne::articleId).toList())
                    .forEach(a -> noms.put(a.getId(), a.getNom()));
            articles = lignes.stream().map(l -> l.quantite().stripTrailingZeros().toPlainString() + " x "
                    + noms.getOrDefault(l.articleId(), "article supprime") + " a "
                    + l.prixUnitaire().stripTrailingZeros().toPlainString() + " F").toList();
        } catch (RuntimeException e) {
            // Vente illisible ou incomplete (conflit d'une vente mal formee) : pas de detail.
            articles = List.of();
        }
        return new VenteDto2(t.getId(), t.getCle(), t.getNumeroProvisoire(), t.getSaisieLe(), t.getCaissierNom(), t.getTotal(),
                t.getStatut(), t.getVenteId(), enregistree ? t.getMessage() : null, enregistree ? null : t.getMessage(),
                t.getManques(), t.getEcartsPrix(), t.getTraiteePar(), t.getTraiteeLe(), t.getNote(), articles);
    }

    private static String motif(RuntimeException e) {
        return e instanceof BusinessException || e instanceof ResourceNotFoundException
                ? e.getMessage() : "Erreur inattendue : " + e.getClass().getSimpleName();
    }

    private static String tronquer(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
