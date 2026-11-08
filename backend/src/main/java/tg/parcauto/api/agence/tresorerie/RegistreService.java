package tg.parcauto.api.agence.tresorerie;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.ReglageAgence;
import tg.parcauto.api.agence.ReglageAgenceRepository;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seule porte d'entree des soldes : chaque operation y ecrit ses lignes, une
 * par compte touche, dans une seule transaction (tout ou rien).
 * <p>
 * Les comptes sont verrouilles dans l'ordre de leurs identifiants avant d'etre
 * lus : deux caissiers qui retirent en meme temps le dernier solde passent l'un
 * apres l'autre, et le second est refuse. Un solde qui descendrait sous le
 * decouvert autorise du compte bloque l'operation avec le compte et le montant
 * manquant.
 */
@Service
@RequiredArgsConstructor
public class RegistreService {

    private final CompteAgenceRepository comptes;
    private final EcritureRepository ecritures;
    private final LigneRegistreRepository lignes;
    private final ReglageAgenceRepository reglages;
    private final NumerotationService numerotation;
    private final tg.parcauto.api.agence.cloture.VerrouJournee verrou;

    @PersistenceContext
    private EntityManager em;

    /** Variation demandee sur un compte ; service et reseau servent aux analyses par service. */
    public record Mouvement(Long compteId, long montant, Long serviceId, Long reseauId) {

        public static Mouvement de(Long compteId, long montant) {
            return new Mouvement(compteId, montant, null, null);
        }
    }

    /** Ce qu'une ecriture doit porter en plus de ses lignes. */
    public record Entete(Long agenceId, NatureEcriture nature, String libelle, String motif,
                         String origineType, Long origineId) {
    }

    /**
     * Ecrit une operation. Les montants nuls sont ignores ; une operation sans
     * aucun mouvement est refusee.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Ecriture enregistrer(Entete entete, List<Mouvement> mouvements) {
        return ecrire(entete, mouvements, null);
    }

    /**
     * Annule une ecriture par l'ecriture inverse : memes comptes, montants
     * opposes, motif obligatoire. Une ecriture ne s'annule qu'une fois, et une
     * annulation ne s'annule pas.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Ecriture contrePasser(Long ecritureId, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif de l'annulation est obligatoire");
        }
        Ecriture origine = ecritures.findById(ecritureId)
                .orElseThrow(() -> ResourceNotFoundException.of("Ecriture", ecritureId));
        if (origine.getNature() == NatureEcriture.ANNULATION) {
            throw new BusinessException("L'ecriture " + origine.getNumero() + " est deja une annulation");
        }
        if (ecritures.existsByEcritureAnnuleeId(ecritureId)) {
            throw new BusinessException("L'ecriture " + origine.getNumero() + " a deja ete annulee");
        }
        List<Mouvement> inverses = lignes.findByEcritureIdOrderByIdAsc(ecritureId).stream()
                .map(l -> new Mouvement(l.getCompteId(), -l.getMontant(), l.getServiceId(), l.getReseauId()))
                .toList();
        Entete entete = new Entete(origine.getAgenceId(), NatureEcriture.ANNULATION,
                "Annulation de " + origine.getNumero() + " - " + origine.getLibelle(), motif.trim(),
                origine.getOrigineType(), origine.getOrigineId());
        return ecrire(entete, inverses, origine.getId());
    }

    /**
     * Verrouille les comptes dans l'ordre de leurs identifiants et relit leur
     * solde en base. Un compte deja charge dans la session (pour un apercu, une
     * synchronisation) est rafraichi sous verrou : sans cela, la seconde de deux
     * operations simultanees calculerait sur un solde perime.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CompteAgence> verrouiller(java.util.Collection<Long> ids) {
        // Les changements en attente (une ecriture precedente de la meme operation) sont
        // d'abord envoyes a la base : le rafraichissement les effacerait sinon.
        em.flush();
        List<CompteAgence> resultat = new ArrayList<>();
        for (Long id : ids.stream().distinct().sorted().toList()) {
            CompteAgence deja = em.find(CompteAgence.class, id);
            if (deja == null) {
                continue;
            }
            em.refresh(deja, LockModeType.PESSIMISTIC_WRITE);
            resultat.add(deja);
        }
        return resultat;
    }

    /** Journee de caisse d'un instant, selon l'heure de bascule de l'entreprise. */
    @Transactional(readOnly = true)
    public LocalDate journee(Instant instant) {
        int bascule = reglages.findFirstByOrderByIdAsc().map(ReglageAgence::getHeureBascule).orElse(0);
        return JourneeCaisse.de(instant, bascule);
    }

