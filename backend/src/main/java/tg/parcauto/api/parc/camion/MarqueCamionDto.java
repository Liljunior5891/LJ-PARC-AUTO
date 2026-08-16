package tg.parcauto.api.parc.camion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MarqueCamionDto {

    private MarqueCamionDto() {
    }

    public record Requete(
            @NotBlank @Size(max = 80) String nom,
            @Size(max = 500) String logoUrl,
            @Size(max = 20) String couleur,
            @Size(max = 80) String pays,
            Integer ordre,
            Boolean actif) {
    }

    public record Reponse(
            Long id,
            String nom,
            String logoUrl,
            String couleur,
            String monogramme,
            String pays,
            Integer ordre,
            boolean actif) {

        public static Reponse de(MarqueCamion m) {
            return new Reponse(m.getId(), m.getNom(), m.getLogoUrl(), m.getCouleur(),
                    m.monogramme(), m.getPays(), m.getOrdre(), m.isActif());
        }
    }
}
