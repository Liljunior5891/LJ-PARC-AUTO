package tg.parcauto.api.plateforme.parametres;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tg.parcauto.api.common.audit.EcritureJournal;
import tg.parcauto.api.common.audit.JournalActiviteService;
import tg.parcauto.api.common.notification.NotificationService;

import java.util.Map;

/** Reglages generaux de la plateforme (une seule ligne, id 1). */
@Service
@RequiredArgsConstructor
public class ParametresPlateformeService {

    private final JdbcTemplate jdbc;
    private final JournalActiviteService journal;
    private final NotificationService notifications;

    /**
     * @param inscriptionConfirmationEmail vrai : l'inscription attend l'ouverture du lien recu par e-mail
     * @param serveurEmailConfigure        vrai si un serveur SMTP est branche (sinon les e-mails sont simules)
     */
    public record Vue(boolean inscriptionConfirmationEmail, boolean serveurEmailConfigure) {
    }

    public record Modification(boolean inscriptionConfirmationEmail) {
    }

    /** L'inscription en libre-service exige-t-elle la confirmation de l'adresse e-mail ? */
    public boolean confirmationEmailInscription() {
        Boolean v = jdbc.query("select inscription_confirmation_email from plateforme_parametres where id = 1",
                rs -> rs.next() ? rs.getBoolean(1) : null);
        // Ligne absente : on garde le comportement le plus sur.
        return v == null || v;
    }

    public Vue lire() {
        return new Vue(confirmationEmailInscription(),
                notifications.fournisseurActif(NotificationService.Canal.EMAIL));
    }

    public Vue modifier(Modification m) {
        boolean avant = confirmationEmailInscription();
        jdbc.update("""
                update plateforme_parametres set inscription_confirmation_email = ?, modifie_le = now(), modifie_par = ?
                where id = 1
                """, m.inscriptionConfirmationEmail(), EcritureJournal.contexte().auteurNom());
        if (avant != m.inscriptionConfirmationEmail()) {
            journal.plateforme("MODIFICATION", "ParametresPlateforme", "Parametrage de la plateforme", 1L,
                    m.inscriptionConfirmationEmail()
                            ? "Confirmation par e-mail de l'inscription activee"
                            : "Confirmation par e-mail de l'inscription desactivee",
                    Map.of("inscriptionConfirmationEmail", String.valueOf(avant)),
                    Map.of("inscriptionConfirmationEmail", String.valueOf(m.inscriptionConfirmationEmail())));
        }
        return lire();
    }
}
