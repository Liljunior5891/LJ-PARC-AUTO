package tg.parcauto.api.finance.rentabilite;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.exploitation.location.Location;
import tg.parcauto.api.exploitation.location.LocationRepository;
import tg.parcauto.api.exploitation.mission.Mission;
import tg.parcauto.api.exploitation.mission.MissionRepository;
import tg.parcauto.api.finance.depense.DepenseRepository;
import tg.parcauto.api.finance.depense.DepenseService;
import tg.parcauto.api.finance.recette.RecetteRepository;
import tg.parcauto.api.finance.recette.RecetteService;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionRepository;
import tg.parcauto.api.parc.camion.CamionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calcul de rentabilite, chapitres 7, 29, 30 et 31 du cahier des charges.
 * <p>
 * Regle unique : <b>resultat = recettes - depenses</b>, applique par camion puis
 * agrege au parc. Les depenses refacturees au client sont exclues (voir
 * {@code DepenseRepository}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentabiliteService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final RecetteRepository recetteRepository;
    private final DepenseRepository depenseRepository;
    private final MissionRepository missionRepository;
    private final LocationRepository locationRepository;
    private final CamionRepository camionRepository;
    private final CamionService camionService;
    private final RecetteService recetteService;
    private final DepenseService depenseService;

    /** Rentabilite globale du parc et comparaison des camions. */
    public RentabiliteDto.Global global(LocalDate debut, LocalDate fin) {
        Map<Long, BigDecimal> recettes = recetteRepository.totauxParCamion(debut, fin).stream()
                .collect(Collectors.toMap(RecetteRepository.TotalParCamion::getCamionId,
                        RecetteRepository.TotalParCamion::getTotal));
        Map<Long, BigDecimal> depenses = depenseRepository.totauxParCamion(debut, fin).stream()
                .collect(Collectors.toMap(DepenseRepository.TotalParCamion::getCamionId,
                        DepenseRepository.TotalParCamion::getTotal));
        Map<Long, MissionRepository.DistanceParCamion> distances =
                missionRepository.distancesParCamion(instantDebut(debut), instantFin(fin)).stream()
                        .collect(Collectors.toMap(MissionRepository.DistanceParCamion::getCamionId,
                                Function.identity()));

        List<RentabiliteDto.LigneCamion> lignes = camionRepository.findAll().stream()
                .filter(Camion::isActif)
                .sorted(Comparator.comparing(Camion::getImmatriculation))
                .map(camion -> ligne(camion,
                        recettes.getOrDefault(camion.getId(), BigDecimal.ZERO),
                        depenses.getOrDefault(camion.getId(), BigDecimal.ZERO),
                        distances.get(camion.getId())))
                .toList();

        BigDecimal totalRecettes = somme(lignes, RentabiliteDto.LigneCamion::recettes);
        BigDecimal totalDepenses = somme(lignes, RentabiliteDto.LigneCamion::depenses);
        BigDecimal resultat = totalRecettes.subtract(totalDepenses);
        long nombreCamions = lignes.size();

        return new RentabiliteDto.Global(
                debut, fin, totalRecettes, totalDepenses, resultat,
                pourcentage(resultat, totalRecettes),
                nombreCamions,
                moyenne(totalRecettes, nombreCamions),
                moyenne(totalDepenses, nombreCamions),
                lignes,
                palmares(lignes));
    }

    /** Fiche « performance du camion » du chapitre 7. */
    public RentabiliteDto.FicheCamion ficheCamion(Long camionId, LocalDate debut, LocalDate fin) {
        Camion camion = camionService.charger(camionId);

        BigDecimal totalRecettes = recetteRepository.totalParCamion(camionId, debut, fin);
        BigDecimal totalDepenses = depenseRepository.totalParCamion(camionId, debut, fin);
        BigDecimal resultat = totalRecettes.subtract(totalDepenses);

        MissionRepository.DistanceParCamion distance =
                missionRepository.distancesParCamion(instantDebut(debut), instantFin(fin)).stream()
                        .filter(d -> d.getCamionId().equals(camionId))
                        .findFirst()
                        .orElse(null);
        long km = distance == null ? 0L : distance.getDistance();
        long nombreMissions = distance == null ? 0L : distance.getNombre();

        String marqueModele = ((camion.getMarque() == null ? "" : camion.getMarque()) + " "
                + (camion.getModele() == null ? "" : camion.getModele())).trim();

        return new RentabiliteDto.FicheCamion(
                camion.getId(), camion.getImmatriculation(), camion.libelleMarque(),
                marqueModele.isEmpty() ? null : marqueModele, camion.getStatut().getLibelle(),
                debut, fin,
                revenus(camionId),
                totalRecettes, totalDepenses, resultat,
                pourcentage(resultat, totalRecettes),
                parKm(totalDepenses, km), parKm(totalRecettes, km),
                utilisation(camionId, debut, fin, km, nombreMissions),
                recetteService.repartitionParType(debut, fin, camionId),
                depenseService.repartitionParCategorie(debut, fin, camionId),
                depenseService.repartitionParNature(camionId, debut, fin));
    }

    /** Revenus du jour, de la semaine, du mois, de l'annee et cumules (chapitre 7). */
    private RentabiliteDto.Revenus revenus(Long camionId) {
        LocalDate aujourdhui = LocalDate.now();
        return new RentabiliteDto.Revenus(
                recetteRepository.totalParCamion(camionId, aujourdhui, aujourdhui),
                recetteRepository.totalParCamion(camionId, aujourdhui.minusDays(6), aujourdhui),
                recetteRepository.totalParCamion(camionId, aujourdhui.withDayOfMonth(1), aujourdhui),
                recetteRepository.totalParCamion(camionId, aujourdhui.withDayOfYear(1), aujourdhui),
                recetteRepository.totalParCamion(camionId, LocalDate.of(2000, 1, 1), aujourdhui));
    }

    /**
     * Jours d'exploitation : union des journees couvertes par un contrat ou par une
     * mission sur la periode. Les jours d'immobilisation seront alimentes par le
     * module Maintenance (phase 5) ; ils sont a zero a ce stade.
     */
    private RentabiliteDto.Utilisation utilisation(Long camionId, LocalDate debut, LocalDate fin,
                                                   long km, long nombreMissions) {
        long joursPeriode = ChronoUnit.DAYS.between(debut, fin) + 1;
        Set<LocalDate> joursExploites = new HashSet<>();

        for (Location contrat : locationRepository.contratsDuCamionSurPeriode(camionId, debut, fin)) {
            LocalDate courant = contrat.getDateDebut().isBefore(debut) ? debut : contrat.getDateDebut();
            LocalDate finContrat = contrat.finRetenue() == null || contrat.finRetenue().isAfter(fin)
                    ? fin : contrat.finRetenue();
            while (!courant.isAfter(finContrat)) {
                joursExploites.add(courant);
                courant = courant.plusDays(1);
            }
        }
        for (Mission mission : missionRepository.missionsTermineesDuCamion(
                camionId, instantDebut(debut), instantFin(fin))) {
            LocalDate depart = LocalDate.ofInstant(mission.getDateDepart(), ZONE);
            LocalDate arrivee = mission.getDateArrivee() == null
                    ? depart : LocalDate.ofInstant(mission.getDateArrivee(), ZONE);
            LocalDate courant = depart.isBefore(debut) ? debut : depart;
            LocalDate borne = arrivee.isAfter(fin) ? fin : arrivee;
            while (!courant.isAfter(borne)) {
                joursExploites.add(courant);
                courant = courant.plusDays(1);
            }
        }

        long exploites = joursExploites.size();
        return new RentabiliteDto.Utilisation(
                joursPeriode, exploites, Math.max(0, joursPeriode - exploites), 0L,
                km, nombreMissions,
                joursPeriode == 0 ? BigDecimal.ZERO
                        : BigDecimal.valueOf(exploites).multiply(CENT)
                                .divide(BigDecimal.valueOf(joursPeriode), 1, RoundingMode.HALF_UP));
    }

    private RentabiliteDto.LigneCamion ligne(Camion camion, BigDecimal recettes, BigDecimal depenses,
                                             MissionRepository.DistanceParCamion distance) {
        BigDecimal resultat = recettes.subtract(depenses);
        long km = distance == null ? 0L : distance.getDistance();
        long missions = distance == null ? 0L : distance.getNombre();
        return new RentabiliteDto.LigneCamion(
                camion.getId(), camion.getImmatriculation(), camion.libelleMarque(),
                camion.getStatut().getLibelle(),
                recettes, depenses, resultat, pourcentage(resultat, recettes),
                km, missions, parKm(depenses, km), parKm(recettes, km));
    }

    private RentabiliteDto.Palmares palmares(List<RentabiliteDto.LigneCamion> lignes) {
        if (lignes.isEmpty()) {
            return new RentabiliteDto.Palmares(null, null, null, null, null);
        }
        return new RentabiliteDto.Palmares(
                extremum(lignes, Comparator.comparing(RentabiliteDto.LigneCamion::resultat)),
                extremum(lignes, Comparator.comparing(RentabiliteDto.LigneCamion::resultat).reversed()),
                extremum(lignes, Comparator.comparing(RentabiliteDto.LigneCamion::recettes)),
                extremum(lignes, Comparator.comparing(RentabiliteDto.LigneCamion::depenses)),
                extremum(lignes, Comparator.comparingLong(RentabiliteDto.LigneCamion::distanceKm)));
    }

    private RentabiliteDto.LigneCamion extremum(List<RentabiliteDto.LigneCamion> lignes,
                                                Comparator<RentabiliteDto.LigneCamion> ordre) {
        return lignes.stream().max(ordre).orElse(null);
    }

    private BigDecimal somme(List<RentabiliteDto.LigneCamion> lignes,
                             Function<RentabiliteDto.LigneCamion, BigDecimal> extracteur) {
        return lignes.stream().map(extracteur).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Marge = resultat / recettes x 100 (chapitre 31). */
    private BigDecimal pourcentage(BigDecimal numerateur, BigDecimal denominateur) {
        if (denominateur == null || denominateur.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return numerateur.multiply(CENT).divide(denominateur, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal parKm(BigDecimal montant, long km) {
        if (km <= 0) {
            return null;
        }
        return montant.divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal moyenne(BigDecimal total, long nombre) {
        if (nombre <= 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(nombre), 2, RoundingMode.HALF_UP);
    }

    private Instant instantDebut(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    /** Borne haute exclusive : le lendemain du dernier jour de la periode. */
    private Instant instantFin(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZONE).toInstant();
    }
}
