import { Societe } from '../core/models/parametrage';
import { Ticket } from '../core/models/pdv';
import { CommandeDetail } from '../core/models/restauration';
import { RecuReglement } from '../core/models/materiaux';

/** Echappe le texte libre avant de l'inserer dans le document du ticket. */
function echapper(valeur: string | number | null | undefined): string {
  return String(valeur ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

const nombre = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });
const quantite = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 3 });
const fcfa = (v: number | null | undefined) => `${nombre.format(v ?? 0)} F`;

/**
 * Imprime un ticket de caisse au format des imprimantes thermiques (80 mm).
 * <p>
 * Le ticket est compose dans un cadre invisible plutot qu'une nouvelle
 * fenetre : aucun bloqueur de fenetres surgissantes ne l'empeche, et l'ecran de
 * caisse reste en place derriere la boite d'impression.
 */
export function imprimerTicket(ticket: Ticket, societe: Societe | null): void {
  const date = new Date(ticket.dateVente).toLocaleString('fr-FR', {
    dateStyle: 'short',
    timeStyle: 'short'
  });
  const lignes = ticket.lignes
    .map(
      (l) => `
      <tr><td colspan="2" class="libelle">${echapper(l.libelle)}</td></tr>
      <tr>
        <td class="detail">${quantite.format(l.quantite)} x ${nombre.format(l.prixUnitaire)}${
          l.remise > 0 ? ` (-${nombre.format(l.remise)})` : ''
        }</td>
        <td class="montant">${nombre.format(l.total)}</td>
      </tr>`
    )
    .join('');

  const coordonnees = [societe?.adresse, societe?.ville, societe?.telephone].filter(Boolean).map(echapper).join(' · ');
  // Vente a credit (quincaillerie) : l'acompte paye et le reste du, a signer par le client.
  const aCredit = ticket.statut === 'VALIDEE' && (ticket.montantPaye ?? ticket.total) < ticket.total;
  const html = `<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>${echapper(ticket.numero)}</title>
<style>
  @page { size: 80mm auto; margin: 0; }
  * { box-sizing: border-box; }
  body { width: 80mm; margin: 0; padding: 4mm 4mm 8mm; font: 12px/1.35 'Courier New', monospace; color: #000; }
  .centre { text-align: center; }
  .logo { max-width: 40mm; max-height: 18mm; display: block; margin: 0 auto 2mm; }
  h1 { margin: 0; font-size: 15px; }
  .petit { font-size: 10px; }
  hr { border: none; border-top: 1px dashed #000; margin: 2mm 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 0; vertical-align: top; }
  .libelle { padding-top: 1mm; font-weight: bold; }
  .montant { text-align: right; white-space: nowrap; }
  .total td { padding-top: 1mm; font-size: 15px; font-weight: bold; }
  .annule { margin: 2mm 0; padding: 1mm; border: 2px solid #000; text-align: center; font-weight: bold; }
</style></head><body>
  <div class="centre">
    ${societe?.logo ? `<img class="logo" src="${societe.logo}" alt="">` : ''}
    <h1>${echapper(societe?.nom ?? '')}</h1>
    ${coordonnees ? `<div class="petit">${coordonnees}</div>` : ''}
    ${societe?.nif ? `<div class="petit">NIF : ${echapper(societe.nif)}</div>` : ''}
  </div>
  <hr>
  <div>Commande : <b>${echapper(ticket.numero)}</b></div>
  <div>${echapper(date)} · Caisse ${echapper(ticket.sessionNumero)}</div>
  <div>Caissier : ${echapper(ticket.caissierNom)}</div>
  ${ticket.tableNom ? `<div>Table : <b>${echapper(ticket.tableNom)}</b></div>` : ''}
  ${ticket.clientNom ? `<div>Client : ${echapper(ticket.clientNom)}</div>` : ''}
  ${ticket.statut === 'ANNULEE' ? '<div class="annule">COMMANDE ANNULEE</div>' : ''}
  <hr>
  <table>${lignes}</table>
  <hr>
  <table>
    ${ticket.remise > 0 ? `<tr><td>Sous-total</td><td class="montant">${fcfa(ticket.totalBrut)}</td></tr>
    <tr><td>Remise</td><td class="montant">-${fcfa(ticket.remise)}</td></tr>` : ''}
    <tr class="total"><td>TOTAL</td><td class="montant">${fcfa(ticket.total)}</td></tr>
    ${aCredit ? `<tr><td>Acompte (${echapper(ticket.modePaiementLibelle)})</td><td class="montant">${fcfa(ticket.montantPaye)}</td></tr>`
      : `<tr><td>${echapper(ticket.modePaiementLibelle)}</td><td class="montant">${fcfa(ticket.montantRecu)}</td></tr>`}
    ${ticket.monnaieRendue ? `<tr><td>Monnaie rendue</td><td class="montant">${fcfa(ticket.monnaieRendue)}</td></tr>` : ''}
    ${ticket.referencePaiement ? `<tr><td colspan="2" class="petit">Ref. ${echapper(ticket.referencePaiement)}</td></tr>` : ''}
    ${aCredit ? `<tr class="total"><td>RESTE DU</td><td class="montant">${fcfa(ticket.resteDu)}</td></tr>
    ${ticket.echeance ? `<tr><td colspan="2">A regler avant le ${echapper(new Date(ticket.echeance).toLocaleDateString('fr-FR'))}</td></tr>` : ''}` : ''}
  </table>
  ${aCredit ? '<div class="petit" style="margin-top:4mm">Signature du client :<br><br><br></div>' : ''}
  <hr>
  <div class="centre">Merci de votre visite !</div>
  ${societe?.mentionsFacture ? `<div class="centre petit">${echapper(societe.mentionsFacture)}</div>` : ''}
</body></html>`;

  imprimerDocument(html);
}

