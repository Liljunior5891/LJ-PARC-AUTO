package tg.parcauto.api.parc.chauffeur;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChauffeurService {

    private final ChauffeurRepository repository;

    @Transactional(readOnly = true)
    public Page<ChauffeurDto.Reponse> rechercher(String q, StatutChauffeur statut, Boolean actif, Pageable pageable) {
        String terme = (q == null || q.isBlank()) ? null : q.trim();
        return repository.rechercher(terme, statut, actif, pageable).map(ChauffeurDto.Reponse::de);
    }

    @Transactional(readOnly = true)
    public ChauffeurDto.Reponse parId(Long id) {
        return ChauffeurDto.Reponse.de(charger(id));
    }

    @Transactional(readOnly = true)
    public Chauffeur charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Chauffeur", id));
    }

    /** Chauffeurs dont le permis expire dans les {@code jours} prochains jours (chapitre 34). */
    @Transactional(readOnly = true)
    public List<ChauffeurDto.Reponse> permisAExpirer(int jours) {
        return repository.permisExpirantAvant(LocalDate.now().plusDays(jours)).stream()
                .map(ChauffeurDto.Reponse::de)
                .toList();
    }

    public ChauffeurDto.Reponse creer(ChauffeurDto.Requete requete) {
        if (repository.existsByMatriculeIgnoreCase(requete.matricule())) {
            throw new BusinessException("Un chauffeur existe deja avec le matricule " + requete.matricule());
        }
        Chauffeur chauffeur = new Chauffeur();
        appliquer(chauffeur, requete);
        return ChauffeurDto.Reponse.de(repository.save(chauffeur));
    }

    public ChauffeurDto.Reponse modifier(Long id, ChauffeurDto.Requete requete) {
        Chauffeur chauffeur = charger(id);
        appliquer(chauffeur, requete);
        return ChauffeurDto.Reponse.de(repository.save(chauffeur));
    }

    public void archiver(Long id) {
        Chauffeur chauffeur = charger(id);
        chauffeur.setActif(false);
        chauffeur.setStatut(StatutChauffeur.INACTIF);
        repository.save(chauffeur);
    }

    private void appliquer(Chauffeur c, ChauffeurDto.Requete r) {
        if (r.permisObtention() != null && r.permisExpiration() != null
                && r.permisExpiration().isBefore(r.permisObtention())) {
            throw new BusinessException("La date d'expiration du permis precede sa date d'obtention");
        }
        c.setMatricule(r.matricule().trim().toUpperCase());
        c.setNom(r.nom().trim());
        c.setPrenom(r.prenom().trim());
        c.setPhotoUrl(r.photoUrl());
        c.setTelephone(r.telephone().trim());
        c.setTelephoneUrgence(r.telephoneUrgence());
        c.setEmail(r.email());
        c.setAdresse(r.adresse());
        c.setDateNaissance(r.dateNaissance());
        c.setNumeroPermis(r.numeroPermis());
        c.setCategoriesPermis(r.categoriesPermis());
        c.setPermisObtention(r.permisObtention());
        c.setPermisExpiration(r.permisExpiration());
        c.setDateEmbauche(r.dateEmbauche());
        c.setSalaireMensuel(r.salaireMensuel());
        c.setObservations(r.observations());
        if (r.statut() != null) {
            c.setStatut(r.statut());
        }
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
    }
}
