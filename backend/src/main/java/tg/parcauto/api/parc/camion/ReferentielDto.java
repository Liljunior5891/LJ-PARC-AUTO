package tg.parcauto.api.parc.camion;

/**
 * Entree de referentiel presentee aux listes deroulantes. Les types de vehicule
 * portent en plus leur famille et le dessin de la carte.
 */
public record ReferentielDto(Long id, String libelle, String complement, String categorie, String dessin) {

    public static ReferentielDto de(TypeCamion type) {
        return new ReferentielDto(type.getId(), type.getNom(), type.getDescription(),
                type.getCategorie(), type.getDessin());
    }

    public static ReferentielDto de(CouleurCamion couleur) {
        return new ReferentielDto(couleur.getId(), couleur.getNom(), couleur.getCodeHexa(), null, null);
    }
}
