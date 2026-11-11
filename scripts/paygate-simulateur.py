#!/usr/bin/env python3
"""
Simulateur local de PayGate Global, pour essayer le paiement des abonnements
sans compte marchand. DEVELOPPEMENT UNIQUEMENT : l'API refuse de demarrer
avec une adresse PayGate non HTTPS hors PARCAUTO_DEV.

Lancement :
    python scripts/paygate-simulateur.py            (port 8099, cle "simulation-locale")

API a lancer avec :
    PAYGATE_URL=http://127.0.0.1:8099  PAYGATE_AUTH_TOKEN=simulation-locale

Comportement, selon le numero de telephone utilise :
    se termine par 00 : le client annule (statut 6)
    se termine par 11 : la demande expire (statut 4)
    se termine par 99 : demande refusee d'emblee (statut 4, parametres invalides)
    autre             : en cours (2) pendant 20 s, puis paye (0)

Le simulateur rappelle aussi l'API (comme PayGate) 22 s apres la demande, sur
http://127.0.0.1:8085/api/public/paygate/abonnements (option --rappel pour changer).
"""
import argparse
import json
import threading
import time
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PAIEMENTS = {}
VERROU = threading.Lock()


def statut(paiement):
    telephone = paiement["telephone"]
    age = time.time() - paiement["cree"]
    if telephone.endswith("00"):
        return 6 if age > 10 else 2
    if telephone.endswith("11"):
        return 4 if age > 10 else 2
    return 0 if age > 20 else 2


def rappeler(adresse, identifiant, paiement):
    time.sleep(22)
    corps = json.dumps({
        "tx_reference": paiement["tx"], "identifier": identifiant, "payment_reference": "SIM-" + paiement["tx"][-6:],
        "amount": paiement["montant"], "datetime": time.strftime("%Y-%m-%d %H:%M:%S"),
        "payment_method": paiement["reseau"], "phone_number": paiement["telephone"],
    }).encode()
    try:
        requete = urllib.request.Request(adresse, data=corps, headers={"Content-Type": "application/json"})
        urllib.request.urlopen(requete, timeout=10).read()
        print(f"[rappel] {identifiant} -> {adresse}")
    except Exception as e:  # le serveur verifie aussi de lui-meme toutes les 2 minutes
        print(f"[rappel] echec pour {identifiant} : {e}")


class Simulateur(BaseHTTPRequestHandler):
    cle = "simulation-locale"
    rappel = "http://127.0.0.1:8085/api/public/paygate/abonnements"

    def _json(self, code, corps):
        donnees = json.dumps(corps).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(donnees)))
        self.end_headers()
        self.wfile.write(donnees)

    def do_POST(self):
        longueur = int(self.headers.get("Content-Length", "0"))
        try:
            corps = json.loads(self.rfile.read(longueur) or b"{}")
        except ValueError:
            return self._json(400, {"error": "json"})
        if corps.get("auth_token") != self.cle:
            return self._json(200, {"status": 2})
        if self.path == "/api/v1/pay":
            telephone = str(corps.get("phone_number", ""))
            identifiant = str(corps.get("identifier", ""))
            if telephone.endswith("99") or corps.get("network") not in ("FLOOZ", "TMONEY") or not identifiant:
                return self._json(200, {"status": 4})
            with VERROU:
                if identifiant in PAIEMENTS:
                    return self._json(200, {"status": 6})
                paiement = {"telephone": telephone, "montant": corps.get("amount"), "reseau": corps.get("network"),
                            "cree": time.time(), "tx": uuid.uuid4().hex[:12].upper()}
                PAIEMENTS[identifiant] = paiement
            print(f"[demande] {identifiant} {paiement['montant']} F {paiement['reseau']} {telephone}")
            threading.Thread(target=rappeler, args=(self.rappel, identifiant, paiement), daemon=True).start()
            return self._json(200, {"tx_reference": paiement["tx"], "status": 0})
        if self.path == "/api/v2/status":
            identifiant = str(corps.get("identifier", ""))
            with VERROU:
                paiement = PAIEMENTS.get(identifiant)
            if not paiement:
                return self._json(200, {"status": 4})
            s = statut(paiement)
            return self._json(200, {"tx_reference": paiement["tx"], "identifier": identifiant,
                                    "payment_reference": "SIM-" + paiement["tx"][-6:] if s == 0 else None,
                                    "status": s, "datetime": time.strftime("%Y-%m-%d %H:%M:%S"),
                                    "payment_method": paiement["reseau"]})
        return self._json(404, {"error": "inconnu"})

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    options = argparse.ArgumentParser(description="Simulateur local de PayGate Global")
    options.add_argument("--port", type=int, default=8099)
    options.add_argument("--cle", default="simulation-locale")
    options.add_argument("--rappel", default=Simulateur.rappel)
    a = options.parse_args()
    Simulateur.cle = a.cle
    Simulateur.rappel = a.rappel
    print(f"Simulateur PayGate sur http://127.0.0.1:{a.port} (cle : {a.cle})")
    ThreadingHTTPServer(("127.0.0.1", a.port), Simulateur).serve_forever()
