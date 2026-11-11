package tg.parcauto.api.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.stereotype.Component;
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.common.EntiteAuditee;
import tg.parcauto.api.plateforme.entreprise.Entreprise;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.security.Utilisateur;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Journal d'audit automatique de tous les modules (hors Agences, qui trace
 * lui-meme chaque operation) : chaque creation, modification et suppression
 * d'une donnee est notee une fois la transaction validee, avec l'auteur, son
 * adresse IP et les valeurs avant / apres des champs changes.
 * <p>
 * Les donnees techniques ou tres frequentes (positions GPS, lignes de vente,
 * mouvements de stock deja traces...) et les champs sans interet (version,
 * dates techniques, niveaux de stock recalcules) sont ignores ; les secrets
 * (mots de passe, PIN) ne sont jamais recopies.
 */
@Component
@RequiredArgsConstructor
public class EcouteurAudit implements PostCommitInsertEventListener, PostCommitUpdateEventListener, PostCommitDeleteEventListener {

    private static final String RACINE = "tg.parcauto.api.";

    /** Entites non tracees : techniques, recalculees ou deja tracees ailleurs. */
    private static final Set<String> ENTITES_IGNOREES = Set.of(
            "PositionGps", "AlerteGps", "StockLieu", "MouvementStock", "JournalActivite", "OffrePhoto");

    /** Champs ignores dans les comparaisons. */
    private static final Set<String> CHAMPS_IGNORES = Set.of(
            "version", "creeLe", "creePar", "modifieLe", "modifiePar", "entrepriseId", "stockActuel", "stockReserve",
            // Signal de vie des bornes de presence, toutes les 30 secondes : du bruit, pas une action.
            "dernierContact");

    /** Champs secrets : on note qu'ils ont change, jamais leur valeur. */
    private static final Set<String> CHAMPS_SECRETS = Set.of("motDePasse", "pinHash", "pin", "token", "secret", "cleApi",
            // Secret des QR de presence, jetons des liens de suivi en ligne, appairage des bornes.
            "secretQr", "jeton", "jetonEmpreinte", "codeAppairage", "pinEmpreinte");

    /** Noms lisibles des principales donnees ; les autres sont deduits du nom de la classe. */
    private static final Map<String, String> LIBELLES = Map.ofEntries(
            Map.entry("Camion", "Vehicule"), Map.entry("MarqueCamion", "Marque de vehicule"),
            Map.entry("TypeCamion", "Type de vehicule"), Map.entry("CouleurCamion", "Couleur de vehicule"),
            Map.entry("Chauffeur", "Chauffeur"), Map.entry("Affectation", "Affectation"), Map.entry("Document", "Document"),
            Map.entry("Client", "Client"), Map.entry("Livraison", "Livraison"), Map.entry("Location", "Location"),
            Map.entry("Mission", "Mission"), Map.entry("Depense", "Depense"), Map.entry("Facture", "Facture"),
            Map.entry("Paiement", "Paiement"), Map.entry("Recette", "Recette"), Map.entry("ZoneGeofence", "Zone GPS"),
            Map.entry("Entretien", "Entretien"), Map.entry("Incident", "Incident"), Map.entry("Pneu", "Pneu"),
            Map.entry("Boutique", "Boutique en ligne"), Map.entry("CommandeEnLigne", "Commande en ligne"),
            Map.entry("DemandeLocation", "Demande de location"), Map.entry("Offre", "Offre en ligne"),
            Map.entry("Societe", "Societe"), Map.entry("Entreprise", "Entreprise"), Map.entry("EntrepriseModule", "Module"),
            Map.entry("Utilisateur", "Utilisateur"), Map.entry("CommandeFournisseur", "Commande fournisseur"),
            Map.entry("AssociePart", "Part d'associe"), Map.entry("Caisse", "Caisse"), Map.entry("MouvementCaisse", "Mouvement de caisse"),
            Map.entry("SessionCaisse", "Session de caisse"), Map.entry("Article", "Produit"), Map.entry("Categorie", "Categorie"),
            Map.entry("Conditionnement", "Conditionnement"), Map.entry("CategorieDepensePdv", "Categorie de depense"),
            Map.entry("DepensePdv", "Depense"), Map.entry("DepenseRecurrente", "Depense recurrente"),
            Map.entry("Fournisseur", "Fournisseur"), Map.entry("ReglementFournisseur", "Reglement fournisseur"),
            Map.entry("BonRetrait", "Bon de retrait"), Map.entry("LieuStock", "Magasin / entrepot"),
            Map.entry("RemiseHorsLigne", "Remise hors ligne"), Map.entry("Transfert", "Transfert de stock"),
            Map.entry("BonLivraison", "Bon de livraison"), Map.entry("ClientPdv", "Client"), Map.entry("Devis", "Devis"),
            Map.entry("ReglageMateriaux", "Reglages materiaux"), Map.entry("ReglementClient", "Reglement client"),
            Map.entry("SoldeReste", "Solde du reste a livrer"), Map.entry("ReglagePdv", "Reglages du point de vente"),
            Map.entry("Commande", "Commande (restaurant)"), Map.entry("EcranPreparation", "Ecran de preparation"),
            Map.entry("ReglageRestauration", "Reglages restauration"), Map.entry("Salle", "Salle"),
            Map.entry("TableRestaurant", "Table"), Map.entry("DegagementCoffre", "Degagement au coffre"),
            Map.entry("RemiseCaisse", "Remise de caisse"), Map.entry("Vente", "Vente"),
            Map.entry("Abonnement", "Abonnement"), Map.entry("PaiementAbonnement", "Paiement d'abonnement"),
            Map.entry("Formule", "Formule d'abonnement"), Map.entry("ParametresAbonnement", "Reglages des abonnements"));

