package tg.parcauto.api.pointdevente.restauration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.catalogue.PostePreparation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ecrans de la cuisine et du bar.
 * <p>
 * Chaque envoi d'une commande y apparait comme un bon, des plus anciens aux
 * plus recents. Un bon avance d'un geste : a preparer, puis en preparation,
 * puis pret. Les bons prets restent affiches {@value #MINUTES_PRETS} minutes
 * pour le serveur, ou jusqu'au service.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PreparationService {

    static final long MINUTES_PRETS = 15;

    private final LigneCommandeRepository ligneRepository;
    private final CommandeRepository commandeRepository;
    private final EcranPreparationService ecranService;
    private final EcranPreparationRepository ecranRepository;

    /** Bons d'un poste (cuisine, bar), quand l'etablissement n'a pas cree d'ecrans. */
    @Transactional(readOnly = true)
    public List<RestaurationDto.Bon> bons(PostePreparation poste) {
        exigerSansEcran();
        exigerPoste(poste);
        return regrouper(ligneRepository.aPreparer(poste, EN_COURS, depuis()));
    }

    /** Bons d'un ecran de preparation : les articles de ses categories. */
    @Transactional(readOnly = true)
    public List<RestaurationDto.Bon> bonsEcran(Long ecranId, tg.parcauto.api.security.UtilisateurPrincipal utilisateur) {
        Set<Long> categories = ecranService.categoriesPour(ecranId, utilisateur);
        if (categories.isEmpty()) {
            return List.of();
        }
        return regrouper(ligneRepository.aPreparerParCategories(categories, EN_COURS, depuis()));
    }

    private static final Set<LigneCommande.Statut> EN_COURS =
            EnumSet.of(LigneCommande.Statut.A_PREPARER, LigneCommande.Statut.EN_PREPARATION);

    private static Instant depuis() {
        return Instant.now().minus(Duration.ofMinutes(MINUTES_PRETS));
    }

    private static List<RestaurationDto.Bon> regrouper(List<LigneCommande> lignes) {
        Map<String, List<LigneCommande>> parBon = new LinkedHashMap<>();
        for (LigneCommande ligne : lignes) {
            parBon.computeIfAbsent(ligne.getCommande().getId() + "/" + ligne.getEnvoi(), cle -> new ArrayList<>())
                    .add(ligne);
        }
        return parBon.values().stream().map(PreparationService::bon).toList();
    }

    /**
     * Fait avancer tout un bon : ce qui attend passe en preparation ; si tout
     * est deja en preparation, le bon devient pret.
     */
    public RestaurationDto.Bon avancerBon(RestaurationDto.AvancementBon demande,
                                          tg.parcauto.api.security.UtilisateurPrincipal utilisateur) {
        java.util.function.Predicate<LigneCommande> duBon;
        if (demande.ecranId() != null) {
            Set<Long> categories = ecranService.categoriesPour(demande.ecranId(), utilisateur);
            duBon = l -> l.getPoste() != PostePreparation.AUCUN && l.getArticle().getCategorie() != null
                    && categories.contains(l.getArticle().getCategorie().getId());
        } else {
            exigerSansEcran();
            exigerPoste(demande.poste());
            duBon = l -> l.getPoste() == demande.poste();
        }
        Commande commande = commandeRepository.detail(demande.commandeId())
                .orElseThrow(() -> ResourceNotFoundException.of("Commande", demande.commandeId()));
        List<LigneCommande> lignes = commande.getLignes().stream()
                .filter(l -> duBon.test(l) && demande.envoi().equals(l.getEnvoi()))
                .filter(l -> l.getStatut() == LigneCommande.Statut.A_PREPARER
                        || l.getStatut() == LigneCommande.Statut.EN_PREPARATION
                        || l.getStatut() == LigneCommande.Statut.PRETE)
                .toList();
        if (lignes.isEmpty()) {
            throw new BusinessException("Ce bon n'a plus rien a preparer");
        }
        boolean attente = lignes.stream().anyMatch(l -> l.getStatut() == LigneCommande.Statut.A_PREPARER);
        Instant maintenant = Instant.now();
        for (LigneCommande ligne : lignes) {
            if (attente && ligne.getStatut() == LigneCommande.Statut.A_PREPARER) {
                ligne.setStatut(LigneCommande.Statut.EN_PREPARATION);
            } else if (!attente && ligne.getStatut() == LigneCommande.Statut.EN_PREPARATION) {
                ligne.setStatut(LigneCommande.Statut.PRETE);
                ligne.setPreteLe(maintenant);
            }
        }
        return bon(lignes);
    }

    /** Fait avancer une seule ligne, quand un plat du bon sort avant les autres. */
    public RestaurationDto.LigneBon avancerLigne(Long id) {
        LigneCommande ligne = charger(id);
        switch (ligne.getStatut()) {
            case A_PREPARER -> ligne.setStatut(LigneCommande.Statut.EN_PREPARATION);
            case EN_PREPARATION -> {
                ligne.setStatut(LigneCommande.Statut.PRETE);
                ligne.setPreteLe(Instant.now());
            }
            default -> throw new BusinessException("Cette ligne n'est plus en preparation");
        }
        return RestaurationDto.LigneBon.de(ligne);
    }

    /** Retour arriere d'un geste trop rapide : une ligne prete repasse en preparation. */
    public RestaurationDto.LigneBon reculerLigne(Long id) {
        LigneCommande ligne = charger(id);
        switch (ligne.getStatut()) {
            case PRETE -> {
                ligne.setStatut(LigneCommande.Statut.EN_PREPARATION);
                ligne.setPreteLe(null);
            }
            case EN_PREPARATION -> ligne.setStatut(LigneCommande.Statut.A_PREPARER);
            default -> throw new BusinessException("Cette ligne ne peut pas revenir en arriere");
        }
        return RestaurationDto.LigneBon.de(ligne);
    }

    /** Statut d'un bon : celui de sa ligne la moins avancee. */
    private static RestaurationDto.Bon bon(List<LigneCommande> lignes) {
        LigneCommande premiere = lignes.get(0);
        Commande commande = premiere.getCommande();
        LigneCommande.Statut statut = lignes.stream()
                .map(LigneCommande::getStatut)
                .min(Enum::compareTo)
                .orElse(LigneCommande.Statut.A_PREPARER);
        return new RestaurationDto.Bon(commande.getId(), commande.getNumero(), commande.repere(),
                commande.getServeurNom(), commande.getCouverts(), premiere.getEnvoi(), premiere.getEnvoyeeLe(),
                statut, lignes.stream().map(RestaurationDto.LigneBon::de).toList());
    }

    /**
     * Les postes cuisine et bar ne servent qu'aux etablissements sans ecran :
     * des qu'un ecran existe, chacun ne voit que les ecrans qui lui sont affectes.
     */
    private void exigerSansEcran() {
        if (ecranRepository.findAll().stream().anyMatch(e -> e.isActif() && e.isAfficherEcran())) {
            throw new BusinessException("Aucun ecran de preparation ne vous est affecte : demandez a votre gerant");
        }
    }

    private static void exigerPoste(PostePreparation poste) {
        if (poste == null || poste == PostePreparation.AUCUN) {
            throw new BusinessException("Choisissez l'ecran de la cuisine ou du bar");
        }
    }

    private LigneCommande charger(Long id) {
        return ligneRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Ligne de commande", id));
    }
}
