#!/bin/zsh
# Starts the application with the CURATED DEMO DATASET.
#
# This is the documented way to launch the demo: it points the
# application at the dedicated demonstration database ("omsai_demo"),
# fills it with the Maharashtra coaching catalogue and the fictional
# sample identities, and leaves the development database ("omsai") and
# every real record in it completely untouched.
#
# Safety:
#   * the script refuses to run against any database whose name is not
#     "omsai_demo" - the development database can never be seeded by
#     accident;
#   * the demo seeder itself refuses to write to any other database and
#     refuses to run in the "prod" profile;
#   * no password is stored in this repository. The administrator of the
#     demo database is created from the environment on the first run.
#
# The demonstration database is DISPOSABLE and is therefore reset on
# every start, so the application always shows exactly the curated
# dataset - never the E2E records an end-to-end smoke test may have
# left behind. Set KEEP_DEMO_DATA=1 to keep the current content.
#
# Usage:
#   zsh scripts/start-demo-app.sh
#
#   # with your own administrator credentials:
#   APP_ADMIN_EMAIL=admin@example.invalid \
#   APP_ADMIN_PASSWORD='choose-a-strong-one' \
#   zsh scripts/start-demo-app.sh
#
#   # on another port:
#   PORT=8091 zsh scripts/start-demo-app.sh
#
# The application is then reachable at http://localhost:8080 (or the
# chosen port).
set -eu

DB_NAME=${DEMO_DB_NAME:-omsai_demo}
DB_HOST=${DEMO_DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-5433}
DB_USER=${DEMO_DB_USERNAME:-tojo}
DB_PASS=${DEMO_DB_PASSWORD:?set DEMO_DB_PASSWORD - the password of the local PostgreSQL user}
PORT=${PORT:-8080}
PSQL_BIN=${PSQL_BIN:-/usr/lib/postgresql/18/bin/psql}
PROJECT_DIR=${PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}

# Hard guard: this script may only ever touch the demonstration
# database. The development database "omsai" and the test databases are
# out of reach.
if [ "$DB_NAME" != "omsai_demo" ]; then
  echo "Refusing to start: this script may only use the demonstration database 'omsai_demo'." >&2
  echo "  (asked for '$DB_NAME')" >&2
  exit 1
fi

export PGPASSWORD=$DB_PASS
DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"

echo "===================================================================="
echo " Om Sai Coaching ERP - demo start"
echo "===================================================================="
echo "  database : $DB_NAME  ($DB_HOST:$DB_PORT)"
echo "  profile  : demo"
echo "  url      : http://localhost:${PORT}"
echo

# --- 1. the demonstration database -------------------------------------
# The demonstration database is DISPOSABLE: it holds nothing but the
# curated catalogue. It is therefore reset on every start, so the
# application always shows exactly the curated dataset and never the
# E2E records an end-to-end smoke test may have left behind.
# Set KEEP_DEMO_DATA=1 to keep whatever is in it.
if [ "${KEEP_DEMO_DATA:-0}" = "1" ]; then
  echo "Keeping the existing content of '$DB_NAME' (KEEP_DEMO_DATA=1)."
  if ! "$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres \
        -t -A -X -c "select 1 from pg_database where datname = '$DB_NAME'" | grep -q 1; then
    echo "Creating the demonstration database '$DB_NAME' ..."
    "$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres \
      -c "CREATE DATABASE $DB_NAME OWNER $DB_USER"
  fi
else
  echo "Resetting the demonstration database '$DB_NAME' (it is disposable) ..."
  "$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres \
    -c "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE);" \
    -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;"
fi
echo

# --- 2. the administrator of the demonstration database ----------------
# A freshly reset database has no schema yet - Flyway creates it when
# the application starts. A missing table simply means "no
# administrator yet".
ADMIN_EMAIL=${APP_ADMIN_EMAIL:-admin@example.invalid}
ADMIN_COUNT=$("$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -t -A -X -c "select count(*) from user_accounts where role = 'ADMIN'" 2>/dev/null || echo 0)

if [ "$ADMIN_COUNT" = "0" ]; then
  if [ -z "${APP_ADMIN_PASSWORD:-}" ]; then
    echo "The demonstration database has no administrator yet." >&2
    echo "Set APP_ADMIN_PASSWORD (at least 12 characters) and run the command again." >&2
    echo "Example:" >&2
    echo "  APP_ADMIN_EMAIL=$ADMIN_EMAIL APP_ADMIN_PASSWORD='choose-a-strong-one' \\" >&2
    echo "    zsh scripts/start-demo-app.sh" >&2
    exit 1
  fi
  echo "No administrator yet - one will be created from the environment."
  echo "  email    : $ADMIN_EMAIL"
  echo "  password : (the value of APP_ADMIN_PASSWORD, not shown)"
else
  echo "The demonstration database already has $ADMIN_COUNT administrator account(s)."
  echo "  The bootstrap does nothing; sign in with the account created earlier."
fi
echo

# --- 3. start the application ------------------------------------------
echo "Starting the application (Flyway applies the migrations, the seeder"
echo "writes the curated catalogue). Stop it with Ctrl-C."
echo

cd "$PROJECT_DIR"
# The student-portal learning pack (timetable, past attendance,
# resources, quizzes, a sample fee agreement) is presentation content:
# on by default here, off unless asked for otherwise.
#
# The two DEMO portal accounts are re-created on every start. Their
# passwords are demo-only: you can set DEMO_STUDENT_PASSWORD and
# DEMO_TEACHER_PASSWORD, otherwise the published demo values are used.
DEMO_LEARNING_DATA="${DEMO_LEARNING_DATA:-1}" \
DEMO_STUDENT_PASSWORD="${DEMO_STUDENT_PASSWORD:-StudentDemo#2026}" \
DEMO_TEACHER_PASSWORD="${DEMO_TEACHER_PASSWORD:-TeacherDemo#2026}" \
SPRING_PROFILES_ACTIVE=demo \
DEMO_DB_URL="$DB_URL" \
DEMO_DB_USERNAME="$DB_USER" \
DEMO_DB_PASSWORD="$DB_PASS" \
SERVER_PORT="$PORT" \
APP_ADMIN_EMAIL="$ADMIN_EMAIL" \
APP_ADMIN_PASSWORD="${APP_ADMIN_PASSWORD:-}" \
exec ./mvnw -o spring-boot:run