/**
 * Addition remise a la table avant le paiement : les articles servis ou a
 * servir, le deja regle et le reste a payer. Ce n'est pas un ticket de caisse.
 */
export function imprimerAddition(commande: CommandeDetail, societe: Societe | null): void {
  const date = new Date().toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
  const facturables = commande.lignes.filter((l) => l.statut !== 'BROUILLON' && l.statut !== 'ANNULEE');
  // Une ligne par article et par prix : l'addition se lit d'un coup d'oeil.
  const regroupees = new Map<string, { libelle: string; prix: number; quantite: number; total: number }>();
  for (const l of facturables) {
    const cle = `${l.articleId}|${l.prixUnitaire}|${l.note ?? ''}`;
    const libelle = l.note ? `${l.libelle} (${l.note})` : l.libelle;
    const existante = regroupees.get(cle) ?? { libelle, prix: l.prixUnitaire, quantite: 0, total: 0 };
    existante.quantite += l.quantite;
    existante.total += l.total;
    regroupees.set(cle, existante);
  }
  const lignes = [...regroupees.values()]
    .map(
      (l) => `
      <tr><td colspan="2" class="libelle">${echapper(l.libelle)}</td></tr>
      <tr><td class="detail">${quantite.format(l.quantite)} x ${nombre.format(l.prix)}</td>
          <td class="montant">${nombre.format(l.total)}</td></tr>`
    )
    .join('');
  const deja = commande.resume.total - commande.resume.resteAPayer;
  const r = commande.resume;
  const html = `<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>Addition ${echapper(r.repere)}</title>
<style>
  @page { size: 80mm auto; margin: 0; }
  * { box-sizing: border-box; }
  body { width: 80mm; margin: 0; padding: 4mm 4mm 8mm; font: 12px/1.35 'Courier New', monospace; color: #000; }
  .centre { text-align: center; }
  h1 { margin: 0; font-size: 15px; }
  h2 { margin: 1mm 0; font-size: 16px; letter-spacing: 2px; }
  .petit { font-size: 10px; }
  hr { border: none; border-top: 1px dashed #000; margin: 2mm 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 0; vertical-align: top; }
  .libelle { padding-top: 1mm; font-weight: bold; }
  .montant { text-align: right; white-space: nowrap; }
  .total td { padding-top: 1mm; font-size: 15px; font-weight: bold; }
</style></head><body>
  <div class="centre">
    <h1>${echapper(societe?.nom ?? '')}</h1>
    <h2>ADDITION</h2>
  </div>
  <hr>
  <div>Table : <b>${echapper(r.repere)}</b>${r.couverts ? ` · ${r.couverts} pers.` : ''}</div>
  <div>${echapper(date)} · ${echapper(r.numero)}</div>
  <div>Serveur : ${echapper(r.serveurNom)}</div>
  <hr>
  <table>${lignes}</table>
  <hr>
  <table>
    ${deja > 0 ? `<tr><td>Total</td><td class="montant">${fcfa(r.total)}</td></tr>
    <tr><td>Deja regle</td><td class="montant">-${fcfa(deja)}</td></tr>` : ''}
    <tr class="total"><td>A PAYER</td><td class="montant">${fcfa(r.resteAPayer)}</td></tr>
  </table>
  <hr>
  <div class="centre petit">Ce document n'est pas un recu de paiement.</div>
  <div class="centre">Merci de votre visite !</div>
</body></html>`;
  imprimerDocument(html);
}

