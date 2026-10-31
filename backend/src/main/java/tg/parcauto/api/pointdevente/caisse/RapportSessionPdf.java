package tg.parcauto.api.pointdevente.caisse;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.parametrage.SocieteService;
import tg.parcauto.api.pilotage.MiseEnPagePdf;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static tg.parcauto.api.pilotage.MiseEnPagePdf.montant;

/**
 * Rapport d'activite d'une session de caisse, a telecharger apres sa
 * fermeture : especes et ecart, billettage d'ouverture et de fermeture,
 * ventes par moyen de paiement et par vendeur, mouvements d'especes, detail
 * des commandes puis produits vendus.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RapportSessionPdf {

    private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final CaisseService caisseService;
    private final VenteRepository venteRepository;
    private final SocieteService societeService;

    public byte[] generer(Long sessionId, ZoneId fuseau) {
        CaisseService.Reponse s = caisseService.rapport(sessionId);
        String entreprise = societeService.entite().getNom();
        String sousTitre = s.caisseNom() + " · ouverte le " + date(s.ouverteLe(), fuseau) + " par " + s.caissierNom()
                + (s.fermeeLe() == null ? " · session encore ouverte"
                : " · fermee le " + date(s.fermeeLe(), fuseau) + " par " + (s.fermeeParNom() == null ? "—" : s.fermeeParNom()));
        MiseEnPagePdf pdf = new MiseEnPagePdf(entreprise == null || entreprise.isBlank() ? "LJ SaaS" : entreprise,
                "Rapport de caisse " + s.numero(), sousTitre, false);
        try (pdf) {
            BigDecimal ecart = s.ecart();
            pdf.indicateurs(List.of(
                    MiseEnPagePdf.Indicateur.de("Chiffre d'affaires", montant(s.chiffreAffaires()) + " F",
                            s.nombreVentes() + " vente(s) · " + s.ventesAnnulees() + " annulee(s)"),
                    MiseEnPagePdf.Indicateur.de("Especes attendues", montant(s.especesAttendues()) + " F",
                            "fond " + montant(s.fondInitial()) + " F"),
                    MiseEnPagePdf.Indicateur.de("Especes comptees",
                            s.montantCompte() == null ? "—" : montant(s.montantCompte()) + " F"),
                    new MiseEnPagePdf.Indicateur("Ecart", ecart == null ? "—" : montant(ecart) + " F",
                            ecart == null ? "session ouverte" : ecart.signum() == 0 ? "caisse juste" : "a justifier",
                            ecart == null || ecart.signum() == 0 ? MiseEnPagePdf.VERT : MiseEnPagePdf.ROUGE)));

            pdf.section("Controle des especes");
            List<String[]> controle = new ArrayList<>();
            controle.add(new String[]{"Fond de caisse a l'ouverture", montant(s.fondInitial())});
            s.parMode().stream().filter(m -> m.mode().name().equals("ESPECES")).findFirst()
                    .ifPresent(m -> controle.add(new String[]{"+ Ventes en especes", montant(m.total())}));
            controle.add(new String[]{"+ Entrees d'especes", montant(s.entrees())});
            controle.add(new String[]{"- Sorties d'especes", montant(s.sorties())});
            controle.add(new String[]{"= Especes attendues", montant(s.especesAttendues())});
            if (s.montantCompte() != null) {
                controle.add(new String[]{"Especes comptees a la fermeture", montant(s.montantCompte())});
                controle.add(new String[]{"Ecart (compte - attendu)", montant(s.ecart())});
            }
            pdf.tableau(new float[]{70, 30}, new String[]{"Calcul", "Montant (F)"}, "gd", controle);
            if (s.justificationEcart() != null) {
                pdf.note("Justification de l'ecart : " + s.justificationEcart());
            }
            if (s.observations() != null) {
                pdf.note("Observations : " + s.observations());
            }

            pdf.section("Ventes par moyen de paiement");
            pdf.tableau(new float[]{50, 20, 30}, new String[]{"Moyen de paiement", "Ventes", "Montant (F)"}, "gdd",
                    s.parMode().stream().map(m -> new String[]{m.libelle(), String.valueOf(m.nombre()), montant(m.total())})
                            .toList());

            pdf.section("Ventes par vendeur");
            pdf.tableau(new float[]{50, 20, 30}, new String[]{"Vendeur", "Ventes", "Montant (F)"}, "gdd",
                    venteRepository.parVendeurSession(sessionId).stream()
                            .map(l -> new String[]{String.valueOf(l[0]), String.valueOf(l[2]), montant((BigDecimal) l[1])})
                            .toList());

            pdf.section("Entrees et sorties d'especes");
            pdf.tableau(new float[]{18, 12, 40, 15, 15}, new String[]{"Heure", "Sens", "Motif", "Par", "Montant (F)"},
                    "ggggd",
                    s.mouvements().stream().map(m -> new String[]{date(m.date(), fuseau),
                            m.type() == MouvementCaisse.Type.ENTREE ? "Entree" : "Sortie", m.motif(), m.auteurNom(),
                            (m.type() == MouvementCaisse.Type.ENTREE ? "+" : "-") + montant(m.montant())}).toList());

            pdf.section("Billettage");
            pdf.tableau(new float[]{25, 25, 25, 25},
                    new String[]{"Coupure", "Ouverture", "Fermeture", "Montant ferme (F)"}, "gddd",
                    billettage(s));

            pdf.section("Detail des ventes");
            List<Vente> ventes = venteRepository.findBySessionIdOrderByDateVenteDesc(sessionId, PageRequest.of(0, 5000))
                    .getContent();
            pdf.tableau(new float[]{20, 12, 22, 18, 14, 14},
                    new String[]{"Commande", "Heure", "Vendeur", "Paiement", "Statut", "Total (F)"}, "gggggd",
                    ventes.stream().map(v -> new String[]{v.getNumero(), v.getDateVente().atZone(fuseau).format(HEURE),
                            v.getCaissierNom(), v.getModePaiement().getLibelle(),
                            v.getStatut() == Vente.Statut.ANNULEE ? "Annulee" : "Encaissee", montant(v.getTotal())}).toList());

            // Ce qui est sorti du comptoir : chaque produit, ses quantites et ce qu'il a rapporte.
            pdf.section("Produits vendus");
            List<CaisseService.VenteProduit> produits = caisseService.parProduit(sessionId);
            BigDecimal totalProduits = produits.stream().map(CaisseService.VenteProduit::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<String[]> lignesProduits = new ArrayList<>(produits.stream()
                    .map(p -> new String[]{p.produit(), MiseEnPagePdf.nombre(p.quantite()), String.valueOf(p.commandes()),
                            montant(p.total()), totalProduits.signum() == 0 ? "—"
                            : MiseEnPagePdf.nombre(p.total().multiply(BigDecimal.valueOf(100))
                                    .divide(totalProduits, 1, java.math.RoundingMode.HALF_UP)) + " %"})
                    .toList());
            if (!produits.isEmpty()) {
                lignesProduits.add(new String[]{"TOTAL", MiseEnPagePdf.nombre(produits.stream()
                        .map(CaisseService.VenteProduit::quantite).reduce(BigDecimal.ZERO, BigDecimal::add)), "",
                        montant(totalProduits), "100 %"});
            }
            pdf.tableau(new float[]{40, 15, 15, 18, 12},
                    new String[]{"Produit", "Quantite", "Commandes", "Montant (F)", "Part"}, "gdddd", lignesProduits);
        }
        return pdf.octets();
    }

    /** Une ligne par coupure comptee a l'ouverture ou a la fermeture, avec les totaux. */
    private static List<String[]> billettage(CaisseService.Reponse s) {
        List<String[]> lignes = new ArrayList<>();
        for (BigDecimal valeur : Coupure.VALEURS) {
            int ouverture = quantite(s.billettageOuverture(), valeur);
            int fermeture = quantite(s.billettageFermeture(), valeur);
            if (ouverture > 0 || fermeture > 0) {
                lignes.add(new String[]{Coupure.nature(valeur) + " de " + montant(valeur) + " F",
                        String.valueOf(ouverture), String.valueOf(fermeture),
                        montant(valeur.multiply(BigDecimal.valueOf(fermeture)))});
            }
        }
        lignes.add(new String[]{"TOTAL", montant(s.fondInitial()) + " F",
                s.montantCompte() == null ? "—" : montant(s.montantCompte()) + " F",
                s.montantCompte() == null ? "" : montant(s.montantCompte())});
        return lignes;
    }

    private static int quantite(List<CaisseService.CoupureDto> coupures, BigDecimal valeur) {
        return coupures.stream().filter(c -> c.valeur().compareTo(valeur) == 0).mapToInt(CaisseService.CoupureDto::quantite)
                .sum();
    }

    private static String date(Instant instant, ZoneId fuseau) {
        return instant == null ? "—" : instant.atZone(fuseau).format(JOUR_HEURE);
    }
}
