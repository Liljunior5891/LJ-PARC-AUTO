package tg.parcauto.api.parametrage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.common.BusinessException;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Parametrage de l'entreprise (chapitre 39).
 * <p>
 * Une seule ligne existe. Tant qu'elle n'a pas ete enregistree, la lecture
 * renvoie une identite par defaut portant le nom fourni par la configuration :
 * l'application a toujours un nom a afficher, sans rien ecrire en base.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SocieteService {

    /** Au-dela, un logo alourdit inutilement chaque page et chaque PDF. */
    private static final long TAILLE_LOGO_MAX = 512 * 1024;
    private static final List<String> TYPES_LOGO =
            List.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final SocieteRepository repository;

    @Value("${parcauto.entreprise.nom:PARC AUTO}")
    private String nomParDefaut;

    @Transactional(readOnly = true)
    public SocieteDto.Reponse lire() {
        return versDto(charger());
    }

    /** Ce que la page de connexion affiche : nom et logo, sans authentification. */
    @Transactional(readOnly = true)
    public SocieteDto.Identite identite() {
        Societe societe = charger();
        return new SocieteDto.Identite(societe.getNom(), societe.getSigle(), logo(societe));
    }

    /** Identite et coordonnees publiees par le site vitrine, sans authentification. */
    @Transactional(readOnly = true)
    public SocieteDto.Vitrine vitrine() {
        Societe s = charger();
        return new SocieteDto.Vitrine(
                s.getNom(), s.getSigle(), s.getFormeJuridique(),
                s.getAdresse(), s.getVille(), s.getPays(),
                s.getTelephone(), s.getEmail(), s.getSiteWeb(), logo(s));
    }

    public SocieteDto.Reponse enregistrer(SocieteDto.Requete requete) {
        Societe societe = chargerPourEcriture();
        societe.setNom(requete.nom().trim());
        societe.setSigle(vide(requete.sigle()));
        societe.setFormeJuridique(vide(requete.formeJuridique()));
        societe.setAdresse(vide(requete.adresse()));
        societe.setVille(vide(requete.ville()));
        societe.setPays(vide(requete.pays()));
        societe.setTelephone(vide(requete.telephone()));
        societe.setEmail(vide(requete.email()));
        societe.setSiteWeb(vide(requete.siteWeb()));
        societe.setNif(vide(requete.nif()));
        societe.setRccm(vide(requete.rccm()));
        societe.setBanque(vide(requete.banque()));
        societe.setCompteBancaire(vide(requete.compteBancaire()));
        societe.setMentionsFacture(vide(requete.mentionsFacture()));
        societe.setTauxTva(requete.tauxTva());
        return versDto(repository.save(societe));
    }

    public SocieteDto.Reponse remplacerLogo(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException("Aucun fichier de logo n'a ete transmis");
        }
        if (fichier.getSize() > TAILLE_LOGO_MAX) {
            throw new BusinessException("Le logo ne doit pas depasser 512 Ko");
        }
        String type = fichier.getContentType();
        if (type == null || !TYPES_LOGO.contains(type.toLowerCase())) {
            throw new BusinessException("Format de logo non accepte : utilisez PNG, JPEG, GIF ou WEBP");
        }

        Societe societe = chargerPourEcriture();
        try {
            societe.setLogoBase64(Base64.getEncoder().encodeToString(fichier.getBytes()));
        } catch (IOException e) {
            throw new BusinessException("Lecture du logo impossible : " + e.getMessage());
        }
        societe.setLogoType(type.toLowerCase());
        return versDto(repository.save(societe));
    }

    public SocieteDto.Reponse supprimerLogo() {
        Societe societe = chargerPourEcriture();
        societe.setLogoBase64(null);
        societe.setLogoType(null);
        return versDto(repository.save(societe));
    }

    /** Taux de TVA en vigueur, applique aux factures etablies desormais. */
    @Transactional(readOnly = true)
    public java.math.BigDecimal tauxTva() {
        return charger().getTauxTva();
    }

    /** Octets du logo, pour l'entete des documents PDF. */
    @Transactional(readOnly = true)
    public Optional<byte[]> octetsLogo() {
        return repository.lire()
                .map(Societe::getLogoBase64)
                .filter(encode -> !encode.isBlank())
                .map(encode -> Base64.getDecoder().decode(encode));
    }

    /** L'entite, pour les services qui composent un document. */
    @Transactional(readOnly = true)
    public Societe entite() {
        return charger();
    }

    /**
     * Ligne existante, ou une identite par defaut non persistee.
     * <p>
     * La lecture ne cree rien : elle s'execute en transaction en lecture seule,
     * qu'un INSERT ferait echouer. La ligne nait au premier enregistrement.
     */
    private Societe charger() {
        return repository.lire().orElseGet(this::parDefaut);
    }

    /** Ligne existante, creee au besoin : reserve aux ecritures. */
    private Societe chargerPourEcriture() {
        return repository.lire().orElseGet(() -> repository.save(parDefaut()));
    }

    private Societe parDefaut() {
        Societe societe = new Societe();
        societe.setNom(nomParDefaut);
        return societe;
    }

    private String logo(Societe societe) {
        if (societe.getLogoBase64() == null || societe.getLogoBase64().isBlank()) {
            return null;
        }
        String type = societe.getLogoType() == null ? "image/png" : societe.getLogoType();
        return "data:" + type + ";base64," + societe.getLogoBase64();
    }

    private String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private SocieteDto.Reponse versDto(Societe s) {
        return new SocieteDto.Reponse(
                s.getNom(), s.getSigle(), s.getFormeJuridique(), s.getAdresse(), s.getVille(),
                s.getPays(), s.getTelephone(), s.getEmail(), s.getSiteWeb(), s.getNif(),
                s.getRccm(), s.getBanque(), s.getCompteBancaire(), s.getMentionsFacture(),
                s.getTauxTva(), logo(s));
    }
}
