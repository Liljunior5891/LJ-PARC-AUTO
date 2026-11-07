package tg.parcauto.api.parc.document;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.exploitation.client.ClientService;
import tg.parcauto.api.parc.camion.CamionService;
import tg.parcauto.api.parc.chauffeur.ChauffeurService;
import tg.parcauto.api.pointdevente.materiaux.ClientPdv;
import tg.parcauto.api.pointdevente.materiaux.ClientPdvRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentRepository repository;
    private final StockageFichierService stockage;
    private final CamionService camionService;
    private final ChauffeurService chauffeurService;
    private final ClientService clientService;
    private final ClientPdvRepository clientPdvRepository;
    private final tg.parcauto.api.pointdevente.fournisseur.FournisseurRepository fournisseurPdvRepository;

    @Transactional(readOnly = true)
    public Page<DocumentDto.Reponse> rechercher(ProprietaireDocument proprietaireType, Long proprietaireId,
                                                TypeDocument type, Pageable pageable) {
        return repository
                .findAll(DocumentSpecifications.rechercher(proprietaireType, proprietaireId, type), pageable)
                .map(d -> DocumentDto.Reponse.de(d, libelleProprietaire(d)));
    }

    @Transactional(readOnly = true)
    public DocumentDto.Reponse parId(Long id) {
        Document document = charger(id);
        return DocumentDto.Reponse.de(document, libelleProprietaire(document));
    }

    /** Documents expirant dans les {@code jours} prochains jours, deja expires inclus (chapitre 34). */
    @Transactional(readOnly = true)
    public List<DocumentDto.Reponse> aExpirer(int jours) {
        return repository.expirantAvant(LocalDate.now().plusDays(jours)).stream()
                .map(d -> DocumentDto.Reponse.de(d, libelleProprietaire(d)))
                .toList();
    }

    public DocumentDto.Reponse creer(DocumentDto.Requete requete, MultipartFile fichier) {
        verifierProprietaire(requete.proprietaireType(), requete.proprietaireId());
        if (requete.dateEmission() != null && requete.dateExpiration() != null
                && requete.dateExpiration().isBefore(requete.dateEmission())) {
            throw new BusinessException("La date d'expiration precede la date d'emission");
        }
        Document document = new Document();
        appliquer(document, requete);
        attacherFichier(document, fichier);
        return DocumentDto.Reponse.de(repository.save(document), libelleProprietaire(document));
    }

    public DocumentDto.Reponse modifier(Long id, DocumentDto.Requete requete, MultipartFile fichier) {
        Document document = charger(id);
        verifierProprietaire(requete.proprietaireType(), requete.proprietaireId());
        appliquer(document, requete);
        if (fichier != null && !fichier.isEmpty()) {
            stockage.supprimer(document.getFichierChemin());
            attacherFichier(document, fichier);
        }
        return DocumentDto.Reponse.de(repository.save(document), libelleProprietaire(document));
    }

    @Transactional(readOnly = true)
    public Resource telecharger(Long id) {
        Document document = charger(id);
        if (document.getFichierChemin() == null) {
            throw new ResourceNotFoundException("Aucun fichier attache au document " + id);
        }
        return stockage.charger(document.getFichierChemin());
    }

    @Transactional(readOnly = true)
    public Document charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Document", id));
    }

    public void archiver(Long id) {
        Document document = charger(id);
        document.setArchive(true);
        repository.save(document);
    }

    private void attacherFichier(Document document, MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            return;
        }
        String sousDossier = document.getProprietaireType().name().toLowerCase() + "/" + document.getProprietaireId();
        document.setFichierChemin(stockage.enregistrer(fichier, sousDossier));
        document.setFichierNom(fichier.getOriginalFilename());
        document.setFichierType(fichier.getContentType());
        document.setFichierTaille(fichier.getSize());
    }

    private void appliquer(Document d, DocumentDto.Requete r) {
        d.setProprietaireType(r.proprietaireType());
        d.setProprietaireId(r.proprietaireId());
        d.setType(r.type());
        d.setLibelle(r.libelle().trim());
        d.setNumero(r.numero());
        d.setEmetteur(r.emetteur());
        d.setDateEmission(r.dateEmission());
        d.setDateExpiration(r.dateExpiration());
        d.setCout(r.cout());
        d.setObservations(r.observations());
    }

    private void verifierProprietaire(ProprietaireDocument type, Long id) {
        switch (type) {
            case CAMION -> camionService.charger(id);
            case CHAUFFEUR -> chauffeurService.charger(id);
            case CLIENT -> clientService.charger(id);
            case CLIENT_PDV -> clientPdvRepository.findById(id)
                    .orElseThrow(() -> ResourceNotFoundException.of("Client", id));
            case FOURNISSEUR_PDV -> fournisseurPdvRepository.findById(id)
                    .orElseThrow(() -> ResourceNotFoundException.of("Fournisseur", id));
        }
    }

    private String libelleProprietaire(Document d) {
        try {
            return switch (d.getProprietaireType()) {
                case CAMION -> camionService.charger(d.getProprietaireId()).getImmatriculation();
                case CHAUFFEUR -> chauffeurService.charger(d.getProprietaireId()).nomComplet();
                case CLIENT -> clientService.charger(d.getProprietaireId()).getRaisonSociale();
                case CLIENT_PDV -> clientPdvRepository.findById(d.getProprietaireId())
                        .map(ClientPdv::getNom).orElse(null);
                case FOURNISSEUR_PDV -> fournisseurPdvRepository.findById(d.getProprietaireId())
                        .map(tg.parcauto.api.pointdevente.fournisseur.Fournisseur::getNom).orElse(null);
            };
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }
}
