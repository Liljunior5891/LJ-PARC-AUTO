package tg.parcauto.api.pointdevente.rapport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportExportTest {

    @Test
    @DisplayName("Le CSV d'un tableur francais se lit : point-virgule, guillemets, entetes accentues")
    void lectureCsv() throws Exception {
        String csv = "﻿Code;Nom;Catégorie;Prix de vente\r\n"
                + "ART-1;\"Biere \"\"Flag\"\" 65cl\";Boissons;800\r\n"
                + ";;;\r\n"
                + ";Jus; frais;\"1 200,50\"\r\n";
        List<LecteurTableau.Ligne> lignes = LecteurTableau.lire("produits.csv",
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(0).valeur("nom")).isEqualTo("Biere \"Flag\" 65cl");
        assertThat(lignes.get(0).valeur("categorie")).isEqualTo("Boissons");
        assertThat(lignes.get(1).numero()).isEqualTo(4);
        assertThat(lignes.get(1).valeur("prix_de_vente")).isEqualTo("1 200,50");
    }

    @Test
    @DisplayName("Un export CSV se rouvre dans le tableur : virgule decimale, totaux, formules neutralisees")
    void exportCsv() {
        Rapport rapport = new Rapport.Constructeur("TEST", "Ventes", "Octobre")
                .colonne("produit", "Produit", Rapport.Format.TEXTE)
                .colonne("ca", "CA", Rapport.Format.MONTANT)
                .ligne("=HYPERLINK(\"x\")", new BigDecimal("1500.50"))
                .total("ca", new BigDecimal("1500.50"))
                .construire();

        String texte = new String(ExportRapport.csv(rapport, ZoneId.of("Africa/Lome")), StandardCharsets.UTF_8);

        assertThat(texte).startsWith("﻿");
        assertThat(texte).contains("Produit;CA");
        assertThat(texte).contains("\"'=HYPERLINK(\"\"x\"\")\";1500,5");
        assertThat(texte).contains("TOTAL;1500,5");
    }

    @Test
    @DisplayName("L'export Excel produit un classeur valide")
    void exportExcel() throws Exception {
        Rapport rapport = new Rapport.Constructeur("TEST", "Ventes", "Octobre")
                .colonne("produit", "Produit", Rapport.Format.TEXTE)
                .colonne("ca", "CA", Rapport.Format.MONTANT)
                .ligne("Poulet", new BigDecimal("3500"))
                .construire();
        byte[] classeur = ExportRapport.excel(rapport, ZoneId.of("Africa/Lome"));

        // Relu par le lecteur d'import : le fichier est un vrai .xlsx.
        List<LecteurTableau.Ligne> relu = LecteurTableau.lire("export.xlsx", new ByteArrayInputStream(classeur));
        assertThat(relu).extracting(l -> l.valeur("produit")).contains("Poulet");
    }
}
