#!/bin/zsh
# Creates a compressed backup of a PostgreSQL database.
#
# The target database is always named explicitly: the script refuses to
# run without DB_NAME, and it prints what it is about to back up.
#
# Usage:
#   DB_NAME=omsai DB_HOST=127.0.0.1 DB_PORT=5432 DB_USER=omsai \
#   BACKUP_DIR=/var/backups/omsai zsh scripts/backup-db.sh
set -eu
set -o pipefail

DB_NAME=${DB_NAME:?Please name the database to back up (DB_NAME).}
DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-5432}
DB_USER=${DB_USER:?Please name the database user (DB_USER).}
DB_PASS=${DB_PASS:-}
BACKUP_DIR=${BACKUP_DIR:-backups}
PSQL_BIN=${PSQL_BIN:-/usr/lib/postgresql/18/bin/psql}
PG_DUMP_BIN=${PG_DUMP_BIN:-/usr/lib/postgresql/18/bin/pg_dump}

mkdir -p "$BACKUP_DIR"
STAMP=$(date +%Y%m%d-%H%M%S)
FILE="$BACKUP_DIR/${DB_NAME}-$STAMP.sql.gz"

echo "Backing up database '$DB_NAME' on $DB_HOST:$DB_PORT to $FILE"
export PGPASSWORD="$DB_PASS"
"$PG_DUMP_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" \
  --format=plain --no-owner --no-privileges --clean --if-exists "$DB_NAME" | gzip -9 > "$FILE"

SIZE=$(du -h "$FILE" | cut -f1)
echo "Backup finished: $FILE ($SIZE)"
echo "Keep it somewhere outside the database server and test a restore regularly."
echo "Verify it with:  zsh scripts/restore-db.sh --help"
