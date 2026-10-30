package tg.parcauto.api.common;

import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.io.InputStream;

/**
 * Police des documents PDF (factures, rapports) : Plus Jakarta Sans, celle de
 * l'application web, incorporee au document.
 * <p>
 * Les deux graisses sont lues une fois depuis les ressources (fonts/). Le gras
 * vient du fichier gras, pas d'un epaississement simule. Si un fichier manque
 * ou ne se lit pas, Helvetica prend le relais : un document ne doit jamais
 * echouer pour une question de police.
 */
@Slf4j
public final class PolicesPdf {

    private static final BaseFont NORMALE = charger("fonts/PlusJakartaSans-Regular.ttf");
    private static final BaseFont GRASSE = charger("fonts/PlusJakartaSans-Bold.ttf");

    private PolicesPdf() {
    }

    /**
     * Police a la taille, au style (Font.NORMAL, BOLD, ITALIC...) et a la
     * couleur demandes. L'italique n'a pas de fichier : il reste droit.
     */
    public static Font police(float taille, int style, Color couleur) {
        boolean gras = (style & Font.BOLD) != 0;
        BaseFont base = gras ? GRASSE : NORMALE;
        if (base == null) {
            return FontFactory.getFont(FontFactory.HELVETICA, taille, style, couleur);
        }
        return new Font(base, taille, Font.NORMAL, couleur);
    }

    /** Police de base en gras, pour le texte trace directement (filigrane). */
    public static BaseFont baseGrasse() throws Exception {
        return GRASSE != null ? GRASSE : BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);
    }

    private static BaseFont charger(String ressource) {
        try (InputStream flux = PolicesPdf.class.getClassLoader().getResourceAsStream(ressource)) {
            if (flux == null) {
                log.warn("Police {} introuvable : les PDF utiliseront Helvetica", ressource);
                return null;
            }
            // Identity-H : tous les caracteres (accents, espace fine des milliers) sont couverts.
            return BaseFont.createFont(ressource, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true,
                    flux.readAllBytes(), null);
        } catch (Exception e) {
            log.warn("Police {} illisible, les PDF utiliseront Helvetica : {}", ressource, e.getMessage());
            return null;
        }
    }
}
