package tg.parcauto.api.pilotage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.parc.affectation.AffectationRepository;
import tg.parcauto.api.parc.camion.CamionService;
import tg.parcauto.api.parc.camion.StatutCamion;
import tg.parcauto.api.parc.chauffeur.ChauffeurRepository;
import tg.parcauto.api.parc.chauffeur.ChauffeurService;
import tg.parcauto.api.parc.chauffeur.StatutChauffeur;
import tg.parcauto.api.parc.document.DocumentDto;
import tg.parcauto.api.parc.document.DocumentService;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TableauBordService {

    private final CamionService camionService;
    private final ChauffeurService chauffeurService;
    private final ChauffeurRepository chauffeurRepository;
    private final AffectationRepository affectationRepository;
    private final DocumentService documentService;

    public TableauBordDto.Parc parc(int joursAlerte) {
        Map<StatutCamion, Long> repartition = camionService.repartitionParStatut();
        long total = repartition.values().stream().mapToLong(Long::longValue).sum();
        long affectationsEnCours = affectationRepository.affectationsEnCours().size();

        List<DocumentDto.Reponse> documents = documentService.aExpirer(joursAlerte);
        long expires = documents.stream().filter(DocumentDto.Reponse::expire).count();

        return new TableauBordDto.Parc(
                total,
                repartition.getOrDefault(StatutCamion.DISPONIBLE, 0L),
                repartition.getOrDefault(StatutCamion.EN_LOCATION, 0L),
                repartition.getOrDefault(StatutCamion.EN_MISSION, 0L),
                repartition.getOrDefault(StatutCamion.EN_MAINTENANCE, 0L),
                repartition.getOrDefault(StatutCamion.IMMOBILISE, 0L),
                repartition,
                chauffeurRepository.countByStatut(StatutChauffeur.ACTIF),
                affectationsEnCours,
                Math.max(0, total - affectationsEnCours),
                documents.size() - expires,
                expires,
                chauffeurService.permisAExpirer(joursAlerte).size());
    }
}
