package tg.parcauto.api.agence.horsligne;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.DroitsAgence;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.PermissionAgence;
import tg.parcauto.api.agence.carte.CarteService;
import tg.parcauto.api.agence.depense.DepenseAgenceService;
import tg.parcauto.api.agence.operation.TransactionService;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Operations des caissiers d'agence saisies sans connexion : transactions,
 * ventes de cartes et depenses. Au retour du reseau, l'appareil les envoie ;
 * chacune est rejouee dans sa propre transaction par les services habituels
 * (memes controles, memes ecritures). Une operation refusee devient un conflit
 * que le superviseur reprend (une fois la cause levee) ou abandonne avec une
 * note.
 */
@Service
@RequiredArgsConstructor
public class HorsLigneService {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Africa/Lome"));

    private final OperationHorsLigneRepository repository;
    private final TransactionService transactions;
    private final CarteService cartes;
    private final DepenseAgenceService depenses;
    private final PerimetreAgence perimetre;
    private final DroitsAgence droits;
    private final AuditAgenceService audit;
    private final PlatformTransactionManager gestionnaire;
    private final ObjectMapper json;
    private final Validator validateur;
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;

    private static final int OPERATIONS_MAX = 200;
    private static final int DONNEES_MAX = 8_192;

    /** Operation envoyee par l'appareil : sa cle, son type, l'heure de saisie et la saisie elle-meme. */
    public record OperationEnvoyee(@NotBlank @Size(max = 64) String cle, @NotNull OperationHorsLigne.Type type,
                                   @NotNull Instant saisieLe, @Size(max = 255) String resume, @NotNull JsonNode donnees) {
    }

    public record Resultat(String cle, OperationHorsLigne.Statut statut, String reference, String message) {
    }

    public record ConflitDto(Long id, String cle, OperationHorsLigne.Type type, Long agenceId, String agenceNom, String auteurNom,
                             Instant saisieLe, Instant recueLe, String resume, String motif, OperationHorsLigne.Statut statut,
                             String reference, String traiteeParNom, Instant traiteeLe, String noteTraitement) {
    }

    public record Abandon(@NotBlank @Size(max = 500) String note) {
    }

    // ------------------------------------------------------------- envoi en ligne

    /**
     * Operation envoyee en ligne avec sa cle : la cle est retenue dans la meme
     * transaction que l'ecriture. Si la reponse se perd et que l'appareil
     * renvoie l'operation par la file hors ligne, la synchronisation la
     * reconnait et ne la refait pas. Sans cle, l'operation passe telle quelle.
     */
    @Transactional
    public <T> T enLigne(String cle, OperationHorsLigne.Type type, Object saisie, UtilisateurPrincipal auteur,
                         java.util.function.Supplier<T> action, java.util.function.Function<T, String> reference) {
        if (cle == null || cle.isBlank()) {
            return action.get();
        }
        if (cle.length() > 64) {
            throw new BusinessException("Cle d'operation trop longue");
        }
        repository.findByCle(cle).ifPresent(o -> {
            throw new BusinessException("Operation deja recue" + (o.getReference() == null ? "" : " : " + o.getReference()));
        });
        T resultat = action.get();
        OperationHorsLigne o = trace(new OperationEnvoyee(cle, type, Instant.now(), "Envoi en ligne", json.valueToTree(saisie)),
                auteur, OperationHorsLigne.Statut.ENREGISTREE, null);
        o.setReference(reference.apply(resultat));
        repository.save(o);
        return resultat;
    }

    // ---------------------------------------------------------- synchronisation

