package tg.parcauto.api.marketplace;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.exploitation.client.Client;
import tg.parcauto.api.exploitation.client.ClientRepository;
import tg.parcauto.api.exploitation.client.TypeClient;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * L'acheteur en ligne n'a pas de compte : son nom et son telephone suffisent.
 * Il devient une fiche client de l'entreprise, retrouvee par son numero a la
 * commande suivante. Sert aussi a tirer les jetons des liens de suivi.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClientEnLigneService {

    private static final SecureRandom ALEA = new SecureRandom();

    private final ClientRepository clientRepository;
    private final NumerotationService numerotation;

    /** Fiche client de l'acheteur : celle qui porte deja ce telephone, sinon une nouvelle. */
    public Client trouverOuCreer(String nom, String telephone, String email) {
        String numero = telephone.trim();
        return clientRepository.findFirstByTelephoneOrderByIdAsc(numero).orElseGet(() -> {
            Client client = new Client();
            client.setCode(numerotation.prochainNumero("CLI"));
            client.setType(TypeClient.PARTICULIER);
            client.setRaisonSociale(nom.trim());
            client.setTelephone(numero);
            client.setEmail(email == null || email.isBlank() ? null : email.trim());
            client.setObservations("Client cree par une commande en ligne (Marketplace).");
            return clientRepository.save(client);
        });
    }

    /** Jeton imprevisible d'un lien de suivi (32 caracteres hexadecimaux). */
    public static String nouveauJeton() {
        byte[] octets = new byte[16];
        ALEA.nextBytes(octets);
        return HexFormat.of().formatHex(octets);
    }
}
