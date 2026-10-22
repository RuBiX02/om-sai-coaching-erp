#!/bin/zsh
# Restores a PostgreSQL backup into an EXPLICITLY named database.
#
# A restore overwrites the target database completely, so the script
# is deliberately hard to use by accident:
#   * the target database must be named (DB_NAME) and must not be the
#     development or test database of this project;
#   * the file must exist and be readable;
#   * unless CONFIRM_RESTORE=YES is set, the script only shows what it
#     would do and stops.
#
# Usage:
#   DB_NAME=omsai_restore DB_USER=omsai DB_PASS=... \
#   CONFIRM_RESTORE=YES zsh scripts/restore-db.sh backups/omsai-20260101-120000.sql.gz
set -eu

DB_NAME=${DB_NAME:?Please name the target database (DB_NAME).}
DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-5432}
DB_USER=${DB_USER:?Please name the database user (DB_USER).}
DB_PASS=${DB_PASS:-}
PSQL_BIN=${PSQL_BIN:-/usr/lib/postgresql/18/bin/psql}
CONFIRM_RESTORE=${CONFIRM_RESTORE:-NO}

case "$DB_NAME" in
  omsai|omsai_test|omsai_browser)
    echo "Refusing to restore into '$DB_NAME': that is a live development or test database." >&2
    echo "Choose an explicit restore target, for example DB_NAME=omsai_restore." >&2
    exit 1
    ;;
esac

if [ $# -lt 1 ]; then
  echo "Usage: DB_NAME=<target> DB_USER=<user> CONFIRM_RESTORE=YES \\"
  echo "       $0 <backup-file.sql.gz>" >&2
  exit 1
fi
FILE=$1

if [ ! -r "$FILE" ]; then
  echo "The backup file '$FILE' does not exist or cannot be read." >&2
  exit 1
fi

echo "Restore plan:"
echo "  source : $FILE"
echo "  target : $DB_NAME on $DB_HOST:$DB_PORT (user $DB_USER)"
echo "  the target database is DROPPED and recreated with the backup's content"

if [ "$CONFIRM_RESTORE" != "YES" ]; then
  echo
  echo "Nothing was changed. Set CONFIRM_RESTORE=YES to run the restore."
  exit 0
fi

export PGPASSWORD="$DB_PASS"
"$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres \
  -c "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE);" \
  -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;"

gzip -dc "$FILE" | "$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1

echo "Restore finished. Verify it with:"
echo "  psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c 'select count(*) from students;'"
echo "  psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c 'select version, success from flyway_schema_history order by installed_rank;'"
