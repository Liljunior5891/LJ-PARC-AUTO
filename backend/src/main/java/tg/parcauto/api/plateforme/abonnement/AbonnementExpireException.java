package tg.parcauto.api.plateforme.abonnement;

/** Ecriture refusee : l'abonnement a expire, l'entreprise est en lecture seule. */
public class AbonnementExpireException extends RuntimeException {

    public AbonnementExpireException() {
        super("Votre abonnement a expire : l'application est en lecture seule. "
                + "Renouvelez-le depuis Mon abonnement pour reprendre la saisie.");
    }
}
