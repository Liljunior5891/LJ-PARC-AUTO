package tg.parcauto.api.finance.depense;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyse de la consommation de carburant, chapitre 18 du cahier des charges.
 * <p>
 * La consommation est calculee « plein a plein » : la distance separant deux
 * pleins successifs est rapportee au volume du second plein. Le premier plein
 * de la periode sert donc uniquement de reference et n'a pas de consommation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarburantService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final DepenseRepository repository;
    private final CamionService camionService;

    public CarburantDto.Synthese synthese(Long camionId, LocalDate debut, LocalDate fin) {
        Camion camion = camionService.charger(camionId);
        List<Depense> pleins = repository.pleinsDuCamion(camionId, debut, fin);

        List<CarburantDto.Plein> lignes = new ArrayList<>();
        BigDecimal totalLitres = BigDecimal.ZERO;
        BigDecimal totalMontant = BigDecimal.ZERO;
        long distanceCouverte = 0L;
        BigDecimal litresAvecDistance = BigDecimal.ZERO;
        BigDecimal montantAvecDistance = BigDecimal.ZERO;

        Depense precedent = null;
        for (Depense plein : pleins) {
            totalLitres = totalLitres.add(plein.getQuantiteLitres());
            totalMontant = totalMontant.add(plein.getMontant());

            Long distance = null;
            BigDecimal consommation = null;
            if (precedent != null) {
                distance = plein.getKilometrage() - precedent.getKilometrage();
                if (distance > 0 && plein.getQuantiteLitres().signum() > 0) {
                    // Litres consommes pour 100 km parcourus depuis le plein precedent.
                    consommation = plein.getQuantiteLitres()
                            .multiply(CENT)
                            .divide(BigDecimal.valueOf(distance), 2, RoundingMode.HALF_UP);
                    distanceCouverte += distance;
                    litresAvecDistance = litresAvecDistance.add(plein.getQuantiteLitres());
                    montantAvecDistance = montantAvecDistance.add(plein.getMontant());
                }
            }

            lignes.add(new CarburantDto.Plein(
                    plein.getId(), plein.getNumero(), plein.getDateDepense(), plein.getStation(),
                    plein.getKilometrage(), distance, plein.getQuantiteLitres(),
                    plein.prixLitreCalcule(), plein.getMontant(), consommation,
                    plein.getChauffeur() == null ? null : plein.getChauffeur().nomComplet()));
            precedent = plein;
        }

        BigDecimal consommationMoyenne = distanceCouverte > 0
                ? litresAvecDistance.multiply(CENT)
                        .divide(BigDecimal.valueOf(distanceCouverte), 2, RoundingMode.HALF_UP)
                : null;
        // Cout au km : seulement les pleins qui ont des kilometres en face (pas le premier de la periode).
        BigDecimal coutParKm = distanceCouverte > 0
                ? montantAvecDistance.divide(BigDecimal.valueOf(distanceCouverte), 2, RoundingMode.HALF_UP)
                : null;
        BigDecimal prixMoyenLitre = totalLitres.signum() > 0
                ? totalMontant.divide(totalLitres, 2, RoundingMode.HALF_UP)
                : null;

        return new CarburantDto.Synthese(
                camion.getId(), camion.getImmatriculation(), debut, fin,
                lignes.size(), totalLitres.setScale(2, RoundingMode.HALF_UP),
                totalMontant.setScale(2, RoundingMode.HALF_UP),
                distanceCouverte, consommationMoyenne, coutParKm, prixMoyenLitre, lignes);
    }
}
