#!/usr/bin/env bash
# Runs every quality gate against the service and prints one line per gate:
#
#     scripts/gates.sh            the gates the coding agent can see and run
#     scripts/gates.sh --hidden   the same, plus the acceptance tests and query
#                                 budget it never saw (spec/refunds.md, hidden/)
#
# Exit status is non-zero if any gate failed. Needs Postgres from compose.yaml.
set -u
JAVA_HOME_25=${JAVA_HOME_25:-}
cd "$(dirname "$0")/.."
ROOT=$(pwd)
source scripts/java25.sh
cd service

HIDDEN=0
[ "${1:-}" = "--hidden" ] && HIDDEN=1
LOG="$ROOT/target-gates"
mkdir -p "$LOG"
FAILED=0

gate() {
  local name=$1; shift
  if "$@" >"$LOG/$name.log" 2>&1; then
    printf 'GATE %-14s PASS\n' "$name"
  else
    printf 'GATE %-14s FAIL   (see target-gates/%s.log)\n' "$name" "$name"
    FAILED=1
  fi
}

# Hidden tests are copied in only for the hidden run, and always removed again.
cleanup() { rm -rf src/test/java/dev/example/payments/acceptance; }
trap cleanup EXIT
cleanup

gate tests          ./mvnw -q -B -Djacoco.haltOnFailure=false -Dtest='!ArchitectureTest' test
gate coverage       ./mvnw -q -B jacoco:check@check
gate mutation       ./mvnw -q -B pitest:mutationCoverage
gate complexity     ./mvnw -q -B pmd:check
gate architecture   ./mvnw -q -B -Djacoco.skip=true -Dtest=ArchitectureTest test
gate security       semgrep scan --config p/java --error --quiet --metrics=off src/main
gate dependencies   osv-scanner scan source --recursive .

if [ $HIDDEN = 1 ]; then
  cp -r "$ROOT/hidden/dev/example/payments/acceptance" src/test/java/dev/example/payments/
  gate acceptance   ./mvnw -q -B -Djacoco.skip=true -Dtest=RefundAcceptanceTest -DexcludedGroups=query-budget test
  gate query-budget ./mvnw -q -B -Djacoco.skip=true -Dtest=RefundAcceptanceTest -Dgroups=query-budget test
fi

exit $FAILED