    /**
     * Enregistre les operations faites sans connexion, dans l'ordre de saisie.
     * Chacune a sa propre transaction : une operation refusee ne bloque pas les
     * suivantes. Une cle deja recue renvoie le resultat deja connu.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<Resultat> synchroniser(List<OperationEnvoyee> operations, UtilisateurPrincipal auteur) {
        if (operations.size() > OPERATIONS_MAX) {
            throw new BusinessException("Envoyez au plus " + OPERATIONS_MAX + " operations a la fois");
        }
        limiteur.exiger("agences-hors-ligne:" + auteur.getId(), 30, java.time.Duration.ofMinutes(1),
                "Trop de synchronisations a la suite : patientez une minute");
        // Rejet simple, sans conflit ni trace immuable : saisie trop lourde, ou agence absente
        // ou hors du perimetre de l'emetteur.
        for (OperationEnvoyee op : operations) {
            if (op.donnees().toString().length() > DONNEES_MAX) {
                throw new BusinessException("Operation " + op.cle() + " trop volumineuse");
            }
            JsonNode agence = op.donnees().get("agenceId");
            if (agence == null || !agence.canConvertToLong()) {
                throw new BusinessException("Operation " + op.cle() + " sans agence");
            }
            perimetre.exiger(agence.asLong());
        }
        TransactionTemplate tx = new TransactionTemplate(gestionnaire);
        List<Resultat> resultats = new ArrayList<>();
        List<OperationEnvoyee> triees = operations.stream().sorted((a, b) -> a.saisieLe().compareTo(b.saisieLe())).toList();
        for (OperationEnvoyee op : triees) {
            Resultat connu = tx.execute(s -> repository.findByCle(op.cle()).map(this::resultat).orElse(null));
            if (connu != null) {
                resultats.add(connu);
                continue;
            }
            Resultat r;
            try {
                r = tx.execute(s -> {
                    String reference = executer(op.type(), op.donnees(), op.saisieLe(), auteur);
                    OperationHorsLigne o = trace(op, auteur, OperationHorsLigne.Statut.ENREGISTREE, null);
                    o.setReference(reference);
                    return resultat(repository.save(o));
                });
            } catch (RuntimeException e) {
                String motif = e instanceof BusinessException || e instanceof ResourceNotFoundException
                        ? e.getMessage() : "Erreur inattendue : " + e.getClass().getSimpleName();
                r = tx.execute(s -> {
                    OperationHorsLigne o = repository.save(trace(op, auteur, OperationHorsLigne.Statut.CONFLIT, motif));
                    audit.tracer(o.getAgenceId(), "CONFLIT_HORS_LIGNE", "OPERATION_HORS_LIGNE", o.getId(),
                            "Operation hors ligne refusee (" + o.getResume() + ", saisie le " + HEURE.format(o.getSaisieLe())
                                    + " par " + o.getAuteurNom() + ") : " + motif, null, null);
                    return resultat(o);
                });
            }
            resultats.add(r);
        }
        return resultats;
    }

    /** Rejoue la saisie par le service habituel et renvoie la reference creee. */
    private String executer(OperationHorsLigne.Type type, JsonNode donnees, Instant saisieLe, UtilisateurPrincipal auteur) {
        String note = "Saisie hors ligne le " + HEURE.format(saisieLe);
        try {
            return switch (type) {
                case TRANSACTION -> {
                    exiger(PermissionAgence.TRANSACTION_SAISIR);
                    TransactionService.Saisie s = valider(json.treeToValue(donnees, TransactionService.Saisie.class));
                    TransactionService.Saisie avecNote = new TransactionService.Saisie(s.agenceId(), s.serviceId(), s.typeOperationId(),
                            s.reseauId(), s.montant(), s.commission(), s.motifCommission(), s.referenceOperateur(), s.clientTelephone(),
                            s.clientNom(), s.clientTypePiece(), s.clientNumeroPiece(), s.carte(), joindre(note, s.observations()), true);
                    yield transactions.enregistrer(avecNote, auteur).reference();
                }
                case VENTE_CARTE -> {
                    exiger(PermissionAgence.TRANSACTION_SAISIR);
                    yield cartes.vendre(valider(json.treeToValue(donnees, CarteService.Vente.class)), auteur).reference();
                }
                case DEPENSE -> {
                    exiger(PermissionAgence.DEPENSE_SAISIR);
                    DepenseAgenceService.Requete d = valider(json.treeToValue(donnees, DepenseAgenceService.Requete.class));
                    DepenseAgenceService.Requete avecNote = new DepenseAgenceService.Requete(d.agenceId(), d.categorieId(), d.libelle(),
                            d.montant(), d.compteId(), d.beneficiaire(), d.reference(), joindre(note, d.observations()),
                            d.recurrenteId(), d.periode());
                    yield depenses.creer(avecNote, auteur).numero();
                }
            };
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException("Saisie illisible : " + e.getOriginalMessage());
        }
    }

