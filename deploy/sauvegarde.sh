#!/bin/sh
# Sauvegarde de Nova chaque nuit : base (pg_dump -Fc) et justificatifs, gardes 14 jours.
# A recopier aussi hors du serveur (docs/mise-en-production.md, section 5).
set -e
N=/mnt/HC_Volume_107456986/nova
D=$N/sauvegardes
J=$(date -u +%Y-%m-%d_%H%M)
umask 077
docker exec nova-postgres pg_dump -U postgres -Fc nova > "$D/nova-$J.dump"
tar czf "$D/documents-$J.tgz" -C "$N/data" documents
# Une sauvegarde vide ou illisible est une erreur.
docker exec -i nova-postgres pg_restore --list < "$D/nova-$J.dump" > /dev/null
find "$D" -name 'nova-*.dump' -mtime +14 -delete
find "$D" -name 'documents-*.tgz' -mtime +14 -delete
echo "$(date -u) sauvegarde nova-$J.dump ok ($(du -h "$D/nova-$J.dump" | cut -f1))"