    /** Proprietes qui nomment une donnee, par ordre de preference. */
    private static final List<String> PROPRIETES_NOM = List.of(
            "numero", "reference", "immatriculation", "code", "nom", "libelle", "email", "titre");

    private final EntityManagerFactory emf;
    private final EcritureJournal ecriture;
    private final ObjectMapper json;

    @PostConstruct
    void enregistrer() {
        EventListenerRegistry registre = emf.unwrap(SessionFactoryImplementor.class).getServiceRegistry()
                .getService(EventListenerRegistry.class);
        registre.appendListeners(EventType.POST_COMMIT_INSERT, this);
        registre.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        registre.appendListeners(EventType.POST_COMMIT_DELETE, this);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return tracee(persister.getMappedClass());
    }

    // ------------------------------------------------------------- creation

    @Override
    public void onPostInsert(PostInsertEvent e) {
        Object entite = e.getEntity();
        if (!tracee(entite.getClass())) {
            return;
        }
        Map<String, String> apres = valeurs(e.getPersister().getPropertyNames(), e.getState(), null);
        tracer(entite, idDe(e.getId()), "CREATION", "cree", null, apres);
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent e) {
        // Transaction annulee : rien n'a ete cree.
    }

    // --------------------------------------------------------- modification

    @Override
    public void onPostUpdate(PostUpdateEvent e) {
        Object entite = e.getEntity();
        Object[] ancien = e.getOldState();
        if (!tracee(entite.getClass()) || ancien == null) {
            return;
        }
        String[] noms = e.getPersister().getPropertyNames();
        Object[] nouveau = e.getState();
        Map<String, String> avant = new LinkedHashMap<>();
        Map<String, String> apres = new LinkedHashMap<>();
        for (int i = 0; i < noms.length; i++) {
            if (CHAMPS_IGNORES.contains(noms[i]) || estCollection(nouveau[i]) || estCollection(ancien[i])) {
                continue;
            }
            String a = valeur(noms[i], ancien[i]);
            String b = valeur(noms[i], nouveau[i]);
            if (!java.util.Objects.equals(a, b)) {
                avant.put(noms[i], a);
                apres.put(noms[i], b);
            }
        }
        if (apres.isEmpty()) {
            return;
        }
        List<String> changements = new ArrayList<>();
        for (String champ : apres.keySet()) {
            if (changements.size() == 3) {
                changements.add("...");
                break;
            }
            changements.add(champLisible(champ) + " : " + court(avant.get(champ)) + " → " + court(apres.get(champ)));
        }
        tracer(entite, idDe(e.getId()), "MODIFICATION", "modifie (" + String.join(", ", changements) + ")", avant, apres);
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent e) {
        // Transaction annulee : rien n'a change.
    }

