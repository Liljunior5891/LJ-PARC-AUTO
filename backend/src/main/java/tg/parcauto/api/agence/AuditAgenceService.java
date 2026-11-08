package tg.parcauto.api.agence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Instant;

/**
 * Trace les actions sensibles du module (parametrage, soldes d'ouverture,
 * annulations, clotures) : auteur, date, action, valeur avant et apres.
 * La trace s'ecrit dans la transaction de l'action : une action refusee ne
 * laisse pas de trace trompeuse.
 */
@Service
@RequiredArgsConstructor
public class AuditAgenceService {

    private final JournalAuditRepository repository;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public void tracer(Long agenceId, String action, String objet, Long objetId, String description,
                       Object avant, Object apres) {
        JournalAudit ligne = new JournalAudit();
        ligne.setAgenceId(agenceId);
        ligne.setDateAction(Instant.now());
        ligne.setAction(action);
        ligne.setObjet(objet);
        ligne.setObjetId(objetId);
        ligne.setDescription(description.length() <= 500 ? description : description.substring(0, 500));
        ligne.setAvant(enTexte(avant));
        ligne.setApres(enTexte(apres));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UtilisateurPrincipal p) {
            ligne.setAuteurId(p.getId());
            ligne.setAuteurNom(p.getNomComplet());
        }
        repository.save(ligne);
    }

    private String enTexte(Object valeur) {
        if (valeur == null) {
            return null;
        }
        if (valeur instanceof String s) {
            return s;
        }
        try {
            return json.writeValueAsString(valeur);
        } catch (JsonProcessingException e) {
            return String.valueOf(valeur);
        }
    }
}
