#!/usr/bin/env bash
# Test de fumee de l'API PARC AUTO.
# Verifie que chaque endpoint de la priorite 1 repond, sur une API deja demarree.
#
#   ./scripts/verifier-api.sh [url-api] [email] [mot-de-passe]

set -u

API="${1:-http://localhost:8081/api}"
EMAIL="${2:-admin@parcauto.tg}"
MOT_DE_PASSE="${3:-${ADMIN_PASSWORD:-Admin@2026}}"

echec=0

TOKEN=$(curl -s -X POST "$API/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"motDePasse\":\"$MOT_DE_PASSE\"}" \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "ECHEC : authentification impossible sur $API"
  exit 1
fi
echo "Authentification : OK"

verifier() {
  local libelle="$1" chemin="$2"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" "$API/$chemin" -H "Authorization: Bearer $TOKEN")
  if [ "$code" = "200" ]; then
    printf '  OK    %-3s %s\n' "$code" "$libelle"
  else
    printf '  ECHEC %-3s %s\n' "$code" "$libelle"
    echec=1
  fi
}

verifier "profil courant"            "auth/moi"
verifier "liste camions"             "camions?page=0&size=20&sort=numeroInterne,asc"
verifier "camions filtres"           "camions?actif=true&statut=DISPONIBLE"
verifier "recherche camions"         "camions?q=a"
verifier "repartition statuts"       "camions/statistiques/statuts"
verifier "liste chauffeurs"          "chauffeurs?page=0&size=20&sort=nom,asc"
verifier "recherche chauffeurs"      "chauffeurs?q=a&actif=true"
verifier "alertes permis"            "chauffeurs/alertes/permis?jours=30"
verifier "affectations en cours"     "affectations?enCours=true"
verifier "historique affectations"   "affectations?page=0&size=20"
verifier "liste documents"           "documents?page=0&size=20"
verifier "alertes documents"         "documents/alertes/expiration?jours=30"
verifier "tableau de bord parc"      "tableau-bord/parc"
verifier "indicateurs exploitation"  "tableau-bord/exploitation"
verifier "vue globale du parc"       "tableau-bord/vue-parc"
verifier "liste clients"             "clients?page=0&size=20"
verifier "recherche clients"         "clients?q=a&actif=true"
verifier "liste contrats"            "locations?page=0&size=20"
verifier "contrats actifs"           "locations/actives"
verifier "echeances contrats"        "locations/alertes/echeances?jours=15"
verifier "liste missions"            "missions?page=0&size=20"
verifier "missions en cours"         "missions/en-cours"
verifier "liste livraisons"          "livraisons?page=0&size=20"
verifier "recherche livraisons"      "livraisons?q=a"

# --- Finances (phase 3) ---
PERIODE="debut=$(date -d '-1 month' +%Y-%m-01 2>/dev/null || echo 2026-01-01)&fin=$(date +%Y-%m-%d)"
verifier "liste recettes"            "recettes?page=0&size=20"
verifier "recettes filtrees"         "recettes?$PERIODE"
verifier "repartition recettes"      "recettes/repartition?$PERIODE"
verifier "liste depenses"            "depenses?page=0&size=20"
verifier "natures de depenses"       "depenses/natures"
verifier "repartition depenses"      "depenses/repartition?$PERIODE"
verifier "synthese carburant"        "carburant/camions/1?$PERIODE"
verifier "liste factures"            "factures?page=0&size=20"
verifier "factures impayees"         "factures/impayees"
verifier "etat des creances"         "factures/creances"
verifier "rentabilite globale"       "rentabilite/global?$PERIODE"
verifier "fiche performance camion"  "rentabilite/camions/1?$PERIODE"
verifier "tableau de bord finances"  "tableau-bord/finances?$PERIODE"

# --- Geolocalisation (phase 4) ---
verifier "fournisseurs GPS"          "gps/fournisseurs"
verifier "carte du parc"             "gps/carte"
verifier "zones de geofencing"       "gps/zones"
verifier "alertes GPS"               "gps/alertes?page=0&size=20"
verifier "alertes recentes"          "gps/alertes/recentes"
verifier "synthese des alertes"      "gps/alertes/synthese"
verifier "trajet d un camion"        "gps/camions/1/trajet"
verifier "dernieres positions"       "gps/camions/1/dernieres?nombre=5"

if [ "$echec" -eq 0 ]; then
  echo "Tous les endpoints repondent."
else
  echo "Au moins un endpoint est en erreur."
fi
exit "$echec"
