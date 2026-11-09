import { BonCuisine } from '../core/models/restauration';
import { imprimerDocument } from './ticket-pdv';

function echapper(valeur: string | number | null | undefined): string {
  return String(valeur ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

const quantite = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 3 });

const BANDEAUX: Record<BonCuisine['type'], string> = {
  BON: '',
  ANNULATION: '*** ANNULATION ***',
  DUPLICATA: 'DUPLICATA',
  TEST: 'ESSAI'
};

/** Un bon (une page par exemplaire) : gros caracteres, sans prix, comme sur une imprimante de cuisine. */
function page(bon: BonCuisine): string {
  const heure = new Date(bon.date).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  const lignes = bon.lignes
    .map(
      (l) => `<div class="ligne"><span class="qte">${quantite.format(l.quantite)} x</span><span class="art">${echapper(l.libelle)}</span></div>
      ${l.note ? `<div class="note-ligne">&gt;&gt; ${echapper(l.note)}</div>` : ''}`
    )
    .join('');
  const bandeau = BANDEAUX[bon.type];
  return `<section class="bon${bon.type === 'ANNULATION' ? ' annulation' : ''}">
  <div class="poste">${echapper(bon.poste)}</div>
  ${bandeau ? `<div class="bandeau">${bandeau}</div>` : ''}
  <div class="entete">
    <div class="rangee"><strong class="repere">${echapper(bon.repere)}</strong><span>${bon.envoi ? 'Envoi n°' + bon.envoi : ''}</span></div>
    <div class="rangee"><span>${echapper(bon.numero)}</span><span>${heure}</span></div>
    ${bon.serveur || bon.caissier ? `<div class="rangee petit"><span>${bon.serveur ? 'Serveur : ' + echapper(bon.serveur) : ''}</span><span>${bon.caissier ? 'Caisse : ' + echapper(bon.caissier) : ''}</span></div>` : ''}
  </div>
  <hr />
  ${lignes}
  <hr />
  ${bon.note ? `<div class="note">Note : ${echapper(bon.note)}</div>` : ''}
</section>`;
}

/**
 * Imprime des bons de preparation par la fenetre d'impression du navigateur :
 * un bon par page (et par exemplaire), pour que l'imprimante coupe entre chacun.
 */
export function imprimerBonsCuisine(bons: BonCuisine[]): void {
  if (!bons.length) {
    return;
  }
  const largeur = Math.max(...bons.map((b) => b.largeurPapier || 80));
  const pages = bons.flatMap((b) => Array.from({ length: Math.max(1, b.copies || 1) }, () => page(b))).join('');
  const html = `<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>Bons de preparation</title>
<style>
  @page { size: ${largeur}mm auto; margin: 0; }
  * { box-sizing: border-box; }
  body { width: ${largeur}mm; margin: 0; font: 13px/1.35 'Courier New', monospace; color: #000; }
  .bon { padding: 3mm 3mm 8mm; page-break-after: always; break-after: page; }
  .bon:last-child { page-break-after: auto; break-after: auto; }
  .poste { text-align: center; font-size: 20px; font-weight: 700; text-transform: uppercase; border-bottom: 2px solid #000; padding-bottom: 1mm; }
  .bandeau { margin: 1.5mm 0; padding: 1mm; text-align: center; font-weight: 700; font-size: 15px; background: #000; color: #fff;
             -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  .entete { margin-top: 1.5mm; }
  .rangee { display: flex; justify-content: space-between; gap: 2mm; }
  .repere { font-size: 17px; }
  .petit { font-size: 11px; }
  hr { border: none; border-top: 1px dashed #000; margin: 2mm 0; }
  .ligne { display: flex; gap: 2mm; font-size: 16px; font-weight: 700; margin-top: 1mm; }
  .qte { white-space: nowrap; }
  .note-ligne { margin-left: 8mm; font-size: 13px; font-weight: 700; font-style: italic; }
  .annulation .ligne .art { text-decoration: line-through; }
  .note { font-weight: 700; }
</style></head><body>${pages}</body></html>`;
  imprimerDocument(html);
}
