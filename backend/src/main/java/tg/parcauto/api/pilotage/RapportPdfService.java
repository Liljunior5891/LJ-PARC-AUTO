package tg.parcauto.api.pilotage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.finance.depense.CarburantService;
import tg.parcauto.api.finance.depense.DepenseDto;
import tg.parcauto.api.finance.depense.DepenseService;
import tg.parcauto.api.finance.facture.FactureDto;
import tg.parcauto.api.finance.facture.FactureService;
import tg.parcauto.api.finance.recette.RecetteDto;
import tg.parcauto.api.finance.recette.RecetteService;
import tg.parcauto.api.finance.rentabilite.RentabiliteDto;
import tg.parcauto.api.finance.rentabilite.RentabiliteService;
import tg.parcauto.api.maintenance.entretien.EntretienDto;
import tg.parcauto.api.maintenance.entretien.EntretienService;
import tg.parcauto.api.maintenance.incident.IncidentDto;
import tg.parcauto.api.maintenance.incident.IncidentService;
import tg.parcauto.api.maintenance.pneu.PneuDto;
import tg.parcauto.api.maintenance.pneu.PneuService;
import tg.parcauto.api.parc.camion.CamionDto;
import tg.parcauto.api.parc.camion.CamionService;
import tg.parcauto.api.parc.camion.OdometreDto;
import tg.parcauto.api.parc.camion.OdometreService;
import tg.parcauto.api.parc.chauffeur.ChauffeurDto;
import tg.parcauto.api.parc.chauffeur.ChauffeurService;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Edition des rapports PDF, chapitre 35 du cahier des charges.
 * <p>
 * Ce service n'interroge pas la base directement : il assemble ce que produisent
 * deja les modules parc, exploitation, finances et maintenance, et confie la mise
 * en page a {@link MiseEnPagePdf}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RapportPdfService {

    private static final int LIGNES_MAX = 500;

    private final RentabiliteService rentabiliteService;
    private final PilotageService pilotageService;
    private final CamionService camionService;
    private final ChauffeurService chauffeurService;
    private final RecetteService recetteService;
    private final DepenseService depenseService;
    private final CarburantService carburantService;
    private final EntretienService entretienService;
    private final PneuService pneuService;
    private final IncidentService incidentService;
    private final FactureService factureService;
    private final OdometreService odometreService;

    @Value("${parcauto.entreprise.nom:PARC AUTO}")
    private String entreprise;

    /**
     * Produit le rapport demande.
     *
     * @param camionIds selection de camions ; vide signifie tout le parc
     */
    public byte[] produire(TypeRapportPdf type, LocalDate debut, LocalDate fin, List<Long> camionIds) {
        if (debut.isAfter(fin)) {
            throw new BusinessException("La date de debut est posterieure a la date de fin");
        }
        try (MiseEnPagePdf pdf = new MiseEnPagePdf(entreprise, type.getLibelle(),
                MiseEnPagePdf.periode(debut, fin), type.paysage())) {
            switch (type) {
                case PARC_GLOBAL -> parcGlobal(pdf, debut, fin);
                case CAMIONS_DETAIL -> camionsDetail(pdf, debut, fin, camionIds);
                case FINANCIER -> financier(pdf, debut, fin);
                case RECETTES -> journalRecettes(pdf, debut, fin, camionIds);
                case DEPENSES -> journalDepenses(pdf, debut, fin, camionIds);
                case CARBURANT -> carburant(pdf, debut, fin, camionIds);
                case MAINTENANCE -> maintenance(pdf, camionIds);
                case CHAUFFEURS -> chauffeurs(pdf);
                case CREANCES -> creances(pdf);
            }
            pdf.close();
            return pdf.octets();
        }
    }

    // ------------------------------------------------------------------
    // Rapport global du parc
    // ------------------------------------------------------------------

    private void parcGlobal(MiseEnPagePdf pdf, LocalDate debut, LocalDate fin) {
        RentabiliteDto.Global global = rentabiliteService.global(debut, fin);

        pdf.indicateurs(List.of(
                MiseEnPagePdf.Indicateur.de("Recettes", MiseEnPagePdf.montant(global.totalRecettes()) + " F"),
                MiseEnPagePdf.Indicateur.de("Depenses", MiseEnPagePdf.montant(global.totalDepenses()) + " F"),
                new MiseEnPagePdf.Indicateur("Resultat",
                        MiseEnPagePdf.montant(global.resultat()) + " F",
                        "marge " + MiseEnPagePdf.nombre(global.margePourcent()) + " %",
                        global.resultat().signum() >= 0 ? MiseEnPagePdf.VERT : MiseEnPagePdf.ROUGE),
                MiseEnPagePdf.Indicateur.de("Camions", String.valueOf(global.nombreCamions()),
                        MiseEnPagePdf.montant(global.recetteMoyenneParCamion()) + " F de recette moyenne")));

        pdf.section("Rentabilite par camion");
        List<String[]> lignes = new ArrayList<>();
        for (RentabiliteDto.LigneCamion camion : global.camions()) {
            lignes.add(new String[]{
                    camion.immatriculation(),
                    camion.marque() == null ? "—" : camion.marque(),
                    camion.statutLibelle(),
                    MiseEnPagePdf.montant(camion.recettes()),
                    MiseEnPagePdf.montant(camion.depenses()),
                    MiseEnPagePdf.montant(camion.resultat()),
                    MiseEnPagePdf.nombre(camion.margePourcent()) + " %",
                    MiseEnPagePdf.nombre(camion.distanceKm()),
                    MiseEnPagePdf.montant(camion.coutParKm()),
                    MiseEnPagePdf.montant(camion.revenuParKm())});
        }
        pdf.tableau(
                new float[]{13f, 13f, 11f, 12f, 12f, 12f, 7f, 8f, 6f, 6f},
                new String[]{"Immatriculation", "Marque", "Statut", "Recettes", "Depenses",
                        "Resultat", "Marge", "Km", "Cout/km", "Rev/km"},
                "gggdddddd d", lignes);

        if (global.palmares() != null && global.palmares().plusRentable() != null) {
            pdf.section("Comparaison");
            pdf.tableau(
                    new float[]{40f, 30f, 30f},
                    new String[]{"Critere", "Camion", "Valeur"},
                    "ggd",
                    List.of(
                            ligneP("Le plus rentable", global.palmares().plusRentable(),
                                    global.palmares().plusRentable().resultat()),
                            ligneP("Le moins rentable", global.palmares().moinsRentable(),
                                    global.palmares().moinsRentable().resultat()),
                            ligneP("Plus de recettes", global.palmares().plusDeRecettes(),
                                    global.palmares().plusDeRecettes().recettes()),
                            ligneP("Coute le plus cher", global.palmares().plusDeDepenses(),
                                    global.palmares().plusDeDepenses().depenses()),
                            new String[]{"Plus de kilometres",
                                    global.palmares().plusDeKilometres().immatriculation(),
                                    MiseEnPagePdf.nombre(
                                            global.palmares().plusDeKilometres().distanceKm()) + " km"}));
        }

        pdf.note("Resultat = recettes - depenses. Les depenses refacturees au client sont exclues.");
    }

    private String[] ligneP(String critere, RentabiliteDto.LigneCamion camion, BigDecimal valeur) {
        return new String[]{critere,
                camion == null ? "—" : camion.immatriculation(),
                MiseEnPagePdf.montant(valeur) + " F"};
    }

    // ------------------------------------------------------------------
    // Rapport complet par camion
    // ------------------------------------------------------------------

    private void camionsDetail(MiseEnPagePdf pdf, LocalDate debut, LocalDate fin, List<Long> camionIds) {
        List<Long> cibles = camionIds.isEmpty() ? tousLesCamions() : camionIds;
        if (cibles.isEmpty()) {
            pdf.texte("Aucun camion a editer.");
            return;
        }

        boolean premier = true;
        for (Long camionId : cibles) {
            if (!premier) {
                pdf.sautDePage();
            }
            premier = false;
            ficheCamion(pdf, camionId, debut, fin);
        }
    }

    private void ficheCamion(MiseEnPagePdf pdf, Long camionId, LocalDate debut, LocalDate fin) {
        CamionDto.Reponse camion = camionService.parId(camionId);
        FicheSyntheseDto synthese = pilotageService.ficheSynthese(camionId, debut, fin);
        RentabiliteDto.FicheCamion performance = rentabiliteService.ficheCamion(camionId, debut, fin);

        pdf.section("Camion " + camion.immatriculation());
        pdf.tableau(
                new float[]{25f, 25f, 25f, 25f},
                new String[]{"Marque et modele", "Type", "Annee", "Statut"},
                "gggg",
                List.<String[]>of(new String[]{
                        (camion.marque() == null ? "" : camion.marque()) + " "
                                + (camion.modele() == null ? "" : camion.modele()),
                        camion.type() == null ? "—" : camion.type(),
                        camion.annee() == null ? "—" : String.valueOf(camion.annee()),
                        camion.statutLibelle()}));

        pdf.indicateurs(List.of(
                MiseEnPagePdf.Indicateur.de("Recettes",
                        MiseEnPagePdf.montant(synthese.recettes()) + " F"),
                MiseEnPagePdf.Indicateur.de("Depenses",
                        MiseEnPagePdf.montant(synthese.depenses()) + " F"),
                new MiseEnPagePdf.Indicateur("Resultat",
                        MiseEnPagePdf.montant(synthese.resultat()) + " F",
                        "marge " + MiseEnPagePdf.nombre(synthese.margePourcent()) + " %",
                        synthese.resultat().signum() >= 0 ? MiseEnPagePdf.VERT : MiseEnPagePdf.ROUGE),
                MiseEnPagePdf.Indicateur.de("Kilometrage",
                        MiseEnPagePdf.nombre(synthese.kilometrageActuel()) + " km",
                        MiseEnPagePdf.nombre(synthese.kilometresSurPeriode()) + " km sur la periode")));

        pdf.section("Exploitation");
        pdf.tableau(
                new float[]{20f, 20f, 20f, 20f, 20f},
                new String[]{"Chauffeur", "Client", "Activite", "Missions", "Taux d'utilisation"},
                "gggdd",
                List.<String[]>of(new String[]{
                        valeur(synthese.chauffeurNom()),
                        valeur(synthese.clientNom()),
                        valeur(synthese.activite()),
                        String.valueOf(synthese.nombreMissions()),
                        MiseEnPagePdf.nombre(
                                performance.utilisation().tauxUtilisationPourcent()) + " %"}));

        pdf.section("Depenses par categorie");
        List<String[]> categories = new ArrayList<>();
        for (DepenseDto.LigneCategorie ligne : synthese.depensesParCategorie()) {
            categories.add(new String[]{ligne.libelle(),
                    MiseEnPagePdf.montant(ligne.total()) + " F",
                    String.valueOf(ligne.nombre())});
        }
        pdf.tableau(new float[]{50f, 30f, 20f},
                new String[]{"Categorie", "Montant", "Operations"}, "gdd", categories);

        pdf.section("Carburant et couts kilometriques");
        pdf.tableau(
                new float[]{25f, 25f, 25f, 25f},
                new String[]{"Consommation", "Cout carburant/km", "Cout total/km", "Revenu/km"},
                "dddd",
                List.<String[]>of(new String[]{
                        synthese.consommationMoyenneL100km() == null ? "—"
                                : MiseEnPagePdf.nombre(synthese.consommationMoyenneL100km()) + " l/100 km",
                        MiseEnPagePdf.montant(synthese.coutCarburantParKm()),
                        MiseEnPagePdf.montant(synthese.coutParKm()),
                        MiseEnPagePdf.montant(synthese.revenuParKm())}));

        pdf.section("Etat du vehicule");
        pdf.tableau(
                new float[]{20f, 20f, 20f, 20f, 20f},
                new String[]{"Entretiens a venir", "En retard", "Pneus a remplacer",
                        "Incidents ouverts", "Documents a renouveler"},
                "ddddd",
                List.<String[]>of(new String[]{
                        String.valueOf(synthese.entretiensAVenir()),
                        String.valueOf(synthese.entretiensEnRetard()),
                        String.valueOf(synthese.pneusARemplacer()),
                        String.valueOf(synthese.incidentsOuverts()),
                        String.valueOf(synthese.documentsAExpirer())}));

        OdometreDto odometre = odometreService.historique(camionId, debut, fin);
        pdf.section("Releves kilometriques");
        List<String[]> releves = new ArrayList<>();
        for (OdometreDto.Releve releve : odometre.releves().stream().limit(15).toList()) {
            releves.add(new String[]{
                    MiseEnPagePdf.date(releve.date()),
                    MiseEnPagePdf.nombre(releve.kilometrage()) + " km",
                    releve.distanceDepuisPrecedent() == null ? "—"
                            : "+ " + MiseEnPagePdf.nombre(releve.distanceDepuisPrecedent()) + " km",
                    releve.source().getLibelle(),
                    valeur(releve.libelle())});
        }
        pdf.tableau(new float[]{14f, 16f, 14f, 18f, 38f},
                new String[]{"Date", "Compteur", "Distance", "Origine", "Detail"}, "gddgg", releves);
        pdf.note("Les quinze derniers releves sont presentes. Moyenne : "
                + MiseEnPagePdf.nombre(odometre.moyenneKmParJour()) + " km par jour.");
    }

    // ------------------------------------------------------------------
    // Autres rapports
    // ------------------------------------------------------------------

    private void financier(MiseEnPagePdf pdf, LocalDate debut, LocalDate fin) {
        KpiDto kpi = pilotageService.kpi(debut, fin);
        FactureDto.Creances creances = factureService.creances();

        pdf.indicateurs(List.of(
                MiseEnPagePdf.Indicateur.de("Chiffre d'affaires",
                        MiseEnPagePdf.montant(kpi.chiffreAffaires()) + " F"),
                MiseEnPagePdf.Indicateur.de("Depenses", MiseEnPagePdf.montant(kpi.depenses()) + " F"),
                new MiseEnPagePdf.Indicateur("Resultat",
                        MiseEnPagePdf.montant(kpi.resultat()) + " F",
                        "marge " + MiseEnPagePdf.nombre(kpi.margePourcent()) + " %",
                        kpi.resultat().signum() >= 0 ? MiseEnPagePdf.VERT : MiseEnPagePdf.ROUGE),
                MiseEnPagePdf.Indicateur.de("Creances",
                        MiseEnPagePdf.montant(creances.montantRestant()) + " F",
                        creances.nombreFacturesImpayees() + " facture(s) non soldee(s)")));

        pdf.section("Indicateurs");
        pdf.tableau(new float[]{60f, 40f},
                new String[]{"Indicateur", "Valeur"}, "gd",
                List.of(
                        new String[]{"Recette moyenne par camion",
                                MiseEnPagePdf.montant(kpi.recetteMoyenneParCamion()) + " F"},
                        new String[]{"Depense moyenne par camion",
                                MiseEnPagePdf.montant(kpi.depenseMoyenneParCamion()) + " F"},
                        new String[]{"Cout au kilometre", MiseEnPagePdf.montant(kpi.coutParKm()) + " F"},
                        new String[]{"Revenu au kilometre", MiseEnPagePdf.montant(kpi.revenuParKm()) + " F"},
                        new String[]{"Cout de maintenance",
                                MiseEnPagePdf.montant(kpi.coutMaintenance()) + " F"},
                        new String[]{"Kilometres parcourus",
                                MiseEnPagePdf.nombre(kpi.kilometresParcourus()) + " km"},
                        new String[]{"Missions terminees", String.valueOf(kpi.nombreMissions())},
                        new String[]{"Taux d'utilisation",
                                MiseEnPagePdf.nombre(kpi.tauxUtilisationPourcent()) + " %"},
                        new String[]{"Taux de disponibilite",
                                MiseEnPagePdf.nombre(kpi.tauxDisponibilitePourcent()) + " %"},
                        new String[]{"Jours d'immobilisation",
                                String.valueOf(kpi.joursImmobilisation())}));

        pdf.section("Repartition des depenses");
        List<String[]> categories = new ArrayList<>();
        for (DepenseDto.LigneCategorie ligne : depenseService.repartitionParCategorie(debut, fin, null)) {
            categories.add(new String[]{ligne.libelle(),
                    MiseEnPagePdf.montant(ligne.total()) + " F", String.valueOf(ligne.nombre())});
        }
        pdf.tableau(new float[]{50f, 30f, 20f},
                new String[]{"Categorie", "Montant", "Operations"}, "gdd", categories);
    }

    private void journalRecettes(MiseEnPagePdf pdf, LocalDate debut, LocalDate fin, List<Long> camionIds) {
        pdf.section("Repartition par type");
        List<String[]> types = new ArrayList<>();
        for (RecetteDto.LigneType ligne : recetteService.repartitionParType(debut, fin, unique(camionIds))) {
            types.add(new String[]{ligne.libelle(),
                    MiseEnPagePdf.montant(ligne.total()) + " F", String.valueOf(ligne.nombre())});
        }
        pdf.tableau(new float[]{50f, 30f, 20f},
                new String[]{"Type", "Montant", "Operations"}, "gdd", types);

        pdf.section("Detail des recettes");
        List<String[]> lignes = new ArrayList<>();
        recetteService.rechercher(null, null, unique(camionIds), null, debut, fin,
                        PageRequest.of(0, LIGNES_MAX, Sort.by("dateRecette").descending()))
                .forEach(recette -> lignes.add(new String[]{
                        recette.numero(),
                        MiseEnPagePdf.date(recette.dateRecette()),
                        recette.camionImmatriculation(),
                        recette.typeLibelle(),
                        recette.libelle(),
                        valeur(recette.clientNom()),
                        MiseEnPagePdf.montant(recette.montant())}));
        pdf.tableau(new float[]{12f, 10f, 12f, 12f, 26f, 16f, 12f},
                new String[]{"Numero", "Date", "Camion", "Type", "Libelle", "Client", "Montant"},
                "ggggggd", lignes);
    }

    private void journalDepenses(MiseEnPagePdf pdf, LocalDate debut, LocalDate fin, List<Long> camionIds) {
        pdf.section("Repartition par categorie");
        List<String[]> categories = new ArrayList<>();
        for (DepenseDto.LigneCategorie ligne :
                depenseService.repartitionParCategorie(debut, fin, unique(camionIds))) {
            categories.add(new String[]{ligne.libelle(),
                    MiseEnPagePdf.montant(ligne.total()) + " F", String.valueOf(ligne.nombre())});
        }
        pdf.tableau(new float[]{50f, 30f, 20f},
                new String[]{"Categorie", "Montant", "Operations"}, "gdd", categories);

        pdf.section("Detail des depenses");
        List<String[]> lignes = new ArrayList<>();
        depenseService.rechercher(null, null, null, unique(camionIds), null, debut, fin,
                        PageRequest.of(0, LIGNES_MAX, Sort.by("dateDepense").descending()))
                .forEach(depense -> lignes.add(new String[]{
                        depense.numero(),
                        MiseEnPagePdf.date(depense.dateDepense()),
                        depense.camionImmatriculation(),
                        depense.categorieLibelle(),
                        depense.sousCategorieLibelle(),
                        depense.libelle(),
                        valeur(depense.fournisseur()),
                        MiseEnPagePdf.montant(depense.montant())}));
        pdf.tableau(new float[]{11f, 9f, 11f, 11f, 12f, 22f, 13f, 11f},
                new String[]{"Numero", "Date", "Camion", "Categorie", "Nature", "Libelle",
                        "Fournisseur", "Montant"},
                "gggggggd", lignes);
        pdf.note("Les depenses refacturees au client figurent au detail mais sont exclues des totaux.");
    }

    private void carburant(MiseEnPagePdf pdf, LocalDate debut, LocalDate fin, List<Long> camionIds) {
        List<Long> cibles = camionIds.isEmpty() ? tousLesCamions() : camionIds;
        for (Long camionId : cibles) {
            var synthese = carburantService.synthese(camionId, debut, fin);
            pdf.section("Camion " + synthese.camionImmatriculation());
            pdf.indicateurs(List.of(
                    MiseEnPagePdf.Indicateur.de("Consommation",
                            synthese.consommationMoyenneL100km() == null ? "—"
                                    : MiseEnPagePdf.nombre(synthese.consommationMoyenneL100km())
                                            + " l/100 km"),
                    MiseEnPagePdf.Indicateur.de("Cout au km",
                            MiseEnPagePdf.montant(synthese.coutCarburantParKm()) + " F"),
                    MiseEnPagePdf.Indicateur.de("Volume",
                            MiseEnPagePdf.nombre(synthese.totalLitres()) + " l",
                            synthese.nombrePleins() + " plein(s)"),
                    MiseEnPagePdf.Indicateur.de("Depense",
                            MiseEnPagePdf.montant(synthese.totalMontant()) + " F")));

            List<String[]> pleins = new ArrayList<>();
            synthese.pleins().forEach(plein -> pleins.add(new String[]{
                    MiseEnPagePdf.date(plein.date()),
                    valeur(plein.station()),
                    MiseEnPagePdf.nombre(plein.kilometrage()) + " km",
                    plein.distanceDepuisPleinPrecedent() == null ? "—"
                            : MiseEnPagePdf.nombre(plein.distanceDepuisPleinPrecedent()) + " km",
                    MiseEnPagePdf.nombre(plein.litres()),
                    MiseEnPagePdf.montant(plein.montant()),
                    plein.consommationL100km() == null ? "reference"
                            : MiseEnPagePdf.nombre(plein.consommationL100km()) + " l/100"}));
            pdf.tableau(new float[]{12f, 22f, 14f, 13f, 11f, 14f, 14f},
                    new String[]{"Date", "Station", "Compteur", "Distance", "Litres",
                            "Montant", "Consommation"},
                    "ggdddd d", pleins);
        }
        pdf.note("La consommation se calcule d'un plein a l'autre : le premier plein sert de reference.");
    }

    private void maintenance(MiseEnPagePdf pdf, List<Long> camionIds) {
        pdf.section("Interventions ouvertes");
        List<String[]> interventions = new ArrayList<>();
        for (EntretienDto.Reponse entretien : entretienService.ouverts()) {
            if (!camionIds.isEmpty() && !camionIds.contains(entretien.camionId())) {
                continue;
            }
            interventions.add(new String[]{
                    entretien.numero(),
                    entretien.camionImmatriculation(),
                    entretien.typeLibelle(),
                    entretien.libelle(),
                    entretien.kilometragePrevu() == null ? "—"
                            : MiseEnPagePdf.nombre(entretien.kilometragePrevu()) + " km",
                    MiseEnPagePdf.date(entretien.datePrevue()),
                    entretien.kilometresRestants() == null ? "—"
                            : MiseEnPagePdf.nombre(entretien.kilometresRestants()) + " km",
                    entretien.statutLibelle()});
        }
        pdf.tableau(new float[]{12f, 12f, 14f, 22f, 12f, 10f, 10f, 8f},
                new String[]{"Numero", "Camion", "Type", "Libelle", "Echeance km",
                        "Echeance", "Restant", "Statut"},
                "ggggddd g", interventions);

        pdf.section("Pneumatiques a remplacer");
        List<String[]> pneus = new ArrayList<>();
        for (PneuDto.Reponse pneu : pneuService.aRemplacer()) {
            if (!camionIds.isEmpty() && !camionIds.contains(pneu.camionId())) {
                continue;
            }
            pneus.add(new String[]{
                    pneu.reference(), valeur(pneu.camionImmatriculation()), valeur(pneu.position()),
                    valeur(pneu.marque()),
                    pneu.kilometresParcourus() == null ? "—"
                            : MiseEnPagePdf.nombre(pneu.kilometresParcourus()) + " km",
                    pneu.usurePourcent() == null ? "—" : pneu.usurePourcent() + " %",
                    pneu.etatLibelle()});
        }
        pdf.tableau(new float[]{16f, 14f, 10f, 16f, 16f, 12f, 16f},
                new String[]{"Reference", "Camion", "Position", "Marque", "Kilometres",
                        "Usure", "Etat"},
                "ggggdd g", pneus);

        pdf.section("Incidents ouverts");
        List<String[]> incidents = new ArrayList<>();
        for (IncidentDto.Reponse incident : incidentService.ouverts()) {
            if (!camionIds.isEmpty() && !camionIds.contains(incident.camionId())) {
                continue;
            }
            incidents.add(new String[]{
                    incident.numero(), incident.camionImmatriculation(), incident.typeLibelle(),
                    incident.graviteLibelle(), valeur(incident.chauffeurNom()),
                    incident.description(), incident.statutLibelle()});
        }
        pdf.tableau(new float[]{12f, 12f, 14f, 10f, 14f, 26f, 12f},
                new String[]{"Numero", "Camion", "Type", "Gravite", "Chauffeur",
                        "Description", "Statut"},
                "ggggggg", incidents);
    }

    private void chauffeurs(MiseEnPagePdf pdf) {
        pdf.section("Effectif");
        List<String[]> lignes = new ArrayList<>();
        chauffeurService.rechercher(null, null, true, PageRequest.of(0, LIGNES_MAX, Sort.by("nom")))
                .forEach(chauffeur -> lignes.add(new String[]{
                        chauffeur.matricule(), chauffeur.nomComplet(), chauffeur.telephone(),
                        valeur(chauffeur.categoriesPermis()),
                        MiseEnPagePdf.date(chauffeur.permisExpiration()),
                        valeur(chauffeur.garantNom()),
                        chauffeur.statutLibelle()}));
        pdf.tableau(new float[]{11f, 20f, 15f, 10f, 13f, 19f, 12f},
                new String[]{"Matricule", "Nom", "Telephone", "Permis", "Expiration",
                        "Garant", "Statut"},
                "ggggggg", lignes);

        List<ChauffeurDto.Reponse> permis = chauffeurService.permisAExpirer(60);
        if (!permis.isEmpty()) {
            pdf.section("Permis a renouveler sous soixante jours");
            List<String[]> alertes = new ArrayList<>();
            permis.forEach(chauffeur -> alertes.add(new String[]{
                    chauffeur.matricule(), chauffeur.nomComplet(),
                    MiseEnPagePdf.date(chauffeur.permisExpiration()), chauffeur.telephone()}));
            pdf.tableau(new float[]{20f, 35f, 20f, 25f},
                    new String[]{"Matricule", "Nom", "Expiration", "Telephone"}, "gggg", alertes);
        }
    }

    private void creances(MiseEnPagePdf pdf) {
        FactureDto.Creances creances = factureService.creances();
        pdf.indicateurs(List.of(
                new MiseEnPagePdf.Indicateur("Restant du",
                        MiseEnPagePdf.montant(creances.montantRestant()) + " F",
                        creances.nombreFacturesImpayees() + " facture(s)", MiseEnPagePdf.ROUGE),
                MiseEnPagePdf.Indicateur.de("Montant facture",
                        MiseEnPagePdf.montant(creances.montantFacture()) + " F"),
                MiseEnPagePdf.Indicateur.de("Deja encaisse",
                        MiseEnPagePdf.montant(creances.montantPaye()) + " F"),
                MiseEnPagePdf.Indicateur.de("En retard", String.valueOf(creances.nombreEnRetard()),
                        "echeance depassee")));

        pdf.section("Factures non soldees");
        List<String[]> lignes = new ArrayList<>();
        for (FactureDto.Reponse facture : factureService.impayees()) {
            lignes.add(new String[]{
                    facture.numero(), facture.clientNom(),
                    MiseEnPagePdf.date(facture.dateEmission()),
                    MiseEnPagePdf.date(facture.dateEcheance()),
                    MiseEnPagePdf.montant(facture.montantTtc()),
                    MiseEnPagePdf.montant(facture.montantPaye()),
                    MiseEnPagePdf.montant(facture.resteAPayer()),
                    facture.enRetard() ? "En retard" : facture.statutLibelle()});
        }
        pdf.tableau(new float[]{13f, 20f, 11f, 11f, 12f, 11f, 12f, 10f},
                new String[]{"Facture", "Client", "Emission", "Echeance", "Total TTC",
                        "Regle", "Reste du", "Statut"},
                "gggddd dg", lignes);
    }

    // ------------------------------------------------------------------

    private List<Long> tousLesCamions() {
        return camionService.rechercher(null, null, true, PageRequest.of(0, LIGNES_MAX))
                .map(CamionDto.Reponse::id)
                .getContent();
    }

    /** Les rapports par camion n'acceptent qu'un filtre simple cote service. */
    private Long unique(List<Long> camionIds) {
        return camionIds.size() == 1 ? camionIds.get(0) : null;
    }

    private String valeur(String texte) {
        return texte == null || texte.isBlank() ? "—" : texte;
    }
}
