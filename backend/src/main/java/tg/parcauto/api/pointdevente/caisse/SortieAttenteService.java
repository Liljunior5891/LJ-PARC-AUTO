package tg.parcauto.api.pointdevente.caisse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Sorties d'especes demandees a la caisse quand la validation des depenses de
 * caisse est activee : visa du gerant, puis approbation de l'administrateur qui
 * fait sortir les especes du tiroir.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SortieAttenteService {

    private final SortieAttenteRepository repository;
    private final CaisseService caisse;

    public record Reponse(Long id, String numero, Long sessionId, String sessionNumero, String caisseNom,
                          BigDecimal montant, String motif, String auteurNom, Instant demandeeLe,
                          SortieAttente.Statut statut, String viseePar, Instant viseeLe, String approuveePar,
                          Instant approuveeLe, String refuseePar, Instant refuseeLe, String motifRefus) {

        static Reponse de(SortieAttente s) {
            return new Reponse(s.getId(), s.getNumero(), s.getSession().getId(), s.getSession().getNumero(),
                    s.getSession().getCaisse().getNom(), s.getMontant(), s.getMotif(), s.getAuteurNom(), s.getDemandeeLe(),
                    s.getStatut(), s.getViseePar(), s.getViseeLe(), s.getApprouveePar(), s.getApprouveeLe(),
                    s.getRefuseePar(), s.getRefuseeLe(), s.getMotifRefus());
        }
    }

    /** Sorties qui attendent une validation de cet utilisateur (gerant : son visa ; administrateur : tout). */
    @Transactional(readOnly = true)
    public List<Reponse> aValider(UtilisateurPrincipal u) {
        List<SortieAttente.Statut> statuts = CircuitDepensesCaisse.administrateur(u)
                ? List.of(SortieAttente.Statut.ATTENTE_GERANT, SortieAttente.Statut.ATTENTE_ADMIN)
                : CircuitDepensesCaisse.gerant(u) ? List.of(SortieAttente.Statut.ATTENTE_GERANT) : List.of();
        return statuts.isEmpty() ? List.of() : repository.parStatuts(statuts).stream().map(Reponse::de).toList();
    }

    /** Dernieres sorties traitees (validees ou refusees), pour l'historique de l'ecran de validation. */
    @Transactional(readOnly = true)
    public List<Reponse> traitees(int nombre) {
        return repository.recentes(List.of(SortieAttente.Statut.VALIDEE, SortieAttente.Statut.REFUSEE),
                org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(nombre, 100))))
                .stream().map(Reponse::de).toList();
    }

    public Reponse valider(Long id, UtilisateurPrincipal u) {
        SortieAttente s = enAttente(id);
        Instant maintenant = Instant.now();
        if (!CircuitDepensesCaisse.administrateur(u)) {
            CircuitDepensesCaisse.exigerVisa(u);
            if (s.getStatut() != SortieAttente.Statut.ATTENTE_GERANT) {
                throw new BusinessException("La sortie " + s.getNumero() + " attend la validation de l'administrateur");
            }
            s.setStatut(SortieAttente.Statut.ATTENTE_ADMIN);
            s.setViseePar(u.getNomComplet());
            s.setViseeLe(maintenant);
            return Reponse.de(s);
        }
        if (s.getViseePar() == null) {
            s.setViseePar(u.getNomComplet());
            s.setViseeLe(maintenant);
        }
        MouvementCaisse m = caisse.sortieApprouvee(s.getSession(), s.getMontant(),
                "Sortie " + s.getNumero() + " demandee par " + s.getAuteurNom() + " : " + s.getMotif(), u);
        s.setMouvementCaisse(m);
        s.setStatut(SortieAttente.Statut.VALIDEE);
        s.setApprouveePar(u.getNomComplet());
        s.setApprouveeLe(maintenant);
        return Reponse.de(s);
    }

    public Reponse refuser(Long id, String motif, UtilisateurPrincipal u) {
        SortieAttente s = enAttente(id);
        if (!CircuitDepensesCaisse.administrateur(u)) {
            CircuitDepensesCaisse.exigerVisa(u);
            if (s.getStatut() != SortieAttente.Statut.ATTENTE_GERANT) {
                throw new BusinessException("La sortie " + s.getNumero() + " attend la decision de l'administrateur");
            }
        }
        s.setStatut(SortieAttente.Statut.REFUSEE);
        s.setRefuseePar(u.getNomComplet());
        s.setRefuseeLe(Instant.now());
        s.setMotifRefus(motif.trim());
        return Reponse.de(s);
    }

    private SortieAttente enAttente(Long id) {
        SortieAttente s = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Sortie de caisse", id));
        if (!s.getStatut().enAttente()) {
            throw new BusinessException("La sortie " + s.getNumero() + " n'attend plus de validation");
        }
        return s;
    }
}
