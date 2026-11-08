#!/usr/bin/env bash
###############################################################################
# Sauvegarde de la base de données du portefeuille d'initiatives.
#
# Utilise mysqldump (fourni avec le client MySQL, aucune dépendance
# supplémentaire) avec --single-transaction pour une sauvegarde cohérente
# sans verrouiller les tables en écriture.
#
# Variables d'environnement (lues depuis .env en production) :
#   DB_NAME, DB_USERNAME, DB_PASSWORD (requis), DB_HOST, DB_PORT,
#   BACKUP_DIR (défaut ./backups), BACKUP_KEEP (défaut 14).
#
# Usage : ./database/backup-database.sh
###############################################################################
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
DB_NAME="${DB_NAME:?DB_NAME requis}"
DB_USER="${DB_USERNAME:?DB_USERNAME requis}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD requis}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
BACKUP_KEEP="${BACKUP_KEEP:-14}"

mkdir -p "$BACKUP_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
ARCHIVE="$BACKUP_DIR/backup_${DB_NAME}_${TIMESTAMP}.sql.gz"

echo "Sauvegarde de la base '$DB_NAME' vers $ARCHIVE ..."

mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
  --single-transaction --routines --triggers "$DB_NAME" | gzip > "$ARCHIVE"

echo "Sauvegarde créée : $ARCHIVE"

# Rotation : conserve les BACKUP_KEEP dernières sauvegardes.
ls -1t "$BACKUP_DIR"/backup_*.sql.gz 2>/dev/null | tail -n +$((BACKUP_KEEP + 1)) | xargs -r rm --

echo "Rotation effectuée (gardes les $BACKUP_KEEP dernières)."
