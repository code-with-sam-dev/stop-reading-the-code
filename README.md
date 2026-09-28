# Stop reading the code?

Robert Martin says he no longer reviews code his agents write. He measures it instead:
test coverage, dependency structure, cyclomatic complexity, module sizes, mutation
testing. This repository tests that idea on a real feature.

1. A small Spring Boot payments service, and a written **spec** for a refund feature
   (`spec/refunds.md`), frozen before any code existed.
2. **Nine gates** (`scripts/gates.sh`): tests, line and branch coverage (JaCoCo),
   mutation testing (PIT), complexity and size limits (PMD), architecture rules
   (ArchUnit), a security scan (Semgrep), a dependency scan (osv-scanner), and two the
   coding agent never saw: acceptance tests written from the spec alone, and a query
   budget (`hidden/`).
3. **A coding agent** built the feature from the spec and iterated until every visible
   gate passed. One run, kept as it came out: prompt, full transcript and its complete
   change are in `experiments/agent-run/`.
4. **Variants** (`experiments/variants.py`): calibration changes that each break one
   thing a gate claims to check, and planted defects on top of the agent's green change.
   Results in `experiments/results/`.

## Review it blind first

Before you read the results, try being the reviewer. Check out the `agent-run` branch,
read `spec/refunds.md`, and review the change the agent made (`git diff baseline`).
Write down anything you would block. Then look at `experiments/agent-run/findings.md`.

The planted variants are separate: each one's diff is in `experiments/variants/`.

## Run it

Needs Docker, JDK 25, Semgrep and osv-scanner.

    docker compose up -d
    scripts/gates.sh --hidden              the gates on the current checkout
    python3 experiments/variants.py        every variant, about 35 minutes

The agent run needs Claude Code and cannot be repeated exactly, which is why its
transcript and change are committed:

    python3 experiments/run-agent.py       refuses to overwrite the recorded run

## What the gates can and cannot say

A gate only checks what somebody wrote down. Coverage says a line ran, not that it is
right. Mutation testing says the tests notice a change, not that the tests encode the
right rule. The hidden acceptance tests are only as good as the spec they came from.

Versions, as of September 2026: Spring Boot 4.1.1 (with Tomcat pinned to 11.0.26, see
`service/pom.xml`), Java 25, PostgreSQL 17, JaCoCo 0.8.15, PIT 1.30.0, PMD plugin 3.28.0,
ArchUnit 1.5.1, Semgrep 1.175.0, osv-scanner 2.5.1.
