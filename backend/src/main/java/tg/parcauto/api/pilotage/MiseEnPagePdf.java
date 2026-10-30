package tg.parcauto.api.pilotage;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import tg.parcauto.api.common.PolicesPdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Mise en page commune des rapports PDF.
 * <p>
 * Regroupe l'entete, le pied de page numerote, les titres de section, les
 * tableaux et les blocs d'indicateurs : chaque rapport decrit son contenu sans
 * repeter la plomberie d'edition.
 */
public class MiseEnPagePdf implements AutoCloseable {

    public static final Color BLEU = new Color(15, 76, 129);
    public static final Color GRIS = new Color(107, 118, 132);
    public static final Color BORDURE = new Color(226, 230, 237);
    public static final Color FOND_DOUX = new Color(242, 247, 252);
    public static final Color VERT = new Color(30, 135, 75);
    public static final Color ROUGE = new Color(198, 40, 40);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Document document;
    private final ByteArrayOutputStream sortie = new ByteArrayOutputStream();

    public MiseEnPagePdf(String entreprise, String titre, String sousTitre, boolean paysage) {
        this.document = new Document(paysage ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 40, 40);
        PdfWriter writer = PdfWriter.getInstance(document, sortie);
        writer.setPageEvent(new PiedDePage(entreprise));
        document.open();
        entete(entreprise, titre, sousTitre);
    }

    private void entete(String entreprise, String titre, String sousTitre) {
        Paragraph nom = new Paragraph(entreprise, police(11, Font.BOLD, BLEU));
        document.add(nom);

        Paragraph titreRapport = new Paragraph(titre, police(17, Font.BOLD, Color.BLACK));
        titreRapport.setSpacingBefore(6);
        document.add(titreRapport);

        if (sousTitre != null && !sousTitre.isBlank()) {
            document.add(new Paragraph(sousTitre, police(9, Font.NORMAL, GRIS)));
        }
        document.add(filet());
    }

    /** Titre de section, avec un filet leger dessous. */
    public void section(String titre) {
        Paragraph paragraphe = new Paragraph(titre, police(12, Font.BOLD, BLEU));
        paragraphe.setSpacingBefore(16);
        paragraphe.setSpacingAfter(6);
        document.add(paragraphe);
    }

    public void texte(String contenu) {
        document.add(new Paragraph(contenu, police(9, Font.NORMAL, Color.BLACK)));
    }

    public void note(String contenu) {
        Paragraph paragraphe = new Paragraph(contenu, police(8, Font.ITALIC, GRIS));
        paragraphe.setSpacingBefore(6);
        document.add(paragraphe);
    }

    /** Bandeau d'indicateurs : libelle au-dessus, valeur en gros dessous. */
    public void indicateurs(List<Indicateur> indicateurs) {
        if (indicateurs.isEmpty()) {
            return;
        }
        PdfPTable table = new PdfPTable(indicateurs.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);

        for (Indicateur indicateur : indicateurs) {
            PdfPCell cellule = new PdfPCell();
            cellule.setBackgroundColor(FOND_DOUX);
            cellule.setBorderColor(BORDURE);
            cellule.setPadding(8);

            Paragraph libelle = new Paragraph(indicateur.libelle().toUpperCase(Locale.FRENCH),
                    police(7, Font.BOLD, GRIS));
            Paragraph valeur = new Paragraph(indicateur.valeur(),
                    police(13, Font.BOLD, indicateur.couleur() == null ? Color.BLACK : indicateur.couleur()));
            valeur.setSpacingBefore(3);
            cellule.addElement(libelle);
            cellule.addElement(valeur);
            if (indicateur.detail() != null) {
                Paragraph detail = new Paragraph(indicateur.detail(), police(7, Font.NORMAL, GRIS));
                detail.setSpacingBefore(2);
                cellule.addElement(detail);
            }
            table.addCell(cellule);
        }
        document.add(table);
    }

