package tg.parcauto.api.presence;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;
import tg.parcauto.api.security.UtilisateurRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Reglages du module de presence : employes et PIN, lieux, bornes et horaire. */
@Service
@RequiredArgsConstructor
@Transactional
public class PresenceService {

    private static final Duration VALIDITE_CODE_APPAIRAGE = Duration.ofMinutes(15);

    private final EmployePresenceRepository employes;
    private final LieuPresenceRepository lieux;
    private final BornePresenceRepository bornes;
    private final ReglagePresenceRepository reglages;
    private final UtilisateurRepository utilisateurs;
    private final NumerotationService numerotation;
    private final SecuritePresence securite;

    // ------------------------------------------------------------- perimetre

    /**
     * Lieux que le compte suit : nul pour l'administrateur et le gerant, qui
     * voient tout ; sinon les lieux dont il est responsable.
     */
    @Transactional(readOnly = true)
    public Set<Long> perimetre(UtilisateurPrincipal p) {
        if (aRole(p, Role.ADMIN) || aRole(p, Role.GERANT)) {
            return null;
        }
        return lieux.findAll().stream().filter(l -> l.getResponsableIds().contains(p.getId()))
                .map(LieuPresence::getId).collect(Collectors.toSet());
    }

    static boolean visible(EmployePresence e, Set<Long> perimetre) {
        return perimetre == null || e.getLieux().stream().anyMatch(l -> perimetre.contains(l.getId()));
    }

    void exigerVisible(EmployePresence e, Set<Long> perimetre) {
        if (!visible(e, perimetre)) {
            throw new BusinessException("Cet employe n'est rattache a aucun de vos lieux");
        }
    }

