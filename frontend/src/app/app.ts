import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Select2Service } from './core/services/select2.service';
import { SuiviOperation } from './shared/suivi-operation';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, SuiviOperation],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly titre = 'PARC AUTO';

  constructor() {
    // Toutes les listes deroulantes passent par Select2 des le premier rendu.
    inject(Select2Service);
  }
}
