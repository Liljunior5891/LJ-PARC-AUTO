package tg.parcauto.api.pointdevente.restauration.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

/** Dessin d'un code QR en SVG : net a toutes les tailles d'impression, sans image a stocker. */
public final class CodeQr {

    private CodeQr() {
    }

    public static String svg(String contenu) {
        BitMatrix matrice;
        try {
            // Correction elevee : le code reste lisible tache ou plastifie sur une table.
            matrice = new QRCodeWriter().encode(contenu, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q, EncodeHintType.MARGIN, 2));
        } catch (WriterException e) {
            throw new IllegalArgumentException("Contenu trop long pour un code QR", e);
        }
        int cote = matrice.getWidth();
        StringBuilder chemin = new StringBuilder();
        for (int y = 0; y < cote; y++) {
            for (int x = 0; x < cote; x++) {
                if (matrice.get(x, y)) {
                    chemin.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + cote + " " + cote
                + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                + "<path fill=\"#000\" d=\"" + chemin + "\"/></svg>";
    }
}
