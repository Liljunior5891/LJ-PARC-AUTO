package tg.parcauto.api.pointdevente.caisse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.pointdevente.personnel.PersonnelService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Session sur laquelle une personne encaisse : celle, ouverte par un gerant,
 * d'une caisse qui lui est affectee. Un gerant peut encaisser sur toutes les
 * sessions ouvertes. Quand plusieurs conviennent, le poste precise laquelle.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionTravailService {

    private final SessionCaisseRepository repository;

    /** Sessions ouvertes sur lesquelles l'utilisateur peut encaisser, par nom de caisse. */
    public List<SessionCaisse> ouvertesPour(UtilisateurPrincipal utilisateur) {
        boolean gestionnaire = PersonnelService.gestionnaire(utilisateur);
        return repository.findByStatut(SessionCaisse.Statut.OUVERTE).stream()
                .filter(s -> s.getCaisse().isActif())
                .filter(s -> gestionnaire || s.getCaisse().getUtilisateurIds().contains(utilisateur.getId()))
                .sorted(Comparator.comparing(s -> s.getCaisse().getNom().toLowerCase()))
                .toList();
    }

    /** La session choisie, ou la seule possible ; vide si aucune ou s'il faut choisir. */
    public Optional<SessionCaisse> courante(UtilisateurPrincipal utilisateur, Long sessionId) {
        List<SessionCaisse> ouvertes = ouvertesPour(utilisateur);
        if (sessionId != null) {
            return ouvertes.stream().filter(s -> s.getId().equals(sessionId)).findFirst();
        }
        return ouvertes.size() == 1 ? Optional.of(ouvertes.get(0)) : Optional.empty();
    }

    /**
     * En restauration, aucune action sur les commandes tant que l'utilisateur
     * n'a pas de caisse ouverte : un gerant doit d'abord ouvrir la session.
     */
    public void exigerCaisseOuverte(UtilisateurPrincipal utilisateur) {
        if (ouvertesPour(utilisateur).isEmpty()) {
            throw new BusinessException(
                    "Caisse fermee : un gerant doit ouvrir la session de votre caisse avant toute commande");
        }
    }

    /** Au moins une caisse ouverte dans l'etablissement : les clients peuvent commander depuis leur table. */
    public boolean uneCaisseOuverte() {
        return repository.countByStatut(SessionCaisse.Statut.OUVERTE) > 0;
    }

    /** Session sur laquelle encaisser ; refus explicite s'il n'y en a pas ou s'il faut choisir. */
    public SessionCaisse exiger(UtilisateurPrincipal utilisateur, Long sessionId) {
        List<SessionCaisse> ouvertes = ouvertesPour(utilisateur);
        if (sessionId != null) {
            return ouvertes.stream().filter(s -> s.getId().equals(sessionId)).findFirst()
                    .orElseThrow(() -> new BusinessException(
                            "Cette session de caisse est fermee ou ne vous est pas ouverte : choisissez une autre caisse"));
        }
        if (ouvertes.isEmpty()) {
            throw new BusinessException(
                    "Aucune caisse ouverte : un gerant doit ouvrir la session de votre caisse avant toute vente");
        }
        if (ouvertes.size() > 1) {
            throw new BusinessException("Plusieurs caisses sont ouvertes : choisissez celle sur laquelle vous encaissez");
        }
        return ouvertes.get(0);
    }
}
