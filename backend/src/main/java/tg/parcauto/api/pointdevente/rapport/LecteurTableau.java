package tg.parcauto.api.pointdevente.rapport;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import tg.parcauto.api.common.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Lecture d'un fichier importe, CSV ou Excel, en lignes de valeurs indexees
 * par l'entete de colonne. Les entetes sont normalises (minuscules, sans
 * accents, espaces en tirets bas) : « Prix de vente » devient prix_de_vente.
 */
public final class LecteurTableau {

    /** Ligne lue : son numero dans le fichier (pour les messages) et ses valeurs. */
    public record Ligne(int numero, Map<String, String> valeurs) {

        public String valeur(String... noms) {
            for (String nom : noms) {
                String v = valeurs.get(nom);
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            }
            return null;
        }
    }

    private static final int LIGNES_MAX = 5_000;

    private LecteurTableau() {
    }

    public static List<Ligne> lire(String nomFichier, InputStream contenu) throws IOException {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase(Locale.ROOT);
        List<List<String>> brut = nom.endsWith(".xlsx") ? excel(contenu)
                : nom.endsWith(".csv") || nom.endsWith(".txt") ? csv(contenu)
                : null;
        if (brut == null) {
            throw new BusinessException("Format non reconnu : importez un fichier .csv ou .xlsx");
        }
        // L'entete est la premiere ligne d'au moins deux colonnes remplies : le titre et le sous-titre
        // places au-dessus par les modeles et les exports sont ainsi sautes.
        int debut = 0;
        while (debut < brut.size() && brut.get(debut).stream().filter(c -> c != null && !c.isBlank()).count() < 2) {
            debut++;
        }
        if (debut >= brut.size()) {
            throw new BusinessException("Le fichier est vide");
        }
        List<String> entetes = brut.get(debut).stream().map(LecteurTableau::normaliser).toList();
        List<Ligne> lignes = new ArrayList<>();
        for (int i = debut + 1; i < brut.size(); i++) {
            List<String> cellules = brut.get(i);
            if (cellules.stream().allMatch(c -> c == null || c.isBlank())) {
                continue;
            }
            Map<String, String> valeurs = new LinkedHashMap<>();
            for (int c = 0; c < entetes.size(); c++) {
                valeurs.put(entetes.get(c), c < cellules.size() ? cellules.get(c) : null);
            }
            lignes.add(new Ligne(i + 1, valeurs));
            if (lignes.size() > LIGNES_MAX) {
                throw new BusinessException("Fichier trop volumineux : " + LIGNES_MAX + " lignes au plus par import");
            }
        }
        return lignes;
    }

    public static String normaliser(String entete) {
        if (entete == null) {
            return "";
        }
        String sansAccent = Normalizer.normalize(entete.replace("﻿", ""), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccent.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static List<List<String>> excel(InputStream contenu) throws IOException {
        List<List<String>> lignes = new ArrayList<>();
        try (ReadableWorkbook classeur = new ReadableWorkbook(contenu)) {
            Sheet feuille = classeur.getFirstSheet();
            try (Stream<Row> rangees = feuille.openStream()) {
                rangees.forEach(rangee -> {
                    List<String> cellules = new ArrayList<>();
                    for (int c = 0; c < rangee.getCellCount(); c++) {
                        cellules.add(rangee.getCellText(c));
                    }
                    // Les rangees vides sautees par Excel gardent leur numero.
                    while (lignes.size() < rangee.getRowNum() - 1) {
                        lignes.add(List.of());
                    }
                    lignes.add(cellules);
                });
            }
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("Fichier Excel illisible : enregistrez-le au format .xlsx");
        }
        return lignes;
    }

    /** CSV : separateur point-virgule ou virgule (deduit de l'entete), guillemets doubles. */
    private static List<List<String>> csv(InputStream contenu) throws IOException {
        String texte = new String(contenu.readAllBytes(), StandardCharsets.UTF_8).replace("﻿", "");
        String premiere = texte.lines().findFirst().orElse("");
        char separateur = premiere.chars().filter(c -> c == ';').count() >= premiere.chars().filter(c -> c == ',').count()
                ? ';' : ',';
        List<List<String>> lignes = new ArrayList<>();
        List<String> courante = new ArrayList<>();
        StringBuilder cellule = new StringBuilder();
        boolean guillemets = false;
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (guillemets) {
                if (c == '"' && i + 1 < texte.length() && texte.charAt(i + 1) == '"') {
                    cellule.append('"');
                    i++;
                } else if (c == '"') {
                    guillemets = false;
                } else {
                    cellule.append(c);
                }
            } else if (c == '"') {
                guillemets = true;
            } else if (c == separateur) {
                courante.add(cellule.toString());
                cellule.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texte.length() && texte.charAt(i + 1) == '\n') {
                    i++;
                }
                courante.add(cellule.toString());
                cellule.setLength(0);
                lignes.add(courante);
                courante = new ArrayList<>();
            } else {
                cellule.append(c);
            }
        }
        if (cellule.length() > 0 || !courante.isEmpty()) {
            courante.add(cellule.toString());
            lignes.add(courante);
        }
        return lignes;
    }
}
