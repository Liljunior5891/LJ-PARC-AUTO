package tg.parcauto.api.pointdevente.restauration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.restauration.qr.CodeQr;

import java.util.regex.Pattern;

/** Reglages de la commande par code QR, et codes QR des tables. */
@Service
@RequiredArgsConstructor
@Transactional
public class ReglageService {

    /** Adresse de l'application qui accueille le client : http(s), sans espace ni guillemet. */
    private static final Pattern ORIGINE = Pattern.compile("^https?://[A-Za-z0-9.:\\-\\[\\]]+(/[A-Za-z0-9._~\\-/]*)?$");

    private final ReglageRestaurationRepository repository;
    private final TableRestaurantRepository tableRepository;

    /** Sans ligne enregistree, les valeurs par defaut : QR actif, validation par un serveur. */
    @Transactional(readOnly = true)
    public ReglageRestauration courant() {
        return repository.courant().orElseGet(ReglageRestauration::new);
    }

    @Transactional(readOnly = true)
    public RestaurationDto.Reglages lire() {
        return RestaurationDto.Reglages.de(courant());
    }

    public RestaurationDto.Reglages modifier(RestaurationDto.Reglages r) {
        ReglageRestauration reglage = repository.courant().orElseGet(ReglageRestauration::new);
        reglage.setQrActif(r.qrActif());
        reglage.setQrValidation(r.qrValidation());
        reglage.setMessageAccueil(r.messageAccueil() == null || r.messageAccueil().isBlank()
                ? null : r.messageAccueil().trim());
        return RestaurationDto.Reglages.de(repository.save(reglage));
    }

    /** Code QR d'une table, pointant vers la carte en ligne servie a l'adresse donnee. */
    @Transactional(readOnly = true)
    public String codeQr(Long tableId, String origine) {
        if (origine == null || origine.length() > 200 || !ORIGINE.matcher(origine).matches()) {
            throw new BusinessException("Adresse de l'application invalide");
        }
        TableRestaurant table = charger(tableId);
        return CodeQr.svg(origine.replaceAll("/+$", "") + "/t/" + table.getJetonQr());
    }

    /** Nouveau jeton : les codes deja imprimes pour la table cessent de fonctionner. */
    public RestaurationDto.TableReponse regenererJeton(Long tableId) {
        TableRestaurant table = charger(tableId);
        table.setJetonQr(TableRestaurant.nouveauJeton());
        return RestaurationDto.TableReponse.de(table);
    }

    private TableRestaurant charger(Long id) {
        return tableRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Table", id));
    }
}
