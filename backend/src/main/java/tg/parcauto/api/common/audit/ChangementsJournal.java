package tg.parcauto.api.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Valeurs avant et apres d'une ligne de journal, champ par champ. Partage par le
 * journal des entreprises et le journal general du super-admin, pour qu'une meme
 * ligne s'affiche partout de la meme facon. Le journal des agences peut contenir
 * des objets imbriques : ils sont rendus en JSON.
 */
public final class ChangementsJournal {

    /** Un champ change : son nom technique, son nom lisible, sa valeur avant et apres. */
    public record Changement(String champ, String libelle, String avant, String apres) {
    }

    private ChangementsJournal() {
    }

    public static List<Changement> lire(ObjectMapper json, String avant, String apres) {
        Map<String, String> a = valeurs(json, avant);
        Map<String, String> b = valeurs(json, apres);
        Set<String> champs = new LinkedHashSet<>(a.keySet());
        champs.addAll(b.keySet());
        List<Changement> resultat = new ArrayList<>();
        for (String champ : champs) {
            resultat.add(new Changement(champ, EcouteurAudit.champLisible(champ), a.get(champ), b.get(champ)));
        }
        return resultat;
    }

    private static Map<String, String> valeurs(ObjectMapper json, String texte) {
        if (texte == null || texte.isBlank()) {
            return Map.of();
        }
        try {
            Object valeur = json.readValue(texte, Object.class);
            Map<String, String> resultat = new LinkedHashMap<>();
            if (valeur instanceof Map<?, ?> m) {
                m.forEach((k, v) -> resultat.put(String.valueOf(k), texte(json, v)));
            } else {
                resultat.put("valeur", texte(json, valeur));
            }
            return resultat;
        } catch (Exception e) {
            return Map.of("valeur", texte.length() > 300 ? texte.substring(0, 299) + "…" : texte);
        }
    }

    private static String texte(ObjectMapper json, Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Map<?, ?> || v instanceof List<?>) {
            try {
                return json.writeValueAsString(v);
            } catch (Exception e) {
                return String.valueOf(v);
            }
        }
        return String.valueOf(v);
    }
}
