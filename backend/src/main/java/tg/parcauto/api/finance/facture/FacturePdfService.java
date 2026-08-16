package tg.parcauto.api.finance.facture;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.exploitation.client.Client;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Edition de la facture au format PDF, chapitre 27 du cahier des charges.
 * <p>
 * Le document reprend les mentions attendues : emetteur, client, periode,
 * detail des prestations par camion, total hors taxes, TVA, total a payer et
 * echeance de reglement.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacturePdfService {

    private static final Color BLEU = new Color(15, 76, 129);
    private static final Color GRIS = new Color(107, 118, 132);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FactureRepository repository;

    @Value("${parcauto.entreprise.nom:PARC AUTO}")
    private String entrepriseNom;

    @Value("${parcauto.entreprise.adresse:}")
    private String entrepriseAdresse;

    @Value("${parcauto.entreprise.telephone:}")
    private String entrepriseTelephone;

    @Value("${parcauto.entreprise.identifiant-fiscal:}")
    private String entrepriseIdentifiantFiscal;

    /** Produit le PDF de la facture. */
    public byte[] produire(Long factureId) {
        Facture facture = repository.chargerAvecLignes(factureId)
                .orElseThrow(() -> new BusinessException("Facture introuvable : " + factureId));

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(document, sortie);
        document.open();

        enteteEmetteur(document);
        titre(document, facture);
        blocClient(document, facture.getClient());
        tableauLignes(document, facture);
        pied(document, facture);
        mentions(document, facture);

        document.close();
        return sortie.toByteArray();
    }

    public String nomFichier(Long factureId) {
        return repository.findById(factureId)
                .map(f -> "facture_" + f.getNumero() + ".pdf")
                .orElse("facture.pdf");
    }

    private void enteteEmetteur(Document document) {
        Paragraph nom = new Paragraph(entrepriseNom, police(16, Font.BOLD, BLEU));
        document.add(nom);
        if (!entrepriseAdresse.isBlank()) {
            document.add(new Paragraph(entrepriseAdresse, police(9, Font.NORMAL, GRIS)));
        }
        StringBuilder contact = new StringBuilder();
        if (!entrepriseTelephone.isBlank()) {
            contact.append("Tel : ").append(entrepriseTelephone);
        }
        if (!entrepriseIdentifiantFiscal.isBlank()) {
            if (contact.length() > 0) {
                contact.append("   -   ");
            }
            contact.append("NIF : ").append(entrepriseIdentifiantFiscal);
        }
        if (contact.length() > 0) {
            document.add(new Paragraph(contact.toString(), police(9, Font.NORMAL, GRIS)));
        }
        document.add(espace(14));
    }

    private void titre(Document document, Facture facture) {
        Paragraph titre = new Paragraph("FACTURE " + facture.getNumero(), police(14, Font.BOLD, BLEU));
        titre.setAlignment(Element.ALIGN_CENTER);
        document.add(titre);

        StringBuilder details = new StringBuilder("Emise le ")
                .append(facture.getDateEmission().format(DATE));
        if (facture.getDateEcheance() != null) {
            details.append("   -   Echeance : ").append(facture.getDateEcheance().format(DATE));
        }
        if (facture.getPeriodeDebut() != null && facture.getPeriodeFin() != null) {
            details.append("   -   Periode : ").append(facture.getPeriodeDebut().format(DATE))
                    .append(" au ").append(facture.getPeriodeFin().format(DATE));
        }
        Paragraph sousTitre = new Paragraph(details.toString(), police(9, Font.NORMAL, GRIS));
        sousTitre.setAlignment(Element.ALIGN_CENTER);
        document.add(sousTitre);
        document.add(espace(16));
    }

    private void blocClient(Document document, Client client) {
        document.add(new Paragraph("Client", police(9, Font.BOLD, GRIS)));
        document.add(new Paragraph(client.getRaisonSociale(), police(11, Font.BOLD, Color.BLACK)));
        if (client.getAdresse() != null) {
            document.add(new Paragraph(client.getAdresse(), police(9, Font.NORMAL, Color.BLACK)));
        }
        StringBuilder complement = new StringBuilder();
        if (client.getVille() != null) {
            complement.append(client.getVille());
        }
        if (client.getTelephone() != null) {
            complement.append(complement.length() > 0 ? "   -   " : "").append(client.getTelephone());
        }
        if (client.getIdentifiantFiscal() != null) {
            complement.append(complement.length() > 0 ? "   -   " : "")
                    .append("NIF : ").append(client.getIdentifiantFiscal());
        }
        if (complement.length() > 0) {
            document.add(new Paragraph(complement.toString(), police(9, Font.NORMAL, GRIS)));
        }
        document.add(espace(16));
    }

    private void tableauLignes(Document document, Facture facture) {
        PdfPTable table = new PdfPTable(new float[]{46f, 16f, 10f, 14f, 14f});
        table.setWidthPercentage(100);

        entete(table, "Designation", Element.ALIGN_LEFT);
        entete(table, "Camion", Element.ALIGN_LEFT);
        entete(table, "Qte", Element.ALIGN_RIGHT);
        entete(table, "Prix unitaire", Element.ALIGN_RIGHT);
        entete(table, "Montant", Element.ALIGN_RIGHT);

        for (FactureLigne ligne : facture.getLignes()) {
            cellule(table, ligne.getDesignation(), Element.ALIGN_LEFT);
            cellule(table, ligne.getCamion() == null ? "-" : ligne.getCamion().getImmatriculation(),
                    Element.ALIGN_LEFT);
            cellule(table, montant(ligne.getQuantite()) + (ligne.getUnite() == null
                    ? "" : " " + ligne.getUnite()), Element.ALIGN_RIGHT);
            cellule(table, montant(ligne.getPrixUnitaire()), Element.ALIGN_RIGHT);
            cellule(table, montant(ligne.getMontant()), Element.ALIGN_RIGHT);
        }
        document.add(table);
        document.add(espace(10));
    }

    private void pied(Document document, Facture facture) {
        PdfPTable totaux = new PdfPTable(new float[]{60f, 40f});
        totaux.setWidthPercentage(100);

        totalLigne(totaux, "Total hors taxes", montant(facture.getMontantHt()) + " FCFA", false);
        totalLigne(totaux, "TVA " + montant(facture.getTauxTva()) + " %",
                montant(facture.getMontantTva()) + " FCFA", false);
        totalLigne(totaux, "TOTAL A PAYER", montant(facture.getMontantTtc()) + " FCFA", true);
        if (facture.montantPaye().signum() > 0) {
            totalLigne(totaux, "Deja regle", montant(facture.montantPaye()) + " FCFA", false);
            totalLigne(totaux, "Reste a payer", montant(facture.resteAPayer()) + " FCFA", true);
        }
        document.add(totaux);
    }

    private void mentions(Document document, Facture facture) {
        document.add(espace(18));
        if (facture.getObservations() != null && !facture.getObservations().isBlank()) {
            document.add(new Paragraph(facture.getObservations(), police(9, Font.ITALIC, GRIS)));
            document.add(espace(8));
        }
        if (facture.getStatut() == StatutFacture.BROUILLON) {
            Paragraph brouillon = new Paragraph(
                    "Document provisoire : cette facture n'est pas encore emise.",
                    police(10, Font.BOLD, new Color(198, 40, 40)));
            brouillon.setAlignment(Element.ALIGN_CENTER);
            document.add(brouillon);
        }
        Paragraph remerciement = new Paragraph(
                "Merci de votre confiance.", police(9, Font.NORMAL, GRIS));
        remerciement.setAlignment(Element.ALIGN_CENTER);
        document.add(remerciement);
    }

    // ------------------------------------------------------------------
    // Aides de mise en page
    // ------------------------------------------------------------------

    private void entete(PdfPTable table, String texte, int alignement) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, police(9, Font.BOLD, Color.WHITE)));
        cellule.setBackgroundColor(BLEU);
        cellule.setHorizontalAlignment(alignement);
        cellule.setPadding(6);
        cellule.setBorderWidth(0);
        table.addCell(cellule);
    }

    private void cellule(PdfPTable table, String texte, int alignement) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, police(9, Font.NORMAL, Color.BLACK)));
        cellule.setHorizontalAlignment(alignement);
        cellule.setPadding(6);
        cellule.setBorderWidthLeft(0);
        cellule.setBorderWidthRight(0);
        cellule.setBorderWidthTop(0);
        cellule.setBorderColor(new Color(226, 230, 237));
        table.addCell(cellule);
    }

    private void totalLigne(PdfPTable table, String libelle, String valeur, boolean fort) {
        Font police = police(fort ? 11 : 9, fort ? Font.BOLD : Font.NORMAL,
                fort ? BLEU : Color.BLACK);

        PdfPCell gauche = new PdfPCell(new Phrase(libelle, police));
        gauche.setHorizontalAlignment(Element.ALIGN_RIGHT);
        gauche.setBorderWidth(0);
        gauche.setPadding(5);

        PdfPCell droite = new PdfPCell(new Phrase(valeur, police));
        droite.setHorizontalAlignment(Element.ALIGN_RIGHT);
        droite.setBorderWidth(0);
        droite.setPadding(5);
        if (fort) {
            droite.setBackgroundColor(new Color(242, 247, 252));
            gauche.setBackgroundColor(new Color(242, 247, 252));
        }
        table.addCell(gauche);
        table.addCell(droite);
    }

    private Font police(int taille, int style, Color couleur) {
        return FontFactory.getFont(FontFactory.HELVETICA, taille, style, couleur);
    }

    private Paragraph espace(float hauteur) {
        Paragraph paragraphe = new Paragraph(" ");
        paragraphe.setSpacingAfter(hauteur);
        return paragraphe;
    }

    /** Montants a la francaise : espace pour les milliers, virgule decimale. */
    private String montant(BigDecimal valeur) {
        if (valeur == null) {
            return "0";
        }
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symboles).format(valeur);
    }
}
