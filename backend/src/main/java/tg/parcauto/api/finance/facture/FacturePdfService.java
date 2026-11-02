package tg.parcauto.api.finance.facture;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPTableEvent;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.PolicesPdf;
import tg.parcauto.api.exploitation.client.Client;
import tg.parcauto.api.parametrage.Societe;
import tg.parcauto.api.parametrage.SocieteService;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Edition de la facture au format PDF, chapitre 27 du cahier des charges.
 * <p>
 * Mise en page : bandeaux decoratifs en haut et en bas de page (bleu de
 * l'application, zone noire du logo en haut, anthracite en pied), identite et coordonnees de l'emetteur
 * dans le bandeau, client a gauche et references de la facture a droite, tableau des
 * prestations a entete bleu, puis remerciement, mentions et signature face
 * aux totaux. Le contenu reste celui du cahier des charges : emetteur, client,
 * periode, detail par vehicule, total hors taxes, TVA, total a payer, echeance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacturePdfService {

    /** Bleu de l'application (menu, boutons). */
    private static final Color BLEU = new Color(31, 111, 209);
    /** Anthracite du bandeau du bas et des titres. */
    private static final Color SOMBRE = new Color(34, 39, 46);
    private static final Color TEXTE = new Color(55, 62, 72);
    private static final Color GRIS = new Color(110, 118, 130);
    private static final Color FILET = new Color(222, 226, 232);
    private static final Color FOND_DOUX = new Color(244, 247, 251);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Hauteur reservee aux bandeaux : le contenu commence et finit en dehors. */
    private static final float MARGE_HAUTE = 132;
    private static final float MARGE_BASSE = 70;
    private static final float MARGE_COTE = 42;

    private final FactureRepository repository;
    private final SocieteService societeService;

    /** Produit le PDF de la facture. */
    public byte[] produire(Long factureId) {
        Facture facture = repository.chargerAvecLignes(factureId)
                .orElseThrow(() -> new BusinessException("Facture introuvable : " + factureId));
        Societe societe = societeService.entite();

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, MARGE_COTE, MARGE_COTE, MARGE_HAUTE, MARGE_BASSE);
        PdfWriter writer = PdfWriter.getInstance(document, sortie);
        writer.setPageEvent(new Decor(societe, facture.getStatut() == StatutFacture.BROUILLON));
        document.open();

        enTete(document, facture, societe);
        tableauLignes(document, facture);
        pied(document, facture, societe);

        document.close();
        return sortie.toByteArray();
    }

    /**
     * Decor de page commun aux documents remis au client (facture, contrat) :
     * bandeaux, logo, coordonnees et identifiants de l'emetteur.
     */
    public PdfPageEventHelper decorPage(Societe societe, boolean brouillon) {
        return new Decor(societe, brouillon);
    }

    public String nomFichier(Long factureId) {
        return repository.findById(factureId)
                .map(f -> "facture_" + f.getNumero() + ".pdf")
                .orElse("facture.pdf");
    }

    // ------------------------------------------------------------------
    // Client a gauche, references de la facture a droite
    // ------------------------------------------------------------------

    private void enTete(Document document, Facture facture, Societe societe) {
        PdfPTable table = new PdfPTable(new float[]{56f, 44f});
        table.setWidthPercentage(100);
        table.addCell(sansBordure(blocClient(facture.getClient())));
        table.addCell(sansBordure(blocReferences(facture, societe)));
        table.setSpacingAfter(22);
        document.add(table);
    }

    private PdfPCell blocClient(Client client) {
        PdfPCell cellule = new PdfPCell();
        cellule.setPaddingTop(28);
        cellule.addElement(paragraphe("FACTURÉ À :", police(9, Font.BOLD, BLEU), 0));
        cellule.addElement(paragraphe(client.getRaisonSociale(), police(17, Font.BOLD, SOMBRE), 2));
        if (renseigne(client.getPersonneContact())) {
            cellule.addElement(paragraphe("À l'attention de " + client.getPersonneContact(),
                    police(8.5f, Font.NORMAL, GRIS), 0));
        }

        PdfPTable details = new PdfPTable(new float[]{28f, 72f});
        details.setWidthPercentage(100);
        details.setSpacingBefore(10);
        ligneDetail(details, "Téléphone :", client.getTelephone());
        ligneDetail(details, "Email :", client.getEmail());
        ligneDetail(details, "Adresse :", joindre(", ", client.getAdresse(), client.getVille(), client.getPays()));
        ligneDetail(details, "NIF :", client.getIdentifiantFiscal());
        cellule.addElement(details);
        return cellule;
    }

    private PdfPCell blocReferences(Facture facture, Societe societe) {
        PdfPCell cellule = new PdfPCell();
        cellule.addElement(paragraphe("FACTURE", police(30, Font.BOLD, SOMBRE), 0));

        PdfPTable references = new PdfPTable(new float[]{40f, 60f});
        references.setWidthPercentage(100);
        references.setSpacingBefore(10);
        ligneDetail(references, "Facture n° :", facture.getNumero());
        ligneDetail(references, "Date d'émission :", facture.getDateEmission().format(DATE));
        ligneDetail(references, "Échéance :", facture.getDateEcheance() == null
                ? null : facture.getDateEcheance().format(DATE));
        if (facture.getPeriodeDebut() != null && facture.getPeriodeFin() != null) {
            ligneDetail(references, "Période :", facture.getPeriodeDebut().format(DATE)
                    + " au " + facture.getPeriodeFin().format(DATE));
        }
        cellule.addElement(references);

        // Coordonnees bancaires : le moyen de reglement, comme sur la maquette.
        if (renseigne(societe.getBanque()) || renseigne(societe.getCompteBancaire())) {
            cellule.addElement(paragraphe("Règlement", police(11, Font.BOLD, BLEU), 12));
            PdfPTable reglement = new PdfPTable(new float[]{40f, 60f});
            reglement.setWidthPercentage(100);
            reglement.setSpacingBefore(3);
            ligneDetail(reglement, "Banque :", societe.getBanque());
            ligneDetail(reglement, "Compte :", societe.getCompteBancaire());
            ligneDetail(reglement, "Bénéficiaire :", societe.getNom());
            cellule.addElement(reglement);
        }
        return cellule;
    }

    private void ligneDetail(PdfPTable table, String libelle, String valeur) {
        if (!renseigne(valeur)) {
            return;
        }
        PdfPCell gauche = sansBordure(new PdfPCell(new Phrase(libelle, police(8.5f, Font.BOLD, SOMBRE))));
        gauche.setPaddingBottom(3);
        PdfPCell droite = sansBordure(new PdfPCell(new Phrase(valeur, police(8.5f, Font.NORMAL, TEXTE))));
        droite.setPaddingBottom(3);
        table.addCell(gauche);
        table.addCell(droite);
    }

    // ------------------------------------------------------------------
    // Tableau des prestations
    // ------------------------------------------------------------------

    private void tableauLignes(Document document, Facture facture) {
        PdfPTable table = new PdfPTable(new float[]{6f, 33f, 14f, 15f, 14f, 18f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setTableEvent(new CadreTableau());

        entete(table, "N°", Element.ALIGN_CENTER);
        entete(table, "DÉSIGNATION", Element.ALIGN_LEFT);
        entete(table, "VÉHICULE", Element.ALIGN_LEFT);
        entete(table, "PRIX UNITAIRE", Element.ALIGN_RIGHT);
        entete(table, "QTÉ", Element.ALIGN_CENTER);
        entete(table, "TOTAL", Element.ALIGN_RIGHT);

        int numero = 1;
        for (FactureLigne ligne : facture.getLignes()) {
            cellule(table, String.format("%02d", numero++), Element.ALIGN_CENTER);
            cellule(table, ligne.getDesignation(), Element.ALIGN_LEFT);
            cellule(table, ligne.getCamion() == null ? "-" : ligne.getCamion().getImmatriculation(),
                    Element.ALIGN_LEFT);
            cellule(table, montant(ligne.getPrixUnitaire()), Element.ALIGN_RIGHT);
            cellule(table, montant(ligne.getQuantite())
                    + (ligne.getUnite() == null ? "" : " " + ligne.getUnite()), Element.ALIGN_CENTER);
            cellule(table, montant(ligne.getMontant()), Element.ALIGN_RIGHT);
        }
        document.add(table);
    }

    private void entete(PdfPTable table, String texte, int alignement) {
        // Le fond bleu arrondi est dessine par CadreTableau, sous les cellules.
        PdfPCell cellule = new PdfPCell(new Phrase(texte, police(8.5f, Font.BOLD, Color.WHITE)));
        cellule.setHorizontalAlignment(alignement);
        cellule.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cellule.setPaddingTop(9);
        cellule.setPaddingBottom(10);
        cellule.setPaddingLeft(8);
        cellule.setPaddingRight(8);
        cellule.setBorder(Rectangle.NO_BORDER);
        table.addCell(cellule);
    }

    private void cellule(PdfPTable table, String texte, int alignement) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, police(9, Font.NORMAL, TEXTE)));
        cellule.setHorizontalAlignment(alignement);
        cellule.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cellule.setPaddingTop(9);
        cellule.setPaddingBottom(10);
        cellule.setPaddingLeft(8);
        cellule.setPaddingRight(8);
        // Filets : bas de ligne et separations verticales, en gris clair.
        cellule.setBorder(Rectangle.BOTTOM | Rectangle.RIGHT);
        cellule.setBorderColor(FILET);
        cellule.setBorderWidth(0.6f);
        table.addCell(cellule);
    }

    // ------------------------------------------------------------------
    // Remerciement, mentions et signature face aux totaux
    // ------------------------------------------------------------------

    private void pied(Document document, Facture facture, Societe societe) {
        PdfPTable table = new PdfPTable(new float[]{52f, 48f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(14);
        table.setKeepTogether(true);
        table.addCell(sansBordure(blocMentions(facture, societe)));
        table.addCell(sansBordure(blocTotaux(facture)));
        document.add(table);
    }

    private PdfPCell blocMentions(Facture facture, Societe societe) {
        PdfPCell cellule = new PdfPCell();
        cellule.setPaddingRight(24);
        cellule.addElement(paragraphe("Merci de votre confiance.", police(11, Font.BOLD, SOMBRE), 10));

        String conditions = joindre("\n", societe.getMentionsFacture(), facture.getObservations());
        if (!conditions.isBlank()) {
            cellule.addElement(paragraphe("Conditions et mentions :", police(8.5f, Font.BOLD, BLEU), 12));
            Paragraph texte = paragraphe(conditions, police(8, Font.NORMAL, GRIS), 2);
            texte.setLeading(11);
            cellule.addElement(texte);
        }
        if (facture.getStatut() == StatutFacture.BROUILLON) {
            cellule.addElement(paragraphe("Document provisoire : cette facture n'est pas encore émise.",
                    police(8.5f, Font.BOLD, new Color(198, 40, 40)), 10));
        }

        // Emplacement de signature : un filet, puis le signataire.
        Paragraph filet = new Paragraph(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(
                0.6f, 55, GRIS, Element.ALIGN_LEFT, -2)));
        filet.setSpacingBefore(38);
        cellule.addElement(filet);
        cellule.addElement(paragraphe("Signature et cachet", police(9, Font.BOLD, SOMBRE), 8));
        cellule.addElement(paragraphe(societe.getNom(), police(7.5f, Font.NORMAL, GRIS), 0));
        return cellule;
    }

    private PdfPCell blocTotaux(Facture facture) {
        PdfPTable totaux = new PdfPTable(new float[]{44f, 56f});
        totaux.setWidthPercentage(100);
        ligneTotal(totaux, "Total HT :", montant(facture.getMontantHt()));
        ligneTotal(totaux, "TVA (" + montant(facture.getTauxTva()) + " %) :", montant(facture.getMontantTva()));
        if (facture.montantPaye().signum() > 0) {
            ligneTotal(totaux, "Déjà réglé :", montant(facture.montantPaye()));
        }

        // Ligne du total : un seul bloc bleu arrondi sur toute la largeur. Il met en avant le
        // reste a payer d'une facture entamee, le total d'une facture soldee ou non reglee.
        boolean reste = facture.montantPaye().signum() > 0 && facture.resteAPayer().signum() > 0;
        boolean soldee = facture.montantPaye().signum() > 0 && facture.resteAPayer().signum() == 0;
        PdfPTable fort = new PdfPTable(new float[]{44f, 56f});
        fort.setWidthPercentage(100);
        PdfPCell libelle = sansBordure(new PdfPCell(new Phrase(reste ? "Reste à payer :" : "Total :",
                police(11, Font.BOLD, Color.WHITE))));
        libelle.setHorizontalAlignment(Element.ALIGN_RIGHT);
        libelle.setPadding(10);
        PdfPCell valeur = sansBordure(new PdfPCell(new Phrase(
                montant(reste ? facture.resteAPayer() : facture.getMontantTtc()) + " FCFA",
                police(11, Font.BOLD, Color.WHITE))));
        valeur.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valeur.setPadding(10);
        fort.addCell(libelle);
        fort.addCell(valeur);

        PdfPCell bloc = new PdfPCell(fort);
        bloc.setColspan(2);
        bloc.setBorder(Rectangle.NO_BORDER);
        bloc.setPadding(0);
        bloc.setCellEvent(new FondArrondi(BLEU, 7));
        totaux.addCell(bloc);

        if (reste) {
            ligneTotal(totaux, "Total TTC :", montant(facture.getMontantTtc()));
        }
        if (soldee) {
            PdfPCell mention = sansBordure(new PdfPCell(new Phrase("Facture soldée - merci pour votre règlement",
                    police(8.5f, Font.BOLD, new Color(26, 127, 87)))));
            mention.setColspan(2);
            mention.setHorizontalAlignment(Element.ALIGN_RIGHT);
            mention.setPaddingTop(6);
            totaux.addCell(mention);
        }

        // Ajoute comme contenu : le tableau garde sa hauteur au lieu de s'etirer sur la ligne.
        PdfPCell cellule = new PdfPCell();
        cellule.setVerticalAlignment(Element.ALIGN_TOP);
        cellule.addElement(totaux);
        return cellule;
    }

    private void ligneTotal(PdfPTable table, String libelle, String valeur) {
        PdfPCell gauche = new PdfPCell(new Phrase(libelle, police(9, Font.NORMAL, TEXTE)));
        gauche.setHorizontalAlignment(Element.ALIGN_RIGHT);
        gauche.setPadding(8);
        gauche.setBorder(Rectangle.BOTTOM | Rectangle.RIGHT);
        gauche.setBorderColor(FILET);
        gauche.setBorderWidth(0.6f);
        PdfPCell droite = new PdfPCell(new Phrase(valeur + " FCFA", police(9, Font.BOLD, SOMBRE)));
        droite.setHorizontalAlignment(Element.ALIGN_RIGHT);
        droite.setPadding(8);
        droite.setBorder(Rectangle.BOTTOM);
        droite.setBorderColor(FILET);
        droite.setBorderWidth(0.6f);
        table.addCell(gauche);
        table.addCell(droite);
    }

    // ------------------------------------------------------------------
    // Decor de page : bandeaux du haut et du bas
    // ------------------------------------------------------------------

    /**
     * Bandeaux dessines sous le contenu de chaque page : en haut, une forme
     * noire a bord arrondi portant le logo et le nom de l'emetteur, sur
     * un bandeau bleu portant ses coordonnees ; en bas, un bandeau bleu et une
     * forme anthracite. Un brouillon porte en plus un filigrane.
     */
    private final class Decor extends PdfPageEventHelper {

        private final Societe societe;
        private final boolean brouillon;
        private final Optional<Image> logo;

        Decor(Societe societe, boolean brouillon) {
            this.societe = societe;
            this.brouillon = brouillon;
            this.logo = societeService.octetsLogo().flatMap(octets -> {
                try {
                    Image image = Image.getInstance(octets);
                    image.scaleToFit(46, 46);
                    return Optional.of(image);
                } catch (Exception e) {
                    // Un logo illisible ne doit pas empecher d'editer la facture.
                    log.warn("Logo de l'entreprise illisible, facture editee sans : {}", e.getMessage());
                    return Optional.empty();
                }
            });
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte dessous = writer.getDirectContentUnder();
            Rectangle page = document.getPageSize();
            float largeur = page.getWidth();
            float haut = page.getHeight();

            // --- Haut : logo et nom de l'emetteur, en blanc sur la zone noire.
            PdfContentByte dessus = writer.getDirectContent();
            float xTexte = MARGE_COTE;
            if (logo.isPresent()) {
                Image image = logo.get();
                image.setAbsolutePosition(MARGE_COTE, haut - 49 - image.getScaledHeight() / 2);
                try {
                    dessus.addImage(image);
                    xTexte += image.getScaledWidth() + 10;
                } catch (Exception e) {
                    log.warn("Logo non place sur la facture : {}", e.getMessage());
                }
            }
            String nom = societe.getNom().toUpperCase(Locale.FRENCH);
            Font policeNom = police(15, Font.BOLD, Color.WHITE);
            texte(dessus, nom, policeNom, xTexte, haut - 52, Element.ALIGN_LEFT);
            String sousTitre = joindre(" - ", societe.getFormeJuridique(), societe.getSigle())
                    .toUpperCase(Locale.FRENCH);
            Font policeSousTitre = police(6.5f, Font.NORMAL, new Color(170, 178, 190));
            if (!sousTitre.isBlank()) {
                texte(dessus, sousTitre, policeSousTitre, xTexte, haut - 64, Element.ALIGN_LEFT);
            }

            // La zone noire s'arrete juste apres le nom : le bandeau bleu recupere le reste de la largeur.
            float finTexte = xTexte + Math.max(
                    policeNom.getCalculatedBaseFont(false).getWidthPoint(nom, 15),
                    policeSousTitre.getCalculatedBaseFont(false).getWidthPoint(sousTitre, 6.5f));
            float bord = Math.max(150, Math.min(finTexte + 14, 290));

            dessous.saveState();
            dessous.setColorFill(BLEU);
            dessous.rectangle(0, haut - 64, largeur, 64);
            dessous.fill();
            dessous.setColorFill(SOMBRE);
            dessous.moveTo(0, haut);
            dessous.lineTo(bord, haut);
            dessous.curveTo(bord + 28, haut, bord + 36, haut - 40, bord + 20, haut - 70);
            dessous.curveTo(bord + 6, haut - 92, bord - 10, haut - 98, bord - 38, haut - 98);
            dessous.lineTo(0, haut - 98);
            dessous.closePath();
            dessous.fill();
            dessous.restoreState();

            // Coordonnees sur le bandeau bleu, en trois colonnes. Telephone et email sont mesures pour
            // tenir sur leur ligne (une adresse email ne se coupe pas) ; l'adresse prend le reste.
            float x = bord + 46;
            float reste = largeur - MARGE_COTE + 10 - x;
            String email = joindre("\n", societe.getEmail(), societe.getSiteWeb());
            float colonneTel = Math.min(Math.max(largeurTexte(societe.getTelephone()), 44) + 14, reste * 0.30f);
            float colonneEmail = Math.min(largeurTexte(email) + 14, reste - colonneTel - 70);
            coordonnee(dessus, "TÉLÉPHONE", societe.getTelephone(), x, haut - 16, colonneTel);
            coordonnee(dessus, "EMAIL", email, x + colonneTel, haut - 16, colonneEmail);
            coordonnee(dessus, "ADRESSE", joindre(", ", societe.getAdresse(), societe.getVille(), societe.getPays()),
                    x + colonneTel + colonneEmail, haut - 16, reste - colonneTel - colonneEmail);

            // --- Bas : bandeau bleu a gauche, forme anthracite arrondie a droite.
            dessous.saveState();
            dessous.setColorFill(BLEU);
            dessous.rectangle(0, 0, 330, 24);
            dessous.fill();
            dessous.setColorFill(SOMBRE);
            dessous.moveTo(largeur, 0);
            dessous.lineTo(largeur, 44);
            dessous.lineTo(330, 44);
            dessous.curveTo(300, 44, 286, 30, 276, 14);
            dessous.curveTo(270, 5, 264, 0, 254, 0);
            dessous.closePath();
            dessous.fill();
            dessous.restoreState();

            String identifiants = joindre("   -   ",
                    renseigne(societe.getNif()) ? "NIF : " + societe.getNif() : null,
                    renseigne(societe.getRccm()) ? "RCCM : " + societe.getRccm() : null);
            if (!identifiants.isBlank()) {
                texte(dessus, identifiants, police(7, Font.NORMAL, Color.WHITE), MARGE_COTE, 9, Element.ALIGN_LEFT);
            }
            texte(dessus, "Page " + writer.getPageNumber(), police(7, Font.NORMAL, Color.WHITE),
                    largeur - MARGE_COTE, 18, Element.ALIGN_RIGHT);

            if (brouillon) {
                filigrane(writer, page);
            }
        }

        private void coordonnee(PdfContentByte canvas, String libelle, String valeur, float x, float y, float largeur) {
            if (!renseigne(valeur)) {
                return;
            }
            ColumnText colonne = new ColumnText(canvas);
            colonne.setSimpleColumn(x, y - 44, x + largeur - 8, y + 8);
            colonne.setLeading(12);
            colonne.addText(new Phrase(libelle + "\n", police(7, Font.BOLD, new Color(214, 230, 251))));
            colonne.addText(new Phrase(valeur, police(9, Font.BOLD, Color.WHITE)));
            colonne.go();
        }

        /** Largeur de la plus longue ligne, dans la police des coordonnees. */
        private float largeurTexte(String valeur) {
            if (!renseigne(valeur)) {
                return 0;
            }
            Font police = police(9, Font.BOLD, Color.WHITE);
            return (float) Arrays.stream(valeur.split("\n"))
                    .mapToDouble(ligne -> police.getCalculatedBaseFont(false).getWidthPoint(ligne, 9))
                    .max().orElse(0);
        }

        private void filigrane(PdfWriter writer, Rectangle page) {
            PdfContentByte canvas = writer.getDirectContentUnder();
            canvas.saveState();
            PdfGState transparence = new PdfGState();
            transparence.setFillOpacity(0.07f);
            canvas.setGState(transparence);
            try {
                canvas.beginText();
                canvas.setFontAndSize(PolicesPdf.baseGrasse(), 92);
                canvas.setColorFill(SOMBRE);
                canvas.showTextAligned(Element.ALIGN_CENTER, "BROUILLON",
                        page.getWidth() / 2, page.getHeight() / 2, 35);
                canvas.endText();
            } catch (Exception e) {
                log.warn("Filigrane non ajoute : {}", e.getMessage());
            }
            canvas.restoreState();
        }
    }

    /** Fond bleu a coins arrondis de l'entete du tableau, et cadre gris autour du tableau. */
    private static final class CadreTableau implements PdfPTableEvent {
        @Override
        public void tableLayout(PdfPTable table, float[][] largeurs, float[] hauteurs, int lignesEntete,
                                int premiereLigne, PdfContentByte[] canvases) {
            float gauche = largeurs[0][0];
            float droite = largeurs[0][largeurs[0].length - 1];
            float haut = hauteurs[0];
            float bas = hauteurs[hauteurs.length - 1];
            float basEntete = hauteurs[1];

            PdfContentByte fond = canvases[PdfPTable.BACKGROUNDCANVAS];
            fond.saveState();
            fond.setColorFill(BLEU);
            fond.roundRectangle(gauche, basEntete, droite - gauche, haut - basEntete, 7);
            fond.fill();
            // Coins du bas de l'entete carres : il se raccorde aux lignes du tableau.
            fond.rectangle(gauche, basEntete, droite - gauche, 7);
            fond.fill();
            fond.restoreState();

            PdfContentByte trait = canvases[PdfPTable.LINECANVAS];
            trait.saveState();
            trait.setColorStroke(FILET);
            trait.setLineWidth(0.8f);
            trait.roundRectangle(gauche, bas, droite - gauche, haut - bas, 7);
            trait.stroke();
            trait.restoreState();
        }
    }

    /** Fond plein a coins arrondis derriere une cellule. */
    private record FondArrondi(Color couleur, float rayon) implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cellule, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte fond = canvases[PdfPTable.BACKGROUNDCANVAS];
            fond.saveState();
            fond.setColorFill(couleur);
            fond.roundRectangle(position.getLeft(), position.getBottom() + 4,
                    position.getWidth(), position.getHeight() - 4, rayon);
            fond.fill();
            fond.restoreState();
        }
    }

    // ------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------

    /** Texte d'une ligne pose a une position absolue ; y est la ligne de base. */
    private static void texte(PdfContentByte canvas, String texte, Font police, float x, float y, int alignement) {
        ColumnText.showTextAligned(canvas, alignement, new Phrase(texte, police), x, y, 0);
    }

    private static PdfPCell sansBordure(PdfPCell cellule) {
        cellule.setBorder(Rectangle.NO_BORDER);
        cellule.setPadding(0);
        return cellule;
    }

    private static Paragraph paragraphe(String texte, Font police, float espaceAvant) {
        Paragraph paragraphe = new Paragraph(texte, police);
        paragraphe.setSpacingBefore(espaceAvant);
        return paragraphe;
    }

    /** Police du projet (Plus Jakarta Sans), commune aux factures et aux rapports. */
    private static Font police(float taille, int style, Color couleur) {
        return PolicesPdf.police(taille, style, couleur);
    }

    private static boolean renseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    private static String joindre(String separateur, String... morceaux) {
        return Arrays.stream(morceaux)
                .filter(FacturePdfService::renseigne)
                .collect(Collectors.joining(separateur));
    }

    /** Montants a la francaise : espace pour les milliers, virgule decimale. */
    private static String montant(BigDecimal valeur) {
        if (valeur == null) {
            return "0";
        }
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symboles).format(valeur);
    }
}
