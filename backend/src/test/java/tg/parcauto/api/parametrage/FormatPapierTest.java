package tg.parcauto.api.parametrage;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FormatPapierTest {

    @Test
    void leNomDeLEntrepriseEstPoseEnFiligraneSurChaquePage() throws Exception {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, sortie);
        document.open();
        document.add(new Paragraph("Facture"));
        document.newPage();
        document.add(new Paragraph("Suite"));
        document.close();

        byte[] signe = FormatPapier.filigrane(sortie.toByteArray(), "Quincaillerie Test");
        PdfReader lecteur = new PdfReader(signe);

        assertThat(lecteur.getNumberOfPages()).isEqualTo(2);
        com.lowagie.text.pdf.parser.PdfTextExtractor extracteur = new com.lowagie.text.pdf.parser.PdfTextExtractor(lecteur);
        assertThat(extracteur.getTextFromPage(1)).contains("QUINCAILLERIE TEST");
        assertThat(extracteur.getTextFromPage(2)).contains("QUINCAILLERIE TEST");
    }

    @Test
    void chaquePageA4DevientUnePageA5() throws Exception {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, sortie);
        document.open();
        document.add(new Paragraph("Page 1"));
        document.newPage();
        document.add(new Paragraph("Page 2"));
        document.close();

        PdfReader lecteur = new PdfReader(FormatPapier.versA5(sortie.toByteArray()));

        assertThat(lecteur.getNumberOfPages()).isEqualTo(2);
        for (int i = 1; i <= 2; i++) {
            Rectangle page = lecteur.getPageSize(i);
            assertThat(page.getWidth()).isEqualTo(PageSize.A5.getWidth());
            assertThat(page.getHeight()).isEqualTo(PageSize.A5.getHeight());
        }
    }
}
