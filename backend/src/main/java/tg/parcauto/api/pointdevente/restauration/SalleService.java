package tg.parcauto.api.pointdevente.restauration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Salles et tables du restaurant, et plan de service avec l'occupation des tables. */
@Service
@RequiredArgsConstructor
@Transactional
public class SalleService {

    private final SalleRepository salleRepository;
    private final TableRestaurantRepository tableRepository;
    private final CommandeRepository commandeRepository;

    // ------------------------------------------------------------ salles

    @Transactional(readOnly = true)
    public List<RestaurationDto.SalleReponse> salles() {
        return salleRepository.findAllByOrderByOrdreAscNomAsc().stream()
                .map(RestaurationDto.SalleReponse::de)
                .toList();
    }

    public RestaurationDto.SalleReponse creerSalle(RestaurationDto.SalleRequete r) {
        if (salleRepository.existsByNomIgnoreCase(r.nom().trim())) {
            throw new BusinessException("Une salle porte deja le nom " + r.nom().trim());
        }
        Salle salle = new Salle();
        appliquer(salle, r);
        return RestaurationDto.SalleReponse.de(salleRepository.save(salle));
    }

    public RestaurationDto.SalleReponse modifierSalle(Long id, RestaurationDto.SalleRequete r) {
        Salle salle = chargerSalle(id);
        if (salleRepository.existsByNomIgnoreCaseAndIdNot(r.nom().trim(), id)) {
            throw new BusinessException("Une salle porte deja le nom " + r.nom().trim());
        }
        appliquer(salle, r);
        return RestaurationDto.SalleReponse.de(salle);
    }

    // ------------------------------------------------------------ tables

    @Transactional(readOnly = true)
    public List<RestaurationDto.TableReponse> tables() {
        return tableRepository.toutes().stream().map(RestaurationDto.TableReponse::de).toList();
    }

    public RestaurationDto.TableReponse creerTable(RestaurationDto.TableRequete r) {
        if (tableRepository.existsByNomIgnoreCase(r.nom().trim())) {
            throw new BusinessException("Une table porte deja le nom " + r.nom().trim());
        }
        TableRestaurant table = new TableRestaurant();
        appliquer(table, r);
        return RestaurationDto.TableReponse.de(tableRepository.save(table));
    }

    public RestaurationDto.TableReponse modifierTable(Long id, RestaurationDto.TableRequete r) {
        TableRestaurant table = chargerTable(id);
        if (tableRepository.existsByNomIgnoreCaseAndIdNot(r.nom().trim(), id)) {
            throw new BusinessException("Une table porte deja le nom " + r.nom().trim());
        }
        if (Boolean.FALSE.equals(r.actif()) && table.isActif() && occupee(id)) {
            throw new BusinessException("La table " + table.getNom() + " a une commande en cours");
        }
        appliquer(table, r);
        return RestaurationDto.TableReponse.de(table);
    }

    /** Positions enregistrees en une fois a la fin de l'edition du plan. */
    public List<RestaurationDto.TableReponse> placer(List<RestaurationDto.PositionTable> positions) {
        for (RestaurationDto.PositionTable position : positions) {
            TableRestaurant table = chargerTable(position.id());
            table.setPosX(borne(position.posX(), 0, TableRestaurant.LARGEUR_PLAN - table.getLargeur()));
            table.setPosY(borne(position.posY(), 0, TableRestaurant.HAUTEUR_PLAN - table.getHauteur()));
        }
        return tables();
    }

    /** Une table qui a servi reste en base pour l'historique : elle est retiree du plan. */
    public void retirerTable(Long id) {
        TableRestaurant table = chargerTable(id);
        if (occupee(id)) {
            throw new BusinessException("La table " + table.getNom() + " a une commande en cours");
        }
        table.setActif(false);
    }

    // -------------------------------------------------------------- plan

