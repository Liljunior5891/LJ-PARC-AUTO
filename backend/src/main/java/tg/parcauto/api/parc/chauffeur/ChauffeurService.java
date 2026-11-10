package tg.parcauto.api.parc.chauffeur;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChauffeurService {

    private final ChauffeurRepository repository;
    private final NumerotationService numerotation;

    @Transactional(readOnly = true)
    public Page<ChauffeurDto.Reponse> rechercher(String q, StatutChauffeur statut, Boolean actif, Pageable pageable) {
        return repository.findAll(ChauffeurSpecifications.rechercher(q, statut, actif), pageable)
                .map(ChauffeurDto.Reponse::de);
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
        String matricule = requete.matricule() == null || requete.matricule().isBlank()
                ? numerotation.prochainNumero("CH")
                : requete.matricule().trim().toUpperCase();
        if (repository.existsByMatriculeIgnoreCase(matricule)) {
            throw new BusinessException("Un chauffeur existe deja avec le matricule " + matricule);
        }
        Chauffeur chauffeur = new Chauffeur();
        chauffeur.setMatricule(matricule);
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
        // En modification, un matricule vide garde le matricule existant.
        if (r.matricule() != null && !r.matricule().isBlank()) {
            c.setMatricule(r.matricule().trim().toUpperCase());
        }
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
        c.setGarantNom(r.garantNom());
        c.setGarantLien(r.garantLien());
        c.setGarantTelephone(r.garantTelephone());
        c.setGarantTelephoneSecondaire(r.garantTelephoneSecondaire());
        c.setGarantAdresse(r.garantAdresse());
        c.setGarantProfession(r.garantProfession());
        c.setGarantPieceIdentite(r.garantPieceIdentite());
        c.setObservations(r.observations());
        if (r.typeChauffeur() != null) {
            c.setTypeChauffeur(r.typeChauffeur());
        }
        c.setTypeContrat(vide(r.typeContrat()));
        c.setDateFinContrat(r.dateFinContrat());
        c.setNumeroCnss(vide(r.numeroCnss()));
        c.setTarif(r.tarif());
        c.setUniteTarif(vide(r.uniteTarif()));
        c.setIdentifiantFiscal(vide(r.identifiantFiscal()));
        c.setGroupeSanguin(vide(r.groupeSanguin()));
        if (r.statut() != null && r.statut() != c.getStatut()) {
            if (r.statut() == StatutChauffeur.EN_MISSION || c.getStatut() == StatutChauffeur.EN_MISSION) {
                throw new BusinessException("Le statut « En mission » vient des missions : demarrez ou cloturez la mission");
            }
            c.setStatut(r.statut());
        }
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
