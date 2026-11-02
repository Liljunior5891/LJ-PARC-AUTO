package tg.parcauto.api.finance.recette;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.exploitation.location.Location;
import tg.parcauto.api.exploitation.mission.Mission;
import tg.parcauto.api.exploitation.mission.StatutMission;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Un vehicule deja engage (location, mission, transport, voyage...) ne peut pas
 * recevoir une nouvelle recette qui l'engage sur les memes dates. Une recette
 * sur d'autres dates reste possible, de meme que les recettes d'appoint (frais
 * ou kilometres supplementaires, autre recette) et celles rattachees au contrat
 * ou a la mission qui engage deja le vehicule.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisponibiliteVehiculeService {

    /** Types de recette qui engagent le vehicule sur leur periode. */
    static final Set<TypeRecette> ENGAGEANTES = EnumSet.of(TypeRecette.LOCATION, TypeRecette.TRANSPORT,
            TypeRecette.LIVRAISON, TypeRecette.VOYAGE, TypeRecette.MISSION_SPECIALE, TypeRecette.CONTRAT);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId FUSEAU = ZoneId.of("Africa/Lome");
    /** Identifiant qui n'existe pas : sert de valeur neutre pour les exclusions. */
    private static final long AUCUN = -1L;

    private final RecetteRepository repository;

    /**
     * Engagements du vehicule qui chevauchent la periode, hors la recette elle-meme
     * (modification) et hors le contrat ou la mission auxquels elle se rattache.
     */
    public List<RecetteDto.Occupation> occupations(Long camionId, TypeRecette type, LocalDate debut, LocalDate fin,
                                                   Long recetteExclue, Long locationExclue, Long missionExclue) {
        return occupations(camionId, type, debut, fin, recetteExclue, locationExclue, missionExclue, null);
    }

    /** Idem, hors la demande en ligne que la recette conclut (sa propre reservation). */
    public List<RecetteDto.Occupation> occupations(Long camionId, TypeRecette type, LocalDate debut, LocalDate fin,
                                                   Long recetteExclue, Long locationExclue, Long missionExclue,
                                                   Long demandeExclue) {
        if (camionId == null || debut == null || (type != null && !ENGAGEANTES.contains(type))) {
            return List.of();
        }
        LocalDate finPeriode = fin == null || fin.isBefore(debut) ? debut : fin;
        long recette = recetteExclue == null ? AUCUN : recetteExclue;
        long location = locationExclue == null ? AUCUN : locationExclue;
        long mission = missionExclue == null ? AUCUN : missionExclue;

        // Une meme reference n'est citee qu'une fois (un contrat et la recette qui le porte).
        Map<String, RecetteDto.Occupation> occupations = new LinkedHashMap<>();
        for (Location l : repository.contratsEngages(camionId, debut, finPeriode, location)) {
            LocalDate finContrat = l.getDateFinReelle() != null ? l.getDateFinReelle() : l.getDateFin();
            occupations.putIfAbsent(l.getNumero(), occupation(l.getNumero(), "en location", "contrat " + l.getNumero(),
                    l.getDateDebut(), finContrat == null ? LocalDate.MAX : finContrat));
        }
        for (Mission m : repository.missionsEngagees(camionId, mission)) {
            if (m.getDateDepart() == null) {
                continue;
            }
            LocalDate depart = LocalDate.ofInstant(m.getDateDepart(), FUSEAU);
            // Sans date d'arrivee, une mission en cours court toujours ; sinon elle tient sur son jour de depart.
            LocalDate arrivee = m.getDateArrivee() != null ? LocalDate.ofInstant(m.getDateArrivee(), FUSEAU)
                    : m.getStatut() == StatutMission.EN_COURS ? LocalDate.MAX : depart;
            if (!depart.isAfter(finPeriode) && !arrivee.isBefore(debut)) {
                occupations.putIfAbsent(m.getNumero(), occupation(m.getNumero(), "en mission",
                        "mission " + m.getNumero(), depart, arrivee));
            }
        }
        for (Recette r : repository.engagements(camionId, ENGAGEANTES, debut, finPeriode, recette, location, mission)) {
            String reference = r.getLocation() != null ? r.getLocation().getNumero()
                    : r.getMission() != null ? r.getMission().getNumero() : r.getNumero();
            Location contrat = r.getLocation();
            LocalDate du = r.getDateDebut() != null ? r.getDateDebut()
                    : contrat != null ? contrat.getDateDebut() : r.getDateRecette();
            LocalDate au = r.getDateFin() != null ? r.getDateFin()
                    : contrat != null && contrat.getDateFin() != null ? contrat.getDateFin() : du;
            String objet = contrat != null ? "contrat " + contrat.getNumero()
                    : r.getMission() != null ? "mission " + r.getMission().getNumero()
                    : r.getType().getLibelle().toLowerCase() + " " + r.getNumero();
            occupations.putIfAbsent(reference, occupation(reference, etat(r.getType()), objet, du, au));
        }
        for (var d : repository.reservationsEnLigne(camionId, debut, finPeriode,
                demandeExclue == null ? AUCUN : demandeExclue)) {
            occupations.putIfAbsent(d.getNumero(), occupation(d.getNumero(), "reserve en ligne",
                    "demande " + d.getNumero() + ", client attendu en agence", d.getDateDebut(), d.getDateFin()));
        }
        return new ArrayList<>(occupations.values());
    }

    /** Refuse la recette si le vehicule est deja engage sur sa periode. */
    public void exiger(Long camionId, TypeRecette type, LocalDate debut, LocalDate fin, Long recetteExclue,
                       Long locationExclue, Long missionExclue) {
        exiger(camionId, type, debut, fin, recetteExclue, locationExclue, missionExclue, null);
    }

    public void exiger(Long camionId, TypeRecette type, LocalDate debut, LocalDate fin, Long recetteExclue,
                       Long locationExclue, Long missionExclue, Long demandeExclue) {
        List<RecetteDto.Occupation> occupations = occupations(camionId, type, debut, fin, recetteExclue,
                locationExclue, missionExclue, demandeExclue);
        if (!occupations.isEmpty()) {
            throw new BusinessException(occupations.get(0).message()
                    + " Choisissez d'autres dates ou un autre vehicule.");
        }
    }

    private static String etat(TypeRecette type) {
        return switch (type) {
            case LOCATION -> "en location";
            case TRANSPORT -> "en transport";
            case LIVRAISON -> "en livraison";
            case VOYAGE -> "en voyage";
            case MISSION_SPECIALE -> "en mission speciale";
            default -> "sous contrat";
        };
    }

    private static RecetteDto.Occupation occupation(String reference, String etat, String objet, LocalDate du,
                                                    LocalDate au) {
        String periode = au == null || au.equals(du) ? "le " + DATE.format(du)
                : au.equals(LocalDate.MAX) ? "depuis le " + DATE.format(du)
                : "du " + DATE.format(du) + " au " + DATE.format(au);
        return new RecetteDto.Occupation(reference, du, au.equals(LocalDate.MAX) ? null : au,
                "Ce vehicule est deja " + etat + " (" + objet + ") " + periode + ".");
    }
}
