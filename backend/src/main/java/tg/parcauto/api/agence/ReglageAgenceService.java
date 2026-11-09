package tg.parcauto.api.agence;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.security.Role;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Reglages communs aux agences, droits des profils et droits de l'utilisateur connecte. */
@Service
@RequiredArgsConstructor
@Transactional
public class ReglageAgenceService {

    private final ReglageAgenceRepository reglages;
    private final tg.parcauto.api.plateforme.entreprise.EntrepriseRepository entreprises;
    private final DroitAgenceRepository droits;
    private final InitialisationAgence initialisation;
    private final DroitsAgence droitsCourants;
    private final PerimetreAgence perimetre;
    private final AuditAgenceService audit;

    public record Reglages(@NotNull @Min(0) @Max(23) Integer heureBascule,
                           @NotNull @PositiveOrZero @Max(1440) Integer delaiDoublonMinutes,
                           @NotNull @Positive Long montantMaxTransaction,
                           @NotNull @PositiveOrZero Long seuilValidationMouvement,
                           @NotNull @PositiveOrZero Long plafondDepenseCaissier,
                           @Min(0) @Max(90) Integer joursAlerteEcheance,
                           @Min(0) @Max(365) Integer joursAlerteCommission,
                           /** Absent : inchange. */
                           Boolean validationDepensesCaisse,
                           /** Minutes sans geste avant le verrouillage de l'ecran du caissier ; absent : inchange. */
                           @Min(1) @Max(120) Integer minutesVerrouCaisse) {
    }

    public record Permission(String code, String libelle, String groupe) {
    }

    public record ProfilDroits(String role, String libelle, List<String> permissions) {
    }

    public record Droits(List<Permission> permissions, List<ProfilDroits> profils) {
    }

    public record AgenceVisible(Long id, String code, String nom, boolean actif) {
    }

    /** Ce que l'utilisateur connecte peut faire et les agences qu'il voit. */
    public record Moi(boolean administrateur, List<String> permissions, List<AgenceVisible> agences,
                      /** Plusieurs agences autorisees par le super-administrateur ; sinon une seule agence ouverte. */
                      boolean agencesMultiples) {
    }

    public Reglages lire() {
        return versDto(initialisation.assurer());
    }

    public Reglages enregistrer(Reglages r) {
        ReglageAgence reglage = initialisation.assurer();
        Reglages avant = versDto(reglage);
        reglage.setHeureBascule(r.heureBascule());
        reglage.setDelaiDoublonMinutes(r.delaiDoublonMinutes());
        reglage.setMontantMaxTransaction(r.montantMaxTransaction());
        reglage.setSeuilValidationMouvement(r.seuilValidationMouvement());
        reglage.setPlafondDepenseCaissier(r.plafondDepenseCaissier());
        if (r.joursAlerteEcheance() != null) {
            reglage.setJoursAlerteEcheance(r.joursAlerteEcheance());
        }
        if (r.joursAlerteCommission() != null) {
            reglage.setJoursAlerteCommission(r.joursAlerteCommission());
        }
        if (r.validationDepensesCaisse() != null) {
            reglage.setValidationDepensesCaisse(r.validationDepensesCaisse());
        }
        if (r.minutesVerrouCaisse() != null) {
            reglage.setMinutesVerrouCaisse(r.minutesVerrouCaisse());
        }
        Reglages apres = versDto(reglage);
        if (!avant.equals(apres)) {
            audit.tracer(null, "MODIFICATION", "REGLAGES", reglage.getId(), "Reglages des agences modifies", avant, apres);
        }
        return apres;
    }

    public Droits droits() {
        initialisation.assurer();
        Map<Role, List<String>> parRole = droits.findAll().stream()
                .collect(Collectors.groupingBy(DroitAgence::getRole,
                        Collectors.mapping(d -> d.getPermission().name(), Collectors.toList())));
        return new Droits(
                Arrays.stream(PermissionAgence.values())
                        .map(p -> new Permission(p.name(), p.getLibelle(), p.getGroupe())).toList(),
                PermissionAgence.PROFILS_REGLABLES.stream()
                        .map(r -> new ProfilDroits(r.name(), r.getLibelle(), parRole.getOrDefault(r, List.of()).stream().sorted().toList()))
                        .toList());
    }

    /** Remplace les droits des profils reglables ; ceux de l'administrateur ne se retirent pas. */
    public Droits enregistrerDroits(List<ProfilDroits> profils) {
        initialisation.assurer();
        Droits avant = droits();
        for (ProfilDroits profil : profils) {
            Role role = Role.valueOf(profil.role());
            if (!PermissionAgence.PROFILS_REGLABLES.contains(role)) {
                continue;
            }
            Set<PermissionAgence> voulues = profil.permissions() == null ? EnumSet.noneOf(PermissionAgence.class)
                    : profil.permissions().stream().map(PermissionAgence::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionAgence.class)));
            List<DroitAgence> actuels = droits.findByRoleIn(Set.of(role));
            actuels.stream().filter(d -> !voulues.contains(d.getPermission())).forEach(droits::delete);
            Set<PermissionAgence> deja = actuels.stream().map(DroitAgence::getPermission)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionAgence.class)));
            voulues.stream().filter(p -> !deja.contains(p)).forEach(p -> droits.save(new DroitAgence(role, p)));
        }
        droits.flush();
        Droits apres = droits();
        audit.tracer(null, "MODIFICATION", "DROITS", null, "Droits des profils d'agence modifies",
                avant.profils(), apres.profils());
        return apres;
    }

    @Transactional(readOnly = true)
    public Moi moi() {
        return new Moi(DroitsAgence.estAdministrateur(),
                droitsCourants.courants().stream().map(Enum::name).toList(),
                perimetre.visibles().stream().map(a -> new AgenceVisible(a.getId(), a.getCode(), a.getNom(), a.isActif()))
                        .toList(),
                entreprises.findById(tg.parcauto.api.plateforme.tenant.ContexteEntreprise.exiger())
                        .map(tg.parcauto.api.plateforme.entreprise.Entreprise::isAgencesMultiples).orElse(false));
    }

    private static Reglages versDto(ReglageAgence r) {
        return new Reglages(r.getHeureBascule(), r.getDelaiDoublonMinutes(), r.getMontantMaxTransaction(),
                r.getSeuilValidationMouvement(), r.getPlafondDepenseCaissier(), r.getJoursAlerteEcheance(),
                r.getJoursAlerteCommission(), r.isValidationDepensesCaisse(), r.getMinutesVerrouCaisse());
    }
}
