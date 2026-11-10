package tg.parcauto.api.presence;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;

import java.time.YearMonth;

/**
 * Pointage sans compte : le telephone de l'employe (lieu designe par le code
 * QR) ou la borne appairee du lieu. L'entreprise vient du jeton de l'adresse
 * ({@link ContextePresenceInterceptor}).
 */
@RestController
@RequestMapping("/api/public/presence")
@RequiredArgsConstructor
@ModuleRequis(value = ModuleApplicatif.PRESENCE, publique = true)
@Tag(name = "Presence - pointage public")
public class PointagePublicController {

    private final PointageService service;
    private final LieuPresenceRepository lieux;
    private final BornePresenceRepository bornes;

    public record DemandeHistorique(String pin, String mois, String code) {
    }

    // ------------------------------------------------------------- telephone

    @GetMapping("/lieux/{jeton}")
    @Operation(summary = "Lieu designe par le code QR scanne, et validite du code")
    public PointageService.Accueil accueil(@PathVariable String jeton, @RequestParam(required = false) String c,
                                           HttpServletRequest requete) {
        return service.accueil(lieu(requete), c, entreprise(requete), appareil(requete));
    }

    @PostMapping("/lieux/{jeton}/pointage")
    @Operation(summary = "Pointe l'arrivee depuis le telephone : code QR, position GPS et PIN")
    public PointageService.Resultat pointer(@PathVariable String jeton, @RequestBody PointageService.PointageTelephone d,
                                            HttpServletRequest requete) {
        try {
            return service.pointerTelephone(lieu(requete), d, appareil(requete));
        } catch (org.springframework.dao.DataIntegrityViolationException doublon) {
            // Deux envois quasi simultanes (double appui) : le second trouve le premier, « deja pointe ».
            return service.pointerTelephone(lieu(requete), d, appareil(requete));
        }
    }

    @PostMapping("/lieux/{jeton}/historique")
    @Operation(summary = "L'employe consulte son propre historique avec son PIN")
    public PointageService.Historique historique(@PathVariable String jeton, @RequestBody DemandeHistorique d,
                                                 HttpServletRequest requete) {
        return service.monHistorique(lieu(requete), d.code(), d.pin(), mois(d.mois()), appareil(requete));
    }

    // ------------------------------------------------------------- borne

    @GetMapping("/bornes/{jeton}/qr")
    @Operation(summary = "Code QR du moment, affiche par la borne")
    public PointageService.QrBorne qr(@PathVariable String jeton, @RequestParam String base, HttpServletRequest requete) {
        if (!base.matches("^https?://[^\\s\"'<>]{1,200}$")) {
            throw new BusinessException("Adresse de l'application invalide");
        }
        return service.qr(borne(requete).getId(), base, entreprise(requete));
    }

    @PostMapping("/bornes/{jeton}/pointage")
    @Operation(summary = "Pointe l'arrivee sur la borne avec le PIN")
    public PointageService.Resultat pointerBorne(@PathVariable String jeton, @RequestBody PointageService.PointageBorne d,
                                                 HttpServletRequest requete) {
        try {
            return service.pointerBorne(borne(requete), d);
        } catch (org.springframework.dao.DataIntegrityViolationException doublon) {
            return service.pointerBorne(borne(requete), d);
        }
    }

    @PostMapping("/bornes/{jeton}/historique")
    @Operation(summary = "L'employe consulte son historique sur la borne avec son PIN")
    public PointageService.Historique historiqueBorne(@PathVariable String jeton, @RequestBody DemandeHistorique d,
                                                      HttpServletRequest requete) {
        BornePresence b = borne(requete);
        return service.monHistorique(d.pin(), mois(d.mois()), "borne:" + b.getId());
    }

    // ------------------------------------------------------------- outils

    private LieuPresence lieu(HttpServletRequest requete) {
        Long id = (Long) requete.getAttribute(ContextePresenceInterceptor.LIEU);
        return lieux.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lieu introuvable"));
    }

    private BornePresence borne(HttpServletRequest requete) {
        Long id = (Long) requete.getAttribute(ContextePresenceInterceptor.BORNE);
        return bornes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Borne introuvable"));
    }

    private static String entreprise(HttpServletRequest requete) {
        return (String) requete.getAttribute(ContextePresenceInterceptor.ENTREPRISE);
    }

    /**
     * Appareil qui tape les PIN : son adresse vue par le serveur. L'en-tete
     * X-Forwarded-For n'est pas lu, le client pourrait en changer a chaque essai.
     * Derriere un proxy, configurer server.forward-headers-strategy pour que
     * l'adresse reelle remonte ici.
     */
    private static String appareil(HttpServletRequest requete) {
        return "ip:" + requete.getRemoteAddr();
    }

    private static YearMonth mois(String mois) {
        try {
            return mois == null || mois.isBlank() ? null : YearMonth.parse(mois);
        } catch (RuntimeException e) {
            throw new BusinessException("Mois invalide (attendu AAAA-MM)");
        }
    }
}