    /**
     * Tableau de donnees.
     *
     * @param alignements alignement par colonne : 'g' gauche, 'd' droite
     */
    public void tableau(float[] largeurs, String[] entetes, String alignements, List<String[]> lignes) {
        PdfPTable table = new PdfPTable(largeurs);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setHeaderRows(1);

        for (int i = 0; i < entetes.length; i++) {
            PdfPCell cellule = new PdfPCell(new Phrase(entetes[i], police(8, Font.BOLD, Color.WHITE)));
            cellule.setBackgroundColor(BLEU);
            cellule.setHorizontalAlignment(alignement(alignements, i));
            cellule.setPadding(6);
            cellule.setBorderWidth(0);
            table.addCell(cellule);
        }

        boolean alterne = false;
        for (String[] ligne : lignes) {
            for (int i = 0; i < ligne.length; i++) {
                PdfPCell cellule = new PdfPCell(
                        new Phrase(ligne[i] == null ? "" : ligne[i], police(8, Font.NORMAL, Color.BLACK)));
                cellule.setHorizontalAlignment(alignement(alignements, i));
                cellule.setPadding(5);
                cellule.setBorderWidth(0);
                cellule.setBorderWidthBottom(0.5f);
                cellule.setBorderColor(BORDURE);
                if (alterne) {
                    cellule.setBackgroundColor(new Color(250, 251, 252));
                }
                table.addCell(cellule);
            }
            alterne = !alterne;
        }
        if (lignes.isEmpty()) {
            PdfPCell vide = new PdfPCell(new Phrase("Aucune donnee sur la periode",
                    police(8, Font.ITALIC, GRIS)));
            vide.setColspan(entetes.length);
            vide.setHorizontalAlignment(Element.ALIGN_CENTER);
            vide.setPadding(10);
            vide.setBorderWidth(0);
            table.addCell(vide);
        }
        document.add(table);
    }

    public void sautDePage() {
        document.newPage();
    }

    @Override
    public void close() {
        document.close();
    }

    public byte[] octets() {
        return sortie.toByteArray();
    }

    // ------------------------------------------------------------------
    // Aides de formatage
    // ------------------------------------------------------------------

    /** Montants a la francaise : espace pour les milliers, virgule decimale. */
    public static String montant(BigDecimal valeur) {
        if (valeur == null) {
            return "—";
        }
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        return new DecimalFormat("#,##0", symboles).format(valeur);
    }

    public static String nombre(Number valeur) {
        if (valeur == null) {
            return "—";
        }
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symboles).format(valeur);
    }

    public static String date(LocalDate valeur) {
        return valeur == null ? "—" : valeur.format(DATE);
    }

    public static String periode(LocalDate debut, LocalDate fin) {
        return "Periode du " + date(debut) + " au " + date(fin);
    }

    private int alignement(String alignements, int colonne) {
        if (alignements == null || colonne >= alignements.length()) {
            return Element.ALIGN_LEFT;
        }
        return alignements.charAt(colonne) == 'd' ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
    }

    /** Police du projet (Plus Jakarta Sans), commune aux rapports et aux factures. */
    private static Font police(int taille, int style, Color couleur) {
        return PolicesPdf.police(taille, style, couleur);
    }

    private Paragraph filet() {
        Paragraph paragraphe = new Paragraph(" ");
        paragraphe.setSpacingAfter(4);
        return paragraphe;
    }

    /** Un indicateur du bandeau de synthese. */
    public record Indicateur(String libelle, String valeur, String detail, Color couleur) {

        public static Indicateur de(String libelle, String valeur) {
            return new Indicateur(libelle, valeur, null, null);
        }

        public static Indicateur de(String libelle, String valeur, String detail) {
            return new Indicateur(libelle, valeur, detail, null);
        }
    }

    /** Pied de page : origine du document, date d'edition et numero de page. */
    private static class PiedDePage extends PdfPageEventHelper {

        private final String entreprise;

        PiedDePage(String entreprise) {
            this.entreprise = entreprise;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            PdfPTable pied = new PdfPTable(2);
            pied.setTotalWidth(page.getWidth() - 72);
            pied.setLockedWidth(true);

            String edite = "Edite le "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm"))
                    + " — " + entreprise;
            PdfPCell gauche = new PdfPCell(new Phrase(edite, police(7, Font.NORMAL, GRIS)));
            gauche.setBorderWidth(0);
            gauche.setHorizontalAlignment(Element.ALIGN_LEFT);

            PdfPCell droite = new PdfPCell(
                    new Phrase("Page " + writer.getPageNumber(), police(7, Font.NORMAL, GRIS)));
            droite.setBorderWidth(0);
            droite.setHorizontalAlignment(Element.ALIGN_RIGHT);

            pied.addCell(gauche);
            pied.addCell(droite);
            pied.writeSelectedRows(0, -1, 36, 30, writer.getDirectContent());
        }
    }
}
