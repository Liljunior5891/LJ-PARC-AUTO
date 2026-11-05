package tg.parcauto.api.pointdevente.materiaux;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.PolicesPdf;
import tg.parcauto.api.finance.facture.FacturePdfService;
import tg.parcauto.api.parametrage.Societe;
import tg.parcauto.api.parametrage.SocieteService;
import tg.parcauto.api.pointdevente.catalogue.Conditionnement;
import tg.parcauto.api.pointdevente.vente.LigneVente;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteService;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Facture A4 d'une vente et devis (pro forma) de la quincaillerie, sous le
 * meme decor que les factures du parc : bandeaux, logo et coordonnees de la
 * societe.
 * <p>
 * Les prix de la caisse sont TTC. Quand la TVA est active dans les reglages,
 * le document en detaille la part au taux de la societe ; sinon il porte la
 * mention « TVA non applicable ».
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentMateriauxPdf {

    private static final Color BLEU = new Color(31, 111, 209);
    private static final Color SOMBRE = new Color(34, 39, 46);
    private static final Color GRIS = new Color(110, 118, 130);
    private static final Color FILET = new Color(222, 226, 232);
    private static final Color FOND_DOUX = new Color(244, 247, 251);
    private static final Color ROUGE = new Color(198, 40, 40);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId ZONE = ZoneId.of("Africa/Lome");

    private final VenteService venteService;
    private final DevisService devisService;
    private final ReglageMateriauxService reglages;
    private final SocieteService societeService;
    private final FacturePdfService facturePdf;
    private final LivraisonClientService livraisons;

    /** Ligne imprimee, commune a la facture et au devis. */
    private record Ligne(String designation, String unite, BigDecimal quantite, BigDecimal prix, BigDecimal remise,
                         BigDecimal total) {
    }

    /** Destinataire du document. */
    private record Destinataire(String nom, String telephone, String adresse, String nif, String code) {
    }

    // -------------------------------------------------------------- facture

    public byte[] facture(Long venteId) {
        ReglageMateriaux reglage = reglages.exiger();
        if (!reglage.isFactureA4()) {
            throw new BusinessException("La facture A4 est desactivee dans les reglages");
        }
        Vente v = venteService.detail(venteId);
        Societe societe = societeService.entite();
        List<Ligne> lignes = new ArrayList<>();
        for (LigneVente l : v.getLignes()) {
            // Une ligne entierement soldee n'est plus vendue : l'avoir en garde la trace.
            if (l.getQuantite().signum() > 0) {
                lignes.add(new Ligne(l.getLibelle(), unite(l.getConditionnement(), l.getArticle().getUnite()),
                        l.getQuantite(), l.getPrixUnitaire(), l.getRemise(), l.getTotal()));
            }
        }
        ClientPdv c = v.getClient();
        Destinataire destinataire = c == null
                ? new Destinataire(v.getClientNom() == null ? "Client comptoir" : v.getClientNom(), null, null, null, null)
                : new Destinataire(c.getNom(), c.getTelephone(), c.getAdresse(), c.getNif(), c.getCode());

        List<String[]> references = new ArrayList<>();
        references.add(new String[]{"Facture N° :", v.getNumero()});
        references.add(new String[]{"Date :", DATE.format(v.getDateVente().atZone(ZONE))});
        if (v.getEcheance() != null && v.getResteDu().signum() > 0) {
            references.add(new String[]{"Échéance :", DATE.format(v.getEcheance())});
        }
        references.add(new String[]{"Vendeur :", v.getCaissierNom()});
        references.add(new String[]{"Paiement :", v.getModePaiement().getLibelle()});

        List<String[]> soldes = new ArrayList<>();
        BigDecimal paye = v.getTotal().subtract(v.getResteDu());
        if (v.getStatut() == Vente.Statut.VALIDEE && v.getResteDu().signum() > 0) {
            soldes.add(new String[]{"Déjà payé :", montant(paye) + " F"});
            soldes.add(new String[]{"Reste à payer :", montant(v.getResteDu()) + " F"});
        }

        // Ce qui n'est pas encore remis reste du au client : la facture le rappelle.
        StringBuilder aLivrer = new StringBuilder();
        if (v.getStatut() == Vente.Statut.VALIDEE) {
            for (LigneVente l : v.getLignes()) {
                if (l.resteALivrer().signum() > 0) {
                    String u = unite(l.getConditionnement(), l.getArticle().getUnite());
                    aLivrer.append(aLivrer.isEmpty() ? "Reste à livrer à ce jour : " : ", ")
                            .append(quantite(l.resteALivrer())).append(u == null ? "" : " " + u)
                            .append(" - ").append(l.getLibelle());
                }
            }
        }
        StringBuilder avoirs = new StringBuilder();
        for (SoldeReste s : livraisons.soldesValides(venteId)) {
            avoirs.append(avoirs.isEmpty() ? "" : "\n").append("Avoir ").append(s.getNumero()).append(" du ")
                    .append(DATE.format(s.getDateSolde().atZone(ZONE))).append(" : ").append(montant(s.getMontant()))
                    .append(" F retirés de cette facture");
        }
        return produire(societe, reglage, v.getStatut() == Vente.Statut.ANNULEE ? "FACTURE ANNULÉE" : "FACTURE",
                destinataire, "FACTURÉ À :", references, lignes, v.getTotalBrut(), v.getRemise(), v.getTotal(),
                soldes, joindre(aLivrer.toString(), avoirs.toString(), societe.getMentionsFacture()), false);
    }

    public String nomFacture(Long venteId) {
        return "facture_" + venteService.detail(venteId).getNumero() + ".pdf";
    }

    // ---------------------------------------------------------------- devis

    public byte[] devis(Long devisId) {
        ReglageMateriaux reglage = reglages.exiger();
        Devis d = devisService.entite(devisId);
        Societe societe = societeService.entite();
        List<Ligne> lignes = new ArrayList<>();
        for (LigneDevis l : d.getLignes()) {
            lignes.add(new Ligne(l.getLibelle(), unite(l.getConditionnement(), l.getArticle().getUnite()),
                    l.getQuantite(), l.getPrixUnitaire(), l.getRemise(), l.getTotal()));
        }
        ClientPdv c = d.getClient();
        Destinataire destinataire = c == null
                ? new Destinataire(d.getClientNom(), null, null, null, null)
                : new Destinataire(c.getNom(), c.getTelephone(), c.getAdresse(), c.getNif(), c.getCode());
        List<String[]> references = new ArrayList<>();
        references.add(new String[]{"Devis N° :", d.getNumero()});
        references.add(new String[]{"Date :", DATE.format(d.getDateDevis())});
        references.add(new String[]{"Valable jusqu'au :", DATE.format(d.getValableJusqu())});
        references.add(new String[]{"Établi par :", d.getVendeurNom()});

        String titre = switch (d.getStatut()) {
            case ANNULE -> "DEVIS ANNULÉ";
            default -> "DEVIS / PRO FORMA";
        };
        String mentions = joindre(d.getNote(), reglage.getMentionsDevis());
        return produire(societe, reglage, titre, destinataire, "ÉTABLI POUR :", references, lignes,
                d.getTotalBrut(), d.getRemise(), d.getTotal(), List.of(), mentions, true);
    }

    public String nomDevis(Long devisId) {
        return "devis_" + devisService.entite(devisId).getNumero() + ".pdf";
    }

    // ------------------------------------------------------ bon de livraison

    /** Ce qui a ete remis ce jour-la et ce qu'il reste a livrer, sans aucun prix, a signer par le client. */
    public byte[] bonLivraison(Long bonId) {
        reglages.exiger();
        BonLivraison bon = livraisons.charger(bonId);
        Vente v = bon.getVente();
        ClientPdv c = v.getClient();
        Societe societe = societeService.entite();

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 132, 70);
        PdfWriter writer = PdfWriter.getInstance(document, sortie);
        writer.setPageEvent(facturePdf.decorPage(societe, false));
        document.open();

        boolean annule = bon.getStatut() == BonLivraison.Statut.ANNULE;
        List<String[]> references = new ArrayList<>();
        references.add(new String[]{"Bon N° :", bon.getNumero()});
        references.add(new String[]{"Date :", DATE.format(bon.getDateBon().atZone(ZONE))});
        references.add(new String[]{"Commande N° :", v.getNumero()});
        references.add(new String[]{"Date de commande :", DATE.format(v.getDateVente().atZone(ZONE))});
        references.add(new String[]{"Remis par :", bon.getRemisPar()});
        enTete(document, annule ? "BON DE LIVRAISON ANNULÉ" : "BON DE LIVRAISON", c == null
                ? new Destinataire(v.getClientNom() == null ? "Client comptoir" : v.getClientNom(), null, null, null, null)
                : new Destinataire(c.getNom(), c.getTelephone(), c.getAdresse(), c.getNif(), c.getCode()),
                "LIVRÉ À :", references);

        PdfPTable table = new PdfPTable(new float[]{46f, 12f, 14f, 14f, 14f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        String[] entetes = {"Désignation", "Unité", "Commandé", "Livré ce jour", "Reste à livrer"};
        for (int i = 0; i < entetes.length; i++) {
            PdfPCell cellule = cellule(entetes[i], police(8.5f, Font.BOLD, Color.WHITE),
                    i < 2 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT, BLEU);
            cellule.setPadding(6);
            table.addCell(cellule);
        }
        boolean pair = false;
        boolean resteAuClient = false;
        for (LigneBonLivraison l : bon.getLignes()) {
            LigneVente ligne = l.getLigneVente();
            Color fond = pair ? FOND_DOUX : null;
            String u = unite(ligne.getConditionnement(), ligne.getArticle().getUnite());
            table.addCell(ligneCellule(ligne.getLibelle(), Element.ALIGN_LEFT, fond));
            table.addCell(ligneCellule(u == null ? "" : u, Element.ALIGN_LEFT, fond));
            table.addCell(ligneCellule(quantite(ligne.getQuantite()), Element.ALIGN_RIGHT, fond));
            table.addCell(ligneCellule(quantite(l.getQuantite()), Element.ALIGN_RIGHT, fond));
            table.addCell(ligneCellule(quantite(l.getResteApres()), Element.ALIGN_RIGHT, fond));
            resteAuClient = resteAuClient || l.getResteApres().signum() > 0;
            pair = !pair;
        }
        table.setSpacingAfter(12);
        document.add(table);

        if (annule) {
            document.add(new Paragraph("Annulé" + (bon.getMotifAnnulation() == null ? "" : " : " + bon.getMotifAnnulation()),
                    police(9, Font.BOLD, ROUGE)));
        }
        if (bon.getNote() != null) {
            document.add(new Paragraph(bon.getNote(), police(9, Font.NORMAL, GRIS)));
        }
        Paragraph bilan = new Paragraph(resteAuClient
                ? "Le reste de la commande sera remis ultérieurement." : "Les articles de ce bon sont entièrement livrés.",
                police(9, Font.NORMAL, GRIS));
        bilan.setSpacingAfter(26);
        document.add(bilan);

        PdfPTable signatures = new PdfPTable(2);
        signatures.setWidthPercentage(100);
        signatures.addCell(cellule("Remis par : " + bon.getRemisPar(), police(9, Font.BOLD, SOMBRE), Element.ALIGN_LEFT, null));
        signatures.addCell(cellule("Reçu par : " + (bon.getRecuPar() == null ? "" : bon.getRecuPar())
                + "\n(nom et signature)", police(9, Font.BOLD, SOMBRE), Element.ALIGN_LEFT, null));
        document.add(signatures);

        document.close();
        return sortie.toByteArray();
    }

    public String nomBonLivraison(Long bonId) {
        return "bon-livraison_" + livraisons.charger(bonId).getNumero() + ".pdf";
    }

    // ----------------------------------------------------------------- avoir

    /** Ce a quoi le client a renonce, ce qui a ete deduit de sa dette et ce qui lui a ete rendu ; a signer par lui. */
    public byte[] avoir(Long soldeId) {
        reglages.exiger();
        SoldeReste s = livraisons.chargerSolde(soldeId);
        Vente v = s.getVente();
        ClientPdv c = v.getClient();
        Societe societe = societeService.entite();

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 132, 70);
        PdfWriter writer = PdfWriter.getInstance(document, sortie);
        writer.setPageEvent(facturePdf.decorPage(societe, false));
        document.open();

        boolean annule = s.getStatut() == SoldeReste.Statut.ANNULE;
        List<String[]> references = new ArrayList<>();
        references.add(new String[]{"Avoir N° :", s.getNumero()});
        references.add(new String[]{"Date :", DATE.format(s.getDateSolde().atZone(ZONE))});
        references.add(new String[]{"Commande N° :", v.getNumero()});
        references.add(new String[]{"Établi par :", s.getAuteurNom()});
        enTete(document, annule ? "AVOIR ANNULÉ" : "AVOIR - SOLDE DU RESTE À LIVRER", c == null
                ? new Destinataire(v.getClientNom() == null ? "Client comptoir" : v.getClientNom(), null, null, null, null)
                : new Destinataire(c.getNom(), c.getTelephone(), c.getAdresse(), c.getNif(), c.getCode()),
                "CLIENT :", references);

        PdfPTable table = new PdfPTable(new float[]{55f, 13f, 14f, 18f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        String[] entetes = {"Désignation", "Unité", "Quantité", "Montant"};
        for (int i = 0; i < entetes.length; i++) {
            PdfPCell cellule = cellule(entetes[i], police(8.5f, Font.BOLD, Color.WHITE),
                    i < 2 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT, BLEU);
            cellule.setPadding(6);
            table.addCell(cellule);
        }
        boolean pair = false;
        for (LigneSoldeReste l : s.getLignes()) {
            LigneVente ligne = l.getLigneVente();
            Color fond = pair ? FOND_DOUX : null;
            String u = unite(ligne.getConditionnement(), ligne.getArticle().getUnite());
            table.addCell(ligneCellule(ligne.getLibelle(), Element.ALIGN_LEFT, fond));
            table.addCell(ligneCellule(u == null ? "" : u, Element.ALIGN_LEFT, fond));
            table.addCell(ligneCellule(quantite(l.getQuantite()), Element.ALIGN_RIGHT, fond));
            table.addCell(ligneCellule(montant(l.getTotal()), Element.ALIGN_RIGHT, fond));
            pair = !pair;
        }
        table.setSpacingAfter(14);
        document.add(table);

        PdfPTable bas = new PdfPTable(new float[]{52f, 48f});
        bas.setWidthPercentage(100);
        PdfPCell notes = sansBordure(new PdfPCell());
        notes.addElement(new Paragraph("Motif", police(9, Font.BOLD, BLEU)));
        notes.addElement(new Paragraph(s.getMotif(), police(8.5f, Font.NORMAL, GRIS)));
        if (annule) {
            notes.addElement(new Paragraph("Annulé" + (s.getMotifAnnulation() == null ? "" : " : " + s.getMotifAnnulation()),
                    police(9, Font.BOLD, ROUGE)));
        }
        bas.addCell(notes);
        PdfPTable totaux = new PdfPTable(new float[]{55f, 45f});
        totaux.setWidthPercentage(100);
        ligneTotal(totaux, "VALEUR :", montant(s.getMontant()) + " F", true);
        if (s.getMontantDeduit().signum() > 0) {
            ligneTotal(totaux, "Déduit de ce qui est dû :", montant(s.getMontantDeduit()) + " F", false);
        }
        if (s.getMontantRembourse().signum() > 0) {
            ligneTotal(totaux, "Remboursé (" + s.getModeRemboursement().getLibelle() + ") :",
                    montant(s.getMontantRembourse()) + " F", false);
        }
        bas.addCell(sansBordure(new PdfPCell(totaux)));
        bas.setSpacingAfter(30);
        document.add(bas);

        document.add(new Paragraph("Signature du client :", police(9, Font.BOLD, SOMBRE)));
        document.close();
        return sortie.toByteArray();
    }

    public String nomAvoir(Long soldeId) {
        return "avoir_" + livraisons.chargerSolde(soldeId).getNumero() + ".pdf";
    }

    // ------------------------------------------------------------ gabarit

    /** Titre, puis destinataire a gauche et references a droite. */
    private void enTete(Document document, String titre, Destinataire destinataire, String libelleDestinataire,
                        List<String[]> references) {
        Paragraph entete = new Paragraph(titre, police(18, Font.BOLD, titre.contains("ANNUL") ? ROUGE : SOMBRE));
        entete.setSpacingAfter(12);
        document.add(entete);

        PdfPTable haut = new PdfPTable(new float[]{56f, 44f});
        haut.setWidthPercentage(100);
        PdfPCell gauche = sansBordure(new PdfPCell());
        gauche.addElement(new Paragraph(libelleDestinataire, police(9, Font.BOLD, BLEU)));
        gauche.addElement(new Paragraph(destinataire.nom(), police(14, Font.BOLD, SOMBRE)));
        ajouterSi(gauche, "Code client : ", destinataire.code());
        ajouterSi(gauche, "Téléphone : ", destinataire.telephone());
        ajouterSi(gauche, "Adresse : ", destinataire.adresse());
        ajouterSi(gauche, "NIF : ", destinataire.nif());
        haut.addCell(gauche);
        PdfPTable refs = new PdfPTable(new float[]{45f, 55f});
        refs.setWidthPercentage(100);
        for (String[] r : references) {
            refs.addCell(cellule(r[0], police(9, Font.NORMAL, GRIS), Element.ALIGN_LEFT, null));
            refs.addCell(cellule(r[1], police(9, Font.BOLD, SOMBRE), Element.ALIGN_RIGHT, null));
        }
        PdfPCell droite = sansBordure(new PdfPCell(refs));
        droite.setPaddingTop(4);
        haut.addCell(droite);
        haut.setSpacingAfter(18);
        document.add(haut);
    }

    private byte[] produire(Societe societe, ReglageMateriaux reglage, String titre, Destinataire destinataire,
                            String libelleDestinataire, List<String[]> references, List<Ligne> lignes,
                            BigDecimal totalBrut, BigDecimal remise, BigDecimal total, List<String[]> soldes,
                            String mentions, boolean bonPourAccord) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 132, 70);
        PdfWriter writer = PdfWriter.getInstance(document, sortie);
        writer.setPageEvent(facturePdf.decorPage(societe, false));
        document.open();

        enTete(document, titre, destinataire, libelleDestinataire, references);

        // Lignes.
        PdfPTable table = new PdfPTable(new float[]{40f, 10f, 10f, 14f, 11f, 15f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        String[] entetes = {"Désignation", "Qté", "Unité", "P.U. TTC", "Remise", "Montant"};
        for (int i = 0; i < entetes.length; i++) {
            PdfPCell c = cellule(entetes[i], police(8.5f, Font.BOLD, Color.WHITE),
                    i == 0 || i == 2 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT, BLEU);
            c.setPadding(6);
            table.addCell(c);
        }
        boolean pair = false;
        for (Ligne l : lignes) {
            Color fond = pair ? FOND_DOUX : null;
            table.addCell(ligneCellule(l.designation(), Element.ALIGN_LEFT, fond));
            table.addCell(ligneCellule(quantite(l.quantite()), Element.ALIGN_RIGHT, fond));
            table.addCell(ligneCellule(l.unite() == null ? "" : l.unite(), Element.ALIGN_LEFT, fond));
            table.addCell(ligneCellule(montant(l.prix()), Element.ALIGN_RIGHT, fond));
            table.addCell(ligneCellule(l.remise().signum() == 0 ? "" : "-" + montant(l.remise()), Element.ALIGN_RIGHT, fond));
            table.addCell(ligneCellule(montant(l.total()), Element.ALIGN_RIGHT, fond));
            pair = !pair;
        }
        table.setSpacingAfter(14);
        document.add(table);

        // Mentions a gauche, totaux a droite.
        PdfPTable bas = new PdfPTable(new float[]{52f, 48f});
        bas.setWidthPercentage(100);
        PdfPCell notes = sansBordure(new PdfPCell());
        if (mentions != null && !mentions.isBlank()) {
            notes.addElement(new Paragraph("Conditions", police(9, Font.BOLD, BLEU)));
            notes.addElement(new Paragraph(mentions, police(8.5f, Font.NORMAL, GRIS)));
        }
        if (bonPourAccord) {
            Paragraph accord = new Paragraph("Bon pour accord (date, nom et signature du client) :",
                    police(8.5f, Font.BOLD, SOMBRE));
            accord.setSpacingBefore(18);
            notes.addElement(accord);
        }
        bas.addCell(notes);

        PdfPTable totaux = new PdfPTable(new float[]{55f, 45f});
        totaux.setWidthPercentage(100);
        if (remise.signum() > 0) {
            ligneTotal(totaux, "Sous-total :", montant(totalBrut) + " F", false);
            ligneTotal(totaux, "Remise :", "-" + montant(remise) + " F", false);
        }
        if (reglage.isTvaActive()) {
            BigDecimal taux = societe.getTauxTva() == null ? BigDecimal.ZERO : societe.getTauxTva();
            BigDecimal ht = total.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(100).add(taux), 0, RoundingMode.HALF_UP);
            ligneTotal(totaux, "Total HT :", montant(ht) + " F", false);
            ligneTotal(totaux, "TVA (" + taux.stripTrailingZeros().toPlainString() + " %) :",
                    montant(total.subtract(ht)) + " F", false);
            ligneTotal(totaux, "TOTAL TTC :", montant(total) + " F", true);
        } else {
            ligneTotal(totaux, "TOTAL :", montant(total) + " F", true);
            ligneTotal(totaux, "", "TVA non applicable", false);
        }
        for (String[] s : soldes) {
            ligneTotal(totaux, s[0], s[1], s[0].startsWith("Reste"));
        }
        bas.addCell(sansBordure(new PdfPCell(totaux)));
        document.add(bas);

        document.close();
        return sortie.toByteArray();
    }

    // -------------------------------------------------------------- outils

    private static String unite(Conditionnement conditionnement, String uniteDeBase) {
        return conditionnement != null ? conditionnement.getNom() : uniteDeBase;
    }

    private static void ajouterSi(PdfPCell cellule, String libelle, String valeur) {
        if (valeur != null && !valeur.isBlank()) {
            Paragraph p = new Paragraph();
            p.add(new Phrase(libelle, police(8.5f, Font.NORMAL, GRIS)));
            p.add(new Phrase(valeur, police(8.5f, Font.NORMAL, SOMBRE)));
            cellule.addElement(p);
        }
    }

    private static void ligneTotal(PdfPTable table, String libelle, String valeur, boolean fort) {
        Font police = police(fort ? 11 : 9, fort ? Font.BOLD : Font.NORMAL, fort ? SOMBRE : GRIS);
        PdfPCell a = cellule(libelle, police, Element.ALIGN_LEFT, fort ? FOND_DOUX : null);
        PdfPCell b = cellule(valeur, police(fort ? 11 : 9, Font.BOLD, SOMBRE), Element.ALIGN_RIGHT, fort ? FOND_DOUX : null);
        a.setPadding(5);
        b.setPadding(5);
        table.addCell(a);
        table.addCell(b);
    }

    private static PdfPCell ligneCellule(String texte, int alignement, Color fond) {
        PdfPCell c = cellule(texte, police(8.5f, Font.NORMAL, SOMBRE), alignement, fond);
        c.setPadding(5);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(FILET);
        return c;
    }

    private static PdfPCell cellule(String texte, Font police, int alignement, Color fond) {
        PdfPCell c = new PdfPCell(new Phrase(texte == null ? "" : texte, police));
        c.setHorizontalAlignment(alignement);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingBottom(3);
        if (fond != null) {
            c.setBackgroundColor(fond);
        }
        return c;
    }

    private static PdfPCell sansBordure(PdfPCell c) {
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static Font police(float taille, int style, Color couleur) {
        return PolicesPdf.police(taille, style, couleur);
    }

    private static String montant(BigDecimal valeur) {
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRENCH);
        symboles.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symboles).format(valeur == null ? BigDecimal.ZERO : valeur);
    }

    private static String quantite(BigDecimal valeur) {
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRENCH);
        symboles.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.###", symboles).format(valeur);
    }

    private static String joindre(String... textes) {
        StringBuilder sb = new StringBuilder();
        for (String t : textes) {
            if (t != null && !t.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(t.trim());
            }
        }
        return sb.toString();
    }
}
