package tg.parcauto.api.pointdevente.rapport;

import tg.parcauto.api.common.BusinessException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Archive ZIP d'import des produits : un fichier Excel ou CSV et les photos des
 * produits. Une photo se rattache a son produit par la colonne « image » du
 * tableau, ou par son nom de fichier egal au code (ou au code-barres) du produit.
 * <p>
 * L'archive est lue en memoire avec des plafonds : un fichier qui grossit
 * anormalement a la decompression est refuse.
 */
public final class ArchiveImport {

    /** Plafond d'un fichier de l'archive, une fois decompresse. */
    private static final long FICHIER_MAX = 20L * 1024 * 1024;
    /** Plafond de toute l'archive decompressee. */
    private static final long TOTAL_MAX = 250L * 1024 * 1024;
    private static final int PHOTOS_MAX = 5_000;

    /** Le tableau des produits, et les photos indexees par nom de fichier en minuscules (sans dossier). */
    public record Contenu(String nomTableau, byte[] tableau, Map<String, byte[]> photos, List<String> ignores) {
    }

    private ArchiveImport() {
    }

    public static boolean estArchive(String nomFichier) {
        return nomFichier != null && nomFichier.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public static Contenu lire(InputStream flux) throws IOException {
        String nomTableau = null;
        byte[] tableau = null;
        Map<String, byte[]> photos = new LinkedHashMap<>();
        List<String> ignores = new ArrayList<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(flux)) {
            ZipEntry entree;
            while ((entree = zip.getNextEntry()) != null) {
                if (entree.isDirectory()) {
                    continue;
                }
                String chemin = entree.getName().replace('\\', '/');
                String nom = chemin.substring(chemin.lastIndexOf('/') + 1);
                // Fichiers techniques glisses par macOS ou Windows : sans interet.
                if (nom.isBlank() || nom.startsWith(".") || nom.startsWith("~$") || chemin.startsWith("__MACOSX/")
                        || nom.equalsIgnoreCase("Thumbs.db") || nom.equalsIgnoreCase("desktop.ini")) {
                    continue;
                }
                String minuscule = nom.toLowerCase(Locale.ROOT);
                boolean estTableau = minuscule.endsWith(".xlsx") || minuscule.endsWith(".csv");
                boolean estPhoto = PhotoProduit.estPhoto(minuscule);
                if (!estTableau && !estPhoto) {
                    ignores.add(nom);
                    continue;
                }
                byte[] octets = lireEntree(zip, nom);
                total += octets.length;
                if (total > TOTAL_MAX) {
                    throw new BusinessException("Archive trop volumineuse une fois decompressee : 250 Mo au plus");
                }
                if (estTableau) {
                    if (tableau != null) {
                        throw new BusinessException("L'archive contient plusieurs tableaux (" + nomTableau + ", " + nom
                                + ") : gardez-en un seul");
                    }
                    nomTableau = nom;
                    tableau = octets;
                } else {
                    if (photos.containsKey(minuscule)) {
                        throw new BusinessException("Deux photos portent le nom " + nom + " dans l'archive");
                    }
                    if (photos.size() >= PHOTOS_MAX) {
                        throw new BusinessException("Trop de photos dans l'archive : " + PHOTOS_MAX + " au plus");
                    }
                    photos.put(minuscule, octets);
                }
            }
        }
        if (tableau == null) {
            throw new BusinessException("L'archive ne contient pas de tableau des produits (.xlsx ou .csv)");
        }
        return new Contenu(nomTableau, tableau, photos, ignores);
    }

    private static byte[] lireEntree(ZipInputStream zip, String nom) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        byte[] tampon = new byte[64 * 1024];
        long lu = 0;
        int n;
        while ((n = zip.read(tampon)) > 0) {
            lu += n;
            if (lu > FICHIER_MAX) {
                throw new BusinessException("Le fichier " + nom + " depasse 20 Mo une fois decompresse");
            }
            sortie.write(tampon, 0, n);
        }
        return sortie.toByteArray();
    }
}
