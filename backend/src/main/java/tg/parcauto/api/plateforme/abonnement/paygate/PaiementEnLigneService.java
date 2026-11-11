package tg.parcauto.api.plateforme.abonnement.paygate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.LimiteurRequetes;
import tg.parcauto.api.plateforme.abonnement.AbonnementService;
import tg.parcauto.api.plateforme.abonnement.MoyenPaiementAbonnement;
import tg.parcauto.api.plateforme.abonnement.PaiementAbonnement;
import tg.parcauto.api.plateforme.abonnement.PaiementAbonnementRepository;
import tg.parcauto.api.plateforme.abonnement.StatutPaiementAbonnement;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Paiement des abonnements par PayGate Global.
 * <ol>
 *   <li>l'entreprise choisit sa formule, son reseau (Flooz, T-Money) et son numero ;</li>
 *   <li>le paiement est enregistre en attente, puis la demande part chez PayGate ;</li>
 *   <li>le client valide sur son telephone avec son code secret ;</li>
 *   <li>l'abonnement s'ouvre quand PayGate confirme le paiement, verifie par notre serveur :
 *       au rappel de PayGate, a la demande de l'ecran, et par une verification periodique.</li>
 * </ol>
 * Les appels a PayGate se font hors transaction : pas de connexion a la base tenue pendant l'attente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaiementEnLigneService {

    /** Verification periodique des paiements en attente de moins de ce delai. */
    private static final Duration SUIVI_MAX = Duration.ofDays(3);

    private final PayGateClient payGate;
    private final AbonnementService abonnements;
    private final PaiementAbonnementRepository paiements;
    private final LimiteurRequetes limiteur;

    /** Lance le paiement : la demande arrive sur le telephone du client. */
    public AbonnementService.PaiementVue payer(Long entrepriseId, AbonnementService.DemandeEnLigne d) {
        if (!payGate.actif()) {
            throw new BusinessException("Le paiement en ligne n'est pas encore disponible : declarez votre paiement");
        }
        limiteur.exiger("paygate-paiement:" + entrepriseId, 5, Duration.ofMinutes(15),
                "Trop de demandes de paiement a la suite : patientez quelques minutes");
        String telephone = telephone(d.telephone());
        PaiementAbonnement p = abonnements.preparerPaiementEnLigne(entrepriseId, d, telephone);
        PayGateClient.Demande reponse;
        try {
            reponse = payGate.demanderPaiement(telephone, p.getMontant().longValueExact(), p.getPaygateIdentifiant(),
                    d.reseau(), "Abonnement " + p.getFormuleNom() + " - " + p.getPeriodicite().getLibelle());
        } catch (BusinessException e) {
            abonnements.demandeRefusee(p.getId(), "PayGate injoignable : demande non envoyee");
            throw e;
        }
        if (reponse.statut() != PayGateClient.DEMANDE_ENREGISTREE) {
            String motif = switch (reponse.statut()) {
                case 2 -> "Paiement en ligne mal configure sur la plateforme (cle PayGate refusee)";
                case 4 -> "Numero ou reseau refuse par PayGate : verifiez le numero et le reseau";
                case 6 -> "Demande en double refusee par PayGate";
                default -> "Demande refusee par PayGate";
            };
            abonnements.demandeRefusee(p.getId(), motif);
            if (reponse.statut() == 2) {
                log.error("PayGate refuse la cle configuree (PAYGATE_AUTH_TOKEN)");
            }
            throw new BusinessException(motif);
        }
        return abonnements.demandeEnvoyee(p.getId(), reponse.txReference());
    }

    /** Etat du paiement verifie aupres de PayGate, a la demande de l'ecran de l'entreprise. */
    public AbonnementService.PaiementVue verifier(Long entrepriseId, Long id) {
        PaiementAbonnement p = abonnements.paiementEnLigneDe(entrepriseId, id);
        if (p.getStatut() == StatutPaiementAbonnement.VALIDE) {
            return abonnements.vuePaiement(id);
        }
        limiteur.exiger("paygate-verif:" + id, 30, Duration.ofMinutes(5),
                "Verification trop frequente : patientez un instant");
        return appliquer(p.getPaygateIdentifiant(), payGate.etat(p.getPaygateIdentifiant()));
    }

    /**
     * Rappel de PayGate. Il n'est pas signe : seul notre identifiant en est lu, et l'etat est
     * redemande a PayGate. Un identifiant inconnu est ignore sans le dire.
     */
    public void rappel(String identifiant) {
        if (identifiant == null || !identifiant.matches("^ABO[0-9A-F]{24}$")) {
            return;
        }
        boolean suivi = paiements.findByMoyenAndStatutAndCreeLeAfter(MoyenPaiementAbonnement.PAYGATE,
                        StatutPaiementAbonnement.EN_ATTENTE, Instant.now().minus(SUIVI_MAX)).stream()
                .anyMatch(p -> identifiant.equals(p.getPaygateIdentifiant()))
                || paiements.findByMoyenAndStatutAndCreeLeAfter(MoyenPaiementAbonnement.PAYGATE,
                        StatutPaiementAbonnement.REJETE, Instant.now().minus(SUIVI_MAX)).stream()
                .anyMatch(p -> identifiant.equals(p.getPaygateIdentifiant()));
        if (!suivi) {
            return;
        }
        try {
            appliquer(identifiant, payGate.etat(identifiant));
        } catch (RuntimeException e) {
            log.warn("Rappel PayGate {} non traite : {}", identifiant, e.getMessage());
        }
    }

    /** Filet de securite : les paiements en attente sont reverifies, meme sans rappel de PayGate. */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void verifierEnAttente() {
        if (!payGate.actif()) {
            return;
        }
        List<PaiementAbonnement> enAttente = paiements.findByMoyenAndStatutAndCreeLeAfter(
                MoyenPaiementAbonnement.PAYGATE, StatutPaiementAbonnement.EN_ATTENTE, Instant.now().minus(SUIVI_MAX));
        for (PaiementAbonnement p : enAttente) {
            try {
                appliquer(p.getPaygateIdentifiant(), payGate.etat(p.getPaygateIdentifiant()));
            } catch (RuntimeException e) {
                log.warn("Verification PayGate {} impossible : {}", p.getPaygateIdentifiant(), e.getMessage());
            }
        }
    }

    /**
     * Validation en mode systeme, comme la bascule nocturne des abonnements : elle active les
     * modules de l'entreprise du paiement, quel que soit l'appelant (rappel, tache, ecran).
     */
    private AbonnementService.PaiementVue appliquer(String identifiant, PayGateClient.Etat etat) {
        return tg.parcauto.api.plateforme.tenant.ContexteEntreprise.enModeSysteme(
                () -> abonnements.appliquerEtatPayGate(identifiant, etat));
    }

    /** Numero mobile money togolais : les 8 chiffres locaux, sans indicatif ni espaces. */
    static String telephone(String saisi) {
        String chiffres = saisi == null ? "" : saisi.replaceAll("[^0-9]", "");
        if (chiffres.length() == 11 && chiffres.startsWith("228")) {
            chiffres = chiffres.substring(3);
        }
        if (!chiffres.matches("^[279]\\d{7}$")) {
            throw new BusinessException("Numero mobile money invalide : 8 chiffres, par exemple 90 12 34 56");
        }
        return chiffres;
    }
}
