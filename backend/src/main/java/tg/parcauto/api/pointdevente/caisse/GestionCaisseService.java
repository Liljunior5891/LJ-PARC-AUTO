package tg.parcauto.api.pointdevente.caisse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.personnel.PersonnelService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Caisses creees par le gerant et leur affectation au personnel. */
@Service
@RequiredArgsConstructor
@Transactional
public class GestionCaisseService {

    private final CaisseRepository repository;
    private final SessionCaisseRepository sessionRepository;
    private final PersonnelService personnel;
    private final tg.parcauto.api.pointdevente.lieu.LieuStockRepository lieux;

    public record Requete(
            @NotBlank @Size(max = 80) String nom,
            @Size(max = 255) String description,
            Boolean actif,
            Set<Long> utilisateurIds,
            /** Magasin d'ou vend la caisse, quand l'entreprise a des entrepots. */
            Long lieuId) {
    }

    /** Session en cours sur la caisse, pour savoir qui la tient. */
    public record SessionEnCours(Long id, String numero, Long caissierId, String caissierNom, Instant ouverteLe) {
    }

    public record Reponse(
            Long id, String nom, String description, boolean actif,
            List<PersonnelService.Membre> utilisateurs, SessionEnCours session, long sessions,
            Long lieuId, String lieuNom) {
    }

    @Transactional(readOnly = true)
    public List<Reponse> lister() {
        List<Caisse> caisses = repository.findAllByOrderByNomAsc();
        Map<Long, SessionCaisse> ouvertes = ouvertes();
        Map<Long, PersonnelService.Membre> membres = personnel.parId(caisses.stream()
                .flatMap(c -> c.getUtilisateurIds().stream()).collect(Collectors.toSet()));
        return caisses.stream().map(c -> versDto(c, ouvertes.get(c.getId()), membres)).toList();
    }

    /** Caisses que l'utilisateur peut ouvrir : les siennes, ou toutes pour un gerant. */
    @Transactional(readOnly = true)
    public List<Reponse> disponibles(UtilisateurPrincipal utilisateur) {
        boolean gestionnaire = PersonnelService.gestionnaire(utilisateur);
        Map<Long, SessionCaisse> ouvertes = ouvertes();
        return repository.findAllByOrderByNomAsc().stream()
                .filter(Caisse::isActif)
                .filter(c -> gestionnaire || c.getUtilisateurIds().contains(utilisateur.getId()))
                .map(c -> versDto(c, ouvertes.get(c.getId()), Map.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Reponse detail(Long id) {
        Caisse caisse = charger(id);
        return versDto(caisse, sessionRepository.findByCaisseIdAndStatut(id, SessionCaisse.Statut.OUVERTE).orElse(null),
                personnel.parId(caisse.getUtilisateurIds()));
    }

    public Reponse creer(Requete r) {
        if (repository.existsByNomIgnoreCase(r.nom().trim())) {
            throw new BusinessException("Une caisse porte deja le nom " + r.nom().trim());
        }
        Caisse caisse = new Caisse();
        appliquer(caisse, r);
        return detail(repository.saveAndFlush(caisse).getId());
    }

    public Reponse modifier(Long id, Requete r) {
        Caisse caisse = charger(id);
        if (repository.existsByNomIgnoreCaseAndIdNot(r.nom().trim(), id)) {
            throw new BusinessException("Une caisse porte deja le nom " + r.nom().trim());
        }
        if (Boolean.FALSE.equals(r.actif()) && caisse.isActif()
                && sessionRepository.findByCaisseIdAndStatut(id, SessionCaisse.Statut.OUVERTE).isPresent()) {
            throw new BusinessException("La caisse " + caisse.getNom() + " est ouverte : fermez-la avant de la desactiver");
        }
        appliquer(caisse, r);
        repository.flush();
        return detail(id);
    }

    /**
     * Caisse sur laquelle ouvrir une session : celle demandee si l'utilisateur y
     * a droit, sinon sa seule caisse libre.
     */
    @Transactional(readOnly = true)
    public Caisse aOuvrir(Long caisseId, UtilisateurPrincipal utilisateur) {
        boolean gestionnaire = PersonnelService.gestionnaire(utilisateur);
        if (caisseId != null) {
            Caisse caisse = charger(caisseId);
            if (!caisse.isActif()) {
                throw new BusinessException("La caisse " + caisse.getNom() + " est desactivee");
            }
            if (!gestionnaire && !caisse.getUtilisateurIds().contains(utilisateur.getId())) {
                throw new BusinessException("La caisse " + caisse.getNom() + " ne vous est pas affectee");
            }
            return caisse;
        }
        Map<Long, SessionCaisse> ouvertes = ouvertes();
        List<Caisse> libres = repository.findAllByOrderByNomAsc().stream()
                .filter(Caisse::isActif)
                .filter(c -> gestionnaire || c.getUtilisateurIds().contains(utilisateur.getId()))
                .filter(c -> !ouvertes.containsKey(c.getId()))
                .toList();
        if (libres.isEmpty()) {
            throw new BusinessException("Aucune caisse libre ne vous est affectee : demandez a votre gerant");
        }
        if (libres.size() > 1) {
            throw new BusinessException("Choisissez la caisse a ouvrir");
        }
        return libres.get(0);
    }

    private Map<Long, SessionCaisse> ouvertes() {
        return sessionRepository.findByStatut(SessionCaisse.Statut.OUVERTE).stream()
                .collect(Collectors.toMap(s -> s.getCaisse().getId(), s -> s, (a, b) -> a));
    }

    private void appliquer(Caisse c, Requete r) {
        c.setNom(r.nom().trim());
        c.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
        if (r.utilisateurIds() != null) {
            c.getUtilisateurIds().clear();
            c.getUtilisateurIds().addAll(personnel.verifier(r.utilisateurIds()));
        }
        if (r.lieuId() != null) {
            tg.parcauto.api.pointdevente.lieu.LieuStock lieu = lieux.findById(r.lieuId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Lieu", r.lieuId()));
            // Un entrepot stocke et sert les bons, mais il ne vend pas.
            if (lieu.getType() != tg.parcauto.api.pointdevente.lieu.LieuStock.Type.MAGASIN) {
                throw new BusinessException("Une caisse se rattache a un magasin : " + lieu.getNom() + " est un entrepot");
            }
            c.setLieu(lieu);
        }
    }

    private Reponse versDto(Caisse c, SessionCaisse session, Map<Long, PersonnelService.Membre> membres) {
        List<PersonnelService.Membre> affectes = c.getUtilisateurIds().stream()
                .map(membres::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(PersonnelService.Membre::nomComplet))
                .toList();
        return new Reponse(c.getId(), c.getNom(), c.getDescription(), c.isActif(), affectes,
                session == null ? null : new SessionEnCours(session.getId(), session.getNumero(),
                        session.getCaissierId(), session.getCaissierNom(), session.getOuverteLe()),
                sessionRepository.countByCaisseId(c.getId()),
                c.getLieu() == null ? null : c.getLieu().getId(), c.getLieu() == null ? null : c.getLieu().getNom());
    }

    private Caisse charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Caisse", id));
    }
}
