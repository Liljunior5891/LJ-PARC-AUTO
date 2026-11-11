package tg.parcauto.api.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.pointdevente.restauration.qr.CodeQr;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pointage des arrivees et suivi des presences : le telephone de l'employe
 * (code QR qui change + position GPS) ou la borne du lieu, puis le PIN.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PointageService {

    public static final ZoneId FUSEAU = ZoneId.of("Africa/Lome");
    /** Marge accordee a l'imprecision du GPS, au-dela du rayon du lieu. */
    private static final int MARGE_GPS_MAX = 30;

    private final EmployePresenceRepository employes;
    private final PointagePresenceRepository pointages;
    private final BornePresenceRepository bornes;
    private final PresenceService presence;
    private final SecuritePresence securite;
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;

    // ------------------------------------------------------------- pointage

    public record PointageTelephone(String code, String pin, Double latitude, Double longitude, Double precision) {
    }

    public record PointageBorne(String pin) {
    }

    public enum Issue { ENREGISTRE, DEJA_POINTE }

    public record Resultat(Issue issue, String nom, String matricule, Instant arriveeLe, int retardMinutes,
                           boolean apresLimite, String lieu, String message) {
    }

    /** Ce que le telephone affiche apres le scan : le lieu, et si le code est encore valable. */
    public record Accueil(String lieu, String entreprise, boolean codeValide, boolean positionExigee) {
    }

    @Transactional(readOnly = true)
    public Accueil accueil(LieuPresence lieu, String code, String entreprise, String appareil) {
        // Chaque appareil ne teste qu'un nombre limite de codes : deviner le code du moment est impraticable.
        limiteur.exiger("qr:" + appareil, 30, java.time.Duration.ofMinutes(10),
                "Trop de codes QR verifies depuis cet appareil : rescannez la borne dans quelques minutes");
        return new Accueil(lieu.getNom(), entreprise, securite.codeValide(lieu.getSecretQr(), code), true);
    }

    /** Pointage depuis le telephone : code QR encore valable, telephone dans le rayon du lieu, puis PIN. */
    public Resultat pointerTelephone(LieuPresence lieu, PointageTelephone d, String appareil) {
        if (!lieu.isActif()) {
            throw new BusinessException("Ce lieu n'accepte plus de pointage");
        }
        exigerCode(lieu, d.code(), appareil);
        if (!lieu.aPosition()) {
            throw new BusinessException("La position de ce lieu n'est pas encore enregistree : pointez sur la borne ou prevenez le gerant");
        }
        if (d.latitude() == null || d.longitude() == null) {
            throw new BusinessException("Autorisez la localisation de votre telephone pour pointer");
        }
        // NaN ou infini rendraient la distance nulle : une position hors des bornes terrestres est refusee.
        if (!Double.isFinite(d.latitude()) || !Double.isFinite(d.longitude())
                || Math.abs(d.latitude()) > 90 || Math.abs(d.longitude()) > 180
                || (d.precision() != null && (!Double.isFinite(d.precision()) || d.precision() < 0))) {
            throw new BusinessException("Position du telephone invalide : reactivez la localisation et reessayez");
        }
        int distance = (int) Math.round(distanceMetres(lieu.getLatitude(), lieu.getLongitude(), d.latitude(), d.longitude()));
        int precision = d.precision() == null ? 0 : (int) Math.round(d.precision());
        if (distance > lieu.getRayonMetres() + Math.min(precision, MARGE_GPS_MAX)) {
            // Pas de distance exacte dans la reponse : elle permettrait de situer le lieu par essais.
            throw new BusinessException("Vous etes hors du perimetre de " + lieu.getNom()
                    + " : approchez-vous du lieu pour pointer");
        }
        return pointer(lieu, d.pin(), PointagePresence.Mode.TELEPHONE, distance, precision, appareil);
    }

    /** Pointage sur la borne du lieu : la tablette ne bouge pas, seul le PIN est demande. */
    public Resultat pointerBorne(BornePresence borne, PointageBorne d) {
        String appareil = "borne:" + borne.getId();
        if (!borne.getLieu().isActif()) {
            throw new BusinessException("Ce lieu n'accepte plus de pointage");
        }
        return pointer(borne.getLieu(), d.pin(), PointagePresence.Mode.BORNE, null, null, appareil);
    }

    private Resultat pointer(LieuPresence lieu, String pin, PointagePresence.Mode mode, Integer distance,
                             Integer precision, String appareil) {
        EmployePresence e = reconnaitre(pin, appareil);
        if (!e.getLieux().isEmpty() && e.getLieux().stream().noneMatch(l -> l.getId().equals(lieu.getId()))) {
            throw new BusinessException(e.nomComplet() + ", vous n'etes pas rattache a " + lieu.getNom());
        }
        Instant maintenant = Instant.now();
        LocalDate jour = LocalDate.ofInstant(maintenant, FUSEAU);
        var deja = pointages.findByEmployeIdAndJour(e.getId(), jour);
        if (deja.isPresent()) {
            PointagePresence p = deja.get();
            return new Resultat(Issue.DEJA_POINTE, e.nomComplet(), e.getMatricule(), p.getArriveeLe(), p.getRetardMinutes(),
                    p.isApresLimite(), p.getLieu() == null ? null : p.getLieu().getNom(),
                    "Deja pointe aujourd'hui a " + heure(p.getArriveeLe()));
        }
        ReglagePresence reglage = presence.reglageCourant();
        PointagePresence p = new PointagePresence();
        p.setEmploye(e);
        p.setJour(jour);
        p.setArriveeLe(maintenant);
        p.setLieu(lieu);
        p.setMode(mode);
        p.setDistanceMetres(distance);
        p.setPrecisionMetres(precision);
        calculerRetard(p, reglage);
        pointages.saveAndFlush(p);
        String message = p.getRetardMinutes() > 0
                ? "Arrivee enregistree a " + heure(maintenant) + " · retard " + duree(p.getRetardMinutes())
                : "Arrivee enregistree a " + heure(maintenant) + " · a l'heure";
        return new Resultat(Issue.ENREGISTRE, e.nomComplet(), e.getMatricule(), maintenant, p.getRetardMinutes(),
                p.isApresLimite(), lieu.getNom(), message);
    }

    /** Retard compte depuis l'heure prevue, des que la tolerance est depassee ; rien un jour non travaille. */
    static void calculerRetard(PointagePresence p, ReglagePresence reglage) {
        LocalTime arrivee = LocalTime.ofInstant(p.getArriveeLe(), FUSEAU);
        if (!reglage.travaille(p.getJour().getDayOfWeek())) {
            p.setRetardMinutes(0);
            p.setApresLimite(false);
            return;
        }
        long minutes = Duration.between(reglage.getHeureArrivee(), arrivee).toMinutes();
        p.setRetardMinutes(minutes > reglage.getToleranceMinutes() ? (int) minutes : 0);
        p.setApresLimite(arrivee.isAfter(reglage.getHeureLimite()));
    }

    private EmployePresence reconnaitre(String pin, String appareil) {
        if (!SecuritePresence.pinValide(pin)) {
            throw new BusinessException("Tapez votre PIN a 6 chiffres");
        }
        long essai = securite.reserverEssai(appareil);
        Long entreprise = ContexteEntreprise.exiger();
        long essaiEntreprise = securite.reserverEssaiEntreprise(entreprise);
        EmployePresence e = employes.findByPinEmpreinte(securite.empreintePin(entreprise, pin))
                .filter(EmployePresence::isActif).orElse(null);
        if (e == null) {
            throw new BusinessException("PIN inconnu : verifiez votre code");
        }
        securite.reussi(appareil, essai);
        securite.reussi("entreprise:" + entreprise, essaiEntreprise);
        return e;
    }

    /** Code QR du moment : un code faux compte comme un essai de l'appareil (pas de devinette a volonte). */
    private void exigerCode(LieuPresence lieu, String code, String appareil) {
        long essai = securite.reserverEssaiCode(appareil);
        if (!securite.codeValide(lieu.getSecretQr(), code)) {
            throw new BusinessException("Ce code QR a expire : scannez celui affiche en ce moment sur la borne");
        }
        securite.reussi("code:" + appareil, essai);
    }

    /** Distance en metres entre deux points GPS (formule de haversine). */
    static double distanceMetres(double lat1, double lon1, double lat2, double lon2) {
        double rayonTerre = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * rayonTerre * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ------------------------------------------------------------- borne

    public record Appairage(String jeton, String lieu, String borne) {
    }

    /** La tablette echange le code a 6 chiffres contre son jeton, remis une seule fois. */
    public Appairage appairer(Long borneId) {
        BornePresence b = bornes.findById(borneId).orElseThrow(() -> new BusinessException("Borne introuvable"));
        String jeton = securite.jetonAleatoire();
        b.setJetonEmpreinte(SecuritePresence.sha256(jeton));
        b.setCodeAppairage(null);
        b.setCodeExpireLe(null);
        b.setAppaireeLe(Instant.now());
        b.setDernierContact(Instant.now());
        bornes.flush();
        return new Appairage(jeton, b.getLieu().getNom(), b.getNom());
    }

    public record QrBorne(String lieu, String borne, String entreprise, String adresse, String svg, int secondesRestantes) {
    }

    /** Code QR du moment, affiche par la borne ; {@code base} est l'adresse de l'application vue par la tablette. */
    public QrBorne qr(Long borneId, String base, String entreprise) {
        BornePresence b = bornes.findById(borneId).orElseThrow(() -> new BusinessException("Borne introuvable"));
        b.setDernierContact(Instant.now());
        String adresse = joignable(base).replaceAll("/+$", "") + "/pointage/" + b.getLieu().getJetonPublic()
                + "?c=" + securite.codeCourant(b.getLieu().getSecretQr());
        return new QrBorne(b.getLieu().getNom(), b.getNom(), entreprise, adresse, CodeQr.svg(adresse),
                SecuritePresence.secondesRestantes());
    }

    /**
     * Une borne ouverte en « localhost » produirait un code QR qu'aucun telephone
     * ne peut ouvrir : on remplace alors l'hote par l'adresse du serveur sur le
     * reseau local (l'application et l'API tournent sur la meme machine).
     */
    static String joignable(String base) {
        try {
            java.net.URI uri = java.net.URI.create(base.trim());
            String hote = uri.getHost();
            if (hote == null || !(hote.equals("localhost") || hote.startsWith("127.") || hote.equals("[::1]") || hote.equals("::1"))) {
                return base;
            }
            String ip = adresseReseau();
            if (ip == null) {
                return base;
            }
            return new java.net.URI(uri.getScheme(), null, ip, uri.getPort(), uri.getPath(), null, null).toString();
        } catch (Exception e) {
            return base;
        }
    }

    /** Premiere adresse IPv4 privee d'une interface active (Wi-Fi ou Ethernet), hors interfaces virtuelles. */
    private static String adresseReseau() throws java.net.SocketException {
        String secours = null;
        for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                continue;
            }
            String nom = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
            boolean virtuelle = nom.contains("vmware") || nom.contains("virtualbox") || nom.contains("hyper-v")
                    || nom.contains("vethernet") || nom.contains("docker") || nom.contains("wsl");
            for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress()) {
                    if (!virtuelle) {
                        return a.getHostAddress();
                    }
                    if (secours == null) {
                        secours = a.getHostAddress();
                    }
                }
            }
        }
        return secours;
    }

    // ------------------------------------------------------------- suivi

    public enum Statut { A_L_HEURE, EN_RETARD, PAS_ENCORE, ABSENT, REPOS, NON_EMBAUCHE }

    public record LigneJour(Long employeId, String matricule, String nom, String fonction, Statut statut,
                            Instant arriveeLe, int retardMinutes, boolean apresLimite, String lieu,
                            PointagePresence.Mode mode, Integer distanceMetres) {
    }

    public record Compteurs(int attendus, int presents, int aLHeure, int enRetard, int pasEncore, int absents) {
    }

    public record Jour(LocalDate date, boolean jourTravaille, LocalTime heureArrivee, int toleranceMinutes,
                       LocalTime heureLimite, Compteurs compteurs, List<LigneJour> lignes) {
    }

    /** Situation d'une journee : qui est arrive, a quelle heure, avec quel retard, qui manque. */
    @Transactional(readOnly = true)
    public Jour jour(LocalDate date, Long lieuId, UtilisateurPrincipal p) {
        Set<Long> perimetre = presence.perimetre(p);
        ReglagePresence reglage = presence.reglageCourant();
        Map<Long, PointagePresence> parEmploye = pointages.duJour(date).stream()
                .collect(Collectors.toMap(x -> x.getEmploye().getId(), Function.identity()));
        List<LigneJour> lignes = new ArrayList<>();
        for (EmployePresence e : employes.findAllByOrderByNomAscPrenomAsc()) {
            if (!PresenceService.visible(e, perimetre)
                    || (lieuId != null && e.getLieux().stream().noneMatch(l -> l.getId().equals(lieuId)))) {
                continue;
            }
            PointagePresence pt = parEmploye.get(e.getId());
            if (pt == null && !e.isActif()) {
                continue;
            }
            Statut statut = statut(e, date, pt, reglage);
            if (statut == Statut.NON_EMBAUCHE) {
                continue;
            }
            lignes.add(new LigneJour(e.getId(), e.getMatricule(), e.nomComplet(), e.getFonction(), statut,
                    pt == null ? null : pt.getArriveeLe(), pt == null ? 0 : pt.getRetardMinutes(),
                    pt != null && pt.isApresLimite(), pt == null || pt.getLieu() == null ? null : pt.getLieu().getNom(),
                    pt == null ? null : pt.getMode(), pt == null ? null : pt.getDistanceMetres()));
        }
        lignes.sort(Comparator.comparing((LigneJour l) -> ordre(l.statut())).thenComparing(LigneJour::nom));
        int aLHeure = compter(lignes, Statut.A_L_HEURE);
        int enRetard = compter(lignes, Statut.EN_RETARD);
        int pasEncore = compter(lignes, Statut.PAS_ENCORE);
        int absents = compter(lignes, Statut.ABSENT);
        return new Jour(date, reglage.travaille(date.getDayOfWeek()), reglage.getHeureArrivee(),
                reglage.getToleranceMinutes(), reglage.getHeureLimite(),
                new Compteurs(aLHeure + enRetard + pasEncore + absents, aLHeure + enRetard, aLHeure, enRetard, pasEncore, absents),
                lignes);
    }

    static Statut statut(EmployePresence e, LocalDate date, PointagePresence pt, ReglagePresence reglage) {
        if (pt != null) {
            return pt.getRetardMinutes() > 0 ? Statut.EN_RETARD : Statut.A_L_HEURE;
        }
        if (e.getDateEmbauche() != null && date.isBefore(e.getDateEmbauche())) {
            return Statut.NON_EMBAUCHE;
        }
        // Desactive : plus attendu a partir de ce jour (ni absent, ni compte dans la ponctualite).
        if (e.getDesactiveLe() != null && !date.isBefore(e.getDesactiveLe())) {
            return Statut.NON_EMBAUCHE;
        }
        if (!reglage.travaille(date.getDayOfWeek())) {
            return Statut.REPOS;
        }
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        if (date.isAfter(aujourdhui)
                || (date.equals(aujourdhui) && LocalTime.now(FUSEAU).isBefore(reglage.getHeureLimite()))) {
            return Statut.PAS_ENCORE;
        }
        return Statut.ABSENT;
    }

    private static int ordre(Statut s) {
        return switch (s) {
            case EN_RETARD -> 0;
            case ABSENT -> 1;
            case PAS_ENCORE -> 2;
            case A_L_HEURE -> 3;
            default -> 4;
        };
    }

    private static int compter(List<LigneJour> lignes, Statut s) {
        return (int) lignes.stream().filter(l -> l.statut() == s).count();
    }

    public record LigneHistorique(LocalDate date, Statut statut, Instant arriveeLe, int retardMinutes, boolean apresLimite,
                                  String lieu, PointagePresence.Mode mode, Integer distanceMetres) {
    }

    public record Totaux(int joursAttendus, int presents, int retards, int minutesRetard, int absences,
                         /** Arrivees a l'heure sur les jours attendus, en pourcentage. */
                         int ponctualite) {
    }

    public record Historique(Long employeId, String nom, String matricule, String fonction, YearMonth mois,
                             Totaux totaux, List<LigneHistorique> jours) {
    }

    /** Mois d'un employe, jour par jour, avec ses totaux. */
    @Transactional(readOnly = true)
    public Historique historique(Long employeId, YearMonth mois, UtilisateurPrincipal p) {
        EmployePresence e = presence.chargerEmploye(employeId);
        presence.exigerVisible(e, presence.perimetre(p));
        return historique(e, mois);
    }

    Historique historique(EmployePresence e, YearMonth mois) {
        ReglagePresence reglage = presence.reglageCourant();
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth().isAfter(aujourdhui) ? aujourdhui : mois.atEndOfMonth();
        Map<LocalDate, PointagePresence> parJour = fin.isBefore(debut) ? Map.of()
                : pointages.deLEmploye(e.getId(), debut, fin).stream()
                .collect(Collectors.toMap(PointagePresence::getJour, Function.identity()));
        List<LigneHistorique> jours = new ArrayList<>();
        int attendus = 0, presents = 0, retards = 0, minutes = 0, absences = 0, aLHeure = 0;
        for (LocalDate d = fin; !d.isBefore(debut); d = d.minusDays(1)) {
            PointagePresence pt = parJour.get(d);
            Statut s = statut(e, d, pt, reglage);
            if (s == Statut.NON_EMBAUCHE) {
                continue;
            }
            boolean attendu = reglage.travaille(d.getDayOfWeek()) && s != Statut.PAS_ENCORE;
            if (attendu) {
                attendus++;
            }
            if (pt != null) {
                presents++;
                if (pt.getRetardMinutes() > 0) {
                    retards++;
                    minutes += pt.getRetardMinutes();
                } else if (attendu) {
                    aLHeure++;
                }
            } else if (s == Statut.ABSENT) {
                absences++;
            }
            jours.add(new LigneHistorique(d, s, pt == null ? null : pt.getArriveeLe(), pt == null ? 0 : pt.getRetardMinutes(),
                    pt != null && pt.isApresLimite(), pt == null || pt.getLieu() == null ? null : pt.getLieu().getNom(),
                    pt == null ? null : pt.getMode(), pt == null ? null : pt.getDistanceMetres()));
        }
        int ponctualite = attendus == 0 ? 100 : Math.round(aLHeure * 100f / attendus);
        return new Historique(e.getId(), e.nomComplet(), e.getMatricule(), e.getFonction(), mois,
                new Totaux(attendus, presents, retards, minutes, absences, ponctualite), jours);
    }

    /** L'employe consulte son propre historique avec son PIN, depuis la page de pointage. */
    @Transactional(readOnly = true)
    public Historique monHistorique(String pin, YearMonth mois, String appareil) {
        EmployePresence e = reconnaitre(pin, appareil);
        return historique(e, mois == null ? YearMonth.now(FUSEAU) : mois);
    }

    /** Depuis le telephone : le code QR du moment est exige, comme pour pointer (on est sur place). */
    public Historique monHistorique(LieuPresence lieu, String code, String pin, YearMonth mois, String appareil) {
        exigerCode(lieu, code, appareil);
        EmployePresence e = reconnaitre(pin, appareil);
        return historique(e, mois == null ? YearMonth.now(FUSEAU) : mois);
    }

    private static String heure(Instant instant) {
        return LocalTime.ofInstant(instant, FUSEAU).withSecond(0).withNano(0).toString().replace(':', 'h');
    }

    static String duree(int minutes) {
        return minutes < 60 ? minutes + " min" : (minutes / 60) + " h " + String.format("%02d", minutes % 60);
    }
}
