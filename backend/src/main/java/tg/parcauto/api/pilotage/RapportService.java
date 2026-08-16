package tg.parcauto.api.pilotage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.finance.depense.CarburantService;
import tg.parcauto.api.finance.depense.DepenseService;
import tg.parcauto.api.finance.rentabilite.RentabiliteDto;
import tg.parcauto.api.finance.rentabilite.RentabiliteService;
import tg.parcauto.api.gps.position.PositionGpsDto;
import tg.parcauto.api.gps.CarteParcService;
import tg.parcauto.api.maintenance.entretien.EntretienDto;
import tg.parcauto.api.maintenance.entretien.EntretienService;
import tg.parcauto.api.parc.camion.CamionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Rapports de gestion, chapitre 35 du cahier des charges.
 * <p>
 * Les rapports sont produits en CSV, format universellement ouvrable par Excel
 * et LibreOffice. Le separateur est le point-virgule et les nombres utilisent la
 * virgule decimale : c'est ce qu'attend un Excel configure en francais.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RapportService {

    private static final char SEPARATEUR = ';';
    /** Marque d'ordre des octets, sans laquelle Excel lit mal les accents. */
    private static final String BOM = "﻿";

    private final RentabiliteService rentabiliteService;
    private final DepenseService depenseService;
    private final CarburantService carburantService;
    private final EntretienService entretienService;
    private final CamionService camionService;
    private final CarteParcService carteService;

    public enum TypeRapport {
        FINANCIER("Rapport financier"),
        RENTABILITE_CAMIONS("Rentabilite par camion"),
        DEPENSES("Depenses par categorie"),
        CARBURANT("Consommation de carburant"),
        MAINTENANCE("Interventions de maintenance"),
        PARC("Etat du parc");

        private final String libelle;

        TypeRapport(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }

        public String nomFichier(LocalDate debut, LocalDate fin) {
            return "%s_%s_%s.csv".formatted(name().toLowerCase(Locale.ROOT), debut, fin);
        }
    }

    public String produire(TypeRapport type, LocalDate debut, LocalDate fin) {
        return switch (type) {
            case FINANCIER -> financier(debut, fin);
            case RENTABILITE_CAMIONS -> rentabiliteParCamion(debut, fin);
            case DEPENSES -> depenses(debut, fin);
            case CARBURANT -> carburant(debut, fin);
            case MAINTENANCE -> maintenance();
            case PARC -> parc();
        };
    }

    /** Recettes, depenses et resultat consolides. */
    private String financier(LocalDate debut, LocalDate fin) {
        RentabiliteDto.Global global = rentabiliteService.global(debut, fin);
        StringBuilder csv = new StringBuilder(BOM);
        ligne(csv, "Rapport financier", "du " + debut, "au " + fin);
        csv.append('\n');
        ligne(csv, "Indicateur", "Montant (FCFA)");
        ligne(csv, "Total des recettes", nombre(global.totalRecettes()));
        ligne(csv, "Total des depenses", nombre(global.totalDepenses()));
        ligne(csv, "Resultat", nombre(global.resultat()));
        ligne(csv, "Marge (%)", nombre(global.margePourcent()));
        ligne(csv, "Nombre de camions", String.valueOf(global.nombreCamions()));
        ligne(csv, "Recette moyenne par camion", nombre(global.recetteMoyenneParCamion()));
        ligne(csv, "Depense moyenne par camion", nombre(global.depenseMoyenneParCamion()));
        return csv.toString();
    }

    /** Le tableau de comparaison des camions du chapitre 29. */
    private String rentabiliteParCamion(LocalDate debut, LocalDate fin) {
        StringBuilder csv = new StringBuilder(BOM);
        ligne(csv, "Immatriculation", "Marque", "Statut", "Recettes", "Depenses", "Resultat",
                "Marge (%)", "Kilometres", "Missions", "Cout/km", "Revenu/km");
        for (RentabiliteDto.LigneCamion ligne : rentabiliteService.global(debut, fin).camions()) {
            ligne(csv, ligne.immatriculation(), ligne.marque() == null ? "" : ligne.marque(),
                    ligne.statutLibelle(),
                    nombre(ligne.recettes()), nombre(ligne.depenses()), nombre(ligne.resultat()),
                    nombre(ligne.margePourcent()), String.valueOf(ligne.distanceKm()),
                    String.valueOf(ligne.nombreMissions()),
                    nombre(ligne.coutParKm()), nombre(ligne.revenuParKm()));
        }
        return csv.toString();
    }

    private String depenses(LocalDate debut, LocalDate fin) {
        StringBuilder csv = new StringBuilder(BOM);
        ligne(csv, "Categorie", "Montant (FCFA)", "Nombre d'operations");
        depenseService.repartitionParCategorie(debut, fin, null)
                .forEach(l -> ligne(csv, l.libelle(), nombre(l.total()), String.valueOf(l.nombre())));
        return csv.toString();
    }

    private String carburant(LocalDate debut, LocalDate fin) {
        StringBuilder csv = new StringBuilder(BOM);
        ligne(csv, "Camion", "Pleins", "Litres", "Montant (FCFA)", "Distance (km)",
                "Consommation (l/100km)", "Cout carburant/km", "Prix moyen/litre");
        rentabiliteService.global(debut, fin).camions().forEach(camion -> {
            var synthese = carburantService.synthese(camion.camionId(), debut, fin);
            ligne(csv, synthese.camionImmatriculation(),
                    String.valueOf(synthese.nombrePleins()), nombre(synthese.totalLitres()),
                    nombre(synthese.totalMontant()), String.valueOf(synthese.distanceCouverte()),
                    nombre(synthese.consommationMoyenneL100km()),
                    nombre(synthese.coutCarburantParKm()), nombre(synthese.prixMoyenLitre()));
        });
        return csv.toString();
    }

    private String maintenance() {
        StringBuilder csv = new StringBuilder(BOM);
        ligne(csv, "Numero", "Camion", "Type", "Libelle", "Statut", "Echeance km",
                "Echeance date", "Km restants", "Cout", "Fournisseur");
        for (EntretienDto.Reponse e : entretienService.ouverts()) {
            ligne(csv, e.numero(), e.camionImmatriculation(), e.typeLibelle(), e.libelle(),
                    e.statutLibelle(),
                    e.kilometragePrevu() == null ? "" : String.valueOf(e.kilometragePrevu()),
                    e.datePrevue() == null ? "" : e.datePrevue().toString(),
                    e.kilometresRestants() == null ? "" : String.valueOf(e.kilometresRestants()),
                    nombre(e.cout()), e.fournisseur() == null ? "" : e.fournisseur());
        }
        return csv.toString();
    }

    /** Etat du parc avec la derniere position connue de chaque camion. */
    private String parc() {
        StringBuilder csv = new StringBuilder(BOM);
        ligne(csv, "Immatriculation", "Marque", "Statut", "Chauffeur", "Activite",
                "Kilometrage", "Latitude", "Longitude", "Derniere communication");
        List<PositionGpsDto.MarqueurCarte> marqueurs = carteService.marqueurs();
        camionService.rechercher(null, null, true,
                        org.springframework.data.domain.PageRequest.of(0, 500))
                .forEach(camion -> {
                    var position = marqueurs.stream()
                            .filter(m -> m.camionId().equals(camion.id()))
                            .findFirst().orElse(null);
                    ligne(csv, camion.immatriculation(), camion.marque() == null ? "" : camion.marque(),
                            camion.statutLibelle(),
                            position == null || position.chauffeurNom() == null ? "" : position.chauffeurNom(),
                            position == null || position.activite() == null ? "" : position.activite(),
                            String.valueOf(camion.kilometrageActuel()),
                            position == null ? "" : nombre(position.latitude()),
                            position == null ? "" : nombre(position.longitude()),
                            position == null ? "" : horodatage(position.derniereCommunication()));
                });
        return csv.toString();
    }

    private void ligne(StringBuilder csv, String... cellules) {
        for (int i = 0; i < cellules.length; i++) {
            if (i > 0) {
                csv.append(SEPARATEUR);
            }
            csv.append(echapper(cellules[i]));
        }
        csv.append('\n');
    }

    /** Protege les cellules contenant le separateur, un guillemet ou un retour ligne. */
    private String echapper(String valeur) {
        if (valeur == null) {
            return "";
        }
        if (valeur.indexOf(SEPARATEUR) >= 0 || valeur.indexOf('"') >= 0 || valeur.indexOf('\n') >= 0) {
            return '"' + valeur.replace("\"", "\"\"") + '"';
        }
        return valeur;
    }

    /** Virgule decimale : c'est ce qu'attend un tableur configure en francais. */
    private String nombre(BigDecimal valeur) {
        return valeur == null ? "" : valeur.toPlainString().replace('.', ',');
    }

    private String horodatage(java.time.Instant instant) {
        return instant == null ? "" : instant.atZone(ZoneId.systemDefault()).toLocalDateTime().toString();
    }

    public void verifierPeriode(LocalDate debut, LocalDate fin) {
        if (debut.isAfter(fin)) {
            throw new BusinessException("La date de debut est posterieure a la date de fin");
        }
    }
}