    // ----------------------------------------------------------- suppression

    @Override
    public void onPostDelete(PostDeleteEvent e) {
        Object entite = e.getEntity();
        if (!tracee(entite.getClass())) {
            return;
        }
        Map<String, String> avant = valeurs(e.getPersister().getPropertyNames(), e.getDeletedState(), null);
        tracer(entite, idDe(e.getId()), "SUPPRESSION", "supprime", avant, null);
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent e) {
        // Transaction annulee : rien n'a ete supprime.
    }

    // ---------------------------------------------------------------- outils

    private void tracer(Object entite, Long id, String action, String verbe, Map<String, String> avant, Map<String, String> apres) {
        Long entreprise = entrepriseDe(entite);
        String module = moduleDe(Hibernate.getClass(entite));
        // Sans entreprise, seules les donnees de la plateforme sont tracees ; l'ecriture les range en PLATEFORME.
        if (entreprise == null && !deLaPlateforme(entite)) {
            return;
        }
        String objet = Hibernate.getClass(entite).getSimpleName();
        String libelle = libelleObjet(objet);
        String nom = nomDe(entite, apres != null ? apres : avant);
        EcritureJournal.Contexte c = EcritureJournal.contexte();
        String description = libelle + (nom != null ? " « " + nom + " »" : id != null ? " n° " + id : "") + " " + verbe;
        ecriture.ecrire(new EcritureJournal.Evenement(entreprise, Instant.now(), c.auteurId(), c.auteurNom(),
                module, action, objet, libelle, id, description, enJson(avant), enJson(apres), c.adresseIp()));
    }

    private static boolean tracee(Class<?> classe) {
        String paquet = classe.getName();
        return paquet.startsWith(RACINE) && !paquet.startsWith(RACINE + "agence.")
                && !ENTITES_IGNOREES.contains(classe.getSimpleName()) && !classe.getSimpleName().startsWith("Ligne");
    }

    /** Module d'apres le paquet de l'entite. */
    static String moduleDe(Class<?> classe) {
        String reste = classe.getName().substring(RACINE.length());
        String paquet = reste.contains(".") ? reste.substring(0, reste.indexOf('.')) : reste;
        return switch (paquet) {
            case "parc" -> "PARC";
            case "exploitation" -> "EXPLOITATION";
            case "finance" -> "FINANCES";
            case "maintenance" -> "MAINTENANCE";
            case "gps" -> "GPS";
            case "pointdevente" -> "POINT_DE_VENTE";
            case "marketplace" -> "MARKETPLACE";
            case "presence" -> "PRESENCE";
            case "plateforme" -> reste.startsWith("plateforme.abonnement.") ? "ABONNEMENT" : "ADMINISTRATION";
            default -> "ADMINISTRATION";
        };
    }

    private static Long entrepriseDe(Object entite) {
        if (entite instanceof BaseEntity b && b.getEntrepriseId() != null) {
            return b.getEntrepriseId();
        }
        if (entite instanceof Entreprise en) {
            return en.getId();
        }
        if (entite instanceof Utilisateur u) {
            // Compte du super-admin : jamais range sous l'entreprise du contexte.
            return u.getEntrepriseId();
        }
        // Abonnement, paiement d'abonnement, module active : rattaches a leur entreprise. Formule et
        // reglages d'abonnement n'en ont pas : ils restent a la plateforme, quel que soit le contexte.
        if (Hibernate.getClass(entite).getName().startsWith(RACINE + "plateforme.")) {
            String id = lire(entite, "entrepriseId");
            return id == null ? null : Long.valueOf(id);
        }
        return ContexteEntreprise.courante().orElse(null);
    }

