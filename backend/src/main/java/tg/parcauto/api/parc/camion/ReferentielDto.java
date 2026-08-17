package tg.parcauto.api.parc.camion;

/** Entree de referentiel presentee aux listes deroulantes. */
public record ReferentielDto(Long id, String libelle, String complement) {

    public static ReferentielDto de(TypeCamion type) {
        return new ReferentielDto(type.getId(), type.getNom(), type.getDescription());
    }

    public static ReferentielDto de(CouleurCamion couleur) {
        return new ReferentielDto(couleur.getId(), couleur.getNom(), couleur.getCodeHexa());
    }
}
