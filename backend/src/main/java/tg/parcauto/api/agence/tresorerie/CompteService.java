package tg.parcauto.api.agence.tresorerie;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AgenceRepository;
import tg.parcauto.api.agence.catalogue.ServiceFinancier;
import tg.parcauto.api.agence.catalogue.ServiceFinancierRepository;
import tg.parcauto.api.agence.catalogue.TypeOperation;
import tg.parcauto.api.agence.catalogue.TypeOperationRepository;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comptes crees d'office pour chaque agence : la caisse generale, le
 * sous-compte des commissions, un float par service dont une operation touche
 * le float, un compte de recharge par service qui recharge des cartes. Un
 * service desactive garde ses comptes (et leur historique).
 */
@Service
@RequiredArgsConstructor
public class CompteService {

    private final CompteAgenceRepository comptes;
    private final AgenceRepository agences;
    private final ServiceFinancierRepository services;
    private final TypeOperationRepository types;

    /** Cree les comptes manquants de l'agence et suit le nom des services. */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<CompteAgence> synchroniser(Agence agence) {
        Map<Long, ServiceFinancier> parId = services.findAll().stream()
                .collect(Collectors.toMap(ServiceFinancier::getId, Function.identity()));
        Map<Long, Set<TypeOperation.CompteVirtuel>> besoins = types.findAll().stream()
                .filter(TypeOperation::isActif)
                .filter(t -> t.getCompteVirtuel() != TypeOperation.CompteVirtuel.AUCUN)
                .collect(Collectors.groupingBy(t -> t.getService().getId(),
                        Collectors.mapping(TypeOperation::getCompteVirtuel,
                                Collectors.toCollection(() -> EnumSet.noneOf(TypeOperation.CompteVirtuel.class)))));

        List<CompteAgence> existants = comptes.findByAgenceIdOrderByIdAsc(agence.getId());
        assurer(existants, agence, TypeCompte.CAISSE_GENERALE, null, "Caisse generale");
        assurer(existants, agence, TypeCompte.COMMISSIONS, null, "Commissions");
        besoins.forEach((serviceId, virtuels) -> {
            ServiceFinancier service = parId.get(serviceId);
            if (service == null || !service.isActif()) {
                return;
            }
            if (virtuels.contains(TypeOperation.CompteVirtuel.FLOAT)) {
                assurer(existants, agence, TypeCompte.FLOAT, serviceId, libelle(TypeCompte.FLOAT, service));
            }
            if (virtuels.contains(TypeOperation.CompteVirtuel.RECHARGE)) {
                assurer(existants, agence, TypeCompte.RECHARGE, serviceId, libelle(TypeCompte.RECHARGE, service));
            }
        });
        // Un service renomme renomme ses comptes.
        existants.stream()
                .filter(c -> c.getServiceId() != null && parId.containsKey(c.getServiceId()))
                .forEach(c -> {
                    String attendu = libelle(c.getType(), parId.get(c.getServiceId()));
                    if (!attendu.equals(c.getLibelle())) {
                        c.setLibelle(attendu);
                    }
                });
        return existants;
    }

    /** Caisse du caissier dans l'agence, creee a sa premiere ouverture. */
    @Transactional(propagation = Propagation.REQUIRED)
    public CompteAgence caisseDuCaissier(Agence agence, Long caissierId, String caissierNom) {
        return comptes.findByAgenceIdOrderByIdAsc(agence.getId()).stream()
                .filter(c -> c.getType() == TypeCompte.CAISSE_CAISSIER && caissierId.equals(c.getUtilisateurId()))
                .findFirst()
                .orElseGet(() -> {
                    CompteAgence c = new CompteAgence();
                    c.setAgenceId(agence.getId());
                    c.setType(TypeCompte.CAISSE_CAISSIER);
                    c.setUtilisateurId(caissierId);
                    c.setLibelle("Caisse de " + caissierNom);
                    return comptes.saveAndFlush(c);
                });
    }

    /** Toutes les agences, apres un changement du catalogue. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void synchroniserTout() {
        agences.findAll().forEach(this::synchroniser);
    }

    static String libelle(TypeCompte type, ServiceFinancier service) {
        return switch (type) {
            case FLOAT -> "Float " + service.getNom();
            case RECHARGE -> "Recharge " + service.getNom();
            default -> type.getLibelle();
        };
    }

    private void assurer(List<CompteAgence> existants, Agence agence, TypeCompte type, Long serviceId, String libelle) {
        boolean present = existants.stream().anyMatch(c -> c.getType() == type
                && java.util.Objects.equals(c.getServiceId(), serviceId));
        if (present) {
            return;
        }
        CompteAgence compte = new CompteAgence();
        compte.setAgenceId(agence.getId());
        compte.setType(type);
        compte.setServiceId(serviceId);
        compte.setLibelle(libelle);
        existants.add(comptes.save(compte));
    }
}
