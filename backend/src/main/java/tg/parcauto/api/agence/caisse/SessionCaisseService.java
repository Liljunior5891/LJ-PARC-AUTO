package tg.parcauto.api.agence.caisse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.operation.TransactionAgenceRepository;
import tg.parcauto.api.agence.tresorerie.CompteAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgenceRepository;
import tg.parcauto.api.agence.tresorerie.CompteService;
import tg.parcauto.api.agence.tresorerie.Ecriture;
import tg.parcauto.api.agence.tresorerie.NatureEcriture;
import tg.parcauto.api.agence.tresorerie.RegistreService;
import tg.parcauto.api.agence.tresorerie.TypeCompte;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.pointdevente.caisse.Coupure;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;
import tg.parcauto.api.security.UtilisateurRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Caisses des caissiers d'agence, sur le modele du point de vente : le
 * superviseur ouvre la caisse en comptant le fond (pris sur la caisse
 * generale), le caissier y encaisse ses transactions, le superviseur la ferme
 * en comptant le tiroir avec le montant attendu sous les yeux. L'ecart, justifie,
 * est regularise, puis l'argent compte revient a la caisse generale : la caisse
 * du caissier repart a zero.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SessionCaisseService {

    private static final Set<Role> PROFILS_CAISSE = EnumSet.of(Role.CAISSIER_AGENCE, Role.SUPERVISEUR_AGENCE);

    /** Caisse en cours : ouverte, ou comptee et soumise en attente de validation. */
    public static final Set<SessionCaisseAgence.Statut> EN_COURS =
            EnumSet.of(SessionCaisseAgence.Statut.OUVERTE, SessionCaisseAgence.Statut.SOUMISE);

    private final SessionCaisseAgenceRepository repository;
    private final PerimetreAgence perimetre;
    private final CompteService comptes;
    private final CompteAgenceRepository compteRepository;
    private final RegistreService registre;
    private final TransactionAgenceRepository transactions;
    private final UtilisateurRepository utilisateurs;
    private final NumerotationService numerotation;
    private final AuditAgenceService audit;
    private final ObjectMapper json;

    public record LigneBillettage(@NotNull Long valeur, @NotNull @PositiveOrZero Integer quantite) {
    }

    public record Ouverture(@NotNull Long agenceId, @NotNull Long caissierId, @NotNull List<@Valid LigneBillettage> billettage) {
    }

    /**
     * Comptage du tiroir. A la validation d'une caisse soumise, un billettage
     * absent reprend le comptage du caissier.
     */
    public record Fermeture(List<@Valid LigneBillettage> billettage, @Size(max = 1000) String justification) {
    }

    public record Rejet(@jakarta.validation.constraints.NotBlank @Size(max = 500) String motif) {
    }

    public record SessionDto(Long id, String numero, Long agenceId, String agenceNom, Long caissierId, String caissierNom,
                             SessionCaisseAgence.Statut statut, Instant ouverteLe, String ouverteParNom, long fondInitial,
                             /** Solde actuel de la caisse du caissier (le montant attendu dans le tiroir). */
                             long soldeCaisse, long nombreTransactions, long encaisse, long decaisse, long commissions,
                             /** Approvisionnements et remises de la caisse, hors transactions. */
                             long autresMouvements, Instant fermeeLe, String fermeeParNom, Long montantTheorique,
                             Long montantCompte, Long ecart, String justification, List<LigneBillettage> billettageOuverture,
                             List<LigneBillettage> billettageFermeture,
                             /** Comptage soumis par le caissier, en attente de validation. */
                             Instant soumiseLe, Long montantSoumis, Long ecartSoumis, String justificationSoumise,
                             List<LigneBillettage> billettageSoumis,
                             /** Dernier rejet du comptage par le superviseur. */
                             String motifRejet, Instant rejeteeLe, String rejeteeParNom) {
    }

    /** Caissier d'une agence et l'etat de sa caisse. */
    public record CaissierDto(Long id, String nomComplet, List<String> roles, boolean actif, SessionDto session,
                              /** Session ouverte dans une autre agence. */
                              String ailleurs) {
    }

    public record EtatAgence(Long agenceId, String agenceNom, long caisseGenerale, List<CaissierDto> caissiers) {
    }

    // ----------------------------------------------------------------- lecture

    /** Caissiers rattaches a l'agence et l'etat de leur caisse. */
    @Transactional(readOnly = true)
    public EtatAgence etat(Long agenceId) {
        Agence agence = perimetre.exiger(agenceId);
        Map<Long, Utilisateur> parId = utilisateurs.findAllById(agence.getUtilisateurIds()).stream()
                .filter(u -> ContexteEntreprise.exiger().equals(u.getEntrepriseId()))
                .collect(Collectors.toMap(Utilisateur::getId, Function.identity()));
        List<CaissierDto> caissiers = new ArrayList<>();
        for (Utilisateur u : parId.values()) {
            if (u.getRoles().stream().noneMatch(PROFILS_CAISSE::contains)) {
                continue;
            }
            Optional<SessionCaisseAgence> ouverte = repository.findFirstByCaissierIdAndStatutIn(u.getId(), EN_COURS);
            SessionDto session = ouverte.filter(s -> s.getAgenceId().equals(agence.getId())).map(this::versDto).orElse(null);
            String ailleurs = ouverte.filter(s -> !s.getAgenceId().equals(agence.getId()))
                    .map(s -> "Caisse ouverte dans une autre agence (" + s.getNumero() + ")").orElse(null);
            caissiers.add(new CaissierDto(u.getId(), u.nomComplet(),
                    u.getRoles().stream().filter(PROFILS_CAISSE::contains).map(Role::getLibelle).toList(), u.isActif(), session, ailleurs));
        }
        caissiers.sort(Comparator.comparing(CaissierDto::nomComplet));
        long caisseGenerale = compteRepository.findByAgenceIdOrderByIdAsc(agence.getId()).stream()
                .filter(c -> c.getType() == TypeCompte.CAISSE_GENERALE).mapToLong(CompteAgence::getSolde).sum();
        return new EtatAgence(agence.getId(), agence.getNom(), caisseGenerale, caissiers);
    }

    /** Session ouverte de l'utilisateur dans l'agence, s'il en a une. */
    @Transactional(readOnly = true)
    public Optional<SessionCaisseAgence> ouverte(Long agenceId, Long caissierId) {
        return repository.findByCaissierIdAndStatut(caissierId, SessionCaisseAgence.Statut.OUVERTE)
                .filter(s -> s.getAgenceId().equals(agenceId));
    }

    /** Caisse en cours de l'utilisateur dans l'agence : ouverte, ou soumise en attente de validation. */
    @Transactional(readOnly = true)
    public Optional<SessionDto> maSession(Long agenceId, UtilisateurPrincipal moi) {
        return repository.findFirstByCaissierIdAndStatutIn(moi.getId(), EN_COURS)
                .filter(s -> s.getAgenceId().equals(agenceId)).map(this::versDto);
    }

    /** Message du refus d'une operation a un caissier sans caisse ouverte. */
    @Transactional(readOnly = true)
    public String motifSansCaisse(Long agenceId, String agenceNom, Long caissierId) {
        boolean soumise = repository.findFirstByCaissierIdAndStatutIn(caissierId, EnumSet.of(SessionCaisseAgence.Statut.SOUMISE))
                .filter(s -> s.getAgenceId().equals(agenceId)).isPresent();
        return soumise
                ? "Votre caisse est comptee et en attente de validation par le superviseur : aucune operation n'est possible"
                : "Votre caisse n'est pas ouverte dans l'agence " + agenceNom + " : demandez a votre superviseur de l'ouvrir";
    }

    @Transactional(readOnly = true)
    public SessionDto detail(Long id) {
        SessionCaisseAgence s = charger(id);
        perimetre.exiger(s.getAgenceId());
        return versDto(s);
    }

    @Transactional(readOnly = true)
    public Page<SessionDto> historique(Long agenceId, Pageable pageable) {
        List<Long> agences = agenceId != null ? List.of(perimetre.exiger(agenceId).getId())
                : perimetre.visibles().stream().map(Agence::getId).toList();
        return repository.findByAgenceIdInOrderByOuverteLeDesc(agences.isEmpty() ? List.of(-1L) : agences, pageable)
                .map(this::versDto);
    }

    // ------------------------------------------------------- ouverture et fermeture

    /** Ouvre la caisse du caissier : le fond compte passe de la caisse generale a sa caisse. */
    public SessionDto ouvrir(Ouverture o, UtilisateurPrincipal superviseur) {
        Agence agence = perimetre.exiger(o.agenceId());
        if (!agence.isActif()) {
            throw new BusinessException("L'agence " + agence.getNom() + " est fermee");
        }
        Utilisateur caissier = utilisateurs.findByIdAndEntrepriseId(o.caissierId(), ContexteEntreprise.exiger())
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", o.caissierId()));
        if (!caissier.isActif() || caissier.getRoles().stream().noneMatch(PROFILS_CAISSE::contains)) {
            throw new BusinessException(caissier.nomComplet() + " n'a pas de profil de caisse d'agence actif");
        }
        if (!agence.getUtilisateurIds().contains(caissier.getId())) {
            throw new BusinessException(caissier.nomComplet() + " n'est pas rattache a l'agence " + agence.getNom());
        }
        repository.findFirstByCaissierIdAndStatutIn(caissier.getId(), EN_COURS).ifPresent(s -> {
            throw new BusinessException("La caisse de " + caissier.nomComplet() + " est deja "
                    + (s.getStatut() == SessionCaisseAgence.Statut.SOUMISE ? "comptee et en attente de validation" : "ouverte")
                    + " (" + s.getNumero() + ")");
        });
        List<LigneBillettage> billettage = nettoyer(o.billettage());
        long fond = total(billettage);
        CompteAgence caisse = comptes.caisseDuCaissier(agence, caissier.getId(), caissier.nomComplet());
        CompteAgence generale = comptes.synchroniser(agence).stream()
                .filter(c -> c.getType() == TypeCompte.CAISSE_GENERALE).findFirst()
                .orElseThrow(() -> new BusinessException("L'agence n'a pas de caisse generale"));

        SessionCaisseAgence s = new SessionCaisseAgence();
        s.setAgenceId(agence.getId());
        s.setCompteId(caisse.getId());
        s.setCaissierId(caissier.getId());
        s.setCaissierNom(caissier.nomComplet());
        s.setNumero(numerotation.prochainNumero("CAG"));
        s.setOuverteLe(Instant.now());
        s.setOuverteParId(superviseur.getId());
        s.setOuverteParNom(superviseur.getNomComplet());
        s.setFondInitial(fond);
        s.setBillettageOuverture(enJson(billettage));
        s = repository.save(s);
        if (fond > 0) {
            Ecriture e = registre.enregistrer(new RegistreService.Entete(agence.getId(), NatureEcriture.MOUVEMENT,
                    "Fond de caisse de " + caissier.nomComplet() + " (" + s.getNumero() + ")", null, "SESSION_CAISSE", s.getId()),
                    List.of(RegistreService.Mouvement.de(generale.getId(), -fond), RegistreService.Mouvement.de(caisse.getId(), fond)));
            s.setEcritureOuvertureId(e.getId());
        }
        audit.tracer(agence.getId(), "OUVERTURE_CAISSE", "SESSION_CAISSE", s.getId(), "Caisse de " + caissier.nomComplet()
                + " ouverte (" + s.getNumero() + "), fond " + RegistreService.francs(fond), null, billettage);
        return versDto(s);
    }

    /**
     * Premier temps de la fermeture : le caissier compte son tiroir, le montant
     * attendu sous les yeux ; un ecart doit etre justifie. La caisse passe a
     * SOUMISE : plus aucune operation jusqu'a la decision du superviseur.
     */
    public SessionDto soumettre(Long id, Fermeture f, UtilisateurPrincipal caissier) {
        SessionCaisseAgence s = charger(id);
        Agence agence = perimetre.exiger(s.getAgenceId());
        if (!s.getCaissierId().equals(caissier.getId())) {
            throw new BusinessException("Seul " + s.getCaissierNom() + " peut soumettre le comptage de sa caisse");
        }
        if (s.getStatut() != SessionCaisseAgence.Statut.OUVERTE) {
            throw new BusinessException(s.getStatut() == SessionCaisseAgence.Statut.SOUMISE
                    ? "Le comptage de la caisse " + s.getNumero() + " est deja soumis : attendez la validation du superviseur"
                    : "La caisse " + s.getNumero() + " est deja fermee");
        }
        if (f.billettage() == null) {
            throw new BusinessException("Comptez votre tiroir coupure par coupure");
        }
        List<LigneBillettage> billettage = nettoyer(f.billettage());
        long compte = total(billettage);
        long theorique = compteRepository.findById(s.getCompteId()).map(CompteAgence::getSolde).orElse(0L);
        long ecart = compte - theorique;
        String justification = f.justification() == null || f.justification().isBlank() ? null : f.justification().trim();
        if (ecart != 0 && justification == null) {
            throw new BusinessException("Ecart de " + (ecart > 0 ? "+" : "") + RegistreService.francs(ecart)
                    + " entre le tiroir et le montant attendu (" + RegistreService.francs(theorique) + ") : justifiez-le");
        }
        s.setStatut(SessionCaisseAgence.Statut.SOUMISE);
        s.setSoumiseLe(Instant.now());
        s.setMontantSoumis(compte);
        s.setEcartSoumis(ecart);
        s.setJustificationSoumise(justification);
        s.setBillettageSoumis(enJson(billettage));
        audit.tracer(agence.getId(), "SOUMISSION_CAISSE", "SESSION_CAISSE", s.getId(), "Caisse de " + s.getCaissierNom()
                        + " comptee et soumise (" + s.getNumero() + ") : attendu " + RegistreService.francs(theorique) + ", compte "
                        + RegistreService.francs(compte) + (ecart != 0 ? ", ecart " + RegistreService.francs(ecart) : ""),
                theorique, compte);
        return versDto(s);
    }

    /** Le superviseur refuse le comptage soumis : la caisse est rouverte au caissier, qui recompte. */
    public SessionDto rejeter(Long id, Rejet r, UtilisateurPrincipal superviseur) {
        SessionCaisseAgence s = charger(id);
        Agence agence = perimetre.exiger(s.getAgenceId());
        if (s.getStatut() != SessionCaisseAgence.Statut.SOUMISE) {
            throw new BusinessException("La caisse " + s.getNumero() + " n'a pas de comptage en attente");
        }
        Long montant = s.getMontantSoumis();
        s.setStatut(SessionCaisseAgence.Statut.OUVERTE);
        s.setMotifRejet(r.motif().trim());
        s.setRejeteeLe(Instant.now());
        s.setRejeteeParNom(superviseur.getNomComplet());
        s.setSoumiseLe(null);
        s.setMontantSoumis(null);
        s.setEcartSoumis(null);
        s.setJustificationSoumise(null);
        s.setBillettageSoumis(null);
        audit.tracer(agence.getId(), "REJET_CAISSE", "SESSION_CAISSE", s.getId(), "Comptage de la caisse de " + s.getCaissierNom()
                + " rejete (" + s.getNumero() + ") : " + r.motif().trim(), montant, null);
        return versDto(s);
    }

    /**
     * Ferme la caisse (second temps, ou fermeture directe par le superviseur) :
     * le tiroir compte est compare au solde attendu ; un ecart non nul exige
     * une justification et se regularise, puis tout l'argent compte revient a
     * la caisse generale. Pour une caisse soumise, sans nouveau billettage, le
     * comptage du caissier est repris tel quel.
     */
    public SessionDto fermer(Long id, Fermeture f, UtilisateurPrincipal superviseur) {
        SessionCaisseAgence s = charger(id);
        Agence agence = perimetre.exiger(s.getAgenceId());
        if (s.getStatut() == SessionCaisseAgence.Statut.FERMEE) {
            throw new BusinessException("La caisse " + s.getNumero() + " est deja fermee");
        }
        boolean soumise = s.getStatut() == SessionCaisseAgence.Statut.SOUMISE;
        boolean repris = soumise && (f.billettage() == null || f.billettage().isEmpty());
        if (!soumise && f.billettage() == null) {
            throw new BusinessException("Comptez le tiroir coupure par coupure");
        }
        List<LigneBillettage> billettage = repris ? deJson(s.getBillettageSoumis()) : nettoyer(f.billettage());
        long compte = total(billettage);
        CompteAgence caisse = registre.verrouiller(List.of(s.getCompteId())).get(0);
        long theorique = caisse.getSolde();
        long ecart = compte - theorique;
        String saisie = f.justification() == null || f.justification().isBlank() ? null : f.justification().trim();
        String justification = saisie != null ? saisie : soumise ? s.getJustificationSoumise() : null;
        if (ecart != 0 && justification == null) {
            throw new BusinessException("Ecart de " + (ecart > 0 ? "+" : "") + RegistreService.francs(ecart)
                    + " entre le tiroir et le montant attendu (" + RegistreService.francs(theorique) + ") : justifiez-le");
        }
        CompteAgence generale = comptes.synchroniser(agence).stream()
                .filter(c -> c.getType() == TypeCompte.CAISSE_GENERALE).findFirst()
                .orElseThrow(() -> new BusinessException("L'agence n'a pas de caisse generale"));
        if (ecart != 0) {
            registre.enregistrer(new RegistreService.Entete(agence.getId(), NatureEcriture.REGULARISATION,
                    "Ecart de caisse de " + s.getCaissierNom() + " (" + s.getNumero() + ")", justification, "SESSION_CAISSE", s.getId()),
                    List.of(RegistreService.Mouvement.de(caisse.getId(), ecart)));
        }
        if (compte > 0) {
            Ecriture e = registre.enregistrer(new RegistreService.Entete(agence.getId(), NatureEcriture.MOUVEMENT,
                    "Remise de la caisse de " + s.getCaissierNom() + " (" + s.getNumero() + ")", null, "SESSION_CAISSE", s.getId()),
                    List.of(RegistreService.Mouvement.de(caisse.getId(), -compte), RegistreService.Mouvement.de(generale.getId(), compte)));
            s.setEcritureFermetureId(e.getId());
        }
        s.setStatut(SessionCaisseAgence.Statut.FERMEE);
        s.setFermeeLe(Instant.now());
        s.setFermeeParId(superviseur.getId());
        s.setFermeeParNom(superviseur.getNomComplet());
        s.setMontantTheorique(theorique);
        s.setMontantCompte(compte);
        s.setEcart(ecart);
        s.setJustification(justification);
        s.setBillettageFermeture(enJson(billettage));
        audit.tracer(agence.getId(), "FERMETURE_CAISSE", "SESSION_CAISSE", s.getId(), "Caisse de " + s.getCaissierNom()
                        + (soumise ? (repris ? " validee sur le comptage du caissier" : " validee apres recomptage") : " fermee")
                        + " (" + s.getNumero() + ") : attendu " + RegistreService.francs(theorique) + ", compte "
                        + RegistreService.francs(compte) + (ecart != 0 ? ", ecart " + RegistreService.francs(ecart) : ""),
                theorique, compte);
        return versDto(s);
    }

    // ------------------------------------------------------------------ outils

    /** Une ligne par coupure connue ; une valeur inconnue est refusee. */
    private static List<LigneBillettage> nettoyer(List<LigneBillettage> lignes) {
        Set<Long> valeurs = Coupure.VALEURS.stream().map(BigDecimal::longValue).collect(Collectors.toSet());
        List<LigneBillettage> resultat = new ArrayList<>();
        for (LigneBillettage l : lignes) {
            if (!valeurs.contains(l.valeur())) {
                throw new BusinessException("Coupure inconnue : " + l.valeur() + " F");
            }
            if (l.quantite() > 0) {
                resultat.add(l);
            }
        }
        resultat.sort(Comparator.comparing(LigneBillettage::valeur).reversed());
        return resultat;
    }

    private static long total(List<LigneBillettage> lignes) {
        return lignes.stream().mapToLong(l -> l.valeur() * l.quantite()).sum();
    }

    private String enJson(List<LigneBillettage> lignes) {
        try {
            return json.writeValueAsString(lignes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<LigneBillettage> deJson(String texte) {
        if (texte == null || texte.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(texte, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private SessionCaisseAgence charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Session de caisse", id));
    }

    private SessionDto versDto(SessionCaisseAgence s) {
        Object[] t = transactions.totauxSession(s.getId()).stream().findFirst().orElse(new Object[]{0L, 0L, 0L, 0L});
        long nombre = ((Number) t[0]).longValue();
        long encaisse = ((Number) t[1]).longValue();
        long decaisse = ((Number) t[2]).longValue();
        long commissions = ((Number) t[3]).longValue();
        long solde = s.getStatut() != SessionCaisseAgence.Statut.FERMEE
                ? compteRepository.findById(s.getCompteId()).map(CompteAgence::getSolde).orElse(0L)
                : s.getMontantTheorique() == null ? 0 : s.getMontantTheorique();
        long autres = solde - s.getFondInitial() - encaisse + decaisse;
        String agenceNom = perimetre.visibles().stream().filter(a -> a.getId().equals(s.getAgenceId())).map(Agence::getNom)
                .findFirst().orElse(null);
        return new SessionDto(s.getId(), s.getNumero(), s.getAgenceId(), agenceNom, s.getCaissierId(), s.getCaissierNom(),
                s.getStatut(), s.getOuverteLe(), s.getOuverteParNom(), s.getFondInitial(), solde, nombre, encaisse, decaisse,
                commissions, autres, s.getFermeeLe(), s.getFermeeParNom(), s.getMontantTheorique(), s.getMontantCompte(),
                s.getEcart(), s.getJustification(), deJson(s.getBillettageOuverture()), deJson(s.getBillettageFermeture()),
                s.getSoumiseLe(), s.getMontantSoumis(), s.getEcartSoumis(), s.getJustificationSoumise(), deJson(s.getBillettageSoumis()),
                s.getMotifRejet(), s.getRejeteeLe(), s.getRejeteeParNom());
    }
}
