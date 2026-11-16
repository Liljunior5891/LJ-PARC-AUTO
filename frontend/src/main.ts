import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// Ancien nom de la plateforme : les reglages gardes sur l'appareil (jeton de la borne de
// presence, impression automatique...) passent sous les nouvelles cles.
const ANCIENNES_CLES: [string, string][] = [
  ['lj-borne-presence', 'nova-borne-presence'],
  ['lj-impression-auto', 'nova-impression-auto'],
  ['lj.version.vue', 'nova.version.vue']
];
try {
  for (const [ancienne, nouvelle] of ANCIENNES_CLES) {
    const valeur = localStorage.getItem(ancienne);
    if (valeur !== null) {
      if (localStorage.getItem(nouvelle) === null) {
        localStorage.setItem(nouvelle, valeur);
      }
      localStorage.removeItem(ancienne);
    }
  }
} catch {
  // Stockage indisponible (navigation privee) : rien a reprendre.
}

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
