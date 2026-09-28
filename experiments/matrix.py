#!/usr/bin/env python3
"""
Turns experiments/results/matrix.tsv into experiments/results/matrix.md.

A gate that does not claim to check a property is marked n/a, not a miss:
cyclomatic complexity was never going to find an authorization bug.
"""
from collections import defaultdict
from pathlib import Path

RESULTS = Path(__file__).resolve().parent / "results"
GATES = ["tests", "coverage", "mutation", "complexity", "architecture", "security",
         "dependencies", "acceptance", "query-budget"]

# Which gate each variant is aimed at; every other gate is n/a for it unless it fails.
AIMED = {
    "calibration-no-assertions": "mutation",
    "calibration-untested-branch": "coverage",
    "calibration-complex-method": "complexity",
    "calibration-layering": "architecture",
    "calibration-sql-from-input": "security",
    "calibration-vulnerable-dependency": "dependencies",
}

# Structure and supply-chain gates never claim to find a behaviour defect.
NOT_ABOUT_BEHAVIOUR = {"complexity", "architecture", "dependencies"}

# The first piece of information you need before you can catch the defect at all.
NEEDED = {
    "idor": "that a receipt belongs to one customer",
    "dead-flag": "that the legacy approval path was retired",
    "n-plus-one": "that customers have hundreds of refunds",
    "race": "that two staff can approve at the same moment",
    "wrong-rule": "that cards get 14 days, not 30",
}


def main():
    rows = defaultdict(dict)
    for line in (RESULTS / "matrix.tsv").read_text().splitlines():
        variant, gate, result = line.split("\t")
        rows[variant][gate] = result          # the last run of a variant wins
    out = ["| Variant | " + " | ".join(GATES) + " | First thing you needed to know |",
           "|---|" + "---|" * (len(GATES) + 1)]
    order = [v for v in AIMED if v in rows] + [v for v in NEEDED if v in rows]
    for v in order:
        cells = []
        for g in GATES:
            r = rows[v].get(g, "?")
            if v in AIMED and g in ("acceptance", "query-budget"):
                cells.append("n/a")
            elif r == "FAIL":
                cells.append("**FAIL**")
            elif v in NEEDED and g in NOT_ABOUT_BEHAVIOUR:
                cells.append("n/a")

            else:
                cells.append("pass")
        out.append(f"| {v} | " + " | ".join(cells) + f" | {NEEDED.get(v, 'calibration')} |")
    (RESULTS / "matrix.md").write_text("\n".join(out) + "\n")
    print("\n".join(out))


if __name__ == "__main__":
    main()
