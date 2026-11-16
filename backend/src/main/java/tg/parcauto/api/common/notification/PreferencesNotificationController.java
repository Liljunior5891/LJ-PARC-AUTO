package tg.parcauto.api.common.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.audit.JournalActiviteService;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.security.Autorisations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Choix, par l'administrateur de l'entreprise, des notifications recues par
 * WhatsApp (numero de l'inscription) et par e-mail (administrateurs).
 */
@RestController
@RequestMapping("/api/notifications/preferences")
@PreAuthorize(Autorisations.ADMINISTRATION)
@RequiredArgsConstructor
@Tag(name = "Notifications - preferences")
public class PreferencesNotificationController {

    private final AlertesEntreprise alertes;
    private final NotificationService notifications;
    private final JournalActiviteService journal;
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;

    public record Ligne(TypeAlerte type, String libelle, String description, String module, boolean whatsapp,
                        boolean email, boolean emailObligatoire) {
    }

    public record Vue(String numeroWhatsapp, boolean numeroVerifie, boolean whatsappBranche, List<String> emails,
                      List<Ligne> types) {
    }

    public record Confirmation(@jakarta.validation.constraints.NotBlank
                               @jakarta.validation.constraints.Size(max = 12) String code) {
    }

    public record Choix(@NotNull TypeAlerte type, boolean whatsapp, boolean email) {
    }

    public record Enregistrement(@NotNull @Valid List<Choix> types) {
    }

    @GetMapping
    @Operation(summary = "Types de notification proposes (selon les modules actifs) et choix de l'entreprise")
    public Vue lire() {
        Long entreprise = ContexteEntreprise.exiger();
        List<ModuleApplicatif> modules = alertes.modules(entreprise);
        Map<TypeAlerte, AlertesEntreprise.Choix> choix = alertes.choix(entreprise);
        List<Ligne> lignes = choix.entrySet().stream()
                .filter(e -> e.getKey().getModule() == null || modules.contains(e.getKey().getModule()))
                .map(e -> new Ligne(e.getKey(), e.getKey().getLibelle(), e.getKey().getDescription(),
                        e.getKey().getModule() == null ? null : e.getKey().getModule().getLibelle(),
                        e.getValue().whatsapp(), e.getValue().email(), e.getKey().isEmailObligatoire()))
                .toList();
        return new Vue(alertes.numero(entreprise), alertes.numeroVerifie(entreprise) != null,
                notifications.fournisseurActif(NotificationService.Canal.WHATSAPP),
                alertes.emailsAdministrateurs(entreprise), lignes);
    }

    @PutMapping
    @Transactional
    @Operation(summary = "Enregistre les choix")
    public Vue enregistrer(@Valid @RequestBody Enregistrement e) {
        Long entreprise = ContexteEntreprise.exiger();
        Map<TypeAlerte, AlertesEntreprise.Choix> avant = alertes.choix(entreprise);
        Map<String, String> ancien = new LinkedHashMap<>();
        Map<String, String> nouveau = new LinkedHashMap<>();
        String auteur = tg.parcauto.api.common.audit.EcritureJournal.contexte().auteurNom();
        for (Choix c : e.types()) {
            AlertesEntreprise.Choix precedent = avant.get(c.type());
            boolean email = c.email() || c.type().isEmailObligatoire();
            if (precedent.whatsapp() == c.whatsapp() && precedent.email() == email) {
                continue;
            }
            alertes.choisir(entreprise, c.type(), new AlertesEntreprise.Choix(c.whatsapp(), email), auteur);
            ancien.put(c.type().getLibelle(), resume(precedent.whatsapp(), precedent.email()));
            nouveau.put(c.type().getLibelle(), resume(c.whatsapp(), email));
        }
        if (!nouveau.isEmpty()) {
            journal.administration(entreprise, "MODIFICATION", "PreferenceNotification", "Notifications recues", null,
                    "Choix des notifications modifie (" + String.join(", ", nouveau.keySet()) + ")", ancien, nouveau);
        }
        return lire();
    }

    @PostMapping("/essai")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Envoie un message d'essai sur WhatsApp au numero de l'entreprise (une fois par minute)")
    public void essai() {
        Long entreprise = ContexteEntreprise.exiger();
        String numero = alertes.numeroVerifie(entreprise);
        if (numero == null) {
            throw new BusinessException("Confirmez d'abord le numero WhatsApp avec le code recu.");
        }
        limiteur.exiger("whatsapp-essai:" + entreprise, 5, java.time.Duration.ofDays(1),
                "Cinq messages d'essai par jour au plus : reessayez demain.");
        if (!alertes.marquer(entreprise, "essai-" + (System.currentTimeMillis() / 60_000))) {
            throw new BusinessException("Un message d'essai vient de partir : patientez une minute.");
        }
        Long id = notifications.whatsapp(entreprise, numero, "*Message d'essai*\nLes notifications WhatsApp de Nova"
                + " arrivent bien sur ce numero.\n\n_Nova_", "ESSAI");
        if (id == null) {
            throw new BusinessException("Le numero " + numero + " n'est pas valide pour WhatsApp.");
        }
    }

    @PostMapping("/verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Envoie un code de confirmation sur WhatsApp au numero de l'entreprise")
    public Map<String, String> demanderCode() {
        Long entreprise = ContexteEntreprise.exiger();
        // Le code part vers un numero non encore confirme : peu d'envois, pour ne harceler personne.
        limiteur.exiger("whatsapp-code-heure:" + entreprise, 3, java.time.Duration.ofHours(1),
                "Trois codes par heure au plus : patientez avant d'en demander un autre.");
        limiteur.exiger("whatsapp-code-jour:" + entreprise, 6, java.time.Duration.ofDays(1),
                "Six codes par jour au plus : reessayez demain.");
        String numero = alertes.demanderCode(entreprise);
        return Map.of("numero", numero);
    }

    @PostMapping("/verification/confirmation")
    @Operation(summary = "Valide le code recu : le numero recoit desormais les notifications WhatsApp")
    public Vue confirmerCode(@Valid @RequestBody Confirmation c) {
        Long entreprise = ContexteEntreprise.exiger();
        String auteur = tg.parcauto.api.common.audit.EcritureJournal.contexte().auteurNom();
        alertes.confirmerCode(entreprise, c.code(), auteur);
        journal.administration(entreprise, "MODIFICATION", "PreferenceNotification", "Numero WhatsApp", null,
                "Numero WhatsApp des notifications confirme (" + NotificationService.masquer(alertes.numeroVerifie(entreprise)) + ")",
                null, null);
        return lire();
    }

    private static String resume(boolean whatsapp, boolean email) {
        if (whatsapp && email) {
            return "WhatsApp et e-mail";
        }
        return whatsapp ? "WhatsApp" : email ? "E-mail" : "Aucun";
    }
}
