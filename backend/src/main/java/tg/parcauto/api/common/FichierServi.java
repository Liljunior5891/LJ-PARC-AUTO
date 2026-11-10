package tg.parcauto.api.common;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

/**
 * Renvoi d'un fichier depose par un utilisateur (justificatif, preuve, piece).
 * <p>
 * Le type vient de l'extension du nom, jamais de celui annonce par le
 * navigateur au depot : un fichier deguise (« recu.pdf » qui contient du HTML)
 * ne peut pas s'executer dans l'application. Seuls les PDF et les images
 * s'affichent ; tout le reste part en telechargement.
 */
public final class FichierServi {

    private FichierServi() {
    }

    /** Type affichable d'apres l'extension, ou null s'il faut seulement telecharger. */
    public static MediaType typeAffichable(String nom) {
        String n = nom == null ? "" : nom.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (n.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (n.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (n.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return null;
    }

    /**
     * @param afficher vrai pour l'ouvrir dans le navigateur (PDF, image) ;
     *                 un autre type est toujours telecharge
     */
    public static ResponseEntity<Resource> reponse(Resource contenu, String nom, boolean afficher) {
        String propre = nom == null || nom.isBlank() ? "fichier" : nom.replaceAll("[\"\\r\\n\\\\]", "");
        MediaType type = typeAffichable(propre);
        boolean enLigne = afficher && type != null;
        return ResponseEntity.ok()
                .contentType(type == null ? MediaType.APPLICATION_OCTET_STREAM : type)
                .header(HttpHeaders.CONTENT_DISPOSITION, (enLigne ? "inline" : "attachment") + "; filename=\"" + propre + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(contenu);
    }
}
