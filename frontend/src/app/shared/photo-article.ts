/** Cote maximale de la photo d'un article : nette sur une carte de caisse, legere a charger. */
const COTE_MAX = 480;

/**
 * Reduit une photo choisie par l'utilisateur et la renvoie en data URL JPEG.
 * <p>
 * Une photo de telephone pese plusieurs Mo ; reduite et recadree au carre ici,
 * elle tombe a quelques dizaines de Ko avant meme de quitter le navigateur.
 */
export function reduirePhoto(fichier: File): Promise<string> {
  return new Promise((resoudre, rejeter) => {
    if (!fichier.type.startsWith('image/')) {
      rejeter(new Error('Choisissez une image (JPEG, PNG ou WebP).'));
      return;
    }
    const url = URL.createObjectURL(fichier);
    const image = new Image();
    image.onload = () => {
      // Recadrage carre centre : toutes les cartes de la caisse ont la meme forme.
      const cote = Math.min(image.naturalWidth, image.naturalHeight);
      const sortie = Math.min(COTE_MAX, cote);
      const toile = document.createElement('canvas');
      toile.width = sortie;
      toile.height = sortie;
      const contexte = toile.getContext('2d');
      if (!contexte) {
        URL.revokeObjectURL(url);
        rejeter(new Error('Navigateur incompatible.'));
        return;
      }
      contexte.fillStyle = '#ffffff';
      contexte.fillRect(0, 0, sortie, sortie);
      contexte.drawImage(
        image,
        (image.naturalWidth - cote) / 2, (image.naturalHeight - cote) / 2, cote, cote,
        0, 0, sortie, sortie
      );
      URL.revokeObjectURL(url);
      resoudre(toile.toDataURL('image/jpeg', 0.82));
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      rejeter(new Error('Image illisible.'));
    };
    image.src = url;
  });
}
