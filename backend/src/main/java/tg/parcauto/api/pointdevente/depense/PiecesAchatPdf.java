package tg.parcauto.api.pointdevente.depense;

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
import tg.parcauto.api.finance.facture.FacturePdfService;
import tg.parcauto.api.parametrage.Societe;
import tg.parcauto.api.parametrage.SocieteService;
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseur;
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseurRepository;
import tg.parcauto.api.pointdevente.approvisionnement.LigneCommandeFournisseur;
import tg.parcauto.api.pointdevente.fournisseur.Fournisseur;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pieces imprimees des achats, pour toutes les variantes du point de vente : la
 * fiche de depense (piece justificative a signer) et le bon de commande remis
 * au fournisseur. Meme gabarit que les factures (en-tete de la societe), au
 * format A4 ou A5 choisi dans le parametrage.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PiecesAchatPdf {

    private static final Color BLEU = new Color(31, 111, 209);
    private static final Color SOMBRE = new Color(34, 39, 46);
    private static final Color GRIS = new Color(110, 118, 130);
    private static final Color FILET = new Color(222, 226, 232);
    private static final Color FOND_DOUX = new Color(244, 247, 251);
    private static final Color ROUGE = new Color(198, 40, 40);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId ZONE = ZoneId.of("Africa/Lome");

    private final DepensePdvRepository depenses;
    private final CommandeFournisseurRepository commandes;
    private final SocieteService societeService;
    private final FacturePdfService facturePdf;

    // ------------------------------------------------------------- depense

    public byte[] ficheDepense(Long id) {
        DepensePdv d = depenses.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depense", id));
        boolean annulee = d.getStatut() == DepensePdv.Statut.ANNULEE;
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = ouvrir(sortie);

        List<String[]> references = new ArrayList<>();
        references.add(new String[]{"Depense N° :", d.getNumero()});
        references.add(new String[]{"Date :", DATE.format(d.getDateDepense())});
        references.add(new String[]{"Categorie :", d.getCategorie().getNom()});
        references.add(new String[]{"Payee par :", d.getMode().getLibelle()
                + (d.getSession() != null ? " - " + d.getSession().getCaisse().getNom() : "")});
        if (d.getSession() != null) {
            references.add(new String[]{"Session :", d.getSession().getNumero()});
        }
        if (d.getReference() != null) {
            references.add(new String[]{"Reference :", d.getReference()});
        }
        if (d.getPeriode() != null) {
            references.add(new String[]{"Mois paye :", d.getPeriode()});
        }
        String beneficiaire = d.getBeneficiaire() != null ? d.getBeneficiaire()
                : d.getFournisseur() != null ? d.getFournisseur().getNom() : "—";
        enTete(document, annulee ? "FICHE DE DÉPENSE - ANNULÉE" : "FICHE DE DÉPENSE", "BÉNÉFICIAIRE :", beneficiaire,
                d.getFournisseur() == null ? List.of() : coordonnees(d.getFournisseur()), references);

        PdfPTable table = new PdfPTable(new float[]{70f, 30f});
        table.setWidthPercentage(100);
        table.addCell(entete("Objet de la depense", Element.ALIGN_LEFT));
        table.addCell(entete("Montant", Element.ALIGN_RIGHT));
        table.addCell(ligne(d.getLibelle() + (d.getNote() == null ? "" : "\n" + d.getNote()), Element.ALIGN_LEFT));
        table.addCell(ligne(montant(d.getMontant()) + " F", Element.ALIGN_RIGHT));
        table.setSpacingAfter(10);
        document.add(table);

        PdfPTable total = new PdfPTable(new float[]{70f, 30f});
        total.setWidthPercentage(100);
        ligneTotal(total, "TOTAL PAYÉ :", montant(d.getMontant()) + " F");
        total.setSpacingAfter(8);
        document.add(total);

        Paragraph saisie = new Paragraph("Saisie par " + d.getAuteurNom()
                + (d.getCreeLe() == null ? "" : " le " + DATE_HEURE.format(d.getCreeLe().atZone(ZONE)))
                + (d.getJustificatifChemin() != null ? " · justificatif joint" : ""), police(8.5f, Font.ITALIC, GRIS));
        saisie.setSpacingAfter(annulee ? 6 : 30);
        document.add(saisie);
        if (annulee) {
            Paragraph motif = new Paragraph("Annulee par " + d.getAnnuleePar() + " : " + d.getMotifAnnulation(),
                    police(9, Font.BOLD, ROUGE));
            motif.setSpacingAfter(30);
            document.add(motif);
        }
        document.add(signatures("Etabli par :\n" + d.getAuteurNom(), "Recu par le beneficiaire :", "Visa de la direction :"));
        document.close();
        return societeService.auFormat(sortie.toByteArray());
    }

    public String nomFicheDepense(Long id) {
        return "fiche-depense_" + depenses.findById(id).map(DepensePdv::getNumero).orElse(String.valueOf(id)) + ".pdf";
    }

    // ------------------------------------------------------ bon de commande

    public byte[] bonCommande(Long id) {
        CommandeFournisseur c = commandes.detail(id).orElseThrow(() -> ResourceNotFoundException.of("Commande fournisseur", id));
        boolean annulee = c.getStatut() == CommandeFournisseur.Statut.ANNULEE;
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = ouvrir(sortie);

        List<String[]> references = new ArrayList<>();
        references.add(new String[]{"Commande N° :", c.getNumero()});
        references.add(new String[]{"Date :", DATE.format(c.getDateCommande())});
        if (c.getLivraisonPrevue() != null) {
            references.add(new String[]{"Livraison prevue :", DATE.format(c.getLivraisonPrevue())});
        }
        references.add(new String[]{"Statut :", switch (c.getStatut()) {
            case EN_COURS -> "A livrer";
            case RECUE -> "Receptionnee";
            case ANNULEE -> "Annulee";
        }});
        enTete(document, annulee ? "BON DE COMMANDE - ANNULÉ" : "BON DE COMMANDE", "FOURNISSEUR :", c.getFournisseur(),
                c.getFiche() == null ? List.of() : coordonnees(c.getFiche()), references);

        PdfPTable table = new PdfPTable(new float[]{46f, 12f, 12f, 15f, 15f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        String[] entetes = {"Désignation", "Qté", "Unité", "Prix unitaire", "Montant"};
        for (int i = 0; i < entetes.length; i++) {
            table.addCell(entete(entetes[i], i == 0 || i == 2 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT));
        }
        BigDecimal totalCommande = BigDecimal.ZERO;
        boolean pair = false;
        for (LigneCommandeFournisseur l : c.getLignes()) {
            Color fond = pair ? FOND_DOUX : null;
            table.addCell(ligne(l.getArticle().getNom() + "\n" + l.getArticle().getCode(), Element.ALIGN_LEFT, fond));
            table.addCell(ligne(quantite(l.getQuantite()), Element.ALIGN_RIGHT, fond));
            table.addCell(ligne(l.getArticle().getUnite() == null ? "" : l.getArticle().getUnite(), Element.ALIGN_LEFT, fond));
            table.addCell(ligne(l.getCoutUnitaire() == null ? "" : montant(l.getCoutUnitaire()), Element.ALIGN_RIGHT, fond));
            table.addCell(ligne(montant(l.total()), Element.ALIGN_RIGHT, fond));
            totalCommande = totalCommande.add(l.total());
            pair = !pair;
        }
        table.setSpacingAfter(10);
        document.add(table);

        PdfPTable bas = new PdfPTable(new float[]{55f, 45f});
        bas.setWidthPercentage(100);
        PdfPCell notes = sansBordure(new PdfPCell());
        if (c.getNote() != null) {
            notes.addElement(new Paragraph("Note", police(9, Font.BOLD, BLEU)));
            notes.addElement(new Paragraph(c.getNote(), police(8.5f, Font.NORMAL, GRIS)));
        }
        bas.addCell(notes);
        PdfPTable totaux = new PdfPTable(new float[]{55f, 45f});
        totaux.setWidthPercentage(100);
        ligneTotal(totaux, "TOTAL :", montant(totalCommande) + " F");
        bas.addCell(sansBordure(new PdfPCell(totaux)));
        bas.setSpacingAfter(30);
        document.add(bas);

        document.add(signatures("Etabli par :\n" + (c.getCreePar() == null ? "" : c.getCreePar()),
                "Bon pour accord du fournisseur :", "Visa de la direction :"));
        document.close();
        return societeService.auFormat(sortie.toByteArray());
    }

    public String nomBonCommande(Long id) {
        return "bon-commande_" + commandes.findById(id).map(CommandeFournisseur::getNumero).orElse(String.valueOf(id)) + ".pdf";
    }

    // ------------------------------------------------------------ gabarit

    private Document ouvrir(ByteArrayOutputStream sortie) {
        Societe societe = societeService.entite();
        Document document = new Document(PageSize.A4, 42, 42, 132, 70);
        PdfWriter writer = PdfWriter.getInstance(document, sortie);
        writer.setPageEvent(facturePdf.decorPage(societe, false));
        document.open();
        return document;
    }

    private static List<String> coordonnees(Fournisseur f) {
        List<String> lignes = new ArrayList<>();
        if (f.getTelephone() != null) {
            lignes.add("Téléphone : " + f.getTelephone());
        }
        if (f.getAdresse() != null || f.getVille() != null) {
            lignes.add("Adresse : " + String.join(", ", java.util.stream.Stream.of(f.getAdresse(), f.getVille())
                    .filter(java.util.Objects::nonNull).toList()));
        }
        if (f.getNif() != null) {
            lignes.add("NIF : " + f.getNif());
        }
        if (f.getPersonneContact() != null) {
            lignes.add("Contact : " + f.getPersonneContact());
        }
        return lignes;
    }

    /** Titre, puis le destinataire a gauche et les references a droite. */
    private static void enTete(Document document, String titre, String libelle, String nom, List<String> details,
                               List<String[]> references) {
        Paragraph entete = new Paragraph(titre, police(18, Font.BOLD, titre.contains("ANNUL") ? ROUGE : SOMBRE));
        entete.setSpacingAfter(12);
        document.add(entete);

        PdfPTable haut = new PdfPTable(new float[]{56f, 44f});
        haut.setWidthPercentage(100);
        PdfPCell gauche = sansBordure(new PdfPCell());
        gauche.addElement(new Paragraph(libelle, police(9, Font.BOLD, BLEU)));
        gauche.addElement(new Paragraph(nom, police(14, Font.BOLD, SOMBRE)));
        for (String d : details) {
            gauche.addElement(new Paragraph(d, police(8.5f, Font.NORMAL, GRIS)));
        }
        haut.addCell(gauche);
        PdfPTable refs = new PdfPTable(new float[]{45f, 55f});
        refs.setWidthPercentage(100);
        for (String[] r : references) {
            refs.addCell(cellule(r[0], police(9, Font.NORMAL, GRIS), Element.ALIGN_LEFT, null));
            refs.addCell(cellule(r[1], police(9, Font.BOLD, SOMBRE), Element.ALIGN_RIGHT, null));
        }
        PdfPCell droite = sansBordure(new PdfPCell(refs));
        droite.setPaddingTop(4);
        haut.addCell(droite);
        haut.setSpacingAfter(18);
        document.add(haut);
    }

    private static PdfPTable signatures(String... cases) {
        PdfPTable t = new PdfPTable(cases.length);
        t.setWidthPercentage(100);
        for (String c : cases) {
            PdfPCell cellule = cellule(c, police(9, Font.BOLD, SOMBRE), Element.ALIGN_LEFT, null);
            cellule.setMinimumHeight(60);
            t.addCell(cellule);
        }
        return t;
    }

    private static PdfPCell entete(String texte, int alignement) {
        PdfPCell c = cellule(texte, police(8.5f, Font.BOLD, Color.WHITE), alignement, BLEU);
        c.setPadding(6);
        return c;
    }

    private static PdfPCell ligne(String texte, int alignement) {
        return ligne(texte, alignement, null);
    }

    private static PdfPCell ligne(String texte, int alignement, Color fond) {
        PdfPCell c = cellule(texte, police(9, Font.NORMAL, SOMBRE), alignement, fond);
        c.setPadding(6);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(FILET);
        return c;
    }

    private static void ligneTotal(PdfPTable table, String libelle, String valeur) {
        PdfPCell a = cellule(libelle, police(11, Font.BOLD, SOMBRE), Element.ALIGN_LEFT, FOND_DOUX);
        PdfPCell b = cellule(valeur, police(11, Font.BOLD, SOMBRE), Element.ALIGN_RIGHT, FOND_DOUX);
        a.setPadding(6);
        b.setPadding(6);
        table.addCell(a);
        table.addCell(b);
    }

    private static PdfPCell cellule(String texte, Font police, int alignement, Color fond) {
        PdfPCell c = new PdfPCell(new Phrase(texte == null ? "" : texte, police));
        c.setHorizontalAlignment(alignement);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingBottom(3);
        if (fond != null) {
            c.setBackgroundColor(fond);
        }
        return c;
    }

    private static PdfPCell sansBordure(PdfPCell c) {
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static Font police(float taille, int style, Color couleur) {
        return PolicesPdf.police(taille, style, couleur);
    }

    private static String montant(BigDecimal valeur) {
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRENCH);
        symboles.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symboles).format(valeur == null ? BigDecimal.ZERO : valeur);
    }

    private static String quantite(BigDecimal valeur) {
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRENCH);
        symboles.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.###", symboles).format(valeur);
    }
}
