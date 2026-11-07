package tg.parcauto.api.pointdevente.rapport;

import tg.parcauto.api.common.BusinessException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Photo d'un produit importee : reduite comme le fait l'ecran du produit
 * (recadrage carre centre, 480 px au plus, JPEG), pour qu'une photo importee et
 * une photo choisie a la main aient le meme rendu et le meme poids.
 */
public final class PhotoProduit {

    /** Meme cote que l'ecran du produit (photo-article.ts). */
    private static final int COTE_MAX = 480;
    private static final float QUALITE = 0.82f;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    private PhotoProduit() {
    }

    public static boolean estPhoto(String nomFichier) {
        return EXTENSIONS.contains(extension(nomFichier));
    }

    /** Nom du fichier sans son extension : « CIM-001.jpg » donne « cim-001 ». */
    public static String radical(String nomFichier) {
        String nom = nomFichier.toLowerCase(Locale.ROOT);
        int point = nom.lastIndexOf('.');
        return point > 0 ? nom.substring(0, point) : nom;
    }

    /** Photo reduite, en data URL JPEG prete a etre enregistree sur le produit. */
    public static String reduire(String nomFichier, byte[] octets) {
        if ("webp".equals(extension(nomFichier))) {
            throw new BusinessException("la photo " + nomFichier + " est au format WebP, non pris en charge a l'import : "
                    + "convertissez-la en JPEG ou PNG");
        }
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(octets));
        } catch (IOException e) {
            source = null;
        }
        if (source == null) {
            throw new BusinessException("la photo " + nomFichier + " est illisible");
        }
        int cote = Math.min(source.getWidth(), source.getHeight());
        int sortie = Math.min(COTE_MAX, cote);
        BufferedImage carre = new BufferedImage(sortie, sortie, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = carre.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, sortie, sortie);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int x = (source.getWidth() - cote) / 2;
            int y = (source.getHeight() - cote) / 2;
            g.drawImage(source, 0, 0, sortie, sortie, x, y, x + cote, y + cote, null);
        } finally {
            g.dispose();
        }
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg(carre));
    }

    private static byte[] jpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try (ImageOutputStream flux = ImageIO.createImageOutputStream(sortie)) {
            writer.setOutput(flux);
            ImageWriteParam parametres = writer.getDefaultWriteParam();
            parametres.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametres.setCompressionQuality(QUALITE);
            writer.write(null, new IIOImage(image, null, null), parametres);
        } catch (IOException e) {
            throw new IllegalStateException("Compression de la photo impossible", e);
        } finally {
            writer.dispose();
        }
        return sortie.toByteArray();
    }

    private static String extension(String nomFichier) {
        String nom = nomFichier.toLowerCase(Locale.ROOT);
        int point = nom.lastIndexOf('.');
        return point >= 0 ? nom.substring(point + 1) : "";
    }
}
