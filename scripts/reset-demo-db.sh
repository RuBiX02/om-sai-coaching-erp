#!/bin/zsh
# Resets the DISPOSABLE demonstration database used by the demo
# application.
#
# Safety: the script hard-fails unless the target database is exactly
# `omsai_demo` — it can never touch `omsai` (the development database
# with the real records) or `omsai_test` / `omsai_browser`.
#
# When is this needed?
#   * The end-to-end smoke test writes `E2E …` records into the
#     application it tests. When it is run against the demo instance,
#     those records show up in the demonstration catalogue. This script
#     restores the pure curated dataset.
#   * The demo application itself resets the database on every start,
#     so this is only needed when the smoke test was run in between.
#
# The next start of the demo application recreates the schema (Flyway)
# and writes the curated catalogue again.
#
# Usage:
#   DB_PASS='your-local-postgres-password' zsh scripts/reset-demo-db.sh
set -eu

DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-5433}
DB_USER=${DB_USER:-tojo}
DB_PASS=${DB_PASS:?set DB_PASS - the password of the local PostgreSQL user}
DB_NAME=${DB_NAME:-omsai_demo}
PSQL_BIN=${PSQL_BIN:-/usr/lib/postgresql/18/bin/psql}

if [ "$DB_NAME" != "omsai_demo" ]; then
  echo "Refusing to reset: this script may only reset the disposable 'omsai_demo' database." >&2
  echo "  (asked for '$DB_NAME')" >&2
  exit 1
fi

export PGPASSWORD=$DB_PASS
"$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres \
  -c "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE);" \
  -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;"

echo "Reset complete: '$DB_NAME' is now empty."
echo "The next start of the demo application (zsh scripts/start-demo-app.sh)"
echo "recreates the schema and writes the curated catalogue again."
