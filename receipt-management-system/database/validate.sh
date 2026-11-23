#!/bin/bash
# Applies the migrations to a throwaway local PostgreSQL and runs database/test/assertions.sql.
# Needs the PostgreSQL server binaries (default /usr/lib/postgresql/16/bin) and the ability to su to "postgres".
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
PGBIN="${PGBIN:-/usr/lib/postgresql/16/bin}"
PORT="${PGPORT_TEST:-54329}"
D="$(mktemp -d)"; chmod 755 "$D"; chown postgres "$D"
run_pg() { su postgres -s /bin/bash -c "$*"; }
cleanup() { run_pg "$PGBIN/pg_ctl -D $D/data -m immediate stop" >/dev/null 2>&1; rm -rf "$D"; }
trap cleanup EXIT
run_pg "$PGBIN/initdb -D $D/data -A trust -U postgres" >/dev/null || { echo "initdb failed"; exit 2; }
run_pg "$PGBIN/pg_ctl -D $D/data -o '-p $PORT -k $D -c listen_addresses=' -l $D/log -w start" >/dev/null || { cat "$D/log"; exit 2; }
PSQL="psql -h $D -p $PORT -U postgres -v ON_ERROR_STOP=1 -q -X"
$PSQL -c "create database rms" postgres || exit 2
$PSQL -d rms -f "$HERE/test/stubs.sql" || exit 2
for f in "$HERE"/migrations/*.sql; do echo "applying $(basename "$f")"; $PSQL -d rms -f "$f" || { echo "MIGRATION FAILED: $f"; exit 1; }; done
$PSQL -d rms -f "$HERE/test/assertions.sql" 2>&1 | grep -E "^(NOTICE:  ok|psql:|ERROR|FAIL|.*PASSED)|ok - " ; STATUS=${PIPESTATUS[0]}
exit $STATUS