/** Recu d'un versement de client a credit (quincaillerie), au format 80 mm. */
export function imprimerRecuReglement(recu: RecuReglement, societe: Societe | null): void {
  const date = new Date(recu.date).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
  const imputations = recu.imputations
    .map((i) => `<tr><td>${echapper(i.venteNumero)}</td><td class="montant">${fcfa(i.montant)}</td></tr>`)
    .join('');
  const html = `<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>${echapper(recu.numero)}</title>
<style>
  @page { size: 80mm auto; margin: 0; }
  * { box-sizing: border-box; }
  body { width: 80mm; margin: 0; padding: 4mm 4mm 8mm; font: 12px/1.35 'Courier New', monospace; color: #000; }
  .centre { text-align: center; }
  h1 { margin: 0; font-size: 15px; }
  h2 { margin: 1mm 0; font-size: 14px; letter-spacing: 1px; }
  .petit { font-size: 10px; }
  hr { border: none; border-top: 1px dashed #000; margin: 2mm 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 0; vertical-align: top; }
  .montant { text-align: right; white-space: nowrap; }
  .total td { padding-top: 1mm; font-size: 15px; font-weight: bold; }
  .annule { margin: 2mm 0; padding: 1mm; border: 2px solid #000; text-align: center; font-weight: bold; }
</style></head><body>
  <div class="centre">
    <h1>${echapper(societe?.nom ?? '')}</h1>
    ${societe?.nif ? `<div class="petit">NIF : ${echapper(societe.nif)}</div>` : ''}
    <h2>RECU DE REGLEMENT</h2>
  </div>
  <hr>
  <div>Recu : <b>${echapper(recu.numero)}</b></div>
  <div>${echapper(date)} · Caisse ${echapper(recu.sessionNumero)}</div>
  <div>Caissier : ${echapper(recu.caissierNom)}</div>
  <div>Client : <b>${echapper(recu.clientNom)}</b></div>
  ${recu.statut === 'ANNULE' ? '<div class="annule">REGLEMENT ANNULE</div>' : ''}
  <hr>
  <div class="petit">Impute sur :</div>
  <table>${imputations}</table>
  <hr>
  <table>
    <tr class="total"><td>VERSE</td><td class="montant">${fcfa(recu.montant)}</td></tr>
    <tr><td>${echapper(recu.modePaiementLibelle)}</td><td class="montant">${fcfa(recu.montantRecu)}</td></tr>
    ${recu.monnaieRendue ? `<tr><td>Monnaie rendue</td><td class="montant">${fcfa(recu.monnaieRendue)}</td></tr>` : ''}
    ${recu.referencePaiement ? `<tr><td colspan="2" class="petit">Ref. ${echapper(recu.referencePaiement)}</td></tr>` : ''}
    <tr><td>Reste du</td><td class="montant">${fcfa(recu.soldeRestant)}</td></tr>
  </table>
  <hr>
  <div class="centre">Merci !</div>
</body></html>`;
  imprimerDocument(html);
}

/**
 * Imprime un document compose dans un cadre invisible plutot qu'une nouvelle
 * fenetre : aucun bloqueur de fenetres surgissantes ne l'empeche, et l'ecran
 * reste en place derriere la boite d'impression.
 */
export function imprimerDocument(html: string): void {
  const cadre = document.createElement('iframe');
  cadre.setAttribute('aria-hidden', 'true');
  cadre.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden';
  document.body.appendChild(cadre);
  const doc = cadre.contentDocument;
  if (!doc || !cadre.contentWindow) {
    cadre.remove();
    return;
  }
  doc.open();
  doc.write(html);
  doc.close();
  const fenetre = cadre.contentWindow;
  // Le logo doit etre charge avant l'impression, sinon il manque sur le papier.
  const imprimer = () => {
    fenetre.focus();
    fenetre.print();
    setTimeout(() => cadre.remove(), 1000);
  };
  const images = Array.from(doc.images);
  if (images.every((img) => img.complete)) {
    setTimeout(imprimer, 50);
  } else {
    Promise.all(images.map((img) => new Promise((ok) => (img.onload = img.onerror = ok)))).then(imprimer);
  }
}
