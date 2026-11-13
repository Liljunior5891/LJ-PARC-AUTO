import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Select2Service } from './core/services/select2.service';
import { SuiviOperation } from './shared/suivi-operation';
import { PageMaintenance } from './layout/page-maintenance';
import { EtatPlateformeService } from './core/services/etat-plateforme.service';
import { InstallationService } from './core/services/installation.service';
import { AideInstallation } from './shared/aide-installation';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, SuiviOperation, PageMaintenance, AideInstallation],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly titre = 'PARC AUTO';

  constructor() {
    // Toutes les listes deroulantes passent par Select2 des le premier rendu.
    inject(Select2Service);
    // Maintenances annoncees et version publiee : relues chaque minute, connecte ou non.
    inject(EtatPlateformeService).demarrer();
    // Application installable sur ordinateur et telephone.
    inject(InstallationService).demarrer();
  }
}
