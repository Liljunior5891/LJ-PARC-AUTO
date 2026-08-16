package tg.parcauto.api.gps.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tg.parcauto.api.common.BusinessException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Repertorie les adaptateurs disponibles et route vers le bon fournisseur. */
@Component
public class RegistreAdaptateurs {

    private final Map<String, AdaptateurGps> adaptateurs;

    public RegistreAdaptateurs(List<AdaptateurGps> disponibles) {
        this.adaptateurs = disponibles.stream()
                .collect(Collectors.toMap(a -> a.fournisseur().toLowerCase(Locale.ROOT),
                        Function.identity()));
    }

    public AdaptateurGps pour(String fournisseur) {
        AdaptateurGps adaptateur = adaptateurs.get(fournisseur.toLowerCase(Locale.ROOT));
        if (adaptateur == null) {
            throw new BusinessException("Fournisseur GPS inconnu : " + fournisseur
                    + ". Disponibles : " + String.join(", ", adaptateurs.keySet()));
        }
        return adaptateur;
    }

    public List<String> fournisseurs() {
        return adaptateurs.keySet().stream().sorted().toList();
    }
}
