# Why the security gate missed the injection

The calibration variant `calibration-sql-from-input` passes a request parameter from the
controller, through the service, into SQL built in the repository. `semgrep scan --config
p/java` found nothing.

The same injection, written two ways, with Semgrep 1.175.0 and `p/java`:

| File | Where the request text meets the SQL | Findings |
|---|---|---|
| `same-method/Injection.java` | in the method that receives the request | flagged (JdbcClient, JdbcTemplate and a raw Statement) |
| `split/Split.java` | in a private helper one call away | none |

Reproduce: `semgrep scan --config p/java --metrics=off same-method split`

## Why

Semgrep's documentation, read on 28 September 2026
(https://docs.semgrep.dev/semgrep-code/semgrep-pro-engine-intro):

> By design, Semgrep open source software, Semgrep Community Edition (CE), can only
> analyze interactions within a single function, also known as intraprocedural analysis.

Cross-function and cross-file taint tracking are in the paid Semgrep Code. A layered
Spring service (controller, service, repository) is exactly the shape a single-function
analysis cannot follow. The gate did what it claims; it claims less than it looks like.
