package tg.parcauto.api.parametrage;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Passage d'un document compose en A4 au format A5. A5 est exactement un A4
 * reduit de 1/√2 : chaque page est reprise a l'identique, a cette echelle. Les
 * decors d'en-tete et de pied, places pour l'A4, restent donc justes.
 */
public final class FormatPapier {

    private FormatPapier() {
    }

    public static byte[] versA5(byte[] pdfA4) {
        try {
            PdfReader lecteur = new PdfReader(pdfA4);
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            Document document = new Document(cible(lecteur, 1), 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(document, sortie);
            document.open();
            PdfContentByte contenu = writer.getDirectContent();
            for (int i = 1; i <= lecteur.getNumberOfPages(); i++) {
                Rectangle source = lecteur.getPageSizeWithRotation(i);
                Rectangle cible = cible(lecteur, i);
                if (i > 1) {
                    document.setPageSize(cible);
                    document.newPage();
                }
                float echelle = Math.min(cible.getWidth() / source.getWidth(), cible.getHeight() / source.getHeight());
                PdfImportedPage page = writer.getImportedPage(lecteur, i);
                contenu.addTemplate(page, echelle, 0, 0, echelle, 0, 0);
                // Une page sans autre contenu que le gabarit : on la marque pour qu'elle ne soit pas sautee.
                writer.setPageEmpty(false);
            }
            document.close();
            lecteur.close();
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Mise au format A5 impossible", e);
        }
    }

    /**
     * Nom de l'entreprise en filigrane, en diagonale et tres pale, sous le contenu
     * de chaque page : il atteste que le document vient bien de l'entreprise.
     */
    public static byte[] filigrane(byte[] pdf, String nom) {
        if (nom == null || nom.isBlank()) {
            return pdf;
        }
        String texte = nom.trim().toUpperCase(java.util.Locale.FRENCH);
        try {
            PdfReader lecteur = new PdfReader(pdf);
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            com.lowagie.text.pdf.PdfStamper tampon = new com.lowagie.text.pdf.PdfStamper(lecteur, sortie);
            com.lowagie.text.pdf.BaseFont police = tg.parcauto.api.common.PolicesPdf.baseGrasse();
            com.lowagie.text.pdf.PdfGState transparence = new com.lowagie.text.pdf.PdfGState();
            transparence.setFillOpacity(0.06f);
            for (int i = 1; i <= lecteur.getNumberOfPages(); i++) {
                Rectangle page = lecteur.getPageSizeWithRotation(i);
                // La diagonale de la page, moins les marges : la taille suit la longueur du nom.
                double diagonale = Math.hypot(page.getWidth(), page.getHeight()) * 0.72;
                float taille = (float) Math.min(72, diagonale / Math.max(1, police.getWidthPoint(texte, 1)));
                double angle = Math.toDegrees(Math.atan2(page.getHeight(), page.getWidth()));
                PdfContentByte dessous = tampon.getUnderContent(i);
                dessous.saveState();
                dessous.setGState(transparence);
                dessous.beginText();
                dessous.setFontAndSize(police, taille);
                dessous.setColorFill(new java.awt.Color(20, 40, 70));
                dessous.showTextAligned(com.lowagie.text.Element.ALIGN_CENTER, texte,
                        page.getWidth() / 2, page.getHeight() / 2 - taille / 3, (float) angle);
                dessous.endText();
                dessous.restoreState();
            }
            tampon.close();
            lecteur.close();
            return sortie.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Filigrane impossible a poser", e);
        }
    }

    /** A5 dans le sens de la page d'origine (portrait ou paysage). */
    private static Rectangle cible(PdfReader lecteur, int page) {
        Rectangle source = lecteur.getPageSizeWithRotation(page);
        return source.getWidth() > source.getHeight() ? PageSize.A5.rotate() : PageSize.A5;
    }
}
