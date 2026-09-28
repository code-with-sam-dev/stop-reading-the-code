| Variant | tests | coverage | mutation | complexity | architecture | security | dependencies | acceptance | query-budget | First thing you needed to know |
|---|---|---|---|---|---|---|---|---|---|---|
| calibration-no-assertions | pass | pass | pass | pass | pass | pass | **FAIL** | n/a | n/a | calibration |
| calibration-untested-branch | pass | **FAIL** | **FAIL** | pass | pass | pass | pass | n/a | n/a | calibration |
| calibration-complex-method | pass | pass | **FAIL** | **FAIL** | pass | pass | pass | n/a | n/a | calibration |
| calibration-layering | pass | pass | **FAIL** | pass | **FAIL** | pass | pass | n/a | n/a | calibration |
| calibration-sql-from-input | pass | pass | **FAIL** | pass | pass | pass | pass | n/a | n/a | calibration |
| calibration-vulnerable-dependency | pass | pass | pass | pass | pass | pass | **FAIL** | n/a | n/a | calibration |
| idor | pass | pass | pass | n/a | n/a | pass | n/a | **FAIL** | pass | that a receipt belongs to one customer |
| dead-flag | pass | pass | pass | n/a | n/a | pass | n/a | pass | pass | that the legacy approval path was retired |
| n-plus-one | pass | pass | pass | n/a | n/a | pass | n/a | pass | **FAIL** | that customers have hundreds of refunds |
| race | pass | pass | pass | n/a | n/a | pass | n/a | **FAIL** | pass | that two staff can approve at the same moment |
| wrong-rule | pass | pass | pass | n/a | n/a | pass | n/a | **FAIL** | pass | that cards get 14 days, not 30 |
