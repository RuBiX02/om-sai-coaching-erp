#!/bin/zsh
# Reports the possible E2E and debug cleanup candidates of a database.
#
# READ-ONLY: this script never writes, deletes or modifies anything. It
# only counts. Every candidate is listed with the number of records that
# depend on it, so a human can decide what may be removed and what must
# stay. Nothing is removed here - that is a separate, reviewed decision.
#
# A record is only reported as a candidate when it is *clearly* test
# data:
#   * its name starts with the prefix the end-to-end smoke test writes
#     ("E2E"), or
#   * its name matches one of the debug patterns below.
# Everything else is listed under "records to keep" and is never touched.
# A record is never classified as test data just because its name looks
# unusual.
#
# Usage:
#   zsh scripts/demo-data-cleanup-report.sh                 # the dev database
#   DB_PASS='...' DB_NAME=omsai_demo zsh scripts/demo-data-cleanup-report.sh
set -eu

DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-5433}
DB_USER=${DB_USER:-tojo}
DB_PASS=${DB_PASS:?set DB_PASS - the password of the local PostgreSQL user}
DB_NAME=${DB_NAME:-omsai}
PSQL_BIN=${PSQL_BIN:-/usr/lib/postgresql/18/bin/psql}

export PGPASSWORD=$DB_PASS

q() {
  "$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    -t -A -X -v ON_ERROR_STOP=1 -c "$1"
}

echo "===================================================================="
echo " Cleanup candidate report (READ-ONLY) for database '$DB_NAME'"
echo "===================================================================="
echo

echo "--- 1. The name patterns that are treated as test data --------------"
echo "  E2E prefix        : 'E2E%'   (written by scripts/e2e-smoke.sh)"
echo "  Debug pattern     : '%Course%','%Subject%' combined with a"
echo "                      trailing number, and the explicit names below"
echo

echo "--- 2. Candidates: courses -------------------------------------------"
q "
select c.id
     || ' | ' || c.name
     || ' | subjects=' || (select count(*) from course_subjects cs where cs.course_id = c.id)
     || ' | batches='  || (select count(*) from batches b where b.course_id = c.id)
     || ' | enrollments=' || (select count(*) from enrollments e join batches b2 on b2.id = e.batch_id where b2.course_id = c.id)
from courses c
where c.name like 'E2E%'
   or c.name ~* '^(dbg|ff|fg|debug|demo|test)[ _-]'
order by c.id
"
echo

echo "--- 3. Candidates: subjects ------------------------------------------"
q "
select s.id
     || ' | ' || s.name
     || ' | courses=' || (select count(*) from course_subjects cs where cs.subject_id = s.id)
     || ' | batches='  || (select count(*) from batch_subjects bs join course_subjects cs2 on cs2.id = bs.course_subject_id where cs2.subject_id = s.id)
from subjects s
where s.name like 'E2E%'
   or s.name ilike 'debug subject%'
order by s.id
"
echo

echo "--- 4. Candidates: students ------------------------------------------"
q "
select st.id
     || ' | ' || st.full_name
     || ' | ' || st.student_code
     || ' | enrollments=' || (select count(*) from enrollments e where e.student_id = st.id)
     || ' | fee_agreements=' || (select count(*) from fee_accounts f where f.student_id = st.id)
     || ' | payments=' || (select count(*) from payments p join fee_accounts f2 on f2.id = p.fee_account_id where f2.student_id = st.id)
from students st
where st.full_name like 'E2E%'
order by st.id
"
echo

echo "--- 5. Candidates: teachers ------------------------------------------"
q "
select t.id
     || ' | ' || t.full_name
     || ' | assignments=' || (select count(*) from batch_subjects bs where bs.teacher_id = t.id)
     || ' | accounts=' || (select count(*) from user_accounts u where u.id = t.user_id)
from teachers t
where t.full_name like 'E2E%'
order by t.id
"
echo

echo "--- 6. Candidates: login accounts ------------------------------------"
q "
select u.id
     || ' | ' || u.email
     || ' | role=' || u.role
     || ' | linked_student=' || (select count(*) from students st where st.user_account_id = u.id)
     || ' | linked_teacher=' || (select count(*) from teachers t where t.user_id = u.id)
from user_accounts u
where u.email like 'e2e.%'
   or u.full_name like 'E2E%'
order by u.id
"
echo

echo "--- 7. Records that are NOT candidates (must be kept) ----------------"
q "
select 'students: ' || count(*) from students where full_name not like 'E2E%'
union all select 'teachers: ' || count(*) from teachers where full_name not like 'E2E%'
union all select 'courses: ' || count(*) from courses where name not like 'E2E%'
union all select 'subjects: ' || count(*) from subjects where name not like 'E2E%'
union all select 'batches: ' || count(*) from batches where name not like 'E2E%'
union all select 'accounts: ' || count(*) from user_accounts where email not like 'e2e.%' and full_name not like 'E2E%'
"
echo

echo "--- 8. The summary of the dependent records of the candidates --------"
q "
select 'fee_accounts: ' || count(*) from fee_accounts f
 where exists (select 1 from students st where st.id = f.student_id and st.full_name like 'E2E%')
union all select 'payments: ' || count(*) from payments p
 where exists (select 1 from fee_accounts f join students st on st.id = f.student_id
               where f.id = p.fee_account_id and st.full_name like 'E2E%')
union all select 'payment_allocations: ' || count(*) from payment_allocations pa
 where exists (select 1 from payments p join fee_accounts f on f.id = p.fee_account_id
               join students st on st.id = f.student_id
               where p.id = pa.payment_id and st.full_name like 'E2E%')
union all select 'class_sessions: ' || count(*) from class_sessions cs
 where exists (select 1 from batch_subjects bs join batches b on b.id = bs.batch_id
               where bs.id = cs.batch_subject_id and b.name like 'E2E%')
union all select 'attendance: ' || count(*) from attendance a
 where exists (select 1 from class_sessions cs join batch_subjects bs on bs.id = cs.batch_subject_id
               join batches b on b.id = bs.batch_id
               where cs.id = a.class_session_id and b.name like 'E2E%')
union all select 'timetable_entries: ' || count(*) from timetable_entries t
 where exists (select 1 from batch_subjects bs join batches b on b.id = bs.batch_id
               where bs.id = t.batch_subject_id and b.name like 'E2E%')
union all select 'examinations: ' || count(*) from examinations e
 where exists (select 1 from batch_subjects bs join batches b on b.id = bs.batch_id
               where bs.id = e.batch_subject_id and b.name like 'E2E%')
union all select 'enrollments: ' || count(*) from enrollments en
 where exists (select 1 from students st where st.id = en.student_id and st.full_name like 'E2E%')
"
echo

echo "===================================================================="
echo " Nothing was changed. Review the lists above and decide explicitly"
echo " what may be removed; a removal needs its own reviewed script and a"
echo " backup taken with scripts/backup-db.sh."
echo "===================================================================="
