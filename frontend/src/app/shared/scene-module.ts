import { Component, input } from '@angular/core';

/**
 * Scene animee d'un module, en SVG et animations CSS : elle montre ce que fait
 * le module (le camion suivi par GPS, le produit scanne a la caisse, la commande
 * livree, l'argent qui circule, le pointage par code QR). Decorative : masquee
 * aux lecteurs d'ecran, figee si l'utilisateur limite les animations.
 */
@Component({
  selector: 'app-scene-module',
  template: `
    @switch (code()) {
      @case ('GESTION_PARC') {
        <svg viewBox="0 0 800 600" aria-hidden="true">
          <circle cx="660" cy="110" r="46" fill="#fde68a" opacity=".85"/>
          <path d="M0 420 Q120 330 260 390 T520 370 T800 360 V460 H0Z" fill="#0b4f80" opacity=".55"/>
          <path d="M0 440 Q160 380 330 420 T800 400 V470 H0Z" fill="#0a3d66" opacity=".7"/>
          <rect x="0" y="455" width="800" height="110" fill="#1e293b"/>
          <line class="route" x1="0" y1="510" x2="800" y2="510" stroke="#fbbf24" stroke-width="8" stroke-dasharray="46 34"/>
          <!-- carte de mission -->
          <g class="flotte">
            <rect x="70" y="80" width="230" height="96" rx="14" fill="#fff" opacity=".96"/>
            <text x="90" y="112" font-size="17" font-weight="700" fill="#0f172a">Mission M-024</text>
            <text x="90" y="136" font-size="14" fill="#475569">Lome → Kara · 412 km</text>
            <rect x="90" y="150" width="190" height="10" rx="5" fill="#e2e8f0"/>
            <rect class="progression" x="90" y="150" width="190" height="10" rx="5" fill="#0ea5e9"/>
          </g>
          <!-- camion -->
          <g class="cahot">
            <rect x="200" y="320" width="230" height="120" rx="10" fill="#f8fafc"/>
            <rect x="200" y="320" width="230" height="22" rx="10" fill="#cbd5e1"/>
            <text x="230" y="400" font-size="30" font-weight="800" fill="#0b3a63" letter-spacing="2">LJ</text>
            <rect x="290" y="372" width="120" height="8" rx="4" fill="#38bdf8"/>
            <path d="M436 350h70q18 0 28 16l26 40v34h-124z" fill="#0ea5e9"/>
            <path d="M450 362h50q10 0 16 9l18 28h-84z" fill="#e0f2fe"/>
            <rect x="548" y="420" width="16" height="10" rx="3" fill="#fde047"/>
            <g class="roue" style="transform-origin: 250px 448px"><circle cx="250" cy="448" r="26" fill="#0f172a"/><circle cx="250" cy="448" r="10" fill="#94a3b8"/><rect x="246" y="426" width="8" height="44" fill="#475569"/></g>
            <g class="roue" style="transform-origin: 320px 448px"><circle cx="320" cy="448" r="26" fill="#0f172a"/><circle cx="320" cy="448" r="10" fill="#94a3b8"/><rect x="316" y="426" width="8" height="44" fill="#475569"/></g>
            <g class="roue" style="transform-origin: 500px 448px"><circle cx="500" cy="448" r="26" fill="#0f172a"/><circle cx="500" cy="448" r="10" fill="#94a3b8"/><rect x="496" y="426" width="8" height="44" fill="#475569"/></g>
          </g>
          <!-- suivi GPS -->
          <circle class="onde" cx="390" cy="292" r="18" fill="none" stroke="#f43f5e" stroke-width="4"/>
          <circle class="onde retard" cx="390" cy="292" r="18" fill="none" stroke="#f43f5e" stroke-width="4"/>
          <g class="flotte">
            <path d="M390 292c0-34 34-52 34-84a34 34 0 0 0-68 0c0 32 34 50 34 84z" fill="#f43f5e"/>
            <circle cx="390" cy="208" r="13" fill="#fff"/>
          </g>
          <!-- maintenance -->
          <g class="apparait">
            <rect x="560" y="200" width="170" height="64" rx="14" fill="#fff" opacity=".96"/>
            <circle cx="590" cy="232" r="16" fill="#fef3c7"/>
            <path d="M584 238l10-10m-3-7a7 7 0 0 0 9 9" stroke="#b45309" stroke-width="4" stroke-linecap="round" fill="none"/>
            <text x="614" y="228" font-size="14" font-weight="700" fill="#0f172a">Vidange</text>
            <text x="614" y="248" font-size="13" fill="#b45309">dans 3 jours</text>
          </g>
        </svg>
      }
      @case ('POINT_DE_VENTE') {
        <svg viewBox="0 0 800 600" aria-hidden="true">
          <rect x="60" y="420" width="680" height="26" rx="6" fill="#a7f3d0"/>
          <rect x="80" y="446" width="640" height="110" fill="#047857" opacity=".75"/>
          <!-- produit qui glisse vers la douchette -->
          <g class="glisse">
            <rect x="110" y="352" width="78" height="68" rx="8" fill="#fbbf24"/>
            <rect x="110" y="352" width="78" height="18" rx="8" fill="#f59e0b"/>
            <g stroke="#78350f" stroke-width="3"><path d="M124 382v26M132 382v26M138 382v26M148 382v26M154 382v26M164 382v26M172 382v26"/></g>
          </g>
          <!-- douchette et faisceau -->
          <path d="M268 300h58a12 12 0 0 1 12 12v12a12 12 0 0 1-12 12h-24l-8 44h-20l6-44h-12z" fill="#1f2937"/>
          <path class="faisceau" d="M330 330 L300 420 L360 420Z" fill="#f43f5e" opacity=".55"/>
          <!-- ecran de caisse -->
          <rect x="420" y="170" width="230" height="170" rx="16" fill="#0f172a"/>
          <rect x="436" y="186" width="198" height="138" rx="8" fill="#ecfdf5"/>
          <text x="450" y="214" font-size="14" fill="#065f46">Riz 25 kg</text><text x="604" y="214" font-size="14" fill="#065f46" text-anchor="end">18 000</text>
          <text x="450" y="238" font-size="14" fill="#065f46">Huile 5 L</text><text x="604" y="238" font-size="14" fill="#065f46" text-anchor="end">7 500</text>
          <g class="ligne-ajoutee"><text x="450" y="262" font-size="14" font-weight="700" fill="#065f46">Sucre 1 kg</text><text x="604" y="262" font-size="14" font-weight="700" fill="#065f46" text-anchor="end">900</text></g>
          <rect x="448" y="278" width="174" height="32" rx="8" fill="#10b981"/>
          <text class="clignote" x="535" y="300" font-size="17" font-weight="800" fill="#fff" text-anchor="middle">Total 26 400 F</text>
          <path d="M520 340l-14 80h58l-14-80z" fill="#1f2937"/>
          <!-- imprimante et ticket -->
          <rect x="660" y="340" width="90" height="80" rx="12" fill="#e5e7eb"/>
          <rect x="676" y="350" width="58" height="8" rx="4" fill="#9ca3af"/>
          <g class="ticket" style="transform-origin: 705px 350px">
            <rect x="680" y="200" width="50" height="150" fill="#fff"/>
            <path d="M688 222h34M688 240h26M688 258h34M688 276h20M688 300h34" stroke="#94a3b8" stroke-width="4"/>
          </g>
          <!-- pieces -->
          <circle class="piece" cx="610" cy="400" r="14" fill="#facc15" stroke="#ca8a04" stroke-width="3"/>
          <circle class="piece retard" cx="640" cy="404" r="12" fill="#facc15" stroke="#ca8a04" stroke-width="3"/>
        </svg>
      }
      @case ('MARKETPLACE') {
        <svg viewBox="0 0 800 600" aria-hidden="true">
          <rect x="0" y="470" width="800" height="90" fill="#7c2d12" opacity=".7"/>
          <line class="route" x1="0" y1="515" x2="800" y2="515" stroke="#fed7aa" stroke-width="6" stroke-dasharray="36 28"/>
          <!-- telephone : la boutique en ligne -->
          <rect x="70" y="90" width="180" height="340" rx="28" fill="#1f2937"/>
          <rect x="84" y="118" width="152" height="290" rx="16" fill="#fff7ed"/>
          <rect x="98" y="132" width="124" height="54" rx="10" fill="#fdba74"/>
          <text x="160" y="166" font-size="16" font-weight="800" fill="#7c2d12" text-anchor="middle">Chez Ama</text>
          <g fill="#fed7aa"><rect x="98" y="198" width="58" height="58" rx="8"/><rect x="164" y="198" width="58" height="58" rx="8"/><rect x="98" y="264" width="58" height="58" rx="8"/><rect x="164" y="264" width="58" height="58" rx="8"/></g>
          <circle cx="127" cy="227" r="14" fill="#f97316"/><rect x="180" y="214" width="26" height="28" rx="4" fill="#fb923c"/>
          <circle cx="127" cy="293" r="14" fill="#ea580c"/><rect x="180" y="280" width="26" height="28" rx="4" fill="#f97316"/>
          <rect class="bouton-pulse" x="98" y="340" width="124" height="44" rx="22" fill="#ea580c"/>
          <text x="160" y="368" font-size="15" font-weight="800" fill="#fff" text-anchor="middle">Commander</text>
          <!-- colis qui part du telephone -->
          <g class="colis-vole"><rect x="230" y="300" width="36" height="32" rx="5" fill="#fb923c"/><path d="M230 312h36M248 300v32" stroke="#7c2d12" stroke-width="3"/></g>
          <!-- maison du client -->
          <path d="M600 330l80-70 80 70z" fill="#c2410c"/>
          <rect x="616" y="330" width="128" height="140" fill="#fff7ed"/>
          <rect x="666" y="400" width="34" height="70" rx="4" fill="#fdba74"/>
          <rect x="628" y="350" width="30" height="30" rx="4" fill="#fed7aa"/><rect x="704" y="350" width="30" height="30" rx="4" fill="#fed7aa"/>
          <g class="livre"><circle cx="740" cy="300" r="26" fill="#22c55e"/><path d="M727 300l9 9 17-19" stroke="#fff" stroke-width="6" fill="none" stroke-linecap="round" stroke-linejoin="round"/></g>
          <!-- livreur a moto -->
          <g class="moto">
            <circle cx="330" cy="482" r="22" fill="#111827"/><circle cx="330" cy="482" r="8" fill="#9ca3af"/>
            <circle cx="430" cy="482" r="22" fill="#111827"/><circle cx="430" cy="482" r="8" fill="#9ca3af"/>
            <path d="M330 482l40-40h50l10 40" stroke="#dc2626" stroke-width="12" fill="none" stroke-linejoin="round"/>
            <rect x="300" y="404" width="56" height="48" rx="6" fill="#fb923c"/>
            <path d="M300 420h56" stroke="#7c2d12" stroke-width="3"/>
            <circle cx="392" cy="392" r="16" fill="#1d4ed8"/>
            <path d="M380 408h26l10 34h-36z" fill="#f97316"/>
            <path d="M412 420l22 2" stroke="#1f2937" stroke-width="6" stroke-linecap="round"/>
          </g>
        </svg>
      }
      @case ('AGENCES') {
        <svg viewBox="0 0 800 600" aria-hidden="true">
          <!-- agence -->
          <rect x="70" y="200" width="240" height="250" rx="12" fill="#eef2ff"/>
          <rect x="70" y="200" width="240" height="56" rx="12" fill="#4f46e5"/>
          <text x="190" y="236" font-size="22" font-weight="800" fill="#fff" text-anchor="middle">AGENCE</text>
          <g><rect x="70" y="256" width="40" height="18" fill="#facc15"/><rect x="110" y="256" width="40" height="18" fill="#fff"/><rect x="150" y="256" width="40" height="18" fill="#facc15"/><rect x="190" y="256" width="40" height="18" fill="#fff"/><rect x="230" y="256" width="40" height="18" fill="#facc15"/><rect x="270" y="256" width="40" height="18" fill="#fff"/></g>
          <rect x="96" y="300" width="188" height="70" rx="8" fill="#c7d2fe"/>
          <circle cx="190" cy="318" r="16" fill="#312e81"/><path d="M168 360a22 22 0 0 1 44 0z" fill="#312e81"/>
          <rect x="70" y="370" width="240" height="80" fill="#a5b4fc"/>
          <!-- soldes cash et virtuel -->
          <g>
            <text x="96" y="404" font-size="13" font-weight="700" fill="#1e1b4b">Cash</text>
            <rect x="150" y="394" width="130" height="12" rx="6" fill="#e0e7ff"/><rect class="solde-cash" x="150" y="394" width="130" height="12" rx="6" fill="#16a34a"/>
            <text x="96" y="430" font-size="13" font-weight="700" fill="#1e1b4b">Virtuel</text>
            <rect x="150" y="420" width="130" height="12" rx="6" fill="#e0e7ff"/><rect class="solde-virtuel" x="150" y="420" width="130" height="12" rx="6" fill="#7c3aed"/>
          </g>
          <!-- trajet de l'argent -->
          <path d="M310 290 Q440 150 560 250" stroke="#c7d2fe" stroke-width="4" stroke-dasharray="8 12" fill="none" opacity=".7"/>
          <path d="M560 330 Q440 430 310 350" stroke="#c7d2fe" stroke-width="4" stroke-dasharray="8 12" fill="none" opacity=".7"/>
          <g class="billet"><rect x="-30" y="-17" width="60" height="34" rx="5" fill="#22c55e"/><circle cx="0" cy="0" r="10" fill="#bbf7d0"/></g>
          <g class="jeton"><circle cx="0" cy="0" r="17" fill="#facc15" stroke="#ca8a04" stroke-width="4"/><text x="0" y="6" font-size="16" font-weight="800" fill="#854d0e" text-anchor="middle">F</text></g>
          <!-- telephone mobile money -->
          <rect x="560" y="150" width="160" height="300" rx="26" fill="#1e1b4b"/>
          <rect x="574" y="176" width="132" height="250" rx="14" fill="#eef2ff"/>
          <rect x="586" y="190" width="108" height="40" rx="8" fill="#4f46e5"/>
          <text x="640" y="216" font-size="14" font-weight="800" fill="#fff" text-anchor="middle">Mobile money</text>
          <text x="640" y="262" font-size="13" fill="#4338ca" text-anchor="middle">Depot</text>
          <text x="640" y="292" font-size="24" font-weight="800" fill="#1e1b4b" text-anchor="middle">25 000 F</text>
          <g class="confirme"><rect x="590" y="320" width="100" height="40" rx="20" fill="#22c55e"/><text x="640" y="346" font-size="15" font-weight="800" fill="#fff" text-anchor="middle">Envoye ✓</text></g>
          <text x="640" y="398" font-size="12" fill="#6366f1" text-anchor="middle">Commission 250 F</text>
        </svg>
      }
      @case ('PRESENCE') {
        <svg viewBox="0 0 800 600" aria-hidden="true">
          <!-- borne : code QR qui change -->
          <rect x="80" y="110" width="250" height="360" rx="26" fill="#1e1b2e"/>
          <rect x="98" y="132" width="214" height="316" rx="14" fill="#faf5ff"/>
          <text x="205" y="162" font-size="15" font-weight="800" fill="#581c87" text-anchor="middle">Scannez pour pointer</text>
          <rect x="120" y="180" width="170" height="170" rx="10" fill="#fff"/>
          <g fill="#3b0764">
            <rect x="132" y="192" width="44" height="44" rx="5"/><rect x="234" y="192" width="44" height="44" rx="5"/><rect x="132" y="294" width="44" height="44" rx="5"/>
            <rect class="qr qr-1" x="190" y="196" width="14" height="14"/><rect class="qr qr-2" x="212" y="214" width="14" height="14"/>
            <rect class="qr qr-3" x="190" y="244" width="14" height="14"/><rect class="qr qr-1" x="222" y="244" width="14" height="14"/>
            <rect class="qr qr-2" x="252" y="252" width="14" height="14"/><rect class="qr qr-3" x="140" y="252" width="14" height="14"/>
            <rect class="qr qr-1" x="196" y="276" width="14" height="14"/><rect class="qr qr-3" x="236" y="290" width="14" height="14"/>
            <rect class="qr qr-2" x="264" y="300" width="14" height="14"/><rect class="qr qr-1" x="200" y="316" width="14" height="14"/>
            <rect class="qr qr-3" x="240" y="322" width="26" height="14"/>
          </g>
          <g fill="#fff"><rect x="142" y="202" width="24" height="24" rx="3"/><rect x="244" y="202" width="24" height="24" rx="3"/><rect x="142" y="304" width="24" height="24" rx="3"/></g>
          <rect x="120" y="370" width="170" height="10" rx="5" fill="#e9d5ff"/>
          <rect class="decompte" x="120" y="370" width="170" height="10" rx="5" fill="#9333ea"/>
          <text x="205" y="410" font-size="13" fill="#7e22ce" text-anchor="middle">Nouveau code dans 30 s</text>
          <!-- telephone qui scanne -->
          <path class="scan" d="M420 300 L300 200 L300 330Z" fill="#c084fc" opacity=".35"/>
          <g transform="rotate(-10 470 300)">
            <rect x="420" y="190" width="110" height="210" rx="20" fill="#1e1b2e"/>
            <rect x="430" y="210" width="90" height="170" rx="12" fill="#f3e8ff"/>
            <text x="475" y="260" font-size="13" font-weight="700" fill="#581c87" text-anchor="middle">PIN</text>
            <text x="475" y="288" font-size="20" font-weight="800" fill="#3b0764" text-anchor="middle" letter-spacing="3">••••••</text>
          </g>
          <g class="valide"><circle cx="540" cy="200" r="30" fill="#22c55e"/><path d="M525 200l10 10 20-22" stroke="#fff" stroke-width="7" fill="none" stroke-linecap="round" stroke-linejoin="round"/></g>
          <!-- horloge -->
          <circle cx="670" cy="150" r="62" fill="#faf5ff"/>
          <circle cx="670" cy="150" r="62" fill="none" stroke="#c084fc" stroke-width="6"/>
          <path d="M670 150v-34" stroke="#3b0764" stroke-width="6" stroke-linecap="round"/>
          <path class="aiguille" d="M670 150h40" stroke="#9333ea" stroke-width="4" stroke-linecap="round" style="transform-origin: 670px 150px"/>
          <circle cx="670" cy="150" r="6" fill="#3b0764"/>
          <!-- arrivees du jour -->
          <rect x="570" y="250" width="200" height="150" rx="14" fill="#fff" opacity=".96"/>
          <text x="590" y="280" font-size="15" font-weight="800" fill="#3b0764">Arrivees du jour</text>
          <g class="arrivee un"><circle cx="596" cy="306" r="8" fill="#22c55e"/><text x="612" y="311" font-size="14" fill="#1e1b2e">Kossi · 07:52</text></g>
          <g class="arrivee deux"><circle cx="596" cy="336" r="8" fill="#22c55e"/><text x="612" y="341" font-size="14" fill="#1e1b2e">Ama · 07:58</text></g>
          <g class="arrivee trois"><circle cx="596" cy="366" r="8" fill="#f59e0b"/><text x="612" y="371" font-size="14" fill="#1e1b2e">Yao · 08:16 (retard)</text></g>
          <!-- lieu et rayon GPS -->
          <circle class="onde" cx="430" cy="500" r="24" fill="none" stroke="#e9d5ff" stroke-width="4"/>
          <circle class="onde retard" cx="430" cy="500" r="24" fill="none" stroke="#e9d5ff" stroke-width="4"/>
          <path d="M430 500c0-26 26-40 26-66a26 26 0 0 0-52 0c0 26 26 40 26 66z" fill="#c084fc"/>
          <circle cx="430" cy="434" r="10" fill="#fff"/>
        </svg>
      }
    }
  `,
  styles: `
    :host { display: block; width: 100%; height: 100%; }
    svg { width: 100%; height: 100%; display: block; overflow: visible; font-family: inherit; }
    * { transform-box: fill-box; }

    /* Commun */
    .route { animation: route 1.1s linear infinite; }
    @keyframes route { to { stroke-dashoffset: -80; } }
    .flotte { animation: flotte 3s ease-in-out infinite; }
    @keyframes flotte { 50% { transform: translateY(-8px); } }
    .onde { transform-origin: center; animation: onde 2.4s ease-out infinite; }
    .onde.retard { animation-delay: 1.2s; }
    @keyframes onde { from { transform: scale(1); opacity: .9; } to { transform: scale(4); opacity: 0; } }
    .apparait { animation: apparait 7s ease-in-out infinite; }
    @keyframes apparait { 0%, 30% { opacity: 0; transform: translateY(10px); } 40%, 90% { opacity: 1; transform: none; } 100% { opacity: 0; } }

    /* Parc */
    .cahot { animation: cahot .5s ease-in-out infinite alternate; }
    @keyframes cahot { to { transform: translateY(-3px); } }
    .roue { transform-box: view-box; animation: roue .6s linear infinite; }
    @keyframes roue { to { transform: rotate(360deg); } }
    .progression { transform-origin: left center; animation: progression 7s ease-in-out infinite; }
    @keyframes progression { from { transform: scaleX(.15); } to { transform: scaleX(1); } }

    /* Point de vente */
    .glisse { animation: glisse 3.5s ease-in-out infinite; }
    @keyframes glisse { 0% { transform: translateX(0); opacity: 0; } 10% { opacity: 1; } 45%, 70% { transform: translateX(150px); opacity: 1; } 85%, 100% { transform: translateX(150px); opacity: 0; } }
    .faisceau { animation: faisceau 3.5s steps(1) infinite; }
    @keyframes faisceau { 0%, 44% { opacity: 0; } 45%, 62% { opacity: .6; } 63%, 100% { opacity: 0; } }
    .ligne-ajoutee { animation: ligne 3.5s ease-out infinite; }
    @keyframes ligne { 0%, 55% { opacity: 0; transform: translateX(-8px); } 65%, 100% { opacity: 1; transform: none; } }
    .clignote { animation: clignote 3.5s ease-in-out infinite; }
    @keyframes clignote { 0%, 60% { opacity: .55; } 70%, 100% { opacity: 1; } }
    .ticket { transform-box: view-box; animation: ticket 3.5s ease-out infinite; }
    @keyframes ticket { 0%, 65% { transform: scaleY(0); } 90%, 100% { transform: scaleY(1); } }
    .piece { transform-origin: center; animation: piece 3.5s ease-out infinite; }
    .piece.retard { animation-delay: .25s; }
    @keyframes piece { 0%, 70% { transform: translateY(0); opacity: 0; } 78% { opacity: 1; } 100% { transform: translateY(-70px); opacity: 0; } }

    /* Marketplace */
    .bouton-pulse { transform-origin: center; animation: pulse 7s ease-in-out infinite; }
    @keyframes pulse { 0%, 6% { transform: scale(1); } 10% { transform: scale(.92); } 14%, 100% { transform: scale(1); } }
    .colis-vole { animation: vole 7s ease-in-out infinite; }
    @keyframes vole { 0%, 12% { transform: translate(0, 0); opacity: 0; } 16% { opacity: 1; } 28% { transform: translate(80px, 110px); opacity: 1; } 32%, 100% { transform: translate(80px, 110px); opacity: 0; } }
    .moto { animation: moto 7s ease-in-out infinite; }
    @keyframes moto { 0%, 28% { transform: translateX(0); } 70%, 100% { transform: translateX(210px); } }
    .livre { transform-origin: center; animation: livre 7s ease-out infinite; }
    @keyframes livre { 0%, 70% { transform: scale(0); } 78% { transform: scale(1.15); } 84%, 100% { transform: scale(1); } }

    /* Agences */
    .billet { offset-path: path('M310 290 Q440 150 560 250'); offset-rotate: 0deg; animation: trajet 3.5s ease-in-out infinite; }
    .jeton { offset-path: path('M560 330 Q440 430 310 350'); offset-rotate: 0deg; animation: trajet 3.5s ease-in-out infinite 1.75s; }
    @keyframes trajet { 0% { offset-distance: 0%; opacity: 0; } 10% { opacity: 1; } 80% { opacity: 1; } 100% { offset-distance: 100%; opacity: 0; } }
    .confirme { transform-origin: center; animation: confirme 3.5s ease-out infinite; }
    @keyframes confirme { 0%, 75% { transform: scale(.6); opacity: 0; } 85%, 100% { transform: scale(1); opacity: 1; } }
    .solde-cash { transform-origin: left center; animation: cash 3.5s ease-in-out infinite; }
    .solde-virtuel { transform-origin: left center; animation: virtuel 3.5s ease-in-out infinite; }
    @keyframes cash { 0%, 100% { transform: scaleX(.75); } 50% { transform: scaleX(.45); } }
    @keyframes virtuel { 0%, 100% { transform: scaleX(.4); } 50% { transform: scaleX(.7); } }

    /* Presence */
    .qr { animation: qr 3s steps(1) infinite; }
    .qr-2 { animation-delay: 1s; }
    .qr-3 { animation-delay: 2s; }
    @keyframes qr { 0%, 49% { opacity: 1; } 50%, 100% { opacity: .1; } }
    .decompte { transform-origin: left center; animation: decompte 7s linear infinite; }
    @keyframes decompte { from { transform: scaleX(1); } to { transform: scaleX(0); } }
    .scan { animation: scan 2.4s ease-in-out infinite; }
    @keyframes scan { 0%, 100% { opacity: .1; } 50% { opacity: .45; } }
    .valide { transform-origin: center; animation: valide 7s ease-out infinite; }
    @keyframes valide { 0%, 30% { transform: scale(0); } 36% { transform: scale(1.2); } 42%, 100% { transform: scale(1); } }
    .aiguille { transform-box: view-box; animation: roue 7s linear infinite; }
    .arrivee { opacity: 0; animation: arrivee 7s ease-out infinite; }
    .arrivee.deux { animation-delay: .6s; }
    .arrivee.trois { animation-delay: 1.2s; }
    @keyframes arrivee { 0%, 35% { opacity: 0; transform: translateX(-10px); } 45%, 95% { opacity: 1; transform: none; } 100% { opacity: 0; } }

    @media (prefers-reduced-motion: reduce) {
      * { animation: none !important; }
      .arrivee, .ligne-ajoutee, .confirme, .valide, .livre { opacity: 1; transform: none; }
      .ticket { transform: none; }
    }
  `
})
export class SceneModule {
  readonly code = input.required<string>();
}
