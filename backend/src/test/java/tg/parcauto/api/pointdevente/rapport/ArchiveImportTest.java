package tg.parcauto.api.pointdevente.rapport;

import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveImportTest {

    @Test
    void lArchiveDonneLeTableauEtLesPhotosParNomDeFichier() throws Exception {
        byte[] zip = zip(
                "produits/produits.csv", "code;nom;prix_vente\nCIM-001;Ciment;5000\n".getBytes(StandardCharsets.UTF_8),
                "produits/photos/CIM-001.PNG", png(1200, 800),
                "__MACOSX/._CIM-001.PNG", new byte[]{1, 2},
                "notes.txt", "a lire".getBytes(StandardCharsets.UTF_8));

        ArchiveImport.Contenu contenu = ArchiveImport.lire(new ByteArrayInputStream(zip));

        assertThat(contenu.nomTableau()).isEqualTo("produits.csv");
        assertThat(contenu.photos()).containsOnlyKeys("cim-001.png");
        assertThat(contenu.ignores()).containsExactly("notes.txt");
    }

    @Test
    void uneArchiveSansTableauEstRefusee() throws Exception {
        byte[] zip = zip("CIM-001.jpg", png(10, 10));
        assertThatThrownBy(() -> ArchiveImport.lire(new ByteArrayInputStream(zip)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tableau");
    }

    @Test
    void laPhotoEstRecadreeAuCarreEtReduiteEnJpeg() throws Exception {
        String dataUrl = PhotoProduit.reduire("cim-001.png", png(1200, 800));

        assertThat(dataUrl).startsWith("data:image/jpeg;base64,");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1))));
        assertThat(image.getWidth()).isEqualTo(480);
        assertThat(image.getHeight()).isEqualTo(480);
    }

    @Test
    void unePhotoIllisibleEstSignalee() {
        assertThatThrownBy(() -> PhotoProduit.reduire("abimee.jpg", new byte[]{1, 2, 3}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("illisible");
    }

    private static byte[] png(int largeur, int hauteur) throws Exception {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB), "png", sortie);
        return sortie.toByteArray();
    }

    private static byte[] zip(Object... cheminsEtContenus) throws Exception {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(sortie)) {
            for (int i = 0; i < cheminsEtContenus.length; i += 2) {
                zip.putNextEntry(new ZipEntry((String) cheminsEtContenus[i]));
                zip.write((byte[]) cheminsEtContenus[i + 1]);
                zip.closeEntry();
            }
        }
        return sortie.toByteArray();
    }
}
