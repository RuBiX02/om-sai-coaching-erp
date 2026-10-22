#!/bin/zsh
# End-to-end smoke test for Om Sai Coaching ERP (Phases 1 and 2).
#
# Prerequisites:
#   - Application running on :8080 with a bootstrapped dev administrator
#     (sign-in name passed as OMSAI_ADMIN_EMAIL, default admin@omsai.local).
#   - PostgreSQL reachable, with credentials supplied through the
#     environment - no credentials are stored in this script:
#       OMSAI_DB_PASSWORD  password of the local PostgreSQL user
#       OMSAI_ADMIN_PASS   password of the bootstrapped dev administrator
#   - curl installed.
#
# The script creates uniquely-prefixed data (suffix = run timestamp), so
# it can be re-run against the same database.
#
# The script and the running application MUST look at the same database.
# The script therefore detects it: after the first record has been
# created through the running application, that record is looked up in
# the candidate databases and the one that holds it is used from then on.
# Set DB_NAME explicitly to skip the detection.
set -u
BASE=${BASE:-http://127.0.0.1:8080}
PSQL_BIN=/usr/lib/postgresql/18/bin/psql
DB_URL_HOST=127.0.0.1
DB_PORT=5433
DB_USER=${DB_USER:-tojo}
DB_NAME=${DB_NAME:-}
DB_CANDIDATES=${DB_CANDIDATES:-"omsai omsai_demo"}
DB_PASS=${OMSAI_DB_PASSWORD:?set OMSAI_DB_PASSWORD - the password of the local PostgreSQL user}
ADMIN_EMAIL=${OMSAI_ADMIN_EMAIL:-admin@omsai.local}
ADMIN_PASS=${OMSAI_ADMIN_PASS:?set OMSAI_ADMIN_PASS - the password of the bootstrapped dev administrator}

TS=$(date +%Y%m%d%H%M%S)
WORK=/tmp/opencode/e2e
rm -rf "$WORK"; mkdir -p "$WORK"
cd "$WORK" || exit 1

FAILS=0
TOTAL=0

ck() { # ck <description> <condition-result>  (1 = pass, 0 = fail)
  TOTAL=$((TOTAL + 1))
  if [ "$2" = "1" ]; then
    printf '  OK: %s\n' "$1"
  else
    printf '  FAIL: %s\n' "$1"
    cp -f post.html "trace-$TOTAL.html" 2>/dev/null || true
    printf '%s\n' "$1" > "trace-$TOTAL.txt"
    FAILS=$((FAILS + 1))
  fi
}

has() { # has <text> <needle>
  printf '%s' "$1" | grep -qF -- "$2"
}

say() { printf '\033[1;34m== %s\033[0m\n' "$*"; }

dbq() {
  PGPASSWORD=$DB_PASS "$PSQL_BIN" -h "$DB_URL_HOST" -p "$DB_PORT" -U "$DB_USER" \
    -d "$DB_NAME" -t -A -X -v ON_ERROR_STOP=1 -c "$1"
}

# A query against a named database (used while the real one is unknown).
dbq_in() {
  PGPASSWORD=$DB_PASS "$PSQL_BIN" -h "$DB_URL_HOST" -p "$DB_PORT" -U "$DB_USER" \
    -d "$1" -t -A -X -v ON_ERROR_STOP=1 -c "$2"
}

# Determines which database the running application really uses: the
# record that was just created through it is looked up in the candidates.
# The script and the application must never look at different databases.
detect_db() {  # detect_db <marker-sql>
  local candidate
  if [ -n "$DB_NAME" ]; then
    return 0
  fi
  for candidate in ${=DB_CANDIDATES}; do
    if [ -n "$(dbq_in "$candidate" "$1" 2>/dev/null)" ]; then
      DB_NAME=$candidate
      say "the running application uses the database '$DB_NAME'"
      return 0
    fi
  done
  echo "Could not determine which database the running application uses." >&2
  echo "  Tried: $DB_CANDIDATES" >&2
  echo "  Set DB_NAME explicitly if the application uses another one." >&2
  exit 1
}

# page(): GET an authenticated page into page.html, print status code
page() {
  PAGERESULT=$(curl -s -b jar -c jar -o page.html -w '%{http_code}' "$BASE$1")
  printf '%s\n' "$PAGERESULT"
}

# go(): POST into post.html, print "code|redirect_url"
go() {
  curl -s -b jar -c jar -o post.html -w '%{http_code}|%{redirect_url}' "$@"
}

# goflash(): POST into post.html; follow the redirect and collect flash messages.
# Spring keeps pending flash maps per target path in a FIFO queue and serves
# them ONE PER REQUEST. The script often stacks several POSTs against the
# same page, so after the first GET the fresh message may still be stashed.
# Therefore: repeat GETs on the redirect target until the page shows no alert
# and append every seen alert message into post.html for later greps.
goflash() {
  local out post_url CODE_PART i=1
  out=$(go "$@")
  post_url=${out#*|}
  CODE_PART=${out%%|*}
  printf '%s' "$out"
  if [ -z "$post_url" ] || [ "$post_url" = "$out" ] || [ "$CODE_PART" = "200" ]; then
    return 0
  fi
  curl -s -b jar -c jar -o post.html "$post_url" >/dev/null
  while [ "$i" -le 8 ]; do
    if ! grep -q 'class="alert alert-' post.html; then
      return 0
    fi
    curl -s -b jar -c jar -o flashpage.html "$post_url" >/dev/null
    if ! grep -q 'class="alert alert-' flashpage.html; then
      return 0
    fi
    grep -oE 'me-1"></i><span>[^<]*' flashpage.html | sed 's/.*<span//;s/^>//' >> post.html
    i=$((i+1))
  done
}

# freshlogin(): perform a login on a fresh session, echo "code|redirect_url"
freshlogin() { # freshlogin <jarfile> <email> <password>
  local jarf=$1 user=$2 pass=$3 token out
  curl -s -c "$jarf" -o "$jarf.page" "$BASE/login"
  token=$(grep -o 'name="_csrf" value="[^"]*"' "$jarf.page" | head -1 | sed 's/.*value="//;s/"$//')
  out=$(curl -s -b "$jarf" -c "$jarf" -o "$jarf.resp" -w '%{http_code}|%{redirect_url}' \
    -d "username=$user" -d "password=$pass" --data-urlencode "_csrf=$token" "$BASE/login")
  printf '%s' "$out"
}

# tok(): extract session CSRF token from an authenticated page
tok() {
  curl -s -b jar -c jar -o tok.html "$BASE$1"
  grep -o 'name="_csrf" value="[^"]*"' tok.html | head -1 | sed 's/.*value="//;s/"$//'
}

# ---------------------------------------------------------------- wait for app
say "waiting for application on $BASE"
for i in {1..60}; do
  if curl -s -o /dev/null --max-time 2 "$BASE/login"; then
    break
  fi
  sleep 1
  if [ "$i" = 60 ]; then
    echo "Application did not come up on $BASE — aborting."
    exit 1
  fi
done

# ---------------------------------------------------------------- A: login
say "login and security basics"
out=$(freshlogin jar "$ADMIN_EMAIL" "$ADMIN_PASS")
CODE=${out%%|*}; LOC=${out#*|}
ck "admin login accepted (302)" "$([ "$CODE" = 302 ] && echo 1 || echo 0)"
T=$(tok /dashboard)
ck "csrf token available after login" "$([ -n "$T" ] && echo 1 || echo 0)"

# invalid credentials - a dedicated (non-existing) address, so the admin
# account is never counted towards the rate limit of a repeated run
out=$(freshlogin jar2 "e2e.wrong.$TS@omsai.local" "TotallyWrongPass1")
ck "bad password redirects to /login?error" "$(has "${out#*|}" "/login?error" && echo 1 || echo 0)"

# anonymous request
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/courses")
ck "anonymous /courses redirects to login (302)" "$([ "$code" = 302 ] && echo 1 || echo 0)"

# missing csrf token on authenticated post
code=$(curl -s -b jar -o infra_csrf.html -w '%{http_code}' --data-urlencode "name=x" "$BASE/courses")
ck "post without csrf token returns 403" "$([ "$code" = 403 ] && echo 1 || echo 0)"

# ---------------------------------------------------------------- B: masters
say "subjects, courses and validation"
post_url=$(go -d "_csrf=$T" --data-urlencode "name=E2E Subject $TS" \
  --data-urlencode "description=Smoke testing subject" "$BASE/subjects") || true
SUBJ_URL=${post_url#*|}
ck "subject created (redirect to detail)" "$(has "$SUBJ_URL" "/subjects/" && echo 1 || echo 0)"
SUBJID=${SUBJ_URL##*/}

# From here on the script and the application must read the same
# database: the subject that was just created through the application
# tells which one it is.
detect_db "select id from subjects where name = 'E2E Subject $TS'"

post_url=$(go -d "_csrf=$T" -d "description=" --data-urlencode "durationMonths=6" \
  --data-urlencode "category=E2E" --data-urlencode "name=E2E Course A $TS" "$BASE/courses") || true
C1_URL=${post_url#*|}
ck "course A created" "$(has "$C1_URL" "/courses/" && echo 1 || echo 0)"
C1=${C1_URL##*/}

post_url=$(go -d "_csrf=$T" -d "description=" --data-urlencode "durationMonths=6" \
  --data-urlencode "category=E2E" --data-urlencode "name=E2E Course B $TS" "$BASE/courses") || true
C2_URL=${post_url#*|}
ck "course B created" "$(has "$C2_URL" "/courses/" && echo 1 || echo 0)"
C2=${C2_URL##*/}

goflash -d "_csrf=$T" -d "subjectId=$SUBJID" "$BASE/courses/$C1/subjects" >/dev/null
ck "subject assigned to course A" "$(has "$(cat post.html)" "Subject assigned" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "subjectId=$SUBJID" "$BASE/courses/$C1/subjects" >/dev/null
ck "duplicate course-subject assignment rejected" "$(has "$(cat post.html)" "already assigned to this course" && echo 1 || echo 0)"

CSID=$(dbq "select id from course_subjects where course_id=$C1 and subject_id=$SUBJID")
ck "course_subject row persisted" "$([ -n "$CSID" ] && echo 1 || echo 0)"

# ---------------------------------------------------------------- C: batches
say "batches"
goflash -d "_csrf=$T" -d "courseId=$C1" -d "name=E2E Batch X $TS" \
  --data-urlencode "startDate=2026-09-01" --data-urlencode "endDate=2026-08-01" "$BASE/batches" >/dev/null
ck "batch end-before-start rejected" "$(has "$(cat post.html)" "End date cannot be before the start date" && echo 1 || echo 0)"

post_url=$(go -d "_csrf=$T" -d "courseId=$C1" --data-urlencode "academicYear=2026-27" \
  --data-urlencode "name=E2E Batch A1 $TS" --data-urlencode "startDate=2026-09-01" "$BASE/batches") || true
B1_URL=${post_url#*|}
ck "batch A1 created" "$(has "$B1_URL" "/batches/" && echo 1 || echo 0)"
B1=${B1_URL##*/}

post_url=$(go -d "_csrf=$T" -d "courseId=$C1" --data-urlencode "academicYear=2026-27" \
  --data-urlencode "name=E2E Batch A2 $TS" --data-urlencode "startDate=2026-09-01" "$BASE/batches") || true
B2_URL=${post_url#*|}
B2=${B2_URL##*/}

post_url=$(go -d "_csrf=$T" -d "courseId=$C2" --data-urlencode "academicYear=2026-27" \
  --data-urlencode "name=E2E Batch B1 $TS" --data-urlencode "startDate=2026-09-01" "$BASE/batches") || true
B3_URL=${post_url#*|}
B3=${B3_URL##*/}
ck "batch in course B created" "$([ -n "$B3" ] && echo 1 || echo 0)"

# batch subject mapping (must belong to batch's course)
goflash -d "_csrf=$T" -d "batchId=$B1" -d "courseSubjectId=$CSID" "$BASE/batches/$B1/subjects" >/dev/null
ck "course subject added to batch A1" "$(has "$(cat post.html)" "Subject added to batch" && echo 1 || echo 0)"

BSID=$(dbq "select id from batch_subjects where batch_id=$B1 limit 1")
ck "batch_subject row persisted" "$([ -n "$BSID" ] && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "batchId=$B1" -d "courseSubjectId=$CSID" "$BASE/batches/$B1/subjects" >/dev/null
ck "duplicate batch subject rejected" "$(has "$(cat post.html)" "already assigned to this batch" && echo 1 || echo 0)"
CNT=$(dbq "select count(*) from batch_subjects where batch_id=$B1 and course_subject_id=$CSID")
ck "no duplicate batch_subject row (count=1)" "$([ "$CNT" = 1 ] && echo 1 || echo 0)"

# subject belonging to the OTHER course cannot be attached to batch of course A
SUBJ2_URL=$(go -d "_csrf=$T" --data-urlencode "name=E2E Subject B $TS" "$BASE/subjects")
SUBJ2ID=$(dbq "select id from subjects where name='E2E Subject B $TS'")
goflash -d "_csrf=$T" -d "subjectId=$SUBJ2ID" "$BASE/courses/$C2/subjects" >/dev/null
CS2ID=$(dbq "select id from course_subjects where course_id=$C2 and subject_id=$SUBJ2ID")
ck "subject B assigned to course B" "$([ -n "$CS2ID" ] && echo 1 || echo 0)"
goflash -d "_csrf=$T" -d "batchId=$B1" -d "courseSubjectId=$CS2ID" "$BASE/batches/$B1/subjects" >/dev/null
ck "foreign-course subject blocked from batch" "$(has "$(cat post.html)" "cannot be assigned to a batch" && echo 1 || echo 0)"

# ---------------------------------------------------------------- D: students
say "students"
PH1="98${TS: -8}"
PH2="97${TS: -8}"
PHB="96${TS: -8}"
post_url=$(go -d "_csrf=$T" --data-urlencode "fullName=E2E Student One $TS" \
  --data-urlencode "phone=$PH1" --data-urlencode "admissionDate=2026-08-01" "$BASE/students") || true
S1_URL=${post_url#*|}
ck "student one registered" "$(has "$S1_URL" "/students/" && echo 1 || echo 0)"
S1=${S1_URL##*/}

post_url=$(go -d "_csrf=$T" --data-urlencode "fullName=E2E Student Two $TS" \
  --data-urlencode "phone=$PH2" --data-urlencode "admissionDate=2026-08-01" "$BASE/students") || true
S2=${post_url##*/}

CODE1=$(dbq "select student_code from students where id=$S1")
ck "unique student code generated" "$(has "$code:$CODE1" "OMS-2026-" && echo 1 || echo 0)"

szcode=$(page "/students/$S1")
ck "profile page shows generated code" "$(has "$(cat page.html)" "$CODE1" && echo 1 || echo 0)"
szcode=$(page "/students/$S1/edit")
ck "student edit form renders" "$([ "$szcode" = 200 ] && echo 1 || echo 0)"

goflash -d "_csrf=$T" --data-urlencode "fullName=Bad Phone Student" \
  --data-urlencode "phone=not-a-phone" --data-urlencode "admissionDate=2026-08-01" "$BASE/students" >/dev/null
ck "invalid phone shows validation message" "$(has "$(cat post.html)" "Enter a valid phone number" && echo 1 || echo 0)"

szq=$(page "/students?q=E2E Student One $TS")
ck "student search by name finds student" "$(has "$(cat page.html)" "E2E Student One $TS" && echo 1 || echo 0)"
szq=$(page "/students?q=$PH1")
ck "student search by phone finds student" "$(has "$(cat page.html)" "E2E Student One $TS" && echo 1 || echo 0)"

# ---------------------------------------------------------------- E: enroll
say "enrollments"
goflash -d "_csrf=$T" -d "batchId=$B1" --data-urlencode "enrollmentDate=2026-09-05" \
  "$BASE/students/$S1/enroll" >/dev/null
ck "student enrolled into batch A1" "$(has "$(cat post.html)" "Student enrolled." && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "batchId=$B1" --data-urlencode "enrollmentDate=2026-09-05" \
  "$BASE/students/$S1/enroll" >/dev/null
ck "duplicate enrollment in same batch rejected" "$(has "$(cat post.html)" "already has an active enrollment in that batch" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "batchId=$B2" --data-urlencode "enrollmentDate=2026-09-05" \
  "$BASE/students/$S1/enroll" >/dev/null
ck "same-course second batch suggests transfer" "$(has "$(cat post.html)" "Use a transfer to change batches" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "batchId=$B3" --data-urlencode "enrollmentDate=2026-09-06" \
  "$BASE/students/$S1/enroll" >/dev/null
ck "enroll into a different course allowed" "$(has "$(cat post.html)" "Student enrolled." && echo 1 || echo 0)"

ACTIVE=$(dbq "select count(*) from enrollments where student_id=$S1 and status='ACTIVE'")
ck "two active enrollments persisted (count=2)" "$([ "$ACTIVE" = 2 ] && echo 1 || echo 0)"

# transfer same course A1 -> A2
goflash -d "_csrf=$T" -d "targetBatchId=$B2" --data-urlencode "transferDate=2026-09-15" \
  "$BASE/students/$S1/transfer" >/dev/null
ck "transfer within course accepted" "$(has "$(cat post.html)" "history is preserved" && echo 1 || echo 0)"

STATS=$(dbq "select count(*) from enrollments where student_id=$S1 and status='COMPLETED'")
ck "previous enrollment closed as COMPLETED (count=1)" "$([ "$STATS" = 1 ] && echo 1 || echo 0)"
ACTIVE=$(dbq "select count(*) from enrollments where student_id=$S1 and status='ACTIVE'")
ck "active enrollments after transfer (count=2)" "$([ "$ACTIVE" = 2 ] && echo 1 || echo 0)"
BA=$(dbq "select count(*) from enrollments where student_id=$S1 and batch_id=$B2 and status='ACTIVE'")
ck "new ACTIVE enrollment points at batch A2" "$([ "$BA" = 1 ] && echo 1 || echo 0)"

# transfer when already active in target batch
goflash -d "_csrf=$T" -d "targetBatchId=$B2" --data-urlencode "transferDate=2026-09-16" \
  "$BASE/students/$S1/transfer" >/dev/null
ck "transfer to current batch rejected" "$(has "$(cat post.html)" "already active in that batch" && echo 1 || echo 0)"

# transfer when not enrolled in the target course
goflash -d "_csrf=$T" -d "batchId=$B1" --data-urlencode "enrollmentDate=2026-09-05" \
  "$BASE/students/$S2/enroll" >/dev/null
goflash -d "_csrf=$T" -d "targetBatchId=$B3" --data-urlencode "transferDate=2026-09-16" \
  "$BASE/students/$S2/transfer" >/dev/null
ck "transfer to other course suggests new enrollment" "$(has "$(cat post.html)" "Use a new enrollment instead of a transfer" && echo 1 || echo 0)"

# ---------------------------------------------------------------- F: statuses
say "enrollment status changes"
EID=$(dbq "select id from enrollments where student_id=$S1 and batch_id=$B3 and status='ACTIVE'")
goflash -d "_csrf=$T" -d "studentId=$S1" -d "action=withdraw" \
  "$BASE/students/enrollments/$EID/status" >/dev/null
ck "withdraw accepted" "$(has "$(cat post.html)" "Enrollment withdrawn." && echo 1 || echo 0)"
ST=$(dbq "select status from enrollments where id=$EID")
ck "status stored as WITHDRAWN" "$([ "$ST" = "WITHDRAWN" ] && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "studentId=$S1" -d "action=complete" \
  "$BASE/students/enrollments/$EID/status" >/dev/null
ck "closing a closed enrollment rejected" "$(has "$(cat post.html)" "Only ACTIVE enrollments can be updated" && echo 1 || echo 0)"

# ---------------------------------------------------------------- G: teachers
say "teachers"
post_url=$(go -d "_csrf=$T" --data-urlencode "fullName=E2E Teacher $TS" \
  --data-urlencode "phone=$PHB" "$BASE/teachers") || true
TH_URL=${post_url#*|}
ck "teacher profile created" "$(has "$TH_URL" "/teachers/" && echo 1 || echo 0)"
THID=${TH_URL##*/}

goflash -d "_csrf=$T" -d "batchId=$B1" -d "teacherId=$THID" \
  "$BASE/batches/subjects/$BSID/teacher" >/dev/null
ck "teacher assigned to batch subject" "$(has "$(cat post.html)" "Teacher assigned." && echo 1 || echo 0)"
HA=$(dbq "select count(*) from batch_subjects where id=$BSID and teacher_id=$THID")
ck "teacher assignment persisted" "$([ "$HA" = 1 ] && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "batchId=$B1" "$BASE/batches/subjects/$BSID/teacher" >/dev/null
ck "teacher assignment can be cleared" "$(has "$(cat post.html)" "Teacher assignment cleared." && echo 1 || echo 0)"
HA=$(dbq "select count(*) from batch_subjects where id=$BSID and teacher_id is null")
ck "assignment cleared in database" "$([ "$HA" = 1 ] && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "active=false" "$BASE/teachers/$THID/status" >/dev/null
goflash -d "_csrf=$T" -d "batchId=$B1" -d "teacherId=$THID" \
  "$BASE/batches/subjects/$BSID/teacher" >/dev/null
ck "inactive teacher cannot receive assignments" "$(has "$(cat post.html)" "cannot receive new assignments" && echo 1 || echo 0)"
goflash -d "_csrf=$T" -d "active=true" "$BASE/teachers/$THID/status" >/dev/null
goflash -d "_csrf=$T" -d "batchId=$B1" -d "teacherId=$THID" \
  "$BASE/batches/subjects/$BSID/teacher" >/dev/null
ck "reactivated teacher gets assignment" "$(has "$(cat post.html)" "Teacher assigned." && echo 1 || echo 0)"

# ---------------------------------------------------------------- H: removals
say "referential-safety of removals"
goflash -d "_csrf=$T" "$BASE/courses/$C1/subjects/$CSID/remove" >/dev/null
ck "course subject removal blocked while taught" "$(has "$(cat post.html)" "cannot be removed" && echo 1 || echo 0)"
CSROW=$(dbq "select count(*) from course_subjects where id=$CSID")
ck "course subject row not deleted (count=1)" "$([ "$CSROW" = 1 ] && echo 1 || echo 0)"

# ---------------------------------------------------------------- I: inactive student
say "inactive student restrictions"
goflash -d "_csrf=$T" -d "active=false" "$BASE/students/$S1/status" >/dev/null
goflash -d "_csrf=$T" -d "batchId=$B3" --data-urlencode "enrollmentDate=2026-09-20" \
  "$BASE/students/$S1/enroll" >/dev/null
ck "inactive student cannot enroll" "$(has "$(cat post.html)" "is inactive and cannot be enrolled" && echo 1 || echo 0)"
goflash -d "_csrf=$T" -d "active=true" "$BASE/students/$S1/status" >/dev/null

# ---------------------------------------------------------------- J: users
say "user accounts"
TEACHER_EMAIL="e2e.teacher.$TS@omsai.local"
post_url=$(go -d "_csrf=$T" --data-urlencode "fullName=E2E Teacher Account $TS" \
  --data-urlencode "email=$TEACHER_EMAIL" -d "role=TEACHER" \
  --data-urlencode "password=E2ETeacher#2026#Pass" "$BASE/users") || true
ck "teacher account created via admin" "$(has "$post_url" "302" && echo 1 || echo 0)"
USERID=$(dbq "select id from user_accounts where email='$TEACHER_EMAIL'")
ck "user persisted in database" "$([ -n "$USERID" ] && echo 1 || echo 0)"

ucode=$(page "/users")
ck "users list renders and shows account" "$(has "$(cat page.html)" "$TEACHER_EMAIL" && echo 1 || echo 0)"

# link the account to the teacher profile
goflash -d "_csrf=$T" --data-urlencode "fullName=E2E Teacher Linked $TS" \
  --data-urlencode "phone=$PHB" -d "userId=$USERID" "$BASE/teachers" >/dev/null
ck "teacher profile linked to account" "$(has "$(cat post.html)" "Teacher profile created." && echo 1 || echo 0)"

# ---------------------------------------------------------------- K: teacher role
say "teacher role access (read-only)"
out=$(freshlogin tjar "$TEACHER_EMAIL" "E2ETeacher#2026#Pass")
ck "teacher login accepted" "$([ "${out%%|*}" = 302 ] && echo 1 || echo 0)"
TROOT=$(curl -s -b tjar -o /dev/null -w '%{redirect_url}' "$BASE/")
ck "teacher home redirects to /teacher" "$(has "$TROOT" "/teacher" && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/teacher")
ck "teacher home renders (200)" "$([ "$tcode" = 200 ] && echo 1 || echo 0)"
ck "assignments table present for teacher" "$(has "$(cat tpage.html)" "Subjects and batches you teach" && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/courses")
ck "teacher blocked from /courses (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/users")
ck "teacher blocked from /users (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' -d "name=hack" "$BASE/courses")
ck "teacher blocked from POST /courses (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"

# ================================================================ K2: fee agreements
say "fee agreements"
E2=$(dbq "select id from enrollments where student_id=$S2 and batch_id=$B1 and status='ACTIVE'")
ck "smoke student has an active enrollment for fees" "$([ -n "$E2" ] && echo 1 || echo 0)"

post_url=$(go -d "_csrf=$T" -d "enrollmentId=$E2" -d "grossFee=24000" -d "discountAmount=4000" \
  --data-urlencode "academicYear=2026-27" --data-urlencode "dueDate=2026-12-15" \
  --data-urlencode "notes=E2E fee agreement $TS" "$BASE/fees") || true
FA_URL=${post_url#*|}
ck "fee agreement created for the enrollment" "$(has "$FA_URL" "/fees/" && echo 1 || echo 0)"
FA=$(dbq "select id from fee_accounts where enrollment_id=$E2")
ck "fee agreement persisted in database" "$([ -n "$FA" ] && echo 1 || echo 0)"

fcode=$(page "/fees/$FA")
ck "fee agreement page shows outstanding 20000.00" \
  "$([ "$fcode" = 200 ] && has "$(cat page.html)" "20000.00" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "enrollmentId=$E2" -d "grossFee=25000" "$BASE/fees" >/dev/null
ck "duplicate fee agreement rejected" "$(has "$(cat post.html)" "already has a fee agreement for" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "enrollmentId=$E2" -d "grossFee=24000" \
  --data-urlencode "discountAmount=30000" --data-urlencode "academicYear=2027-28" "$BASE/fees" >/dev/null
ck "discount above gross fee rejected" "$(has "$(cat post.html)" "Discount cannot exceed the gross fee" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "enrollmentId=$E2" -d "grossFee=24000" \
  --data-urlencode "academicYear=2026/27" "$BASE/fees" >/dev/null
ck "invalid academic year rejected" "$(has "$(cat post.html)" "Academic year must use the format" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "enrollmentId=$EID" -d "grossFee=24000" "$BASE/fees" >/dev/null
ck "fee agreement for a closed enrollment rejected" \
  "$(has "$(cat post.html)" "can only be created for an ACTIVE enrollment" && echo 1 || echo 0)"

# ================================================================ K3: payments
say "payments"
pcode=$(page "/payments/new?feeAccountId=$FA")
ck "record payment form renders for the agreement" "$([ "$pcode" = 200 ] && echo 1 || echo 0)"

post_url=$(go -d "_csrf=$T" -d "feeAccountId=$FA" -d "amount=8000" \
  --data-urlencode "paymentDate=2026-09-20" -d "paymentMethod=CASH" \
  --data-urlencode "remarks=E2E first installment" "$BASE/payments") || true
PID_URL=${post_url#*|}
ck "payment recorded, receipt page opens" "$(has "$PID_URL" "/receipt" && echo 1 || echo 0)"
PID=$(dbq "select id from payments where fee_account_id=$FA order by id desc limit 1")
RECEIPT=$(dbq "select receipt_number from payments where id=$PID")
ck "receipt number uses the OMS-RCP-2026-###### sequence" \
  "$(printf '%s' "$RECEIPT" | grep -qE '^OMS-RCP-2026-[0-9]{6}$' && echo 1 || echo 0)"
PSTATUS=$(dbq "select status from payments where id=$PID")
ck "payment stored as RECEIVED" "$([ "$PSTATUS" = "RECEIVED" ] && echo 1 || echo 0)"

rcode=$(page "/payments/$PID/receipt")
ck "receipt page shows the receipt number" \
  "$([ "$rcode" = 200 ] && has "$(cat page.html)" "$RECEIPT" && echo 1 || echo 0)"

fcode=$(page "/fees/$FA")
ck "outstanding recalculated after payment (12000.00)" \
  "$([ "$fcode" = 200 ] && has "$(cat page.html)" "12000.00" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "feeAccountId=$FA" -d "amount=999999" \
  --data-urlencode "paymentDate=2026-09-21" -d "paymentMethod=CASH" "$BASE/payments" >/dev/null
ck "overpayment rejected with the computed balance" \
  "$(has "$(cat post.html)" "Payment exceeds the outstanding balance" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "grossFee=30000" -d "discountAmount=4000" "$BASE/fees/$FA" >/dev/null
ck "fee amounts locked once payments exist" "$(has "$(cat post.html)" "can no longer be changed" && echo 1 || echo 0)"

# voiding needs a reason
goflash -d "_csrf=$T" --data-urlencode "reason= " "$BASE/payments/$PID/void" >/dev/null
VSTATUS0=$(dbq "select status from payments where id=$PID")
ck "voiding without a reason rejected" \
  "$(has "$(cat post.html)" "A reason is required to void a payment." && echo 1 || echo 0)"
ck "void without reason left the payment RECEIVED" "$([ "$VSTATUS0" = "RECEIVED" ] && echo 1 || echo 0)"

goflash -d "_csrf=$T" --data-urlencode "reason=E2E void test $TS" "$BASE/payments/$PID/void" >/dev/null
ck "voided with recorded reason" "$(has "$(cat post.html)" "voided. The outstanding balance has been recalculated." && echo 1 || echo 0)"
VSTATUS=$(dbq "select status from payments where id=$PID")
ck "payment stored as VOIDED" "$([ "$VSTATUS" = "VOIDED" ] && echo 1 || echo 0)"

rcode=$(page "/payments/$PID/receipt")
ck "receipt page shows the VOIDED stamp" "$([ "$rcode" = 200 ] && has "$(cat page.html)" "VOIDED" && echo 1 || echo 0)"

goflash -d "_csrf=$T" --data-urlencode "reason=second void attempt" "$BASE/payments/$PID/void" >/dev/null
ck "duplicate void rejected" "$(has "$(cat post.html)" "This payment has already been voided" && echo 1 || echo 0)"

fcode=$(page "/fees/$FA")
ck "outstanding restored after void (20000.00)" \
  "$([ "$fcode" = 200 ] && has "$(cat page.html)" "20000.00" && echo 1 || echo 0)"

# a fresh receipt after the void must still be strictly ahead of the first one
post_url=$(go -d "_csrf=$T" -d "feeAccountId=$FA" -d "amount=5000" \
  --data-urlencode "paymentDate=2026-09-25" -d "paymentMethod=UPI" "$BASE/payments") || true
PID2=$(dbq "select id from payments where fee_account_id=$FA order by id desc limit 1")
RECEIPT2=$(dbq "select receipt_number from payments where id=$PID2")
ck "second receipt number strictly increases (sequence, not row count)" \
  "$(printf '%s\n' "$RECEIPT" "$RECEIPT2" | sort -c 2>/dev/null && [ "$RECEIPT2" != "$RECEIPT" ] && echo 1 || echo 0)"

# ================================================================ M2: class sessions and attendance
say "class sessions and attendance"
post_url=$(go -d "_csrf=$T" -d "batchSubjectId=$BSID" --data-urlencode "sessionDate=2026-09-18" \
  -d "startTime=10:00" -d "endTime=11:00" --data-urlencode "topic=E2E session topic" "$BASE/sessions") || true
SID_URL=${post_url#*|}
ck "class session created" "$(has "$SID_URL" "/sessions/" && echo 1 || echo 0)"
SID=${SID_URL##*/}
SSTATUS=$(dbq "select status from class_sessions where id=$SID")
ck "new session stored as OPEN" "$([ "$SSTATUS" = "OPEN" ] && echo 1 || echo 0)"

acode=$(page "/sessions/$SID/attendance")
S2CODE=$(dbq "select student_code from students where id=$S2")
ck "attendance roster shows the enrolled student" \
  "$([ "$acode" = 200 ] && has "$(cat page.html)" "$S2CODE" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "status_$E2=PRESENT" --data-urlencode "remark_$E2=On time" \
  "$BASE/sessions/$SID/attendance" >/dev/null
ck "attendance saved for 1 student" "$(has "$(cat post.html)" "Attendance saved for 1 student(s)." && echo 1 || echo 0)"
ASTAT=$(dbq "select status from attendance where class_session_id=$SID and enrollment_id=$E2")
ck "attendance row stored as PRESENT" "$([ "$ASTAT" = "PRESENT" ] && echo 1 || echo 0)"

goflash -d "_csrf=$T" "$BASE/sessions/$SID/cancel" >/dev/null
ck "session with attendance cannot be cancelled" \
  "$(has "$(cat post.html)" "cannot be cancelled. Complete it instead." && echo 1 || echo 0)"

goflash -d "_csrf=$T" "$BASE/sessions/$SID/complete" >/dev/null
ck "session completed" "$(has "$(cat post.html)" "Session completed." && echo 1 || echo 0)"
SSTATUS=$(dbq "select status from class_sessions where id=$SID")
ck "session stored as COMPLETED" "$([ "$SSTATUS" = "COMPLETED" ] && echo 1 || echo 0)"
CROWS=$(dbq "select count(*) from class_sessions where id=$SID")
ck "completed session row still exists (never deleted)" "$([ "$CROWS" = 1 ] && echo 1 || echo 0)"

# edit of a completed session is rejected
goflash -d "_csrf=$T" -d "batchSubjectId=$BSID" --data-urlencode "sessionDate=2026-09-19" \
  -d "startTime=10:00" -d "endTime=11:00" "$BASE/sessions/$SID" >/dev/null
ck "completed session cannot be edited" "$(has "$(cat post.html)" "Only OPEN sessions can be edited" && echo 1 || echo 0)"

# end time before start time rejected on create (validation renders the form again)
ecode=$(go -d "_csrf=$T" -d "batchSubjectId=$BSID" --data-urlencode "sessionDate=2026-09-20" \
  -d "startTime=11:00" -d "endTime=10:00" "$BASE/sessions")
ck "end time before start time rejected" \
  "$(has "${ecode%%|*}" "200" && has "$(cat post.html)" "End time must be after the start time" && echo 1 || echo 0)"

# a clean session can be cancelled, and then rejects attendance
post_url=$(go -d "_csrf=$T" -d "batchSubjectId=$BSID" --data-urlencode "sessionDate=2026-09-21" \
  -d "startTime=11:00" -d "endTime=12:00" "$BASE/sessions") || true
SID2=${post_url##*/}
goflash -d "_csrf=$T" "$BASE/sessions/$SID2/cancel" >/dev/null
ck "open session cancelled with record preserved" "$(has "$(cat post.html)" "Session cancelled." && echo 1 || echo 0)"
SSTATUS2=$(dbq "select status from class_sessions where id=$SID2")
ck "cancelled session stored as CANCELLED" "$([ "$SSTATUS2" = "CANCELLED" ] && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "status_$E2=PRESENT" "$BASE/sessions/$SID2/attendance" >/dev/null
ck "attendance rejected for a cancelled session" \
  "$(has "$(cat post.html)" "cancelled session" && echo 1 || echo 0)"

# ================================================================ M3: attendance reports
say "attendance reports"
rcode=$(page "/attendance/reports/batch?batchId=$B1&from=2026-09-01&to=2026-10-31")
ck "batch report renders (200)" "$([ "$rcode" = 200 ] && echo 1 || echo 0)"
ck "batch report lists the student with attendance" "$(has "$(cat page.html)" "E2E Student Two" && echo 1 || echo 0)"
ck "batch report shows the recorded percentage" "$(has "$(cat page.html)" "100" && echo 1 || echo 0)"

dcode=$(page "/attendance/reports/date?date=2026-09-18")
ck "date report lists the held session" \
  "$([ "$dcode" = 200 ] && has "$(cat page.html)" "E2E Batch A1" && echo 1 || echo 0)"
ck "date report marks the completed session" \
  "$([ "$dcode" = 200 ] && has "$(cat page.html)" "Completed" && echo 1 || echo 0)"

scode=$(page "/attendance/reports/student/$S2")
ck "student report shows the enrollment with counts" \
  "$([ "$scode" = 200 ] && has "$(cat page.html)" "E2E Student Two" && echo 1 || echo 0)"

htcode=$(page "/attendance/reports")
ck "report hub renders" "$([ "$htcode" = 200 ] && echo 1 || echo 0)"

# ================================================================ M4: CSV exports and printable sheets
say "attendance CSV exports and printable attendance sheet"
BSCSID=$(dbq "select course_subject_id from batch_subjects where id=$BSID")

page "/attendance/reports/batch?batchId=$B1&from=2026-09-01&to=2026-10-31" >/dev/null
ck "batch report offers the CSV download" "$(has "$(cat page.html)" "Download CSV" && echo 1 || echo 0)"

# the batch report download: real file, safe name, real columns
dcode=$(curl -s -b jar -c jar -D hdr.txt -o export.csv -w '%{http_code}|%{content_type}' \
  "$BASE/attendance/reports/export/batch?batchId=$B1&from=2026-09-01&to=2026-10-31")
ck "batch report CSV downloads (200)" "$([ "${dcode%%|*}" = 200 ] && echo 1 || echo 0)"
ck "the download is served as UTF-8 CSV" \
  "$(has "${dcode#*|}" "text/csv" && has "${dcode#*|}" "UTF-8" && echo 1 || echo 0)"
ck "the download is an attachment with a safe file name" \
  "$(has "$(cat hdr.txt)" 'attachment; filename="attendance-batch-2026-09-01-to-2026-10-31.csv"' && echo 1 || echo 0)"
ck "the CSV starts with the report header" \
  "$(head -1 export.csv | grep -q '^Student code,Student name,Course,Batch,Subject,Academic year,' && echo 1 || echo 0)"
ck "the CSV contains the student's record" "$(has "$(cat export.csv)" "$S2CODE" && echo 1 || echo 0)"
ck "the CSV carries no fee information" \
  "$(has "$(cat export.csv)" "Outstanding balance" && echo 0 || echo 1)"

# the date report download: one line per recorded mark
dcode=$(curl -s -b jar -c jar -D hdr.txt -o export.csv -w '%{http_code}|%{content_type}' \
  "$BASE/attendance/reports/export/date?date=2026-09-18")
ck "date report CSV downloads (200)" "$([ "${dcode%%|*}" = 200 ] && echo 1 || echo 0)"
ck "the date CSV has the per-student header" \
  "$(head -1 export.csv | grep -q '^Class date,Batch,Subject,Teacher,Student code,Student name,Attendance status,Remarks' && echo 1 || echo 0)"
ck "the date CSV lists the recorded mark" \
  "$(has "$(cat export.csv)" "PRESENT" && has "$(cat export.csv)" "$S2CODE" && echo 1 || echo 0)"

# the student report download
dcode=$(curl -s -b jar -c jar -D hdr.txt -o export.csv -w '%{http_code}|%{content_type}' \
  "$BASE/attendance/reports/export/student/$S2")
ck "student report CSV downloads (200)" "$([ "${dcode%%|*}" = 200 ] && echo 1 || echo 0)"
ck "the student CSV has the enrollment history header" \
  "$(head -1 export.csv | grep -q '^Student code,Student name,Course,Batch,Enrolled from,Enrolled to,' && echo 1 || echo 0)"

# the subject report download
dcode=$(curl -s -b jar -c jar -D hdr.txt -o export.csv -w '%{http_code}|%{content_type}' \
  "$BASE/attendance/reports/export/subject?batchId=$B1&courseSubjectId=$BSCSID")
ck "subject report CSV downloads (200)" "$([ "${dcode%%|*}" = 200 ] && echo 1 || echo 0)"
ck "the subject CSV names the subject" \
  "$(head -1 export.csv | grep -q '^Student code,Student name,Course,Batch,Subject,Academic year,' && echo 1 || echo 0)"

# a download without its filter is refused
dcode=$(curl -s -b jar -c jar -o export.txt -w '%{http_code}' "$BASE/attendance/reports/export/batch")
ck "a download without a batch is refused (400)" "$([ "$dcode" = 400 ] && echo 1 || echo 0)"

# the printable sheet of a class
pcode=$(page "/sessions/$SID/print")
ck "printable attendance sheet renders (200)" "$([ "$pcode" = 200 ] && echo 1 || echo 0)"
ck "the sheet carries the institute heading" "$(has "$(cat page.html)" "Om Sai Coaching" && echo 1 || echo 0)"
ck "the sheet lists the roster student" "$(has "$(cat page.html)" "$S2CODE" && echo 1 || echo 0)"
ck "the sheet links the print stylesheet" "$(has "$(cat page.html)" "/css/attendance-sheet.css" && echo 1 || echo 0)"
ck "the sheet carries no fee information" \
  "$(has "$(cat page.html)" "Outstanding balance" && echo 0 || echo 1)"
pcode=$(page "/sessions/$SID")
ck "the session page offers the printable sheet" \
  "$(has "$(cat page.html)" "Print attendance sheet" && echo 1 || echo 0)"

# ================================================================ K4: teacher session ownership
say "teacher session ownership"
TH2ID=$(dbq "select id from teachers where user_id=$USERID")
goflash -d "_csrf=$T" -d "batchId=$B1" -d "teacherId=$TH2ID" "$BASE/batches/subjects/$BSID/teacher" >/dev/null
ck "linked teacher assigned to the batch subject" "$(has "$(cat post.html)" "Teacher assigned." && echo 1 || echo 0)"

ttcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/sessions")
ck "teacher can open the sessions list (200)" "$([ "$ttcode" = 200 ] && echo 1 || echo 0)"

tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/fees")
ck "teacher blocked from /fees (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/payments")
ck "teacher blocked from /payments (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/attendance/reports")
ck "teacher blocked from /attendance/reports (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/attendance/reports/export/batch?batchId=$B1")
ck "teacher blocked from the batch report CSV (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/attendance/reports/export/date?date=2026-09-18")
ck "teacher blocked from the date report CSV (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/attendance/reports/export/student/$S2")
ck "teacher blocked from the student report CSV (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"

tcsrf=$(curl -s -b tjar -o tpage.html "$BASE/sessions/new"
  grep -o 'name="_csrf" value="[^"]*"' tpage.html | head -1 | sed 's/.*value="//;s/"$//')
out=$(curl -s -b tjar -c tjar -o tpost.html -w '%{http_code}|%{redirect_url}' \
  -d "batchSubjectId=$BSID" --data-urlencode "sessionDate=2026-09-22" \
  -d "startTime=14:00" -d "endTime=15:00" \
  --data-urlencode "_csrf=$tcsrf" "$BASE/sessions")
ck "teacher creates a session for their own subject" "$(has "$out" "/sessions/" && echo 1 || echo 0)"
TSID=${out#*|}; TSID=${TSID##*/}
TSSTATUS=$(dbq "select status from class_sessions where id=$TSID")
ck "teacher session stored as OPEN" "$([ "$TSSTATUS" = "OPEN" ] && echo 1 || echo 0)"

out=$(curl -s -b tjar -c tjar -o tpost.html -w '%{http_code}|%{redirect_url}' \
  -d "status_$E2=PRESENT" --data-urlencode "remark_$E2=Via teacher" \
  --data-urlencode "_csrf=$tcsrf" "$BASE/sessions/$TSID/attendance")
ck "teacher records attendance on own session" "$(has "${out#*|}" "/sessions/$TSID" && echo 1 || echo 0)"
TB=$(dbq "select status from attendance where class_session_id=$TSID and enrollment_id=$E2")
ck "teacher attendance stored" "$([ "$TB" = "PRESENT" ] && echo 1 || echo 0)"

# teacher cannot correct attendance of a COMPLETED session
out=$(curl -s -b tjar -c tjar -o tpost.html -w '%{http_code}|%{redirect_url}' \
  -d "status_$E2=ABSENT" --data-urlencode "_csrf=$tcsrf" "$BASE/sessions/$SID/attendance")
curl -s -b tjar -o tpage.html "${out#*|}"
ck "teacher blocked from correcting a completed session" \
  "$(has "$(cat tpage.html)" "can only be corrected by an administrator" && echo 1 || echo 0)"

# teacher cannot create a session for ANOTHER teacher's subject
CS2=$(dbq "select id from course_subjects where course_id=$C2 and subject_id=$SUBJ2ID")
BS3=$(dbq "select id from batch_subjects where batch_id=$B3 and course_subject_id=$CS2 limit 1")
if [ -z "$BS3" ]; then
  curl -s -b jar -o /dev/null "$BASE/batches/$B3/subjects"
  go -d "_csrf=$T" -d "batchId=$B3" -d "courseSubjectId=$CS2ID" "$BASE/batches/$B3/subjects" >/dev/null
  BS3=$(dbq "select id from batch_subjects where batch_id=$B3 and course_subject_id=$CS2 limit 1")
fi
out=$(curl -s -b tjar -c tjar -o tpost.html -w '%{http_code}' \
  -d "batchSubjectId=$BS3" --data-urlencode "sessionDate=2026-09-23" \
  -d "startTime=10:00" -d "endTime=11:00" \
  --data-urlencode "_csrf=$tcsrf" "$BASE/sessions")
ck "teacher blocked from another teacher's subject (403)" "$([ "$out" = 403 ] && echo 1 || echo 0)"

# an administrator creates a class on a subject this teacher does not own
out=$(curl -s -b jar -c jar -o post.html -w '%{http_code}|%{redirect_url}' \
  -d "_csrf=$T" -d "batchSubjectId=$BS3" --data-urlencode "sessionDate=2026-09-24" \
  -d "startTime=10:00" -d "endTime=11:00" "$BASE/sessions")
ASID=${out#*|}; ASID=${ASID##*/}
ck "administrator creates a class on the other teacher's subject" \
  "$(has "${out#*|}" "/sessions/" && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/sessions/$ASID/print")
ck "teacher blocked from another teacher's printable sheet (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/sessions/$SID/print")
ck "teacher may print the sheet of their own subject (200)" "$([ "$tcode" = 200 ] && echo 1 || echo 0)"

# ================================================================ K5: installment plans
say "installment plans and allocation"
icode=$(page "/fees/$FA/installments")
ck "installment plan form renders" "$([ "$icode" = 200 ] && has "$(cat page.html)" "Installment plan" && echo 1 || echo 0)"

# a schedule that does not add up to the net fee is rejected
goflash -d "_csrf=$T" -d "amount=10000" -d "dueDate=2026-10-01" -d "installmentNumber=1" \
  -d "amount=5000" -d "dueDate=2026-11-01" -d "installmentNumber=2" \
  "$BASE/fees/$FA/installments" >/dev/null
ck "schedule that does not add up to the net fee rejected" \
  "$(has "$(cat post.html)" "must add up to exactly the net fee" && echo 1 || echo 0)"
ck "rejected schedule left no installment behind" \
  "$([ "$(dbq "select count(*) from fee_installments where fee_account_id=$FA")" = 0 ] && echo 1 || echo 0)"

# the valid schedule: two installments of 10000 on a net fee of 20000
goflash -d "_csrf=$T" -d "amount=10000" -d "dueDate=2026-10-01" -d "installmentNumber=1" \
  -d "amount=10000" -d "dueDate=2026-11-01" -d "installmentNumber=2" \
  "$BASE/fees/$FA/installments" >/dev/null
ck "installment plan created" "$(has "$(cat post.html)" "Installment plan created." && echo 1 || echo 0)"
ck "two installments stored" \
  "$([ "$(dbq "select count(*) from fee_installments where fee_account_id=$FA")" = 2 ] && echo 1 || echo 0)"

# the payment that was received before the plan is allocated to it
ck "existing payment allocated to the first installment" \
  "$([ "$(dbq "select coalesce(sum(pa.amount),0) from payment_allocations pa
        join payments p on p.id = pa.payment_id
        where pa.installment_id = (select id from fee_installments where fee_account_id=$FA and installment_number=1)
          and p.status = 'RECEIVED'")" = 5000.00 ] && echo 1 || echo 0)"
ck "the plan did not change the agreed net fee" \
  "$([ "$(dbq "select gross_fee - discount_amount from fee_accounts where id=$FA")" = 20000.00 ] && echo 1 || echo 0)"

fcode=$(page "/fees/$FA")
ck "fee account shows the installment schedule" \
  "$([ "$fcode" = 200 ] && has "$(cat page.html)" "Installment plan" && echo 1 || echo 0)"

# a new payment is allocated oldest due date first
post_url=$(go -d "_csrf=$T" -d "feeAccountId=$FA" -d "amount=7000" \
  --data-urlencode "paymentDate=2026-09-28" -d "paymentMethod=CASH" \
  --data-urlencode "remarks=E2E installment payment $TS" "$BASE/payments") || true
PID3=$(dbq "select id from payments where fee_account_id=$FA order by id desc limit 1")
ck "payment recorded against the planned agreement" \
  "$([ -n "$PID3" ] && [ "$(dbq "select status from payments where id=$PID3")" = RECEIVED ] && echo 1 || echo 0)"
ck "the new payment fills the first installment (10000)" \
  "$([ "$(dbq "select coalesce(sum(pa.amount),0) from payment_allocations pa
        join payments p on p.id = pa.payment_id
        where pa.installment_id = (select id from fee_installments where fee_account_id=$FA and installment_number=1)
          and p.status = 'RECEIVED'")" = 10000.00 ] && echo 1 || echo 0)"
ck "the rest of the payment goes to the second installment (2000)" \
  "$([ "$(dbq "select coalesce(sum(pa.amount),0) from payment_allocations pa
        join payments p on p.id = pa.payment_id
        where pa.installment_id = (select id from fee_installments where fee_account_id=$FA and installment_number=2)
          and p.status = 'RECEIVED'")" = 2000.00 ] && echo 1 || echo 0)"
ck "the installment balances reconcile with the agreement (8000.00)" \
  "$([ "$(dbq "select coalesce(sum(fi.amount - coalesce(pa.paid,0)),0) from fee_installments fi
        left join (select pa.installment_id, sum(pa.amount) as paid from payment_allocations pa
                   join payments p on p.id = pa.payment_id where p.status = 'RECEIVED'
                   group by pa.installment_id) pa on pa.installment_id = fi.id
        where fi.fee_account_id = $FA")" = 8000.00 ] && echo 1 || echo 0)"

# the reports
ocode=$(page "/installments")
ck "installment overview renders" "$([ "$ocode" = 200 ] && has "$(cat page.html)" "Total outstanding" && echo 1 || echo 0)"
ucode=$(page "/installments/upcoming")
ck "upcoming installments report renders" \
  "$([ "$ucode" = 200 ] && has "$(cat page.html)" "E2E Student Two" && echo 1 || echo 0)"
dcode=$(page "/installments/overdue")
ck "overdue installments report renders" "$([ "$dcode" = 200 ] && echo 1 || echo 0)"

# voiding an allocated payment returns the balance
goflash -d "_csrf=$T" --data-urlencode "reason=E2E installment void $TS" "$BASE/payments/$PID3/void" >/dev/null
ck "voided payment reported" "$(has "$(cat post.html)" "voided. The outstanding balance has been recalculated." && echo 1 || echo 0)"
ck "the voided payment stops counting towards the installments (5000)" \
  "$([ "$(dbq "select coalesce(sum(pa.amount),0) from payment_allocations pa
        join payments p on p.id = pa.payment_id
        where pa.installment_id = (select id from fee_installments where fee_account_id=$FA and installment_number=1)
          and p.status = 'RECEIVED'")" = 5000.00 ] && echo 1 || echo 0)"
ck "the allocations of the voided payment are kept for the audit trail" \
  "$([ "$(dbq "select count(*) from payment_allocations pa join payments p on p.id = pa.payment_id
        where pa.payment_id = $PID3 and p.status = 'VOIDED'")" -ge 1 ] && echo 1 || echo 0)"

# teachers are not allowed anywhere near the installment module
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/installments")
ck "teacher blocked from the installment overview (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/installments/upcoming")
ck "teacher blocked from the upcoming report (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/installments/overdue")
ck "teacher blocked from the overdue report (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcode=$(curl -s -b tjar -o tpage.html -w '%{http_code}' "$BASE/fees/$FA/installments")
ck "teacher blocked from the plan form (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"
tcsrf2=$(curl -s -b tjar -o tpage.html "$BASE/sessions/new"
  grep -o 'name="_csrf" value="[^"]*"' tpage.html | head -1 | sed 's/.*value="//;s/"$//')
tcode=$(curl -s -b tjar -c tjar -o tpost.html -w '%{http_code}' \
  -d "amount=10000" -d "dueDate=2026-10-01" -d "installmentNumber=1" \
  -d "amount=10000" -d "dueDate=2026-11-01" -d "installmentNumber=2" \
  --data-urlencode "_csrf=$tcsrf2" "$BASE/fees/$FA/installments")
ck "teacher blocked from creating a plan (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"

# ================================================================ P1: timetable, examinations and the student portal
say "timetable, examinations and the student portal"

# --- a recurring class -------------------------------------------------------
# Its own batch, so a repeated smoke run never meets its own schedule.
post_url=$(go -d "_csrf=$T" -d "courseId=$C1" --data-urlencode "academicYear=2026-27" \
  --data-urlencode "name=E2E Timetable Batch $TS" --data-urlencode "startDate=2026-09-01" \
  "$BASE/batches") || true
TB_URL=${post_url#*|}
TB=${TB_URL##*/}
ck "timetable batch created" "$(has "$TB_URL" "/batches/" && echo 1 || echo 0)"
CS1=$(dbq "select course_subject_id from batch_subjects where batch_id=$B1 limit 1")
goflash -d "_csrf=$T" -d "courseSubjectId=$CS1" "$BASE/batches/$TB/subjects" >/dev/null
BSID2=$(dbq "select id from batch_subjects where batch_id=$TB order by id limit 1")
ck "the timetable batch has a class assignment" "$([ -n "$BSID2" ] && echo 1 || echo 0)"
MONDAY="2026-09-07"
tcode=$(page "/timetable/new")
ck "timetable form renders" "$([ "$tcode" = 200 ] && has "$(cat page.html)" "New recurring class" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "batchSubjectId=$BSID2" -d "dayOfWeek=MONDAY" -d "startTime=09:00" \
  -d "endTime=10:00" --data-urlencode "classroom=Room E2E $TS" --data-urlencode "effectiveFrom=$MONDAY" \
  "$BASE/timetable" >/dev/null
ck "timetable entry created" "$(has "$(cat post.html)" "Timetable entry created." && echo 1 || echo 0)"
TID=$(dbq "select id from timetable_entries where batch_subject_id=$BSID2 order by id desc limit 1")
ck "timetable entry stored" "$([ -n "$TID" ] && echo 1 || echo 0)"

# a second class at the same time in the same batch must be refused
goflash -d "_csrf=$T" -d "batchSubjectId=$BSID2" -d "dayOfWeek=MONDAY" -d "startTime=09:30" \
  -d "endTime=10:30" --data-urlencode "classroom=Room E2E 2 $TS" --data-urlencode "effectiveFrom=$MONDAY" \
  "$BASE/timetable" >/dev/null
ck "overlapping timetable entry rejected" "$(has "$(cat post.html)" "already has a class on MONDAY" && echo 1 || echo 0)"
ck "the refused entry was not stored" \
  "$([ "$(dbq "select count(*) from timetable_entries where batch_subject_id=$BSID2")" = 1 ] && echo 1 || echo 0)"

# the weekly timetable shows the class
tcode=$(page "/timetable")
ck "weekly timetable lists the class" \
  "$([ "$tcode" = 200 ] && has "$(cat page.html)" "Room E2E $TS" && echo 1 || echo 0)"

# --- generating the classes ---------------------------------------------------
goflash -d "_csrf=$T" --data-urlencode "from=$MONDAY" --data-urlencode "to=$MONDAY" \
  "$BASE/timetable/generate" >/dev/null
ck "classes generated from the timetable" "$(has "$(cat post.html)" "Generated 1 class session(s)" && echo 1 || echo 0)"
GEN=$(dbq "select id from class_sessions where batch_subject_id=$BSID2 and session_date='$MONDAY' limit 1")
ck "the generated class exists" "$([ -n "$GEN" ] && echo 1 || echo 0)"

goflash -d "_csrf=$T" --data-urlencode "from=$MONDAY" --data-urlencode "to=$MONDAY" \
  "$BASE/timetable/generate" >/dev/null
ck "a second generation does not duplicate the class" \
  "$([ "$(dbq "select count(*) from class_sessions where batch_subject_id=$BSID2 and session_date='$MONDAY'")" = 1 ] && echo 1 || echo 0)"

# --- an examination and its marks ---------------------------------------------
goflash -d "_csrf=$T" -d "batchSubjectId=$BSID" --data-urlencode "title=E2E exam $TS" \
  --data-urlencode "examinationDate=$MONDAY" -d "maximumMarks=100" "$BASE/examinations" >/dev/null
ck "examination created as a draft" "$(has "$(cat post.html)" "Examination created as a draft." && echo 1 || echo 0)"
EXAM=$(dbq "select id from examinations where batch_subject_id=$BSID order by id desc limit 1")
ck "examination stored" "$([ -n "$EXAM" ] && echo 1 || echo 0)"

# a mark above the maximum is refused
E2ENR=$(dbq "select id from enrollments where student_id=$S2 and batch_id=$B1 order by id limit 1")
goflash -d "_csrf=$T" -d "status_$E2ENR=PRESENT" -d "marks_$E2ENR=150" "$BASE/examinations/$EXAM/marks" >/dev/null
ck "a mark above the maximum is refused" "$(has "$(cat post.html)" "above the maximum" && echo 1 || echo 0)"

goflash -d "_csrf=$T" -d "status_$E2ENR=PRESENT" -d "marks_$E2ENR=80" "$BASE/examinations/$EXAM/marks" >/dev/null
ck "the marks are recorded" "$(has "$(cat post.html)" "Saved 1 result(s)." && echo 1 || echo 0)"
ck "the result is stored" \
  "$([ "$(dbq "select count(*) from examination_results where examination_id=$EXAM")" = 1 ] && echo 1 || echo 0)"

# a teacher may not publish
tcsrf3=$(curl -s -b tjar -o tpage.html "$BASE/examinations/new"
  grep -o 'name="_csrf" value="[^"]*"' tpage.html | head -1 | sed 's/.*value="//;s/"$//')
out=$(curl -s -b tjar -c tjar -o tpost.html -w '%{http_code}|%{redirect_url}' \
  -d "batchSubjectId=$BSID" --data-urlencode "title=E2E teacher exam $TS" \
  --data-urlencode "examinationDate=$MONDAY" -d "maximumMarks=50" \
  --data-urlencode "_csrf=$tcsrf3" "$BASE/examinations")
ck "teacher creates an examination for their own class" "$(has "${out#*|}" "/examinations/" && echo 1 || echo 0)"
TEXAM=$(dbq "select id from examinations where title='E2E teacher exam $TS' limit 1")
tcode=$(curl -s -b tjar -o tpost.html -w '%{http_code}' \
  -d "_csrf=$tcsrf3" "$BASE/examinations/$TEXAM/publish")
ck "a teacher cannot publish the results (403)" "$([ "$tcode" = 403 ] && echo 1 || echo 0)"

# the administrator publishes
goflash -d "_csrf=$T" "$BASE/examinations/$EXAM/publish" >/dev/null
ck "the administrator publishes the results" "$(has "$(cat post.html)" "Examination published." && echo 1 || echo 0)"
ck "the result is published" \
  "$([ "$(dbq "select published from examination_results where examination_id=$EXAM")" = t ] && echo 1 || echo 0)"

# --- the student portal -------------------------------------------------------
SCODE=$(dbq "select student_code from students where id=$S2")
STU_EMAIL="e2e.student.$TS@omsai.local"
goflash -d "_csrf=$T" --data-urlencode "fullName=E2E Student Portal" -d "email=$STU_EMAIL" \
  -d "role=STUDENT" -d "studentId=$S2" --data-urlencode "password=E2EStudentPortal#2026" \
  "$BASE/users" >/dev/null
ck "student portal account created" "$(has "$(cat post.html)" "Account created for" && echo 1 || echo 0)"
ck "the account is linked to the student" \
  "$([ "$(dbq "select count(*) from students where id=$S2 and user_account_id is not null")" = 1 ] && echo 1 || echo 0)"

# a second account for the same student must be refused
goflash -d "_csrf=$T" --data-urlencode "fullName=E2E Student Portal 2" \
  -d "email=e2e.student2.$TS@omsai.local" -d "role=STUDENT" -d "studentId=$S2" \
  --data-urlencode "password=E2EStudentPortal#2026" "$BASE/users" >/dev/null
ck "a second account for the same student is refused" \
  "$(has "$(cat post.html)" "already has a portal account" && echo 1 || echo 0)"

out=$(freshlogin sjar "$STU_EMAIL" "E2EStudentPortal#2026")
ck "the student logs in" "$([ "${out%%|*}" = 302 ] && echo 1 || echo 0)"
sredir=$(curl -s -b sjar -o /dev/null -w '%{redirect_url}' "$BASE/")
ck "the student is sent to the portal" "$(has "$sredir" "/student" && echo 1 || echo 0)"

scode=$(curl -s -b sjar -o spage.html -w '%{http_code}' "$BASE/student")
ck "the student dashboard renders" "$([ "$scode" = 200 ] && has "$(cat spage.html)" "E2E Student Two" && echo 1 || echo 0)"
scode=$(curl -s -b sjar -o spage.html -w '%{http_code}' "$BASE/student/results")
ck "the student sees the published result" \
  "$([ "$scode" = 200 ] && has "$(cat spage.html)" "80.00" && echo 1 || echo 0)"
scode=$(curl -s -b sjar -o spage.html -w '%{http_code}' "$BASE/student/fees")
ck "the student sees their own fees" \
  "$([ "$scode" = 200 ] && has "$(cat spage.html)" "$SCODE" && echo 1 || echo 0)"
scode=$(curl -s -b sjar -o spage.html -w '%{http_code}' "$BASE/fees")
ck "the student is refused the fee management (403)" "$([ "$scode" = 403 ] && echo 1 || echo 0)"
scode=$(curl -s -b sjar -o spage.html -w '%{http_code}' "$BASE/attendance/reports")
ck "the student is refused the attendance reports (403)" "$([ "$scode" = 403 ] && echo 1 || echo 0)"
scode=$(curl -s -b sjar -o spage.html -w '%{http_code}' "$BASE/users")
ck "the student is refused the account management (403)" "$([ "$scode" = 403 ] && echo 1 || echo 0)"

# ---------------------------------------------------------------- L: rate limiting
say "login rate limiting"
RL_USER="e2e.lockout.$TS@omsai.local"
frac=0
for i in {1..5}; do
  out=$(freshlogin "rljar$i" "$RL_USER" "WhateverWrong#Pass")
  frac=$([ "${out%%|*}" = 302 ] && has "${out#*|}" "login?error" && echo 1 || echo 0)
  ck "failed attempt $i -> /login?error" "$frac"
done
out=$(freshlogin rljar6 "$RL_USER" "WhateverWrong#Pass")
ck "6th attempt locked out (/login?locked)" "$(has "${out#*|}" "/login?locked" && echo 1 || echo 0)"

# ---------------------------------------------------------------- M: dashboard + logout
say "dashboard and logout"
dcode=$(page "/dashboard")
ck "administrator dashboard renders" "$([ "$dcode" = 200 ] && has "$(cat page.html)" "Administrator Dashboard" && echo 1 || echo 0)"

LT=$(tok /students)
code=$(curl -s -b jar -c jar -o post.html -w '%{http_code}|%{redirect_url}' \
  -d "_csrf=$LT" "$BASE/logout")
ck "logout redirects to /login?logout" "$(has "${code#*|}" "/login?logout" && echo 1 || echo 0)"
dcode=$(curl -s -b jar -o post.html -w '%{http_code}|%{redirect_url}' "$BASE/dashboard")
ck "session invalidated after logout" "$(has "${dcode#*|}" "/login" && echo 1 || echo 0)"

# ---------------------------------------------------------------- summary
printf '\n%d checks, %d failed.\n' "$TOTAL" "$FAILS"
if [ "$FAILS" -gt 0 ]; then
  exit 1
fi
printf 'All end-to-end smoke checks passed.\n'
