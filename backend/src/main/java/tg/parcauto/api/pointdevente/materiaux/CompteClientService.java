package tg.parcauto.api.pointdevente.materiaux;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionTravailService;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comptes clients de la quincaillerie : fiches, encours, vente a credit et
 * reglements.
 * <p>
 * L'encours d'un client est la somme des restes dus de ses ventes validees ;
 * il n'est stocke nulle part et ne peut donc pas deriver. Un reglement passe
 * par une session de caisse ouverte, comme une vente : les especes recues
 * sont attendues dans le tiroir a la fermeture.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompteClientService {

    private final ClientPdvRepository repository;
    private final ReglementClientRepository reglementRepository;
    private final VenteRepository venteRepository;
    private final ReglageMateriauxService reglages;
    private final SessionTravailService sessionsTravail;
    private final NumerotationService numerotation;
    private final SoldeResteRepository soldeRepository;

    /** Encours d'un client : tout ce qu'il doit, et la part dont l'echeance est passee. */
    private record Encours(BigDecimal total, BigDecimal echu) {
        static final Encours AUCUN = new Encours(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    // -------------------------------------------------------------- fiches

    @Transactional(readOnly = true)
    public Page<MateriauxDto.ClientReponse> rechercher(String q, Boolean actif, ClientPdv.Type type, Boolean debiteurs,
                                                       Pageable pageable) {
        reglages.exiger();
        String motif = q == null ? "" : q.trim().toLowerCase();
        if (Boolean.TRUE.equals(debiteurs)) {
            // Clients qui doivent quelque chose, les plus gros encours d'abord.
            Map<Long, Encours> encours = new HashMap<>();
            for (Object[] l : repository.tousLesEncours()) {
                encours.put((Long) l[0], new Encours((BigDecimal) l[1], (BigDecimal) l[2]));
            }
            List<MateriauxDto.ClientReponse> liste = repository.findAllById(encours.keySet()).stream()
                    .filter(c -> motif.isEmpty() || c.getNom().toLowerCase().contains(motif))
                    .filter(c -> type == null || c.getType() == type)
                    .map(c -> versDto(c, encours.get(c.getId())))
                    .sorted((a, b) -> b.encours().compareTo(a.encours()))
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(liste, Pageable.unpaged(), liste.size());
        }
        Page<ClientPdv> page = repository.rechercher(motif, actif, type, pageable);
        Map<Long, Encours> encours = encours(page.getContent().stream().map(ClientPdv::getId).toList());
        return page.map(c -> versDto(c, encours.getOrDefault(c.getId(), Encours.AUCUN)));
    }

    @Transactional(readOnly = true)
    public MateriauxDto.ClientReponse parId(Long id) {
        reglages.exiger();
        return versDto(charger(id));
    }

    /** Un caissier peut creer la fiche d'un client au comptoir, mais pas lui fixer un plafond ni un delai. */
    public MateriauxDto.ClientReponse creer(MateriauxDto.ClientRequete r, UtilisateurPrincipal auteur) {
        reglages.exiger();
        String code = r.code() == null || r.code().isBlank()
                ? numerotation.prochainNumero("CLI")
                : r.code().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Un client existe deja avec le code " + code);
        }
        ClientPdv client = new ClientPdv();
        client.setCode(code);
        appliquer(client, r);
        if (!estGestionnaire(auteur)) {
            client.setPlafondCredit(null);
            client.setDelaiPaiementJours(null);
            client.setActif(true);
        }
        return versDto(repository.save(client));
    }

    public MateriauxDto.ClientReponse modifier(Long id, MateriauxDto.ClientRequete r) {
        reglages.exiger();
        ClientPdv client = charger(id);
        appliquer(client, r);
        return versDto(client);
    }

    /** Archivage logique : les ventes, devis et reglements du client restent consultables. */
    public void archiver(Long id) {
        reglages.exiger();
        charger(id).setActif(false);
    }

    /**
     * Clients enregistres avec ce numero, quelle que soit son ecriture : on
     * compare les 8 derniers chiffres (numero national, sans indicatif).
     */
    @Transactional(readOnly = true)
    public List<MateriauxDto.ClientReponse> parTelephone(String telephone) {
        reglages.exiger();
        String chiffres = telephone == null ? "" : telephone.replaceAll("[^0-9]", "");
        if (chiffres.length() < 6) {
            return List.of();
        }
        String fin = chiffres.length() > 8 ? chiffres.substring(chiffres.length() - 8) : chiffres;
        return repository.findByTelephoneEndingWithOrderByActifDescIdAsc(fin.substring(fin.length() - 1)).stream()
                .filter(c -> c.getTelephone().replaceAll("[^0-9]", "").endsWith(fin))
                .map(this::versDto)
                .toList();
    }

    /** Compte complet : fiche, ventes dues et reglements. */
    @Transactional(readOnly = true)
    public MateriauxDto.Compte compte(Long id) {
        reglages.exiger();
        ClientPdv client = charger(id);
        return new MateriauxDto.Compte(versDto(client),
                venteRepository.duesDuClient(id).stream().map(MateriauxDto.VenteDue::de).toList(),
                reglementRepository.duClient(id).stream().map(MateriauxDto.ReglementLigne::de).toList(),
                venteRepository.aLivrer("", id, org.springframework.data.domain.Pageable.ofSize(50)).stream()
                        .map(MateriauxDto.VenteALivrer::de).toList());
    }

    /** Fiche active, pour une vente ou un devis. */
    @Transactional(readOnly = true)
    public ClientPdv client(Long id) {
        ClientPdv client = charger(id);
        if (!client.isActif()) {
            throw new BusinessException("Le client " + client.getNom() + " est archive");
        }
        return client;
    }

    // ---------------------------------------------------------------- credit

    /**
     * Controle une vente a credit avant son enregistrement et rend son echeance.
     * <p>
     * Le credit doit etre active ; un caissier n'y a droit que si les reglages
     * le permettent. L'acompte respecte le minimum fixe ; le nouvel encours ne
     * depasse pas le plafond du client, et un client en retard n'obtient pas de
     * nouveau credit. Un gerant ou un administrateur passe outre ces deux
     * dernieres limites si les reglages le lui permettent.
     */
    @Transactional(readOnly = true)
    public LocalDate autoriserCredit(ClientPdv client, BigDecimal total, BigDecimal acompte,
                                     UtilisateurPrincipal vendeur) {
        ReglageMateriaux r = reglages.exiger();
        if (!r.isCreditActif()) {
            throw new BusinessException("La vente a credit est desactivee dans les reglages");
        }
        boolean gestionnaire = estGestionnaire(vendeur);
        if (!gestionnaire && !r.isCreditCaissier()) {
            throw new BusinessException("Seul un gerant peut vendre a credit");
        }
        if (acompte.compareTo(total) >= 0) {
            throw new BusinessException("L'acompte couvre le total : encaissez la vente comptant");
        }
        if (r.getAcompteMinimum() > 0) {
            BigDecimal minimum = total.multiply(BigDecimal.valueOf(r.getAcompteMinimum()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING);
            if (acompte.compareTo(minimum) < 0) {
                throw new BusinessException("Acompte minimum de " + r.getAcompteMinimum() + " % : "
                        + entier(minimum) + " F au moins");
            }
        }
        boolean outrepasser = gestionnaire && r.isDepassementGerant();
        Encours encours = encours(List.of(client.getId())).getOrDefault(client.getId(), Encours.AUCUN);
        if (r.isBloquerSiRetard() && encours.echu().signum() > 0 && !outrepasser) {
            throw new BusinessException(client.getNom() + " a " + entier(encours.echu())
                    + " F d'impayes en retard : pas de nouveau credit avant reglement");
        }
        BigDecimal plafond = plafond(client, r);
        BigDecimal credit = total.subtract(acompte);
        if (plafond != null && encours.total().add(credit).compareTo(plafond) > 0 && !outrepasser) {
            BigDecimal disponible = plafond.subtract(encours.total()).max(BigDecimal.ZERO);
            throw new BusinessException("Plafond de credit de " + client.getNom() + " depasse : "
                    + entier(disponible) + " F disponibles sur " + entier(plafond) + " F");
        }
        int delai = client.getDelaiPaiementJours() != null ? client.getDelaiPaiementJours() : r.getDelaiPaiementJours();
        return LocalDate.now().plusDays(delai);
    }

    // ------------------------------------------------------------ reglements

    /**
     * Versement d'un client sur la session de caisse du poste. Il solde la
     * vente designee, ou a defaut ses ventes dues de la plus ancienne a la plus
     * recente ; il ne peut pas depasser ce que le client doit.
     */
    public MateriauxDto.RecuReglement regler(Long clientId, MateriauxDto.ReglementRequete demande,
                                             UtilisateurPrincipal caissier) {
        reglages.exiger();
        ClientPdv client = charger(clientId);
        SessionCaisse session = sessionsTravail.exiger(caissier, demande.sessionId());
        BigDecimal montant = demande.montant().setScale(2, RoundingMode.HALF_UP);

        List<Vente> dues = venteRepository.duesDuClient(clientId);
        if (demande.venteId() != null) {
            dues = dues.stream().filter(v -> v.getId().equals(demande.venteId())).toList();
            if (dues.isEmpty()) {
                throw new BusinessException("Cette vente n'est pas due par " + client.getNom());
            }
        }
        BigDecimal du = dues.stream().map(Vente::getResteDu).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (du.signum() == 0) {
            throw new BusinessException(client.getNom() + " ne doit rien");
        }
        if (montant.compareTo(du) > 0) {
            throw new BusinessException("Le versement depasse ce qui est du : " + entier(du) + " F");
        }
        BigDecimal recu = montant;
        if (demande.modePaiement() == ModePaiementPdv.ESPECES && demande.montantRecu() != null) {
            recu = demande.montantRecu().setScale(2, RoundingMode.HALF_UP);
            if (recu.compareTo(montant) < 0) {
                throw new BusinessException("Montant recu insuffisant : il manque " + entier(montant.subtract(recu)) + " F");
            }
        }

        String numero = numerotation.prochainNumero("REG");
        Instant maintenant = Instant.now();
        BigDecimal reste = montant;
        List<MateriauxDto.Imputation> imputations = new ArrayList<>();
        for (Vente vente : dues) {
            if (reste.signum() == 0) {
                break;
            }
            BigDecimal part = reste.min(vente.getResteDu());
            vente.setResteDu(vente.getResteDu().subtract(part));
            reste = reste.subtract(part);

            ReglementClient reglement = new ReglementClient();
            reglement.setNumero(numero);
            reglement.setClient(client);
            reglement.setVente(vente);
            reglement.setSession(session);
            reglement.setMontant(part);
            reglement.setModePaiement(demande.modePaiement());
            reglement.setReferencePaiement(vide(demande.referencePaiement()));
            reglement.setDateReglement(maintenant);
            reglement.setCaissierNom(caissier.getNomComplet());
            reglementRepository.save(reglement);
            imputations.add(new MateriauxDto.Imputation(vente.getNumero(), part, vente.getResteDu()));
        }
        return new MateriauxDto.RecuReglement(numero, maintenant, client.getId(), client.getNom(),
                client.getTelephone(), montant, demande.modePaiement(), demande.modePaiement().getLibelle(),
                recu, recu.subtract(montant), vide(demande.referencePaiement()), caissier.getNomComplet(),
                session.getNumero(), imputations, du.subtract(montant), ReglementClient.Statut.VALIDE);
    }

    /** Recu d'un versement, pour reimpression. */
    @Transactional(readOnly = true)
    public MateriauxDto.RecuReglement recu(String numero) {
        reglages.exiger();
        List<ReglementClient> lignes = lignesDuRecu(numero);
        ReglementClient premiere = lignes.get(0);
        BigDecimal montant = lignes.stream().map(ReglementClient::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        ClientPdv client = premiere.getClient();
        BigDecimal solde = encours(List.of(client.getId())).getOrDefault(client.getId(), Encours.AUCUN).total();
        return new MateriauxDto.RecuReglement(numero, premiere.getDateReglement(), client.getId(), client.getNom(),
                client.getTelephone(), montant, premiere.getModePaiement(), premiere.getModePaiement().getLibelle(),
                montant, BigDecimal.ZERO, premiere.getReferencePaiement(), premiere.getCaissierNom(),
                premiere.getSession().getNumero(),
                lignes.stream().map(l -> new MateriauxDto.Imputation(l.getVente().getNumero(), l.getMontant(),
                        l.getVente().getResteDu())).toList(),
                solde, premiere.getStatut());
    }

    /**
     * Annulation d'un versement saisi par erreur, par un gerant, tant que sa
     * session est ouverte : les especes n'ont pas encore ete comptees.
     */
    public MateriauxDto.RecuReglement annuler(String numero, String motif, UtilisateurPrincipal auteur) {
        reglages.exiger();
        List<ReglementClient> lignes = lignesDuRecu(numero);
        if (lignes.get(0).getStatut() == ReglementClient.Statut.ANNULE) {
            throw new BusinessException("Le reglement " + numero + " est deja annule");
        }
        if (!lignes.get(0).getSession().ouverte()) {
            throw new BusinessException("La session " + lignes.get(0).getSession().getNumero()
                    + " est fermee : ce reglement ne peut plus etre annule");
        }
        for (ReglementClient l : lignes) {
            if (soldeRepository.existeValidePourVente(l.getVente().getId())) {
                throw new BusinessException("Le reste de la commande " + l.getVente().getNumero()
                        + " a ete solde : annulez d'abord ce solde");
            }
        }
        Instant maintenant = Instant.now();
        for (ReglementClient l : lignes) {
            l.setStatut(ReglementClient.Statut.ANNULE);
            l.setAnnuleLe(maintenant);
            l.setAnnulePar(auteur.getNomComplet());
            l.setMotifAnnulation(vide(motif));
            l.getVente().setResteDu(l.getVente().getResteDu().add(l.getMontant()));
        }
        return recu(numero);
    }

    // --------------------------------------------------------------- interne

    static boolean estGestionnaire(UtilisateurPrincipal u) {
        return u.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_GERANT"));
    }

    private List<ReglementClient> lignesDuRecu(String numero) {
        List<ReglementClient> lignes = reglementRepository.parNumero(numero);
        if (lignes.isEmpty()) {
            throw new ResourceNotFoundException("Reglement introuvable : " + numero);
        }
        return lignes;
    }

    private Map<Long, Encours> encours(List<Long> ids) {
        Map<Long, Encours> resultat = new HashMap<>();
        if (ids.isEmpty()) {
            return resultat;
        }
        for (Object[] l : repository.encours(ids)) {
            resultat.put((Long) l[0], new Encours((BigDecimal) l[1], (BigDecimal) l[2]));
        }
        return resultat;
    }

    /** Plafond retenu : celui du client, sinon celui des reglages ; nul, aucune limite. */
    private static BigDecimal plafond(ClientPdv client, ReglageMateriaux r) {
        return client.getPlafondCredit() != null ? client.getPlafondCredit() : r.getPlafondDefaut();
    }

    private MateriauxDto.ClientReponse versDto(ClientPdv c) {
        return versDto(c, encours(List.of(c.getId())).getOrDefault(c.getId(), Encours.AUCUN));
    }

    private MateriauxDto.ClientReponse versDto(ClientPdv c, Encours e) {
        BigDecimal plafond = plafond(c, reglages.courant());
        BigDecimal disponible = plafond == null ? null : plafond.subtract(e.total()).max(BigDecimal.ZERO);
        return new MateriauxDto.ClientReponse(c.getId(), c.getCode(), c.getType(), c.getType().getLibelle(),
                c.getNom(), c.getTelephone(), c.getEmail(), c.getAdresse(), c.getNif(), c.getPlafondCredit(),
                c.getDelaiPaiementJours(), c.getNote(), c.isActif(), e.total(), e.echu(), plafond, disponible,
                c.getNomFamille(), c.getPrenoms(), c.getDateNaissance(), c.getProfession(), c.getFormeJuridique(),
                c.getRccm(), c.getSecteurActivite(), c.getOrganismeTutelle(), c.getNumeroAgrement(), c.getVille(),
                c.getPays(), c.getPersonneContact(), c.getFonctionContact(), c.getTelephoneContact(),
                c.getConditionsCommerciales());
    }

    /** Nom affiche : les prenoms et le nom d'une personne, sinon la raison sociale. */
    private static String nomAffiche(ClientPdv.Type type, MateriauxDto.ClientRequete r) {
        if (type.estPersonne()) {
            String complet = String.join(" ",
                    r.prenoms() == null ? "" : r.prenoms().trim(),
                    r.nomFamille() == null ? "" : r.nomFamille().trim().toUpperCase()).trim();
            if (!complet.isEmpty()) {
                return complet;
            }
        }
        String nom = vide(r.nom());
        if (nom == null) {
            throw new BusinessException(type.estPersonne()
                    ? "Le nom et les prenoms du client sont requis"
                    : "La raison sociale du client est requise");
        }
        return nom;
    }

    private void appliquer(ClientPdv c, MateriauxDto.ClientRequete r) {
        c.setType(r.type() == null ? ClientPdv.Type.PARTICULIER : r.type());
        c.setNom(nomAffiche(c.getType(), r));
        c.setNomFamille(vide(r.nomFamille()));
        c.setPrenoms(vide(r.prenoms()));
        c.setDateNaissance(r.dateNaissance());
        c.setProfession(vide(r.profession()));
        c.setFormeJuridique(vide(r.formeJuridique()));
        c.setRccm(vide(r.rccm()));
        c.setSecteurActivite(vide(r.secteurActivite()));
        c.setOrganismeTutelle(vide(r.organismeTutelle()));
        c.setNumeroAgrement(vide(r.numeroAgrement()));
        c.setVille(vide(r.ville()));
        c.setPays(vide(r.pays()));
        c.setPersonneContact(vide(r.personneContact()));
        c.setFonctionContact(vide(r.fonctionContact()));
        c.setTelephoneContact(vide(r.telephoneContact()));
        c.setConditionsCommerciales(vide(r.conditionsCommerciales()));
        c.setTelephone(vide(r.telephone()));
        c.setEmail(vide(r.email()));
        c.setAdresse(vide(r.adresse()));
        c.setNif(vide(r.nif()));
        c.setPlafondCredit(r.plafondCredit());
        c.setDelaiPaiementJours(r.delaiPaiementJours());
        c.setNote(vide(r.note()));
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
    }

    private ClientPdv charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Client", id));
    }

    private static String entier(BigDecimal montant) {
        return montant.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