    @Transactional(readOnly = true)
    public RestaurationDto.Plan plan() {
        List<Commande> ouvertes = commandeRepository.ouvertes();
        Map<Long, Commande> parTable = ouvertes.stream()
                .filter(c -> c.getTable() != null)
                .collect(Collectors.toMap(c -> c.getTable().getId(), Function.identity()));
        Map<Long, List<TableRestaurant>> tablesParSalle = tableRepository.toutes().stream()
                .filter(TableRestaurant::isActif)
                .collect(Collectors.groupingBy(t -> t.getSalle().getId()));

        List<RestaurationDto.SallePlan> salles = salleRepository.findAllByOrderByOrdreAscNomAsc().stream()
                .filter(Salle::isActif)
                .map(salle -> new RestaurationDto.SallePlan(
                        RestaurationDto.SalleReponse.de(salle),
                        tablesParSalle.getOrDefault(salle.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(TableRestaurant::getNom, PAR_NUMERO))
                                .map(t -> new RestaurationDto.TablePlan(
                                        RestaurationDto.TableReponse.de(t),
                                        parTable.containsKey(t.getId())
                                                ? RestaurationDto.CommandeResume.de(parTable.get(t.getId()))
                                                : null))
                                .toList()))
                .toList();
        List<RestaurationDto.CommandeResume> sansTable = ouvertes.stream()
                .filter(c -> c.getTable() == null)
                .map(RestaurationDto.CommandeResume::de)
                .toList();
        return new RestaurationDto.Plan(salles, sansTable);
    }

    // ----------------------------------------------------------- interne

    /** Tri naturel des noms : T2 avant T10. */
    private static final Comparator<String> PAR_NUMERO = (a, b) -> {
        String chiffresA = a.replaceAll("\\D", "");
        String chiffresB = b.replaceAll("\\D", "");
        String lettresA = a.replaceAll("\\d", "");
        String lettresB = b.replaceAll("\\d", "");
        int lettres = lettresA.compareToIgnoreCase(lettresB);
        if (lettres != 0 || chiffresA.isEmpty() || chiffresB.isEmpty() || chiffresA.length() > 9 || chiffresB.length() > 9) {
            return lettres != 0 ? lettres : a.compareToIgnoreCase(b);
        }
        return Integer.compare(Integer.parseInt(chiffresA), Integer.parseInt(chiffresB));
    };

    private boolean occupee(Long tableId) {
        return commandeRepository.existsByTableIdAndStatut(tableId, Commande.Statut.OUVERTE);
    }

    private void appliquer(Salle s, RestaurationDto.SalleRequete r) {
        s.setNom(r.nom().trim());
        if (r.ordre() != null) {
            s.setOrdre(r.ordre());
        }
        if (r.actif() != null) {
            s.setActif(r.actif());
        }
    }

    private void appliquer(TableRestaurant t, RestaurationDto.TableRequete r) {
        t.setSalle(chargerSalle(r.salleId()));
        t.setNom(r.nom().trim());
        if (r.places() != null) {
            t.setPlaces(r.places());
        }
        if (r.forme() != null) {
            t.setForme(r.forme());
        }
        if (r.largeur() != null) {
            t.setLargeur(r.largeur());
        }
        if (r.hauteur() != null) {
            t.setHauteur(r.hauteur());
        }
        if (r.posX() != null) {
            t.setPosX(r.posX());
        }
        if (r.posY() != null) {
            t.setPosY(r.posY());
        }
        // La table reste entiere sur le plan, meme agrandie pres d'un bord.
        t.setPosX(borne(t.getPosX(), 0, TableRestaurant.LARGEUR_PLAN - t.getLargeur()));
        t.setPosY(borne(t.getPosY(), 0, TableRestaurant.HAUTEUR_PLAN - t.getHauteur()));
        if (r.actif() != null) {
            t.setActif(r.actif());
        }
    }

    private static int borne(int valeur, int min, int max) {
        return Math.max(min, Math.min(max, valeur));
    }

    private Salle chargerSalle(Long id) {
        return salleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Salle", id));
    }

    private TableRestaurant chargerTable(Long id) {
        return tableRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Table", id));
    }
}
