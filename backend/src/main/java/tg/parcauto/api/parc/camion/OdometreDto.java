package tg.parcauto.api.parc.camion;

import java.time.LocalDate;
import java.util.List;

/** Historique des releves kilometriques d'un camion. */
public record OdometreDto(
        Long camionId,
        String immatriculation,
        LocalDate debut,
        LocalDate fin,
        long kilometrageActuel,
        long distanceSurPeriode,
        int nombreReleves,
        long moyenneKmParJour,
        List<Releve> releves) {

    /** Origine du releve : chaque module alimente l'odometre a sa maniere. */
    public enum Source {
        MISSION("Mission"),
        CARBURANT("Plein de carburant"),
        DEPENSE("Depense"),
        ENTRETIEN("Entretien"),
        GPS("Traceur GPS"),
        SAISIE("Saisie manuelle");

        private final String libelle;

        Source(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    /**
     * Un releve du compteur.
     *
     * @param distanceDepuisPrecedent kilometres parcourus depuis le releve anterieur
     */
    public record Releve(
            LocalDate date,
            long kilometrage,
            Long distanceDepuisPrecedent,
            Source source,
            String libelle,
            String reference,
            String chauffeurNom) {
    }
}
