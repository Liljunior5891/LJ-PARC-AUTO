import { Observable } from 'rxjs';

/**
 * Ouvre un fichier telecharge (preuve, justificatif) dans un nouvel onglet.
 * L'onglet s'ouvre tout de suite, pendant le clic, pour ne pas etre bloque
 * comme fenetre surgissante ; le fichier s'y affiche une fois recu. Si le
 * navigateur refuse quand meme l'onglet, le fichier est telecharge. L'adresse
 * temporaire est liberee ensuite.
 */
export function ouvrirFichier(chargement: Observable<Blob>, nom = 'fichier'): void {
  const onglet = window.open('', '_blank');
  chargement.subscribe({
    next: (blob) => {
      const url = URL.createObjectURL(blob);
      if (onglet && !onglet.closed) {
        onglet.location.href = url;
      } else {
        const lien = document.createElement('a');
        lien.href = url;
        lien.download = nom;
        lien.click();
      }
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    },
    error: () => onglet?.close()
  });
}
