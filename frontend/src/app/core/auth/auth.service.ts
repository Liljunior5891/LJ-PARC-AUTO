import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CodeModule, LoginResponse, Role, UtilisateurCourant } from '../models/models';
import { PROFILS_MODULES } from '../models/plateforme';

const CLE_TOKEN = 'parcauto.token';
/** Profils de terrain : ils travaillent en poste, sur leur seul ecran. */
const POSTES: Role[] = ['CAISSIER', 'SERVEUR', 'PREPARATEUR'];
const CLE_UTILISATEUR = 'parcauto.utilisateur';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  /** Vrai lorsque la session stockee a ete purgee parce qu'elle avait expire. */
  private readonly sessionExpireeSignal = signal(false);

  private readonly utilisateurSignal = signal<UtilisateurCourant | null>(this.lireUtilisateurStocke());

  readonly utilisateur = this.utilisateurSignal.asReadonly();
  readonly sessionExpiree = this.sessionExpireeSignal.asReadonly();
  readonly connecte = computed(() => this.utilisateurSignal() !== null);

  login(email: string, motDePasse: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, { email, motDePasse })
      .pipe(tap((reponse) => this.ouvrirSession(reponse)));
  }

  /** Enregistre une session ouverte par la connexion ou par l'inscription d'une entreprise. */
  ouvrirSession(reponse: LoginResponse): void {
    this.sessionExpireeSignal.set(false);
    localStorage.setItem(CLE_TOKEN, reponse.token);
    this.memoriser(reponse.utilisateur);
  }

  /** Reporte dans la session une activation, une desactivation ou un changement de type d'activite. */
  mettreAJourModules(modules: CodeModule[], variantes?: Partial<Record<CodeModule, string>>): void {
    const courant = this.utilisateurSignal();
    if (courant) {
      this.memoriser({ ...courant, modules, variantes: variantes ?? courant.variantes });
    }
  }

  /** Type d'activite d'un module qui se decline ; boutique par defaut pour le point de vente. */
  variante(module: CodeModule): string | null {
    const variante = this.utilisateurSignal()?.variantes?.[module];
    return variante ?? (module === 'POINT_DE_VENTE' ? 'BOUTIQUE' : null);
  }

  /** Point de vente d'un bar, d'un restaurant ou d'un cafe : tables, commandes, cuisine. */
  estRestauration(): boolean {
    return this.aModule('POINT_DE_VENTE') && this.variante('POINT_DE_VENTE') === 'RESTAURATION';
  }

  /**
   * Point de vente d'une quincaillerie ou d'un depot de materiaux : la caisse de
   * la boutique, plus les comptes clients, le credit, les devis et les conditionnements.
   */
  estMateriaux(): boolean {
    return this.aModule('POINT_DE_VENTE') && this.variante('POINT_DE_VENTE') === 'MATERIAUX';
  }

  aModule(module: CodeModule): boolean {
    return this.utilisateurSignal()?.modules.includes(module) ?? false;
  }

  /** Module active par l'entreprise et ouvert au profil de l'utilisateur. */
  peutUtiliser(module: CodeModule): boolean {
    return this.aModule(module) && this.aRole(...PROFILS_MODULES[module]);
  }

  /**
   * Page d'entree d'un module : un caissier arrive a sa caisse, un serveur au
   * plan de salle, un cuisinier a son ecran de preparation.
   */
  accueilModule(module: CodeModule): string {
    if (module === 'POINT_DE_VENTE') {
      if (this.aRole('ADMIN', 'GERANT')) {
        return '/pdv/tableau-bord';
      }
      // Un associe sans autre profil n'a que sa part des benefices.
      if (this.aRole('ASSOCIE') && !this.aRole('CAISSIER', 'SERVEUR', 'PREPARATEUR', 'MAGASINIER')) {
        return '/pdv/benefices';
      }
      if (this.aRole('MAGASINIER') && !this.aRole('CAISSIER', 'SERVEUR')) {
        return '/pdv/retraits';
      }
      if (this.estRestauration()) {
        if (this.aRole('CAISSIER')) {
          return '/pdv/comptoir';
        }
        return this.aRole('SERVEUR') ? '/pdv/salle' : '/pdv/cuisine';
      }
      return '/pdv/caisse';
    }
    if (module === 'AGENCES') {
      // Superviseur et caissier arrivent sur le tableau de bord de leur agence.
      return '/agences/tableau-bord';
    }
    if (module === 'PRESENCE') {
      return '/presence/tableau-bord';
    }
    if (module === 'GESTION_PARC') {
      // Le chauffeur n'a ni les chiffres ni le parc entier : il arrive a ses missions.
      return this.aRole('ADMIN', 'GESTIONNAIRE_PARC') ? '/tableau-bord' : '/missions';
    }
    return '/modules';
  }

  /**
   * Poste de travail : un compte qui n'a que des profils de terrain (caissier,
   * serveur, cuisinier) travaille sur son seul ecran, en plein ecran, sans le
   * menu de l'application.
   */
  estPoste(): boolean {
    const roles = this.utilisateurSignal()?.roles ?? [];
    return roles.length > 0 && roles.every((r) => POSTES.includes(r));
  }

  /** Adresses ouvertes a un poste de travail, selon ses profils et le type d'activite. */
  routesPoste(): string[] {
    const routes = new Set<string>();
    const restauration = this.estRestauration();
    if (this.aRole('CAISSIER')) {
      (restauration ? ['/pdv/comptoir', '/pdv/commandes', '/pdv/salle'] : ['/pdv/caisse']).forEach((r) => routes.add(r));
    }
    if (this.aRole('SERVEUR')) {
      (restauration ? ['/pdv/salle', '/pdv/commandes', '/pdv/comptoir'] : ['/pdv/caisse']).forEach((r) => routes.add(r));
    }
    if (this.aRole('PREPARATEUR') && restauration) {
      routes.add('/pdv/cuisine');
    }
    return [...routes];
  }

  estSuperAdmin(): boolean {
    return this.aRole('SUPER_ADMIN');
  }

  /**
   * Premiere page apres connexion : la console pour l'exploitant, sinon le
   * premier module utilisable par le profil, a defaut le catalogue des modules.
   */
  pageAccueil(): string {
    if (this.estSuperAdmin()) {
      return '/plateforme/tableau-bord';
    }
    const module = (['GESTION_PARC', 'POINT_DE_VENTE', 'AGENCES', 'PRESENCE'] as CodeModule[]).find((m) => this.peutUtiliser(m));
    if (module) {
      return this.accueilModule(module);
    }
    if (this.aRole('ADMIN')) {
      return '/modules';
    }
    return this.peutUtiliser('MARKETPLACE') ? '/marketplace/commandes' : '/mon-compte';
  }

  /**
   * Relit le profil au serveur : profils, modules et variantes changent sans
   * reconnexion (formule baissee, profil retire), les menus suivent.
   */
  rafraichirProfil(): void {
    if (!this.token) {
      return;
    }
    this.http.get<UtilisateurCourant>(`${environment.apiUrl}/auth/moi`).subscribe({
      next: (u) => {
        if (this.utilisateurSignal()) {
          this.memoriser(u);
        }
      },
      error: () => undefined
    });
  }

  /** Le serveur exige le changement du mot de passe provisoire. */
  exigerChangementMotDePasse(): void {
    const utilisateur = this.utilisateurSignal();
    if (utilisateur) {
      // Nouvel objet a chaque refus : « Mon compte » se rouvre s'il avait ete ferme.
      this.memoriser({ ...utilisateur, motDePasseAChanger: true });
    }
  }

  /** Nom change depuis « Mon compte » : la barre du haut suit sans reconnexion. */
  mettreAJourNom(nomComplet: string): void {
    const utilisateur = this.utilisateurSignal();
    if (utilisateur) {
      this.memoriser({ ...utilisateur, nomComplet });
    }
  }

  private memoriser(utilisateur: UtilisateurCourant): void {
    localStorage.setItem(CLE_UTILISATEUR, JSON.stringify(utilisateur));
    this.utilisateurSignal.set(utilisateur);
  }

  /**
   * Ferme la session. Le motif est transmis a l'ecran de connexion : une session
   * expiree doit etre annoncee comme telle, et non ressembler a une panne serveur.
   */
  logout(motif?: 'expiration'): void {
    const jeton = localStorage.getItem(CLE_TOKEN);
    if (jeton && motif !== 'expiration') {
      // Invalide le jeton cote serveur : une copie faite avant la deconnexion ne rouvre plus rien.
      this.http.post<void>(`${environment.apiUrl}/auth/logout`, {}, { headers: { Authorization: `Bearer ${jeton}` } })
        .subscribe({ error: () => undefined });
    }
    // Les empreintes de PIN (deverrouillage hors ligne) ne survivent pas a la session.
    try {
      // Copies de reference des agences (clients, pieces d'identite) : effacees ; la file
      // d'operations non envoyees (agences.horsligne.*) reste, pour ne rien perdre.
      Object.keys(localStorage).filter((cle) => cle.startsWith('pin.empreinte.') || cle.startsWith('agences.copie.'))
        .forEach((cle) => localStorage.removeItem(cle));
      sessionStorage.removeItem('pdv.caisse.deverrouillee');
    } catch {
      // Stockage indisponible : rien a effacer.
    }
    localStorage.removeItem(CLE_TOKEN);
    localStorage.removeItem(CLE_UTILISATEUR);
    this.utilisateurSignal.set(null);
    void this.router.navigate(['/connexion'], {
      queryParams: motif ? { motif } : undefined
    });
  }

  get token(): string | null {
    const token = localStorage.getItem(CLE_TOKEN);
    if (token && this.expire(token)) {
      return null;
    }
    return token;
  }

  /** Vrai si l'utilisateur possede au moins un des roles demandes. */
  aRole(...roles: Role[]): boolean {
    const courant = this.utilisateurSignal();
    if (!courant) {
      return false;
    }
    return roles.some((role) => courant.roles.includes(role));
  }

  /** Profils autorises a conduire l'activite : tout sauf la gestion des comptes. */
  peutGererParc(): boolean {
    return this.aRole('ADMIN', 'GESTIONNAIRE_PARC');
  }

  /** Seul l'administrateur gere les comptes utilisateurs. */
  estAdmin(): boolean {
    return this.aRole('ADMIN');
  }

  /**
   * Lit la session stockee. Un jeton expire est purge immediatement : sans cela
   * l'application se croirait connectee et chaque appel echouerait en 401,
   * affichant une erreur de chargement au lieu d'inviter a se reconnecter.
   */
  private lireUtilisateurStocke(): UtilisateurCourant | null {
    const token = localStorage.getItem(CLE_TOKEN);
    const brut = localStorage.getItem(CLE_UTILISATEUR);
    if (!token || !brut || this.expire(token)) {
      if (token && this.expire(token)) {
        this.sessionExpireeSignal.set(true);
      }
      localStorage.removeItem(CLE_TOKEN);
      localStorage.removeItem(CLE_UTILISATEUR);
      return null;
    }
    try {
      const utilisateur = JSON.parse(brut) as UtilisateurCourant;
      // Session ouverte avant le passage multi-entreprises : elle ne connait pas les modules.
      return Array.isArray(utilisateur.modules) ? utilisateur : null;
    } catch {
      return null;
    }
  }

  /** Vrai si la date d'expiration du jeton est depassee. */
  private expire(token: string): boolean {
    const charge = this.lireCharge(token);
    if (!charge?.exp) {
      // Sans date d'expiration lisible, le serveur reste seul juge.
      return false;
    }
    return charge.exp * 1000 <= Date.now();
  }

  /** Decode la charge utile du JWT, sans verifier la signature : le serveur s'en charge. */
  private lireCharge(token: string): { exp?: number } | null {
    const parties = token.split('.');
    if (parties.length !== 3) {
      return null;
    }
    try {
      const base64 = parties[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(base64)) as { exp?: number };
    } catch {
      return null;
    }
  }
}
