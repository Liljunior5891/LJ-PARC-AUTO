import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ReglagesRestauration, Salle, TableRestaurant } from '../../../core/models/restauration';
import { RestaurationService } from '../../../core/services/restauration.service';
import { SocieteService } from '../../../core/services/societe.service';
import { imprimerDocument } from '../../../shared/ticket-pdv';

/** Code QR d'une table : le dessin SVG et son adresse d'affichage dans la page. */
interface CodeTable {
  table: TableRestaurant;
  salle: string;
  svg: string;
  image: string;
}

/**
 * Commande par code QR : reglages de l'etablissement et codes a imprimer pour
 * chaque table. Le client qui scanne le code ouvre la carte en ligne de sa
 * table, commande, suit sa commande et appelle un serveur.
 */
@Component({
  selector: 'app-codes-qr-pdv',
  imports: [FormsModule],
  templateUrl: './codes-qr.html',
  styleUrl: './codes-qr.scss'
})
export class CodesQrPdv implements OnInit {
  private readonly service = inject(RestaurationService);
  private readonly societeService = inject(SocieteService);

  readonly reglages = signal<ReglagesRestauration | null>(null);
  readonly codes = signal<CodeTable[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly chargement = signal(true);

  formulaire: ReglagesRestauration = { qrActif: true, qrValidation: true, messageAccueil: '' };

  readonly origine = window.location.origin;
  readonly parSalle = computed(() => {
    const groupes = new Map<string, CodeTable[]>();
    for (const c of this.codes()) {
      groupes.set(c.salle, [...(groupes.get(c.salle) ?? []), c]);
    }
    return [...groupes.entries()].map(([salle, codes]) => ({ salle, codes }));
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.codes().forEach((c) => URL.revokeObjectURL(c.image)));
  }

  ngOnInit(): void {
    this.service.reglages().subscribe({
      next: (r) => {
        this.reglages.set(r);
        this.formulaire = { ...r, messageAccueil: r.messageAccueil ?? '' };
      },
      error: (e) => this.erreur.set(this.message(e, 'Chargement des reglages impossible.'))
    });
    this.chargerCodes();
  }

  lien(table: TableRestaurant): string {
    return `${this.origine}/t/${table.jetonQr}`;
  }

  enregistrer(): void {
    this.succes.set(null);
    this.service.modifierReglages({ ...this.formulaire, messageAccueil: this.formulaire.messageAccueil?.trim() || null })
      .subscribe({
        next: (r) => {
          this.reglages.set(r);
          this.succes.set('Reglages enregistres.');
        },
        error: (e) => this.erreur.set(this.message(e, 'Enregistrement impossible.'))
      });
  }

  renouveler(code: CodeTable): void {
    this.service.renouvelerJeton(code.table.id).subscribe({
      next: () => {
        this.succes.set(`Nouveau code pour la table ${code.table.nom} : imprimez-le pour remplacer l'ancien.`);
        this.chargerCodes();
      },
      error: (e) => this.erreur.set(this.message(e, 'Renouvellement impossible.'))
    });
  }

  imprimer(codes: CodeTable[] = this.codes()): void {
    this.societeService.lire().subscribe({
      next: (societe) => imprimerDocument(this.feuille(codes, societe?.nom ?? '')),
      error: () => imprimerDocument(this.feuille(codes, ''))
    });
  }

  private chargerCodes(): void {
    this.chargement.set(true);
    forkJoin({ salles: this.service.salles(), tables: this.service.tables() }).subscribe({
      next: ({ salles, tables }) => {
        const actives = tables.filter((t) => t.actif);
        if (actives.length === 0) {
          this.codes.set([]);
          this.chargement.set(false);
          return;
        }
        forkJoin(actives.map((t) => this.service.codeQr(t.id))).subscribe({
          next: (svgs) => {
            this.codes().forEach((c) => URL.revokeObjectURL(c.image));
            this.codes.set(actives.map((table, i) => ({
              table,
              salle: this.nomSalle(salles, table.salleId),
              svg: svgs[i],
              image: URL.createObjectURL(new Blob([svgs[i]], { type: 'image/svg+xml' }))
            })));
            this.chargement.set(false);
          },
          error: (e) => {
            this.chargement.set(false);
            this.erreur.set(this.message(e, 'Generation des codes impossible.'));
          }
        });
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(this.message(e, 'Chargement des tables impossible.'));
      }
    });
  }

  private nomSalle(salles: Salle[], id: number): string {
    return salles.find((s) => s.id === id)?.nom ?? '';
  }

  /** Chevalets a poser sur les tables : deux par ligne, a decouper. */
  private feuille(codes: CodeTable[], etablissement: string): string {
    const echapper = (v: string) => v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const cartes = codes.map((c) => `
      <div class="chevalet">
        <div class="etab">${echapper(etablissement)}</div>
        <div class="qr">${c.svg}</div>
        <div class="table">Table ${echapper(c.table.nom)}</div>
        <div class="appel">Scannez pour voir la carte et commander</div>
      </div>`).join('');
    return `<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>Codes QR des tables</title>
<style>
  @page { size: A4; margin: 12mm; }
  body { margin: 0; font-family: Arial, sans-serif; color: #111; }
  .grille { display: grid; grid-template-columns: 1fr 1fr; gap: 8mm; }
  .chevalet { break-inside: avoid; padding: 8mm; border: 1px dashed #999; border-radius: 4mm; text-align: center; }
  .etab { font-size: 13pt; font-weight: bold; }
  .qr svg { width: 55mm; height: 55mm; margin: 4mm auto; display: block; }
  .table { font-size: 22pt; font-weight: bold; }
  .appel { margin-top: 2mm; font-size: 11pt; color: #1e9e4a; font-weight: bold; }
</style></head><body><div class="grille">${cartes}</div></body></html>`;
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