    private Ecriture ecrire(Entete entete, List<Mouvement> demandes, Long ecritureAnnuleeId) {
        // Un compte touche deux fois par la meme operation ne recoit qu'une ligne par service.
        Map<String, Mouvement> regroupes = new LinkedHashMap<>();
        for (Mouvement m : demandes) {
            if (m.montant() == 0) {
                continue;
            }
            String cle = m.compteId() + "|" + m.serviceId() + "|" + m.reseauId();
            regroupes.merge(cle, m, (a, b) -> new Mouvement(a.compteId(), a.montant() + b.montant(),
                    a.serviceId(), a.reseauId()));
        }
        List<Mouvement> mouvements = regroupes.values().stream().filter(m -> m.montant() != 0).toList();
        if (mouvements.isEmpty()) {
            throw new BusinessException("L'operation ne fait bouger aucun compte");
        }

        List<Long> ids = mouvements.stream().map(Mouvement::compteId).distinct().toList();
        Map<Long, CompteAgence> verrouilles = verrouiller(ids).stream()
                .collect(Collectors.toMap(CompteAgence::getId, Function.identity()));
        for (Long id : ids) {
            CompteAgence compte = verrouilles.get(id);
            if (compte == null) {
                throw ResourceNotFoundException.of("Compte", id);
            }
            if (!compte.getAgenceId().equals(entete.agenceId())) {
                throw new BusinessException("Le compte " + compte.getLibelle() + " n'appartient pas a cette agence");
            }
            if (!compte.isActif() && entete.nature() != NatureEcriture.ANNULATION) {
                throw new BusinessException("Le compte " + compte.getLibelle() + " est desactive");
            }
        }
        // Controle des soldes sur le total par compte, apres regroupement.
        Map<Long, Long> variation = new LinkedHashMap<>();
        mouvements.forEach(m -> variation.merge(m.compteId(), m.montant(), Long::sum));
        variation.forEach((id, montant) -> {
            CompteAgence compte = verrouilles.get(id);
            long nouveau = compte.getSolde() + montant;
            if (montant < 0 && nouveau < -compte.getDecouvertAutorise()) {
                long manque = -compte.getDecouvertAutorise() - nouveau;
                throw new BusinessException(compte.getLibelle() + " insuffisant : il manque " + francs(manque)
                        + " (solde disponible " + francs(compte.getSolde() + compte.getDecouvertAutorise()) + ")");
            }
        });

        Instant maintenant = Instant.now();
        LocalDate jour = journee(maintenant);
        verrou.exigerOuverte(entete.agenceId(), jour, "aucune ecriture");
        Ecriture ecriture = new Ecriture();
        ecriture.setAgenceId(entete.agenceId());
        ecriture.setNumero(numerotation.prochainNumero("ECR"));
        ecriture.setNature(entete.nature());
        ecriture.setDateOperation(maintenant);
        ecriture.setJournee(jour);
        ecriture.setLibelle(tronquer(entete.libelle(), 255));
        ecriture.setMotif(entete.motif() == null ? null : tronquer(entete.motif(), 500));
        ecriture.setOrigineType(entete.origineType());
        ecriture.setOrigineId(entete.origineId());
        ecriture.setEcritureAnnuleeId(ecritureAnnuleeId);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UtilisateurPrincipal p) {
            ecriture.setAuteurId(p.getId());
            ecriture.setAuteurNom(p.getNomComplet());
        }
        ecriture = ecritures.save(ecriture);

        List<LigneRegistre> nouvelles = new ArrayList<>();
        for (Mouvement m : mouvements) {
            CompteAgence compte = verrouilles.get(m.compteId());
            compte.setSolde(compte.getSolde() + m.montant());
            LigneRegistre ligne = new LigneRegistre();
            ligne.setEcriture(ecriture);
            ligne.setCompteId(compte.getId());
            ligne.setAgenceId(entete.agenceId());
            ligne.setServiceId(m.serviceId() != null ? m.serviceId() : compte.getServiceId());
            ligne.setReseauId(m.reseauId());
            ligne.setMontant(m.montant());
            ligne.setSoldeApres(compte.getSolde());
            ligne.setDateOperation(maintenant);
            ligne.setJournee(ecriture.getJournee());
            nouvelles.add(ligne);
        }
        lignes.saveAll(nouvelles);
        return ecriture;
    }

    /** Montant affiche dans les messages : « 25 000 F ». */
    public static String francs(long montant) {
        return NumberFormat.getIntegerInstance(Locale.FRANCE).format(montant).replace(' ', ' ')
                .replace(' ', ' ') + " F";
    }

    private static String tronquer(String texte, int max) {
        return texte.length() <= max ? texte : texte.substring(0, max);
    }
}
