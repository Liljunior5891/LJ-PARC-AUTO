package tg.parcauto.api.pointdevente.rapport;

import org.dhatim.fastexcel.Workbook;
import tg.parcauto.api.pilotage.MiseEnPagePdf;
import org.dhatim.fastexcel.Worksheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ecriture d'un rapport en CSV, en Excel ou en PDF, a l'image de l'ecran.
 * <p>
 * Le CSV suit les habitudes d'un tableur en francais : separateur point-virgule,
 * virgule decimale, encodage UTF-8 avec marque d'ordre pour que les accents
 * s'ouvrent correctement. Le fichier Excel garde de vrais nombres et de vraies
 * dates, avec une ligne de totaux en gras.
 */
public final class ExportRapport {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ExportRapport() {
    }

    public static byte[] csv(Rapport r, ZoneId fuseau) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(cellule(r.titre())).append('\n');
        sb.append(cellule(r.sousTitre())).append("\n\n");
        sb.append(String.join(";", r.colonnes().stream().map(c -> cellule(c.libelle())).toList())).append('\n');
        for (Map<String, Object> ligne : r.lignes()) {
            sb.append(ligneCsv(r.colonnes(), ligne, fuseau)).append('\n');
        }
        if (!r.totaux().isEmpty()) {
            Map<String, Object> totaux = new java.util.LinkedHashMap<>(r.totaux());
            totaux.putIfAbsent(r.colonnes().get(0).cle(), "TOTAL");
            sb.append(ligneCsv(r.colonnes(), totaux, fuseau)).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] excel(Rapport r, ZoneId fuseau) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            Workbook classeur = new Workbook(sortie, "LJ SaaS", "1.0");
            Worksheet feuille = classeur.newWorksheet(nomFeuille(r.titre()));
            feuille.value(0, 0, r.titre());
            feuille.style(0, 0).bold().fontSize(14).set();
            feuille.value(1, 0, r.sousTitre());
            feuille.style(1, 0).italic().set();
            int entete = 3;
            List<Rapport.Colonne> colonnes = r.colonnes();
            for (int c = 0; c < colonnes.size(); c++) {
                feuille.value(entete, c, colonnes.get(c).libelle());
                feuille.style(entete, c).bold().fillColor("E6F5EA").borderStyle("thin").set();
                feuille.width(c, largeur(colonnes.get(c)));
            }
            int ligne = entete + 1;
            for (Map<String, Object> valeurs : r.lignes()) {
                ecrireLigne(feuille, ligne++, colonnes, valeurs, fuseau, false);
            }
            if (!r.totaux().isEmpty()) {
                Map<String, Object> totaux = new java.util.LinkedHashMap<>(r.totaux());
                totaux.putIfAbsent(colonnes.get(0).cle(), "TOTAL");
                ecrireLigne(feuille, ligne, colonnes, totaux, fuseau, true);
            }
            feuille.freezePane(0, entete + 1);
            classeur.finish();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sortie.toByteArray();
    }

    /**
     * PDF au gabarit des rapports de l'application : nom de l'etablissement,
     * titre, indicateurs puis tableau, en paysage des que les colonnes sont
     * nombreuses. La ligne de totaux termine le tableau.
     */
    public static byte[] pdf(Rapport r, ZoneId fuseau, String entreprise) {
        List<Rapport.Colonne> colonnes = r.colonnes();
        boolean paysage = colonnes.size() > 6;
        MiseEnPagePdf pdf = new MiseEnPagePdf(entreprise == null || entreprise.isBlank() ? "LJ SaaS" : entreprise,
                r.titre(), r.sousTitre(), paysage);
        try (pdf) {
            pdf.indicateurs(r.indicateurs().stream()
                    .map(i -> MiseEnPagePdf.Indicateur.de(i.libelle(), textePdf(i.valeur(), i.format(), fuseau)))
                    .toList());
            float[] largeurs = new float[colonnes.size()];
            String[] entetes = new String[colonnes.size()];
            StringBuilder alignements = new StringBuilder();
            for (int c = 0; c < colonnes.size(); c++) {
                Rapport.Colonne colonne = colonnes.get(c);
                largeurs[c] = largeurPdf(colonne, r.lignes());
                entetes[c] = colonne.libelle();
                alignements.append(switch (colonne.format()) {
                    case MONTANT, NOMBRE, POURCENT -> 'd';
                    default -> 'g';
                });
            }
            List<String[]> lignes = new ArrayList<>();
            for (Map<String, Object> ligne : r.lignes()) {
                lignes.add(colonnes.stream().map(c -> textePdf(ligne.get(c.cle()), c.format(), fuseau)).toArray(String[]::new));
            }
            if (!r.totaux().isEmpty()) {
                Map<String, Object> totaux = new java.util.LinkedHashMap<>(r.totaux());
                totaux.putIfAbsent(colonnes.get(0).cle(), "TOTAL");
                lignes.add(colonnes.stream().map(c -> textePdf(totaux.get(c.cle()), c.format(), fuseau)).toArray(String[]::new));
            }
            pdf.tableau(largeurs, entetes, alignements.toString(), lignes);
        }
        return pdf.octets();
    }

    /**
     * Largeur relative d'une colonne du PDF : le texte le plus long qu'elle
     * contient (borne), pour que codes et noms tiennent sur une ligne.
     */
    private static float largeurPdf(Rapport.Colonne c, List<Map<String, Object>> lignes) {
        return switch (c.format()) {
            case DATE_HEURE -> 15;
            case DATE -> 11;
            case MONTANT, NOMBRE, POURCENT -> Math.max(9, Math.min(14, c.libelle().length() + 1));
            default -> {
                int plusLong = lignes.stream().map(l -> l.get(c.cle())).filter(v -> v != null)
                        .mapToInt(v -> v.toString().length()).max().orElse(0);
                yield Math.max(8, Math.min(32, Math.max(plusLong, c.libelle().length()) + 4));
            }
        };
    }

    private static String textePdf(Object v, Rapport.Format format, ZoneId fuseau) {
        return switch (v) {
            case null -> "";
            case Number n when format == Rapport.Format.MONTANT -> MiseEnPagePdf.montant(new BigDecimal(n.toString()));
            case Number n when format == Rapport.Format.POURCENT -> MiseEnPagePdf.nombre(n) + " %";
            case Number n -> MiseEnPagePdf.nombre(n);
            case Instant i when format == Rapport.Format.DATE -> JOUR.format(i.atZone(fuseau));
            case Instant i -> JOUR_HEURE.format(i.atZone(fuseau));
            case LocalDate d -> JOUR.format(d);
            default -> v.toString();
        };
    }

    private static void ecrireLigne(Worksheet f, int ligne, List<Rapport.Colonne> colonnes, Map<String, Object> valeurs,
                                    ZoneId fuseau, boolean total) {
        for (int c = 0; c < colonnes.size(); c++) {
            Rapport.Colonne colonne = colonnes.get(c);
            Object v = valeurs.get(colonne.cle());
            if (v == null) {
                continue;
            }
            switch (v) {
                case BigDecimal n -> f.value(ligne, c, colonne.format() == Rapport.Format.POURCENT
                        ? n.movePointLeft(2) : n);
                case Number n -> f.value(ligne, c, n);
                case Instant i -> f.value(ligne, c, i.atZone(fuseau).toLocalDateTime());
                case LocalDate d -> f.value(ligne, c, d);
                default -> f.value(ligne, c, v.toString());
            }
            var style = f.style(ligne, c);
            switch (colonne.format()) {
                case MONTANT -> style.format("#,##0");
                case NOMBRE -> style.format("#,##0.###");
                case POURCENT -> style.format("0.0%");
                case DATE -> style.format("dd/mm/yyyy");
                case DATE_HEURE -> style.format("dd/mm/yyyy hh:mm");
                default -> {
                }
            }
            if (total) {
                style.bold().borderStyle("thin");
            }
            style.set();
        }
    }

    private static String ligneCsv(List<Rapport.Colonne> colonnes, Map<String, Object> ligne, ZoneId fuseau) {
        return String.join(";", colonnes.stream().map(c -> cellule(texte(ligne.get(c.cle()), fuseau))).toList());
    }

    private static String texte(Object v, ZoneId fuseau) {
        return switch (v) {
            case null -> "";
            case BigDecimal n -> n.stripTrailingZeros().toPlainString().replace('.', ',');
            case Instant i -> JOUR_HEURE.format(i.atZone(fuseau));
            case LocalDate d -> JOUR.format(d);
            default -> v.toString();
        };
    }

    /** Guillemets quand la valeur contient un separateur, un guillemet ou un saut de ligne. */
    private static String cellule(String valeur) {
        if (valeur == null) {
            return "";
        }
        // Une cellule commencant par = + - @ serait executee comme formule par le tableur.
        String sure = valeur.matches("^[=+\\-@].*") && !valeur.matches("^-?\\d.*") ? "'" + valeur : valeur;
        return sure.contains(";") || sure.contains("\"") || sure.contains("\n")
                ? "\"" + sure.replace("\"", "\"\"") + "\"" : sure;
    }

    private static int largeur(Rapport.Colonne c) {
        return switch (c.format()) {
            case DATE_HEURE -> 17;
            case DATE -> 12;
            case MONTANT, NOMBRE, POURCENT -> 14;
            default -> Math.max(12, Math.min(40, c.libelle().length() + 6));
        };
    }

    private static String nomFeuille(String titre) {
        String nom = titre.replaceAll("[\\[\\]*?/\\\\:]", " ");
        return nom.length() > 31 ? nom.substring(0, 31) : nom;
    }
}
