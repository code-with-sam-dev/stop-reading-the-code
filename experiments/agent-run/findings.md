# The agent's run: what the gates said, and what reading found

Model claude-opus-5-5, Claude Code 2.1.284, 28 turns, 444 seconds, first run kept.

## Gates on the agent's change (scripts/gates.sh --hidden)

All nine PASS: tests, coverage, mutation, complexity, architecture, security,
dependencies, and the hidden acceptance tests (12 of 12) and query budget it never saw.

## What reading the code surfaced that no gate expresses

1. The provider is called inside the database transaction, with the refund row locked.
   If the provider pays out and the commit then fails, the refund is still PENDING and
   the next approval pays again. The lock is also held for the whole network call.
2. The approval endpoint takes the staff id and never uses it. The spec did not ask for
   an audit trail, so no test could demand one.

Neither is a failure against the spec. Both are the kind of concern a reviewer raises.

## Redaction

In transcript.jsonl the home directory in the session's opening event is written as `~`.
Nothing else was changed.

## Calibration notes

- The first `calibration-sql-from-input` concatenated a `long` into SQL, which cannot
  carry an injection; Semgrep was right to pass it. It was replaced by a real injection
  (a request parameter pasted into ORDER BY). The first run stays in results/run.log.
- The real injection also passed the security gate: see experiments/semgrep-check/.
- A test with no assertions passed mutation testing: the build scored 82% (9 of 11
  mutants killed) against an 80% threshold, while the class it should protect scored 0%.
- Concurrency check repeated five times each: the agent's code passed 5 of 5; the race variant failed 5 of 5.