    private void exiger(PermissionAgence p) {
        if (!droits.courants().contains(p)) {
            throw new BusinessException("Vous n'avez pas le droit : " + p.getLibelle());
        }
    }

    private <T> T valider(T saisie) {
        Set<ConstraintViolation<T>> erreurs = validateur.validate(saisie);
        if (!erreurs.isEmpty()) {
            throw new BusinessException("Saisie incomplete : " + erreurs.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(", ")));
        }
        return saisie;
    }

    private static String joindre(String note, String observations) {
        String texte = observations == null || observations.isBlank() ? note : note + " - " + observations.trim();
        return texte.length() > 500 ? texte.substring(0, 500) : texte;
    }

    private OperationHorsLigne trace(OperationEnvoyee op, UtilisateurPrincipal auteur, OperationHorsLigne.Statut statut, String motif) {
        OperationHorsLigne o = new OperationHorsLigne();
        o.setCle(op.cle());
        o.setType(op.type());
        JsonNode agence = op.donnees().get("agenceId");
        o.setAgenceId(agence != null && agence.canConvertToLong() ? agence.asLong() : null);
        o.setAuteurId(auteur.getId());
        o.setAuteurNom(auteur.getNomComplet());
        o.setSaisieLe(op.saisieLe());
        o.setRecueLe(Instant.now());
        o.setStatut(statut);
        // Resume construit par le serveur a partir de la saisie : le superviseur voit ce qui sera
        // vraiment rejoue, jamais un texte ecrit par l'emetteur.
        String resume = resumeServeur(op.type(), op.donnees());
        o.setResume(resume.length() > 255 ? resume.substring(0, 255) : resume);
        o.setDonnees(op.donnees().toString());
        o.setMotif(motif == null ? null : motif.length() > 1000 ? motif.substring(0, 1000) : motif);
        return o;
    }

    static String resumeServeur(OperationHorsLigne.Type type, JsonNode d) {
        StringBuilder r = new StringBuilder(switch (type) {
            case TRANSACTION -> "Transaction";
            case VENTE_CARTE -> "Vente de cartes";
            case DEPENSE -> "Depense";
        });
        ajouter(r, "montant", d.path("montant"), " F");
        ajouter(r, "commission", d.path("commission"), " F");
        ajouter(r, "compte", d.path("compteId"), null);
        ajouter(r, "libelle", d.path("libelle"), null);
        ajouter(r, "beneficiaire", d.path("beneficiaire"), null);
        ajouter(r, "client", d.path("clientNom"), null);
        return r.toString();
    }

    private static void ajouter(StringBuilder r, String nom, JsonNode valeur, String unite) {
        if (valeur.isMissingNode() || valeur.isNull()) {
            return;
        }
        String texte = valeur.asText().replaceAll("\\p{Cntrl}", " ").trim();
        if (texte.isEmpty()) {
            return;
        }
        r.append(" · ").append(nom).append(" ").append(texte.length() > 60 ? texte.substring(0, 60) : texte)
                .append(unite == null ? "" : unite);
    }

    private Resultat resultat(OperationHorsLigne o) {
        String message = switch (o.getStatut()) {
            case ENREGISTREE -> "Enregistree";
            case CONFLIT -> o.getMotif();
            case REPRISE -> "Reprise par " + o.getTraiteeParNom();
            case ABANDONNEE -> "Abandonnee par " + o.getTraiteeParNom() + " : " + o.getNoteTraitement();
        };
        return new Resultat(o.getCle(), o.getStatut(), o.getReference(), message);
    }

    // ---------------------------------------------------------------- conflits

    /** Conflits a traiter dans les agences visibles. */
    @Transactional(readOnly = true)
    public List<ConflitDto> conflits() {
        List<Agence> visibles = perimetre.visibles();
        List<Long> ids = visibles.stream().map(Agence::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findByStatutAndAgenceIdInOrderBySaisieLeDesc(OperationHorsLigne.Statut.CONFLIT, ids).stream()
                .map(o -> versDto(o, visibles)).toList();
    }

    @Transactional(readOnly = true)
    public long nombreConflits() {
        List<Long> ids = perimetre.visibles().stream().map(Agence::getId).toList();
        return ids.isEmpty() ? 0 : repository.countByStatutAndAgenceIdIn(OperationHorsLigne.Statut.CONFLIT, ids);
    }

    /**
     * Rejoue l'operation une fois la cause levee (float approvisionne, caisse
     * rouverte...). Elle est enregistree au nom du superviseur qui la reprend,
     * sur sa caisse (la caisse generale s'il n'en a pas).
     */
    @Transactional
    public ConflitDto reprendre(Long id, UtilisateurPrincipal superviseur) {
        OperationHorsLigne o = conflit(id);
        try {
            // Reprise au nom du superviseur : les choix sensibles de l'emetteur ne sont pas repris.
            // Commission : celle du bareme ; depense : le compte par defaut du superviseur.
            com.fasterxml.jackson.databind.node.ObjectNode donnees =
                    (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(o.getDonnees());
            if (o.getType() == OperationHorsLigne.Type.TRANSACTION) {
                donnees.remove("commission");
                donnees.remove("motifCommission");
            }
            if (o.getType() == OperationHorsLigne.Type.DEPENSE) {
                donnees.remove("compteId");
            }
            o.setReference(executer(o.getType(), donnees, o.getSaisieLe(), superviseur));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException("Saisie illisible");
        }
        o.setStatut(OperationHorsLigne.Statut.REPRISE);
        o.setTraiteeParNom(superviseur.getNomComplet());
        o.setTraiteeLe(Instant.now());
        audit.tracer(o.getAgenceId(), "REPRISE_HORS_LIGNE", "OPERATION_HORS_LIGNE", o.getId(), "Operation hors ligne reprise ("
                + o.getResume() + ") : " + o.getReference(), "CONFLIT", "REPRISE");
        return versDto(o, perimetre.visibles());
    }

    /** Regle le conflit hors du logiciel (argent rendu au client, operation refaite autrement...). */
    @Transactional
    public ConflitDto abandonner(Long id, Abandon a, UtilisateurPrincipal superviseur) {
        OperationHorsLigne o = conflit(id);
        o.setStatut(OperationHorsLigne.Statut.ABANDONNEE);
        o.setTraiteeParNom(superviseur.getNomComplet());
        o.setTraiteeLe(Instant.now());
        o.setNoteTraitement(a.note().trim());
        audit.tracer(o.getAgenceId(), "ABANDON_HORS_LIGNE", "OPERATION_HORS_LIGNE", o.getId(), "Operation hors ligne abandonnee ("
                + o.getResume() + ") : " + a.note().trim(), "CONFLIT", "ABANDONNEE");
        return versDto(o, perimetre.visibles());
    }

    private OperationHorsLigne conflit(Long id) {
        OperationHorsLigne o = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Operation hors ligne", id));
        if (o.getAgenceId() != null) {
            perimetre.exiger(o.getAgenceId());
        }
        if (o.getStatut() != OperationHorsLigne.Statut.CONFLIT) {
            throw new BusinessException("Ce conflit est deja traite");
        }
        return o;
    }

    private static ConflitDto versDto(OperationHorsLigne o, List<Agence> visibles) {
        String agence = visibles.stream().filter(a -> a.getId().equals(o.getAgenceId())).map(Agence::getNom).findFirst().orElse(null);
        return new ConflitDto(o.getId(), o.getCle(), o.getType(), o.getAgenceId(), agence, o.getAuteurNom(), o.getSaisieLe(),
                o.getRecueLe(), o.getResume(), o.getMotif(), o.getStatut(), o.getReference(), o.getTraiteeParNom(), o.getTraiteeLe(),
                o.getNoteTraitement());
    }
}
