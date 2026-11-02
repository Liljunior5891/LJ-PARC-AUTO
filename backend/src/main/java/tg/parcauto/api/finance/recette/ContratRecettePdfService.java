package tg.parcauto.api.finance.recette;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.PolicesPdf;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.exploitation.client.Client;
import tg.parcauto.api.exploitation.location.Location;
import tg.parcauto.api.exploitation.mission.Mission;
import tg.parcauto.api.finance.facture.FacturePdfService;
import tg.parcauto.api.parametrage.Societe;
import tg.parcauto.api.parametrage.SocieteService;
import tg.parcauto.api.parc.affectation.AffectationRepository;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.chauffeur.Chauffeur;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Contrat a imprimer apres l'enregistrement d'une recette, adapte a son type :
 * location de vehicule, transport de marchandises, livraison, voyage, mission
 * speciale, prestation sous contrat, avenant (frais ou kilometres
 * supplementaires) ou prestation de services.
 * <p>
 * Meme presentation que la facture (bandeaux, logo, coordonnees de
 * l'emetteur). Le texte reprend les clauses usuelles du droit OHADA
 * applicable au Togo ; il sert de base et doit etre relu par le conseil de
 * l'entreprise avant usage contractuel.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContratRecettePdfService {

    private static final Color BLEU = new Color(31, 111, 209);
    private static final Color SOMBRE = new Color(34, 39, 46);
    private static final Color TEXTE = new Color(55, 62, 72);
    private static final Color GRIS = new Color(110, 118, 130);
    private static final Color FILET = new Color(222, 226, 232);
    private static final Color FOND_DOUX = new Color(244, 247, 251);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId FUSEAU = ZoneId.of("Africa/Lome");

    private final RecetteRepository repository;
    private final SocieteService societeService;
    private final FacturePdfService facturePdf;
    private final AffectationRepository affectationRepository;

    /** Un article du contrat : son titre et ses alineas. */
    private record Article(String titre, List<String> alineas) {
        Article(String titre, String... alineas) {
            this(titre, Arrays.stream(alineas).filter(a -> a != null && !a.isBlank()).toList());
        }
    }

    public byte[] produire(Long recetteId) {
        Recette recette = repository.findById(recetteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Recette", recetteId));
        Societe societe = societeService.entite();
        Chauffeur chauffeur = chauffeur(recette);

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 132, 70);
        PdfWriter writer = PdfWriter.getInstance(document, sortie);
        writer.setPageEvent(facturePdf.decorPage(societe, false));
        document.open();

        titre(document, recette);
        parties(document, societe, recette.getClient());
        String preambule = "Il a été convenu et arrêté ce qui suit :";
        document.add(paragraphe(preambule, police(9.5f, Font.ITALIC, GRIS), 4, 8));

        int numero = 1;
        for (Article article : articles(recette, societe, chauffeur)) {
            article(document, numero++, article);
        }
        if (avecReception(recette.getType())) {
            reception(document, recette.getType());
        }
        signatures(document, societe, recette);

        document.close();
        return sortie.toByteArray();
    }

    public String nomFichier(Long recetteId) {
        return repository.findById(recetteId)
                .map(r -> "contrat_" + r.getNumero() + ".pdf")
                .orElse("contrat.pdf");
    }

    // ------------------------------------------------------------------ Contenu

    /** Intitule du document selon le type de recette. */
    static String intitule(TypeRecette type) {
        return switch (type) {
            case LOCATION -> "CONTRAT DE LOCATION DE VÉHICULE";
            case TRANSPORT -> "CONTRAT DE TRANSPORT DE MARCHANDISES";
            case LIVRAISON -> "CONTRAT DE PRESTATION DE LIVRAISON";
            case VOYAGE -> "CONTRAT DE VOYAGE AVEC CHAUFFEUR";
            case MISSION_SPECIALE -> "CONTRAT DE MISSION SPÉCIALE";
            case CONTRAT -> "CONTRAT DE PRESTATION";
            case FRAIS_SUPPLEMENTAIRE -> "AVENANT — FRAIS SUPPLÉMENTAIRES";
            case KILOMETRAGE_SUPPLEMENTAIRE -> "AVENANT — KILOMÉTRAGE SUPPLÉMENTAIRE";
            case AUTRE -> "CONTRAT DE PRESTATION DE SERVICES";
        };
    }

    private List<Article> articles(Recette r, Societe societe, Chauffeur chauffeur) {
        List<Article> articles = new ArrayList<>();
        Location location = r.getLocation();
        Mission mission = r.getMission();
        TypeRecette type = r.getType();
        String vehicule = vehicule(r.getCamion());
        String conducteur = chauffeur == null ? null : chauffeur.nomComplet()
                + (renseigne(chauffeur.getNumeroPermis()) ? ", titulaire du permis n° " + chauffeur.getNumeroPermis() : "")
                + (renseigne(chauffeur.getTelephone()) ? " (tél. " + chauffeur.getTelephone() + ")" : "");
        String depart = premier(r.getLieuDepart(), location == null ? null : location.getLieuDepart(),
                mission == null ? null : mission.getLieuDepart());
        String destination = premier(r.getDestination(), location == null ? null : location.getDestination(),
                mission == null ? null : mission.getDestination());
        String marchandise = premier(r.getMarchandise(), mission == null ? null : mission.getMarchandise());
        LocalDate debut = premierDate(r.getDateDebut(), location == null ? null : location.getDateDebut(),
                mission == null || mission.getDateDepart() == null ? null : LocalDate.ofInstant(mission.getDateDepart(), FUSEAU),
                r.getDateRecette());
        LocalDate fin = premierDate(r.getDateFin(), location == null ? null : location.getDateFin(),
                mission == null || mission.getDateArrivee() == null ? null : LocalDate.ofInstant(mission.getDateArrivee(), FUSEAU));
        String periode = fin == null || fin.equals(debut) ? "le " + date(debut)
                : "du " + date(debut) + " au " + date(fin) + " inclus, soit " + (ChronoUnit.DAYS.between(debut, fin) + 1) + " jour(s)";
        String trajet = renseigne(depart) || renseigne(destination)
                ? "au départ de " + texte(depart, "lieu convenu") + " à destination de " + texte(destination, "lieu convenu")
                : null;
        String prix = montant(r.getMontant()) + " FCFA (" + enLettres(r.getMontant()) + " francs CFA)";

        // --- Article 1 : objet
        String objet = switch (type) {
            case LOCATION -> "Le Prestataire donne en location au Client, qui l'accepte, le véhicule désigné à l'article 2"
                    + (chauffeur == null ? ", sans chauffeur" : ", avec chauffeur") + ", pour la période " + periode + ".";
            case TRANSPORT -> "Le Client (expéditeur) confie au Prestataire (transporteur), qui l'accepte, le transport par route "
                    + "de la marchandise décrite ci-après, " + texte(trajet, "selon l'itinéraire convenu") + ".";
            case LIVRAISON -> "Le Prestataire s'engage à enlever et à livrer pour le compte du Client la marchandise décrite "
                    + "ci-après, " + texte(trajet, "à l'adresse convenue") + ", " + periode + ".";
            case VOYAGE -> "Le Prestataire met à la disposition du Client un véhicule avec chauffeur pour un voyage "
                    + texte(trajet, "selon l'itinéraire convenu") + ", " + periode + ".";
            case MISSION_SPECIALE -> "Le Prestataire s'engage à exécuter pour le Client la mission spéciale suivante : "
                    + texte(r.getLibelle(), "mission convenue entre les parties") + ".";
            case CONTRAT -> "Le Prestataire s'engage à exécuter pour le Client la prestation suivante, dans le cadre des "
                    + "accords conclus entre les parties : " + texte(r.getLibelle(), "prestation convenue") + ".";
            case FRAIS_SUPPLEMENTAIRE -> "Le présent avenant constate les frais supplémentaires suivants, engagés pour le "
                    + "compte du Client" + rattachement(r) + " : " + texte(r.getLibelle(), "frais supplémentaires") + ".";
            case KILOMETRAGE_SUPPLEMENTAIRE -> "Le présent avenant constate le kilométrage parcouru au-delà de celui prévu"
                    + rattachement(r) + (r.getKilometres() == null ? "" : ", soit " + r.getKilometres() + " km supplémentaires")
                    + ".";
            case AUTRE -> "Le Prestataire s'engage à fournir au Client la prestation suivante : "
                    + texte(r.getLibelle(), "prestation convenue") + ".";
        };
        articles.add(new Article("Objet", objet,
                type == TypeRecette.TRANSPORT || type == TypeRecette.LIVRAISON
                        ? "Marchandise : " + texte(marchandise, "telle que déclarée par le Client au chargement")
                        + ". Le Client garantit l'exactitude de sa déclaration (nature, poids, valeur, caractère dangereux)."
                        : null));

        // --- Article 2 : vehicule et chauffeur
        if (type != TypeRecette.FRAIS_SUPPLEMENTAIRE && type != TypeRecette.KILOMETRAGE_SUPPLEMENTAIRE) {
            articles.add(new Article("Véhicule et chauffeur",
                    "Véhicule affecté : " + vehicule + ".",
                    conducteur == null
                            ? (type == TypeRecette.LOCATION ? "Le véhicule est conduit par le Client ou par une personne qu'il "
                            + "désigne, titulaire d'un permis de conduire valide de la catégorie requise." : null)
                            : "Chauffeur : " + conducteur + ". Il reste sous l'autorité exclusive du Prestataire.",
                    "Le Prestataire peut, en cas de panne ou d'empêchement, remplacer le véhicule ou le chauffeur par un "
                            + "équivalent, sans supplément de prix."));
        }

        // --- Article 3 : duree, itineraire
        if (type != TypeRecette.FRAIS_SUPPLEMENTAIRE && type != TypeRecette.KILOMETRAGE_SUPPLEMENTAIRE) {
            String kilometrage = location != null && location.getKilometragePrevu() != null
                    ? "Kilométrage prévu : " + location.getKilometragePrevu() + " km ; tout kilomètre au-delà fera l'objet "
                    + "d'un avenant au tarif convenu." : null;
            articles.add(new Article(type == TypeRecette.LOCATION ? "Durée de la location" : "Date et itinéraire",
                    "Exécution : " + periode + ".",
                    trajet == null ? null : "Itinéraire : " + trajet + ".",
                    kilometrage));
        }

        // --- Article 4 : prix et paiement
        String reglement = r.getModePaiement() == null ? "selon les modalités convenues entre les parties"
                : "par " + r.getModePaiement().getLibelle().toLowerCase(Locale.FRENCH)
                        .replace("especes", "espèces").replace("cheque", "chèque")
                + (renseigne(r.getReference()) ? " (référence " + r.getReference() + ")" : "");
        articles.add(new Article("Prix et paiement",
                "Le prix de la prestation est fixé à " + prix + ", toutes taxes comprises sauf mention contraire.",
                location != null && location.getTarif() != null && type == TypeRecette.LOCATION
                        ? "Base de calcul : " + montant(location.getTarif()) + " FCFA " + location.getTypeLocation().getUnite()
                        .replace("FCFA ", "") + "." : null,
                "Il est réglé " + reglement + ". Le présent contrat vaut reçu pour toute somme déjà encaissée.",
                type == TypeRecette.LOCATION && location != null && location.getCaution() != null
                        && location.getCaution().signum() > 0
                        ? "Une caution de " + montant(location.getCaution()) + " FCFA est versée par le Client ; elle lui est "
                        + "restituée à la restitution du véhicule, déduction faite des sommes dues au titre du présent contrat."
                        : null,
                "Tout retard de paiement ouvre droit, après mise en demeure restée sans effet, à la suspension des prestations."));

        // --- Clauses propres au type
        switch (type) {
            case LOCATION -> {
                articles.add(new Article("État du véhicule",
                        "Le véhicule est remis en bon état de marche, avec ses documents de bord (carte grise, assurance, "
                                + "visite technique). Un état des lieux contradictoire est dressé au départ et au retour.",
                        "Le carburant est à la charge du Client, sauf stipulation contraire ; le véhicule est restitué avec "
                                + "le même niveau de carburant qu'au départ."));
                articles.add(new Article("Obligations du locataire",
                        "Le Client utilise le véhicule en bon père de famille, conformément à sa destination et au code de la "
                                + "route. Il s'interdit de le sous-louer, de le prêter à un tiers non autorisé, de l'utiliser "
                                + "pour des courses de vitesse, le transport rémunéré de personnes ou toute activité illicite.",
                        "Les amendes, contraventions et péages encourus pendant la location sont à sa charge.",
                        location != null && renseigne(location.getDepensesChargeClient())
                                ? "Dépenses à la charge du Client : " + location.getDepensesChargeClient() + "." : null,
                        location != null && renseigne(location.getDepensesChargeEntreprise())
                                ? "Dépenses à la charge du Prestataire : " + location.getDepensesChargeEntreprise() + "." : null));
                articles.add(new Article("Restitution",
                        "Le véhicule est restitué à la date et au lieu convenus. Toute journée de retard non autorisée est "
                                + "facturée au tarif journalier, sans préjudice des dommages et intérêts.",
                        "Les dégâts constatés au retour et non couverts par l'assurance sont à la charge du Client."));
            }
            case TRANSPORT, LIVRAISON -> {
                articles.add(new Article("Obligations du Client",
                        "Le Client remet la marchandise convenablement emballée et étiquetée, accompagnée des documents "
                                + "nécessaires à son transport (bordereaux, documents douaniers, autorisations).",
                        "Le chargement et le déchargement sont à sa charge, sauf convention contraire."));
                articles.add(new Article("Obligations et responsabilité du Prestataire",
                        "Le Prestataire achemine la marchandise avec diligence, dans les délais convenus, et en assure la "
                                + "garde jusqu'à la livraison.",
                        "Sa responsabilité en cas de perte, d'avarie ou de retard est régie par l'Acte uniforme OHADA "
                                + "relatif aux contrats de transport de marchandises par route, dans les limites qu'il fixe, "
                                + "sauf déclaration de valeur acceptée par écrit.",
                        "Le destinataire vérifie la marchandise à la livraison et porte ses réserves éventuelles, précises "
                                + "et motivées, sur le bon de réception ci-dessous ; à défaut, la marchandise est présumée "
                                + "livrée en bon état."));
            }
            case VOYAGE, MISSION_SPECIALE -> articles.add(new Article("Conditions d'exécution",
                    "Le chauffeur respecte la réglementation sur les temps de conduite et de repos ; l'itinéraire peut être "
                            + "adapté pour des raisons de sécurité.",
                    "Sauf stipulation contraire, le carburant, les péages et l'hébergement du chauffeur sont compris dans le prix.",
                    "Le Client s'interdit de faire transporter des personnes au-delà du nombre de places autorisées ou des "
                            + "marchandises illicites ou dangereuses non déclarées."));
            case FRAIS_SUPPLEMENTAIRE, KILOMETRAGE_SUPPLEMENTAIRE -> articles.add(new Article("Portée de l'avenant",
                    "Le présent avenant complète le contrat principal" + rattachement(r) + ", dont toutes les autres "
                            + "clauses demeurent inchangées."));
            default -> {
            }
        }

        // --- Clauses communes
        if (type != TypeRecette.FRAIS_SUPPLEMENTAIRE && type != TypeRecette.KILOMETRAGE_SUPPLEMENTAIRE) {
            articles.add(new Article("Assurance",
                    "Le véhicule est couvert par l'assurance responsabilité civile obligatoire souscrite par le Prestataire. "
                            + "Tout sinistre est déclaré au Prestataire dans les vingt-quatre heures."));
            articles.add(new Article("Force majeure",
                    "Aucune partie n'est responsable de l'inexécution due à un cas de force majeure (événement imprévisible, "
                            + "irrésistible et extérieur). La partie empêchée en informe l'autre sans délai."));
            articles.add(new Article("Résiliation",
                    "En cas de manquement grave de l'une des parties, l'autre peut résilier le contrat de plein droit huit "
                            + "jours après une mise en demeure restée sans effet."));
        }
        articles.add(new Article("Litiges",
                "Le présent contrat est soumis au droit togolais et aux Actes uniformes de l'OHADA. Les parties recherchent "
                        + "d'abord une solution amiable ; à défaut, le litige est porté devant le tribunal compétent de "
                        + texte(societe.getVille(), "Lomé") + "."));
        if (renseigne(r.getObservations())) {
            articles.add(new Article("Conditions particulières", r.getObservations()));
        }
        if (location != null && renseigne(location.getConditions())) {
            articles.add(new Article("Conditions du contrat de location", location.getConditions()));
        }
        return articles;
    }

    /** Le bon de réception suit les prestations ou une marchandise change de mains. */
    private static boolean avecReception(TypeRecette type) {
        return type == TypeRecette.TRANSPORT || type == TypeRecette.LIVRAISON;
    }

    private static String rattachement(Recette r) {
        if (r.getLocation() != null) {
            return " (contrat de location n° " + r.getLocation().getNumero() + ")";
        }
        if (r.getMission() != null) {
            return " (mission n° " + r.getMission().getNumero() + ")";
        }
        return "";
    }

    /** Chauffeur : celui du contrat ou de la mission, a defaut celui affecte au vehicule. */
    private Chauffeur chauffeur(Recette r) {
        if (r.getLocation() != null && r.getLocation().getChauffeur() != null) {
            return r.getLocation().getChauffeur();
        }
        if (r.getMission() != null && r.getMission().getChauffeur() != null) {
            return r.getMission().getChauffeur();
        }
        if (r.getType() == TypeRecette.LOCATION) {
            // Une location sans chauffeur designe est une location sans chauffeur.
            return null;
        }
        return affectationRepository.affectationsEnCours().stream()
                .filter(a -> a.getCamion().getId().equals(r.getCamion().getId()))
                .map(a -> a.getChauffeur())
                .findFirst().orElse(null);
    }

    private static String vehicule(Camion c) {
        String modele = String.join(" ", texte(c.getMarque(), ""), texte(c.getModele(), "")).trim();
        return (modele.isEmpty() ? "véhicule" : modele) + ", immatriculé " + c.getImmatriculation()
                + (renseigne(c.getType()) ? " (" + c.getType().toLowerCase(Locale.FRENCH) + ")" : "")
                + (c.getCapaciteTonnes() != null ? ", charge utile " + c.getCapaciteTonnes().stripTrailingZeros().toPlainString() + " t" : "");
    }

    // ------------------------------------------------------------------ Mise en page

    private void titre(Document document, Recette r) {
        Paragraph titre = paragraphe(intitule(r.getType()), police(19, Font.BOLD, SOMBRE), 0, 2);
        titre.setAlignment(Element.ALIGN_CENTER);
        document.add(titre);
        Paragraph reference = paragraphe("N° " + r.getNumero() + "   ·   du " + date(r.getDateRecette()),
                police(9, Font.BOLD, BLEU), 0, 14);
        reference.setAlignment(Element.ALIGN_CENTER);
        document.add(reference);
    }

    private void parties(Document document, Societe s, Client c) {
        document.add(paragraphe("ENTRE LES SOUSSIGNÉS :", police(8.5f, Font.BOLD, BLEU), 0, 6));
        PdfPTable table = new PdfPTable(new float[]{50f, 50f});
        table.setWidthPercentage(100);
        table.addCell(blocPartie("LE PRESTATAIRE", s.getNom(), joindre(" — ", s.getFormeJuridique(), s.getSigle()),
                joindre(", ", s.getAdresse(), s.getVille(), s.getPays()), s.getTelephone(), s.getEmail(),
                joindre("   ", renseigne(s.getRccm()) ? "RCCM " + s.getRccm() : null, renseigne(s.getNif()) ? "NIF " + s.getNif() : null)));
        String nomClient = c == null ? "………………………………………………" : c.getRaisonSociale();
        table.addCell(blocPartie("LE CLIENT", nomClient,
                c == null ? null : c.getType().getLibelle()
                        + (renseigne(c.getPersonneContact()) ? " — Représentant : " + c.getPersonneContact() : ""),
                c == null ? "Adresse : ………………………………" : joindre(", ", c.getAdresse(), c.getVille(), c.getPays()),
                c == null ? "Téléphone : ………………………" : c.getTelephone(),
                c == null ? null : c.getEmail(),
                c == null ? null : joindre("   ", renseigne(c.getRccm()) ? "RCCM " + c.getRccm() : null,
                        renseigne(c.getIdentifiantFiscal()) ? "NIF " + c.getIdentifiantFiscal() : null)));
        table.setSpacingAfter(4);
        document.add(table);
    }

    private PdfPCell blocPartie(String role, String nom, String... lignes) {
        PdfPCell cellule = new PdfPCell();
        cellule.setBorder(Rectangle.NO_BORDER);
        cellule.setBackgroundColor(FOND_DOUX);
        cellule.setPadding(10);
        cellule.setPaddingTop(6);
        cellule.addElement(paragraphe(role, police(7.5f, Font.BOLD, GRIS), 0, 0));
        cellule.addElement(paragraphe(nom, police(11.5f, Font.BOLD, SOMBRE), 1, 2));
        for (String ligne : lignes) {
            if (renseigne(ligne)) {
                cellule.addElement(paragraphe(ligne, police(8.5f, Font.NORMAL, TEXTE), 0, 0));
            }
        }
        return cellule;
    }

    private void article(Document document, int numero, Article article) {
        // Une table d'une cellule garde le titre et ses alineas sur la meme page.
        PdfPTable bloc = new PdfPTable(1);
        bloc.setWidthPercentage(100);
        bloc.setKeepTogether(true);
        bloc.setSpacingBefore(7);
        PdfPCell cellule = new PdfPCell();
        cellule.setBorder(Rectangle.NO_BORDER);
        cellule.setPadding(0);
        cellule.addElement(paragraphe("Article " + numero + " — " + article.titre(), police(10, Font.BOLD, SOMBRE), 0, 2));
        for (String alinea : article.alineas()) {
            Paragraph p = paragraphe(alinea, police(9, Font.NORMAL, TEXTE), 0, 2);
            p.setAlignment(Element.ALIGN_JUSTIFIED);
            p.setLeading(12.5f);
            cellule.addElement(p);
        }
        bloc.addCell(cellule);
        document.add(bloc);
    }

    private void reception(Document document, TypeRecette type) {
        PdfPTable cadre = new PdfPTable(1);
        cadre.setWidthPercentage(100);
        cadre.setSpacingBefore(12);
        cadre.setKeepTogether(true);
        PdfPCell cellule = new PdfPCell();
        cellule.setBorderColor(FILET);
        cellule.setPadding(10);
        cellule.addElement(paragraphe(type == TypeRecette.LIVRAISON ? "BON DE LIVRAISON — RÉCEPTION"
                : "RÉCEPTION DE LA MARCHANDISE", police(9, Font.BOLD, BLEU), 0, 6));
        for (String ligne : List.of(
                "Reçue par (nom et qualité) : ……………………………………………………………………………………",
                "Date et heure de livraison : ………………………………    Nombre de colis / poids : ……………………",
                "Réserves éventuelles : ………………………………………………………………………………………………………",
                "……………………………………………………………………………………………………………………………………………………",
                "Signature et cachet du destinataire :")) {
            cellule.addElement(paragraphe(ligne, police(8.5f, Font.NORMAL, TEXTE), 3, 3));
        }
        cellule.setMinimumHeight(118);
        cadre.addCell(cellule);
        document.add(cadre);
    }

    private void signatures(Document document, Societe s, Recette r) {
        Paragraph fait = paragraphe("Fait à " + texte(s.getVille(), "Lomé") + ", le " + date(r.getDateRecette())
                + ", en deux (2) exemplaires originaux, un pour chaque partie.", police(9, Font.NORMAL, TEXTE), 14, 8);
        document.add(fait);
        PdfPTable table = new PdfPTable(new float[]{50f, 50f});
        table.setWidthPercentage(100);
        table.setKeepTogether(true);
        table.addCell(blocSignature("Pour le Prestataire", s.getNom()));
        table.addCell(blocSignature("Le Client", "Précédé de la mention « Lu et approuvé »"));
        document.add(table);
    }

    private PdfPCell blocSignature(String role, String detail) {
        PdfPCell cellule = new PdfPCell();
        cellule.setBorder(Rectangle.TOP);
        cellule.setBorderColor(FILET);
        cellule.setPadding(8);
        cellule.setMinimumHeight(78);
        cellule.addElement(paragraphe(role, police(9, Font.BOLD, SOMBRE), 0, 0));
        cellule.addElement(paragraphe(detail, police(7.5f, Font.ITALIC, GRIS), 0, 0));
        return cellule;
    }

    // ------------------------------------------------------------------ Aides

    private static Paragraph paragraphe(String texte, Font police, float avant, float apres) {
        Paragraph p = new Paragraph(new Phrase(texte, police));
        p.setSpacingBefore(avant);
        p.setSpacingAfter(apres);
        return p;
    }

    private static Font police(float taille, int style, Color couleur) {
        return PolicesPdf.police(taille, style, couleur);
    }

    private static boolean renseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    private static String texte(String valeur, String defaut) {
        return renseigne(valeur) ? valeur.trim() : defaut;
    }

    private static String premier(String... valeurs) {
        return Arrays.stream(valeurs).filter(ContratRecettePdfService::renseigne).findFirst().orElse(null);
    }

    private static LocalDate premierDate(LocalDate... dates) {
        return Arrays.stream(dates).filter(d -> d != null).findFirst().orElse(null);
    }

    private static String joindre(String separateur, String... morceaux) {
        return Arrays.stream(morceaux).filter(ContratRecettePdfService::renseigne).collect(Collectors.joining(separateur));
    }

    private static String date(LocalDate date) {
        return date == null ? "……/……/………" : DATE.format(date);
    }

    private static String montant(BigDecimal valeur) {
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symboles).format(valeur == null ? BigDecimal.ZERO : valeur);
    }

    // --------------------------------------------------------- Montant en lettres

    private static final String[] UNITES = {"zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit",
            "neuf", "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf"};
    private static final String[] DIZAINES = {"", "", "vingt", "trente", "quarante", "cinquante", "soixante",
            "soixante", "quatre-vingt", "quatre-vingt"};

    /** Montant entier en toutes lettres, a la francaise (ex. : 125 000 -> cent vingt-cinq mille). */
    static String enLettres(BigDecimal valeur) {
        long n = valeur == null ? 0 : valeur.setScale(0, RoundingMode.HALF_UP).longValue();
        if (n == 0) {
            return "zéro";
        }
        StringBuilder resultat = new StringBuilder();
        long milliards = n / 1_000_000_000L;
        long millions = (n / 1_000_000L) % 1000;
        long milliers = (n / 1000) % 1000;
        long reste = n % 1000;
        if (milliards > 0) {
            resultat.append(centaines((int) milliards, false)).append(milliards > 1 ? " milliards " : " milliard ");
        }
        if (millions > 0) {
            resultat.append(centaines((int) millions, false)).append(millions > 1 ? " millions " : " million ");
        }
        if (milliers > 0) {
            resultat.append(milliers == 1 ? "" : centaines((int) milliers, true) + " ").append("mille ");
        }
        if (reste > 0) {
            resultat.append(centaines((int) reste, false));
        }
        return resultat.toString().trim();
    }

    /** De 1 a 999. {@code devantMille} : « quatre-vingt » et « deux cent » restent sans s devant « mille ». */
    private static String centaines(int n, boolean devantMille) {
        int c = n / 100;
        int r = n % 100;
        StringBuilder s = new StringBuilder();
        if (c > 0) {
            s.append(c == 1 ? "cent" : UNITES[c] + " cent");
            if (c > 1 && r == 0 && !devantMille) {
                s.append('s');
            }
            if (r > 0) {
                s.append(' ');
            }
        }
        if (r > 0) {
            s.append(dizaines(r, devantMille));
        }
        return s.toString();
    }

    private static String dizaines(int n, boolean devantMille) {
        if (n < 20) {
            return UNITES[n];
        }
        int d = n / 10;
        int u = n % 10;
        if (d == 7 || d == 9) {
            // 70-79 : soixante-dix... ; 90-99 : quatre-vingt-dix...
            return DIZAINES[d] + (d == 7 && u == 1 ? " et " : "-") + UNITES[10 + u];
        }
        if (d == 8) {
            return u == 0 ? (devantMille ? "quatre-vingt" : "quatre-vingts") : "quatre-vingt-" + UNITES[u];
        }
        if (u == 0) {
            return DIZAINES[d];
        }
        return DIZAINES[d] + (u == 1 ? " et un" : "-" + UNITES[u]);
    }
}
