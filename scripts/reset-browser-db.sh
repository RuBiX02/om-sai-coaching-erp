#!/bin/zsh
# Resets the DISPOSABLE database used by the real-browser Playwright tests.
#
# Safety: the script hard-fails unless the target database is exactly
# `omsai_browser` — it can never touch `omsai` or `omsai_test`.
#
# When is this needed?
#   The browser suite cleans up every row it creates, but crashes (or a
#   killed run) can leave partial rows behind. Dropping and recreating the
#   empty database restores a clean slate; Flyway re-creates the schema at
#   the next run.
#
# Usage:
#   zsh scripts/reset-browser-db.sh
set -eu

DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-5433}
DB_USER=${DB_USER:-tojo}
DB_PASS=${DB_PASS:-omsai_dev}
DB_NAME=${DB_NAME:-omsai_browser}
PSQL_BIN=${PSQL_BIN:-/usr/lib/postgresql/18/bin/psql}

if [ "$DB_NAME" != "omsai_browser" ]; then
  echo "Refusing to reset: this script may only reset the disposable 'omsai_browser' database." >&2
  exit 1
fi

export PGPASSWORD="$DB_PASS"
"$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres \
  -c "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE);" \
  -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;"

echo "Reset complete: '$DB_NAME' is now empty (schema is created by Flyway on the next test run)."