    static boolean aRole(UtilisateurPrincipal p, Role role) {
        return p.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role.authority()));
    }

    // ------------------------------------------------------------- employes

    public record EmployeRequete(
            @NotBlank @Size(max = 80) String nom,
            @Size(max = 80) String prenom,
            @Size(max = 30) String telephone,
            @Email @Size(max = 150) String email,
            @Size(max = 80) String fonction,
            @Size(max = 80) String service,
            LocalDate dateEmbauche,
            Set<Long> lieuIds,
            /** Compte utilisateur converti en employe. */
            Long utilisateurId,
            Boolean actif) {
    }

    public record LieuCourt(Long id, String nom) {
        static LieuCourt de(LieuPresence l) {
            return new LieuCourt(l.getId(), l.getNom());
        }
    }

    public record EmployeReponse(Long id, String matricule, String nom, String prenom, String nomComplet,
                                 String telephone, String email, String fonction, String service,
                                 LocalDate dateEmbauche, Long utilisateurId, String utilisateurEmail,
                                 boolean actif, List<LieuCourt> lieux) {
    }

    /** Employe enregistre, avec son PIN en clair : montre une seule fois. */
    public record EmployeAvecPin(EmployeReponse employe, String pin) {
    }

    @Transactional(readOnly = true)
    public List<EmployeReponse> listerEmployes(UtilisateurPrincipal p) {
        Set<Long> perimetre = perimetre(p);
        return employes.findAllByOrderByNomAscPrenomAsc().stream().filter(e -> visible(e, perimetre))
                .map(this::reponse).toList();
    }

    @Transactional(readOnly = true)
    public EmployeReponse employe(Long id, UtilisateurPrincipal p) {
        EmployePresence e = chargerEmploye(id);
        exigerVisible(e, perimetre(p));
        return reponse(e);
    }

    public EmployeAvecPin creerEmploye(EmployeRequete r, UtilisateurPrincipal p) {
        EmployePresence e = new EmployePresence();
        e.setMatricule(numerotation.prochainNumero("EMP"));
        appliquer(e, r, p);
        String pin = attribuerPin(e);
        employes.saveAndFlush(e);
        return new EmployeAvecPin(reponse(e), pin);
    }

    public EmployeReponse modifierEmploye(Long id, EmployeRequete r, UtilisateurPrincipal p) {
        EmployePresence e = chargerEmploye(id);
        exigerVisible(e, perimetre(p));
        appliquer(e, r, p);
        employes.flush();
        return reponse(e);
    }

    /** Nouveau PIN (oublie, divulgue) : l'ancien ne marche plus. */
    public EmployeAvecPin reinitialiserPin(Long id, UtilisateurPrincipal p) {
        EmployePresence e = chargerEmploye(id);
        exigerVisible(e, perimetre(p));
        String pin = attribuerPin(e);
        employes.flush();
        return new EmployeAvecPin(reponse(e), pin);
    }

    private void appliquer(EmployePresence e, EmployeRequete r, UtilisateurPrincipal p) {
        e.setNom(r.nom().trim());
        e.setPrenom(vide(r.prenom()));
        e.setTelephone(vide(r.telephone()));
        e.setEmail(vide(r.email()));
        e.setFonction(vide(r.fonction()));
        e.setService(vide(r.service()));
        e.setDateEmbauche(r.dateEmbauche());
        if (r.actif() != null) {
            e.setActif(r.actif());
        }
        if (r.utilisateurId() != null && !r.utilisateurId().equals(e.getUtilisateurId())) {
            utilisateurs.findById(r.utilisateurId())
                    .filter(u -> ContexteEntreprise.exiger().equals(u.getEntrepriseId()))
                    .orElseThrow(() -> new BusinessException("Le compte utilisateur choisi n'existe pas"));
            employes.findByUtilisateurId(r.utilisateurId()).filter(autre -> !autre.getId().equals(e.getId()))
                    .ifPresent(autre -> {
                        throw new BusinessException("Ce compte est deja relie a l'employe " + autre.nomComplet());
                    });
        }
        e.setUtilisateurId(r.utilisateurId());
        Set<Long> demandes = r.lieuIds() == null ? Set.of() : r.lieuIds();
        List<LieuPresence> choisis = lieux.findAllById(demandes);
        if (choisis.size() != demandes.size()) {
            throw new BusinessException("Un des lieux choisis n'existe pas");
        }
        Set<Long> perimetre = perimetre(p);
        if (perimetre != null && (choisis.isEmpty() || choisis.stream().anyMatch(l -> !perimetre.contains(l.getId())))) {
            throw new BusinessException("Rattachez l'employe a l'un de vos lieux");
        }
        e.getLieux().clear();
        e.getLieux().addAll(choisis);
    }

    /** PIN a 6 chiffres, unique dans l'entreprise. */
    private String attribuerPin(EmployePresence e) {
        Long entreprise = ContexteEntreprise.exiger();
        for (int essai = 0; essai < 50; essai++) {
            String pin = securite.nouveauPin();
            String empreinte = securite.empreintePin(entreprise, pin);
            if (!employes.existsByPinEmpreinte(empreinte)) {
                e.setPinEmpreinte(empreinte);
                return pin;
            }
        }
        throw new IllegalStateException("Aucun PIN libre trouve");
    }

    /** Comptes utilisateurs pas encore relies a un employe, pour la conversion. */
    public record CompteConvertible(Long id, String nom, String prenom, String email, String telephone, List<String> roles) {
    }

    @Transactional(readOnly = true)
    public List<CompteConvertible> comptesConvertibles() {
        Set<Long> relies = employes.findAll().stream().map(EmployePresence::getUtilisateurId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return utilisateurs.findAll().stream()
                .filter(u -> ContexteEntreprise.exiger().equals(u.getEntrepriseId()))
                .filter(u -> u.isActif() && !relies.contains(u.getId()))
                .filter(u -> u.getRoles().stream().noneMatch(r -> r == Role.SUPER_ADMIN))
                .sorted(Comparator.comparing(Utilisateur::getNom, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new CompteConvertible(u.getId(), u.getNom(), u.getPrenom(), u.getEmail(), u.getTelephone(),
                        u.getRoles().stream().map(Role::name).sorted().toList()))
                .toList();
    }

    /** Comptes qui peuvent etre responsables d'un lieu : gestionnaires de parc et superviseurs d'agence. */
    @Transactional(readOnly = true)
    public List<CompteConvertible> responsablesPossibles() {
        return utilisateurs.findAll().stream()
                .filter(u -> ContexteEntreprise.exiger().equals(u.getEntrepriseId()) && u.isActif())
                .filter(u -> u.getRoles().contains(Role.GESTIONNAIRE_PARC) || u.getRoles().contains(Role.SUPERVISEUR_AGENCE))
                .sorted(Comparator.comparing(Utilisateur::getNom, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new CompteConvertible(u.getId(), u.getNom(), u.getPrenom(), u.getEmail(), u.getTelephone(),
                        u.getRoles().stream().map(Role::name).sorted().toList()))
                .toList();
    }

    private EmployeReponse reponse(EmployePresence e) {
        String compte = e.getUtilisateurId() == null ? null
                : utilisateurs.findById(e.getUtilisateurId()).map(Utilisateur::getEmail).orElse(null);
        return new EmployeReponse(e.getId(), e.getMatricule(), e.getNom(), e.getPrenom(), e.nomComplet(),
                e.getTelephone(), e.getEmail(), e.getFonction(), e.getService(), e.getDateEmbauche(),
                e.getUtilisateurId(), compte, e.isActif(),
                e.getLieux().stream().sorted(Comparator.comparing(LieuPresence::getNom)).map(LieuCourt::de).toList());
    }

    EmployePresence chargerEmploye(Long id) {
        return employes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Employe", id));
    }

    // ------------------------------------------------------------- lieux

    public record LieuRequete(
            @NotBlank @Size(max = 80) String nom,
            @Size(max = 255) String adresse,
            @Min(-90) @Max(90) Double latitude,
            @Min(-180) @Max(180) Double longitude,
            @Min(10) @Max(5000) Integer rayonMetres,
            Set<Long> responsableIds,
            Boolean actif) {
    }

    public record LieuReponse(Long id, String nom, String adresse, Double latitude, Double longitude, int rayonMetres,
                              String jetonPublic, boolean actif, List<Long> responsableIds, long employes,
                              List<BorneReponse> bornes) {
    }

    @Transactional(readOnly = true)
    public List<LieuReponse> listerLieux(UtilisateurPrincipal p) {
        Set<Long> perimetre = perimetre(p);
        List<EmployePresence> tous = employes.findAll();
        List<BornePresence> toutesBornes = bornes.findAllByOrderByNomAsc();
        return lieux.findAllByOrderByNomAsc().stream()
                .filter(l -> perimetre == null || perimetre.contains(l.getId()))
                .map(l -> lieuReponse(l, tous, toutesBornes)).toList();
    }

    public LieuReponse creerLieu(LieuRequete r) {
        if (lieux.existsByNomIgnoreCase(r.nom().trim())) {
            throw new BusinessException("Un lieu porte deja le nom " + r.nom().trim());
        }
        LieuPresence l = new LieuPresence();
        l.setJetonPublic(securite.jetonAleatoire());
        l.setSecretQr(securite.secretQr());
        appliquer(l, r);
        lieux.saveAndFlush(l);
        return lieuReponse(l, employes.findAll(), bornes.findAllByOrderByNomAsc());
    }

    public LieuReponse modifierLieu(Long id, LieuRequete r) {
        LieuPresence l = chargerLieu(id);
        if (lieux.existsByNomIgnoreCaseAndIdNot(r.nom().trim(), id)) {
            throw new BusinessException("Un lieu porte deja le nom " + r.nom().trim());
        }
        appliquer(l, r);
        lieux.flush();
        return lieuReponse(l, employes.findAll(), bornes.findAllByOrderByNomAsc());
    }

    /** Nouveau secret : les codes QR deja affiches ou photographies ne marchent plus. */
    public LieuReponse renouvelerSecret(Long id) {
        LieuPresence l = chargerLieu(id);
        l.setSecretQr(securite.secretQr());
        return lieuReponse(l, employes.findAll(), bornes.findAllByOrderByNomAsc());
    }

    private void appliquer(LieuPresence l, LieuRequete r) {
        l.setNom(r.nom().trim());
        l.setAdresse(vide(r.adresse()));
        if ((r.latitude() == null) != (r.longitude() == null)) {
            throw new BusinessException("Indiquez la latitude et la longitude, ou aucune des deux");
        }
        l.setLatitude(r.latitude());
        l.setLongitude(r.longitude());
        if (r.rayonMetres() != null) {
            l.setRayonMetres(r.rayonMetres());
        }
        if (r.actif() != null) {
            l.setActif(r.actif());
        }
        Set<Long> responsables = r.responsableIds() == null ? Set.of() : r.responsableIds();
        for (Long id : responsables) {
            utilisateurs.findById(id).filter(u -> ContexteEntreprise.exiger().equals(u.getEntrepriseId()))
                    .orElseThrow(() -> new BusinessException("Un des responsables choisis n'existe pas"));
        }
        l.getResponsableIds().clear();
        l.getResponsableIds().addAll(responsables);
    }

    private LieuReponse lieuReponse(LieuPresence l, List<EmployePresence> tous, List<BornePresence> toutesBornes) {
        long nombre = tous.stream().filter(e -> e.isActif() && e.getLieux().stream().anyMatch(x -> x.getId().equals(l.getId())))
                .count();
        return new LieuReponse(l.getId(), l.getNom(), l.getAdresse(), l.getLatitude(), l.getLongitude(),
                l.getRayonMetres(), l.getJetonPublic(), l.isActif(),
                l.getResponsableIds().stream().sorted().toList(), nombre,
                toutesBornes.stream().filter(b -> b.getLieu().getId().equals(l.getId())).map(this::borneReponse).toList());
    }

    LieuPresence chargerLieu(Long id) {
        return lieux.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Lieu", id));
    }

    // ------------------------------------------------------------- bornes

    public record BorneRequete(@NotNull Long lieuId, @NotBlank @Size(max = 80) String nom) {
    }

    public record BorneReponse(Long id, Long lieuId, String nom, boolean appairee, String codeAppairage,
                               Instant codeExpireLe, Instant dernierContact, boolean actif) {
    }

    /** Nouvelle borne : un code a 6 chiffres, valable 15 minutes, a saisir sur la tablette. */
    public BorneReponse creerBorne(BorneRequete r) {
        BornePresence b = new BornePresence();
        b.setLieu(chargerLieu(r.lieuId()));
        b.setNom(r.nom().trim());
        nouveauCode(b);
        bornes.saveAndFlush(b);
        return borneReponse(b);
    }

    /** Re-appairer une borne (tablette changee, jeton perdu) : l'ancienne tablette est deconnectee. */
    public BorneReponse reappairer(Long id) {
        BornePresence b = chargerBorne(id);
        b.setJetonEmpreinte(null);
        b.setAppaireeLe(null);
        nouveauCode(b);
        bornes.flush();
        return borneReponse(b);
    }

    public BorneReponse basculerBorne(Long id) {
        BornePresence b = chargerBorne(id);
        b.setActif(!b.isActif());
        if (!b.isActif()) {
            b.setJetonEmpreinte(null);
            b.setAppaireeLe(null);
            b.setCodeAppairage(null);
        }
        return borneReponse(b);
    }

    private void nouveauCode(BornePresence b) {
        b.setCodeAppairage(securite.codeAppairage());
        b.setCodeExpireLe(Instant.now().plus(VALIDITE_CODE_APPAIRAGE));
    }

    private BorneReponse borneReponse(BornePresence b) {
        boolean codeValable = b.getCodeAppairage() != null && b.getCodeExpireLe() != null
                && Instant.now().isBefore(b.getCodeExpireLe());
        return new BorneReponse(b.getId(), b.getLieu().getId(), b.getNom(), b.getJetonEmpreinte() != null,
                codeValable ? b.getCodeAppairage() : null, codeValable ? b.getCodeExpireLe() : null,
                b.getDernierContact(), b.isActif());
    }

    private BornePresence chargerBorne(Long id) {
        return bornes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Borne", id));
    }

    // ------------------------------------------------------------- horaire

    public record ReglageRequete(
            @NotNull LocalTime heureArrivee,
            @NotNull @Min(0) @Max(240) Integer toleranceMinutes,
            @NotNull Set<Integer> joursTravailles,
            @NotNull LocalTime heureLimite) {
    }

    public record ReglageReponse(LocalTime heureArrivee, int toleranceMinutes, Set<Integer> joursTravailles,
                                 LocalTime heureLimite) {
        static ReglageReponse de(ReglagePresence r) {
            return new ReglageReponse(r.getHeureArrivee(), r.getToleranceMinutes(), r.jours(), r.getHeureLimite());
        }
    }

    @Transactional(readOnly = true)
    public ReglageReponse reglage() {
        return ReglageReponse.de(reglageCourant());
    }

    public ReglageReponse modifierReglage(ReglageRequete r) {
        if (r.joursTravailles().isEmpty() || r.joursTravailles().stream().anyMatch(j -> j < 1 || j > 7)) {
            throw new BusinessException("Choisissez au moins un jour travaille");
        }
        if (!r.heureLimite().isAfter(r.heureArrivee())) {
            throw new BusinessException("L'heure limite doit etre apres l'heure d'arrivee");
        }
        ReglagePresence reglage = reglages.findFirstByOrderByIdAsc().orElseGet(ReglagePresence::new);
        reglage.setHeureArrivee(r.heureArrivee());
        reglage.setToleranceMinutes(r.toleranceMinutes());
        reglage.jours(new HashSet<>(r.joursTravailles()));
        reglage.setHeureLimite(r.heureLimite());
        return ReglageReponse.de(reglages.saveAndFlush(reglage));
    }

    /** Horaire enregistre, ou celui par defaut (8 h, 10 min, lundi-samedi, limite 12 h). */
    ReglagePresence reglageCourant() {
        return reglages.findFirstByOrderByIdAsc().orElseGet(ReglagePresence::new);
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
