package tg.parcauto.api.plateforme.abonnement.paygate;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.LimiteurRequetes;

import java.time.Duration;

/**
 * Adresse de rappel a declarer dans l'espace marchand PayGate :
 * {@code https://<domaine>/api/public/paygate/abonnements}. Repond toujours 200 : un tiers
 * n'apprend rien, et seul l'etat redemande a PayGate compte.
 */
@RestController
@RequestMapping("/api/public/paygate")
@RequiredArgsConstructor
@Tag(name = "Abonnement - rappel PayGate")
public class PayGateRappelController {

    private final PaiementEnLigneService service;
    private final LimiteurRequetes limiteur;

    @PostMapping("/abonnements")
    @Operation(summary = "Rappel de PayGate apres un paiement (verifie ensuite aupres de PayGate)")
    public ResponseEntity<Void> rappel(@RequestBody(required = false) JsonNode corps, HttpServletRequest requete) {
        limiteur.exiger("paygate-rappel:" + requete.getRemoteAddr(), 120, Duration.ofMinutes(1), "Trop de rappels");
        if (corps != null && corps.hasNonNull("identifier")) {
            service.rappel(corps.get("identifier").asText());
        }
        return ResponseEntity.ok().build();
    }
}
