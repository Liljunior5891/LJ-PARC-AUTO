package tg.parcauto.api.common;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Lecture d'images envoyees par les utilisateurs, sans faire confiance a leur
 * type annonce ni a leur taille compressee.
 * <ul>
 *   <li>le format est reconnu d'apres le contenu (octets magiques), pas d'apres le navigateur ;</li>
 *   <li>les dimensions sont lues avant le decodage : une image de quelques Ko qui annonce
 *       50 000 x 50 000 pixels est refusee sans allouer sa memoire ;</li>
 *   <li>le re-encodage supprime les metadonnees (EXIF : position GPS, appareil...).</li>
 * </ul>
 */
public final class ImageSure {

    /** Au-dela, refus : 40 millions de pixels et 8 000 pixels de cote. */
    public static final long PIXELS_MAX = 40_000_000L;
    public static final int COTE_MAX = 8_000;

    private ImageSure() {
    }

    /** Decode l'image apres controle de ses dimensions ; message metier si illisible ou trop grande. */
    public static BufferedImage lire(byte[] octets, String libelle) {
        try (ImageInputStream flux = ImageIO.createImageInputStream(new ByteArrayInputStream(octets))) {
            Iterator<ImageReader> lecteurs = flux == null ? null : ImageIO.getImageReaders(flux);
            if (lecteurs == null || !lecteurs.hasNext()) {
                throw new BusinessException(libelle + " : image illisible ou format non reconnu (PNG, JPEG ou GIF)");
            }
            ImageReader lecteur = lecteurs.next();
            try {
                lecteur.setInput(flux, true, true);
                int largeur = lecteur.getWidth(0);
                int hauteur = lecteur.getHeight(0);
                if (largeur <= 0 || hauteur <= 0 || largeur > COTE_MAX || hauteur > COTE_MAX
                        || (long) largeur * hauteur > PIXELS_MAX) {
                    throw new BusinessException(libelle + " : image trop grande (" + largeur + " x " + hauteur
                            + " pixels)");
                }
                BufferedImage image = lecteur.read(0);
                if (image == null) {
                    throw new BusinessException(libelle + " : image illisible");
                }
                return image;
            } finally {
                lecteur.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException b) {
                throw b;
            }
            throw new BusinessException(libelle + " : image illisible");
        }
    }

    /** Image re-encodee en PNG, sans metadonnees. */
    public static byte[] enPng(BufferedImage image) {
        BufferedImage propre = image;
        if (image.getType() == BufferedImage.TYPE_CUSTOM) {
            propre = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = propre.createGraphics();
            try {
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
        }
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(propre, "png", sortie)) {
                throw new BusinessException("Conversion de l'image impossible");
            }
        } catch (IOException e) {
            throw new BusinessException("Conversion de l'image impossible");
        }
        return sortie.toByteArray();
    }
}
