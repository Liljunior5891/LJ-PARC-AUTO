package tg.parcauto.api.pointdevente.rapport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rapport tabulaire, independant de son affichage : l'ecran le montre, les
 * exports CSV et Excel l'ecrivent a l'identique. Les valeurs sont typees par
 * colonne (texte, nombre, montant, pourcentage, date, date et heure).
 */
public record Rapport(
        String type,
        String titre,
        String sousTitre,
        List<Colonne> colonnes,
        List<Map<String, Object>> lignes,
        Map<String, Object> totaux,
        List<Indicateur> indicateurs) {

    public enum Format { TEXTE, NOMBRE, MONTANT, POURCENT, DATE, DATE_HEURE }

    public record Colonne(String cle, String libelle, Format format) {
    }

    public record Indicateur(String libelle, Object valeur, Format format) {
    }

    /** Assemblage pas a pas d'un rapport. */
    public static final class Constructeur {
        private final String type;
        private final String titre;
        private final String sousTitre;
        private final List<Colonne> colonnes = new ArrayList<>();
        private final List<Map<String, Object>> lignes = new ArrayList<>();
        private final Map<String, Object> totaux = new LinkedHashMap<>();
        private final List<Indicateur> indicateurs = new ArrayList<>();

        public Constructeur(String type, String titre, String sousTitre) {
            this.type = type;
            this.titre = titre;
            this.sousTitre = sousTitre;
        }

        public Constructeur colonne(String cle, String libelle, Format format) {
            colonnes.add(new Colonne(cle, libelle, format));
            return this;
        }

        public Constructeur ligne(Object... valeurs) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            for (int i = 0; i < colonnes.size() && i < valeurs.length; i++) {
                ligne.put(colonnes.get(i).cle(), valeurs[i]);
            }
            lignes.add(ligne);
            return this;
        }

        public Constructeur total(String cle, Object valeur) {
            totaux.put(cle, valeur);
            return this;
        }

        public Constructeur indicateur(String libelle, Object valeur, Format format) {
            indicateurs.add(new Indicateur(libelle, valeur, format));
            return this;
        }

        public Rapport construire() {
            return new Rapport(type, titre, sousTitre, colonnes, lignes, totaux, indicateurs);
        }
    }
}