    /** Donnee de la plateforme, sans entreprise : formule, reglages d'abonnement, compte du super-admin. */
    private static boolean deLaPlateforme(Object entite) {
        return Hibernate.getClass(entite).getName().startsWith(RACINE + "plateforme.")
                || (entite instanceof Utilisateur u && u.getEntrepriseId() == null);
    }

    private static Long idDe(Object id) {
        return id instanceof Number n ? n.longValue() : null;
    }

    private Map<String, String> valeurs(String[] noms, Object[] etat, Map<String, String> dans) {
        Map<String, String> resultat = dans != null ? dans : new LinkedHashMap<>();
        if (etat == null) {
            return resultat;
        }
        for (int i = 0; i < noms.length; i++) {
            if (CHAMPS_IGNORES.contains(noms[i]) || estCollection(etat[i])) {
                continue;
            }
            String v = valeur(noms[i], etat[i]);
            if (v != null) {
                resultat.put(noms[i], v);
            }
        }
        return resultat;
    }

    private static boolean estCollection(Object v) {
        return v instanceof PersistentCollection<?> || v instanceof Collection<?> || v instanceof Map<?, ?>;
    }

    /** Valeur lisible d'un champ ; une donnee liee est reduite a son identifiant, sans chargement. */
    private static String valeur(String champ, Object v) {
        if (v == null) {
            return null;
        }
        if (CHAMPS_SECRETS.contains(champ)) {
            return "(masque)";
        }
        if (v instanceof HibernateProxy p) {
            return "n° " + p.getHibernateLazyInitializer().getInternalIdentifier();
        }
        if (v instanceof EntiteAuditee e) {
            return "n° " + e.getId();
        }
        if (v instanceof BigDecimal d) {
            return d.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Instant || v instanceof TemporalAccessor) {
            return v.toString();
        }
        String texte = v.toString();
        if (texte.startsWith("data:") || texte.length() > 300) {
            return "(contenu de " + texte.length() + " caracteres)";
        }
        return texte;
    }

    /** Nom usuel de la donnee (numero, reference, immatriculation, nom...), d'apres ses valeurs. */
    private static String nomDe(Object entite, Map<String, String> valeurs) {
        if (entite instanceof Utilisateur u) {
            String n = ((u.getPrenom() == null ? "" : u.getPrenom()) + " " + (u.getNom() == null ? "" : u.getNom())).trim();
            return n.isEmpty() ? u.getEmail() : n;
        }
        Map<String, String> source = valeurs != null ? valeurs : Map.of();
        for (String p : PROPRIETES_NOM) {
            String v = source.get(p);
            if (v == null) {
                v = lire(entite, p);
            }
            if (v != null && !v.isBlank() && !v.startsWith("(")) {
                if ("nom".equals(p)) {
                    String prenom = lire(entite, "prenom");
                    return prenom != null && !prenom.isBlank() ? prenom + " " + v : v;
                }
                return v;
            }
        }
        return null;
    }

    private static String lire(Object entite, String propriete) {
        try {
            Method m = entite.getClass().getMethod("get" + Character.toUpperCase(propriete.charAt(0)) + propriete.substring(1));
            Object v = m.invoke(entite);
            return v instanceof String s ? s : v instanceof Number || v instanceof Enum<?> ? v.toString() : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static String libelleObjet(String classe) {
        String connu = LIBELLES.get(classe);
        if (connu != null) {
            return connu;
        }
        String mots = classe.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return Character.toUpperCase(mots.charAt(0)) + mots.substring(1);
    }

    /** « prixVente » devient « prix vente ». */
    static String champLisible(String champ) {
        return champ.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    private static String court(String v) {
        if (v == null) {
            return "vide";
        }
        return v.length() > 40 ? v.substring(0, 39) + "…" : v;
    }

    private String enJson(Map<String, String> valeurs) {
        if (valeurs == null || valeurs.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(valeurs);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
