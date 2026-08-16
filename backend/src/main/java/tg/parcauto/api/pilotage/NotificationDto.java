package tg.parcauto.api.pilotage;

import java.time.Instant;

/**
 * Notification presentee a l'utilisateur, chapitre 34 du cahier des charges.
 *
 * @param cible  route de l'ecran concerne, pour rendre la notification actionnable
 * @param refId  identifiant de l'element vise sur cet ecran
 */
public record NotificationDto(
        Niveau niveau,
        String categorie,
        String message,
        String cible,
        Long refId,
        Instant horodatage) {

    /** L'ordre des constantes fixe la priorite d'affichage. */
    public enum Niveau {
        CRITIQUE("Critique"),
        AVERTISSEMENT("Avertissement"),
        INFORMATION("Information");

        private final String libelle;

        Niveau(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    public record Synthese(long total, long critiques, long avertissements, Instant calculeLe) {
    }
}
