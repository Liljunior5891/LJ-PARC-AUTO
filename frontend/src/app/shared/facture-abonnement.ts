import { PaiementAbonnement } from '../core/models/abonnement';
import { imprimerDocument } from './ticket-pdv';

const LIBELLES_MODULES: Record<string, string> = {
  GESTION_PARC: 'Gestion de parc',
  POINT_DE_VENTE: 'Point de vente',
  MARKETPLACE: 'Marketplace',
  AGENCES: 'Agences multiservices',
  PRESENCE: 'Gestion de presence'
};

function echapper(valeur: string | null | undefined): string {
  return (valeur ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);
}

function date(valeur: string | null | undefined): string {
  if (!valeur) {
    return '—';
  }
  const d = new Date(valeur.length === 10 ? valeur + 'T00:00:00' : valeur);
  return d.toLocaleDateString('fr-FR');
}

function montant(valeur: number, devise: string): string {
  return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(valeur).replace(/ /g, ' ') + ' ' + devise;
}

/**
 * Facture A4 d'un paiement d'abonnement valide : l'exploitant (mentions des
 * reglages) facture l'entreprise abonnee pour une periode.
 */
export function imprimerFactureAbonnement(p: PaiementAbonnement, nomPlateforme: string,
                                          mentions: string | null | undefined): void {
  const mois = ({ MENSUEL: 1, TRIMESTRIEL: 3, SEMESTRIEL: 6, ANNUEL: 12 } as Record<string, number>)[p.periodicite] ?? 1;
  // Prix au moment du paiement ; un ancien paiement sans detail se contente de la liste des modules.
  const lignes = p.lignes?.length
    ? p.lignes
    : p.modules.map((m) => ({ module: m, libelle: LIBELLES_MODULES[m] ?? m, prixMensuel: 0 }));
  const sousTotal = lignes.reduce((s, l) => s + l.prixMensuel * mois, 0);
  const remise = Math.max(0, sousTotal - p.montant);
  const corps = lignes.map((l) => `<tr><td>Module ${echapper(LIBELLES_MODULES[l.module] ?? l.libelle)}
      <small>— ${echapper(p.formuleNom)}</small></td>
      <td class="m">${l.prixMensuel ? montant(l.prixMensuel, p.devise) : '—'}</td><td class="m">${mois}</td>
      <td class="m">${l.prixMensuel ? montant(l.prixMensuel * mois, p.devise) : '—'}</td></tr>`).join('');
  const html = `<!doctype html><html lang="fr"><head><meta charset="utf-8">
<title>Facture ${echapper(p.numeroFacture)}</title>
<style>
  @page { size: A4; margin: 18mm; }
  body { font-family: Arial, Helvetica, sans-serif; color: #1f2a37; font-size: 12px; }
  .tete { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #1f2a37; padding-bottom: 12px; }
  h1 { margin: 0; font-size: 22px; letter-spacing: .04em; }
  .emetteur { white-space: pre-line; color: #4b5563; margin-top: 6px; }
  .num { text-align: right; }
  .num strong { font-size: 16px; display: block; }
  .bloc { margin-top: 18px; display: flex; gap: 24px; }
  .bloc > div { flex: 1; border: 1px solid #d1d5db; border-radius: 6px; padding: 10px 12px; }
  .bloc h3 { margin: 0 0 6px; font-size: 11px; text-transform: uppercase; color: #6b7280; }
  table { width: 100%; border-collapse: collapse; margin-top: 18px; }
  th, td { border-bottom: 1px solid #e5e7eb; padding: 8px; text-align: left; vertical-align: top; }
  th { background: #f3f4f6; font-size: 11px; text-transform: uppercase; }
  td.m, th.m { text-align: right; white-space: nowrap; }
  ul { margin: 4px 0 0; padding-left: 18px; }
  .total td { font-weight: bold; font-size: 14px; border-bottom: 2px solid #1f2a37; }
  .paye { margin-top: 18px; padding: 10px 12px; background: #ecfdf3; border: 1px solid #a6e3bf; border-radius: 6px; }
  .pied { margin-top: 30px; color: #6b7280; font-size: 10px; text-align: center; }
</style></head><body>
<div class="tete">
  <div>
    <h1>${echapper(nomPlateforme)}</h1>
    <div class="emetteur">${echapper(mentions)}</div>
  </div>
  <div class="num">FACTURE<strong>${echapper(p.numeroFacture)}</strong>Du ${date(p.traiteLe)}</div>
</div>
<div class="bloc">
  <div><h3>Facture a</h3><strong>${echapper(p.entrepriseNom)}</strong><br>Client n° ${p.entrepriseId}</div>
  <div><h3>Periode couverte</h3>Du ${date(p.periodeDebut)} au ${date(p.periodeFin)}<br>${echapper(p.periodiciteLibelle)}</div>
</div>
<table>
  <thead><tr><th>Designation</th><th class="m">Prix / mois</th><th class="m">Mois</th><th class="m">Montant</th></tr></thead>
  <tbody>
    ${corps}
    ${remise > 0 && sousTotal > 0 ? `<tr><td colspan="3">Remise ${echapper(p.periodiciteLibelle.toLowerCase())}</td><td class="m">-${montant(remise, p.devise)}</td></tr>` : ''}
    <tr class="total"><td colspan="3">Total — formule ${echapper(p.formuleNom)}, ${echapper(p.periodiciteLibelle.toLowerCase())}</td><td class="m">${montant(p.montant, p.devise)}</td></tr>
  </tbody>
</table>
<div class="paye">Payee par ${echapper(p.moyenLibelle)}${p.reference ? ' — reference ' + echapper(p.reference) : ''}.</div>
<div class="pied">Facture generee par ${echapper(nomPlateforme)}.</div>
</body></html>`;
  imprimerDocument(html);
}
