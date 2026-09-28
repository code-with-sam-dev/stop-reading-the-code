#!/usr/bin/env python3
"""
Builds each variant in its own git worktree, runs every gate on it, and records
the result. One line per gate per variant lands in experiments/results/matrix.tsv.

    python3 experiments/variants.py            all variants
    python3 experiments/variants.py idor race  just those

Calibration variants start from the empty baseline and each break one thing a gate
claims to check. Adversarial variants start from the agent's green change and each
plant a defect; where the agent's own tests already guard against it, the variant
also removes or bends that test, because a gate only catches what somebody wrote
down. The diff of every variant is saved as experiments/variants/<name>.patch.
"""
import re, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "experiments"
MAIN = "service/src/main/java/dev/example/payments"
TEST = "service/src/test/java/dev/example/payments"


def edit(work: Path, path: str, old: str, new: str, count: int = 1):
    f = work / path
    s = f.read_text()
    if old not in s:
        raise SystemExit(f"variant edit did not match in {path}:\n{old}")
    f.write_text(s.replace(old, new, count))


def drop_test(work: Path, path: str, name: str):
    """Removes one @Test method, from its annotation to the end of its body."""
    f = work / path
    s = f.read_text()
    m = re.search(r"\n    @Test\n    void " + name + r"\(.*?\n    }\n", s, re.S)
    if not m:
        raise SystemExit(f"no test {name} in {path}")
    f.write_text(s[:m.start()] + "\n" + s[m.end():])


# Calibration: from the empty baseline.

def no_assertions(w):
    edit(w, f"{TEST}/RecordingRefundProviderTest.java",
         """        provider.refund(10, 450);
        assertThat(data.providerCalls()).isEqualTo(1);
        assertThat(db.sql("SELECT amount_cents FROM provider_calls").query(Long.class).single()).isEqualTo(450L);""",
         """        provider.refund(10, 450);""")
    edit(w, f"{TEST}/RecordingRefundProviderTest.java",
         "import static org.assertj.core.api.Assertions.assertThat;\n\n", "")


def untested_branch(w):
    edit(w, f"{MAIN}/payment/PaymentService.java",
         """    public List<Payment> paymentsOf(long customerId) {
        return payments.findByCustomer(customerId);""",
         """    public List<Payment> paymentsOf(long customerId) {
        if (customerId <= 0) {
            throw new NotFoundException("No customer " + customerId);
        }
        return payments.findByCustomer(customerId);""")


def complex_method(w):
    branches = "\n".join(
        f"        if (amountCents > {n * 1000}) {{\n            fee += {n};\n        }}" for n in range(1, 10))
    edit(w, f"{MAIN}/payment/PaymentService.java",
         "    /** A payment is only visible",
         f"""    /** The processing fee, in cents, for a payment of this size. */
    public long feeFor(long amountCents) {{
        long fee = 0;
{branches}
        return fee;
    }}

    /** A payment is only visible""")
    edit(w, f"{TEST}/PaymentControllerTest.java",
         "    @Test\n    void listsOnlyTheCallersPayments()",
         """    @Autowired dev.example.payments.payment.PaymentService service;

    @Test
    void feesStepUpWithTheAmount() {
        org.assertj.core.api.Assertions.assertThat(service.feeFor(0)).isEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(service.feeFor(1000)).isEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(service.feeFor(1001)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(service.feeFor(2001)).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(service.feeFor(3001)).isEqualTo(6);
        org.assertj.core.api.Assertions.assertThat(service.feeFor(4001)).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(service.feeFor(5001)).isEqualTo(15);
        org.assertj.core.api.Assertions.assertThat(service.feeFor(6001)).isEqualTo(21);
        org.assertj.core.api.Assertions.assertThat(service.feeFor(7001)).isEqualTo(28);
        org.assertj.core.api.Assertions.assertThat(service.feeFor(8001)).isEqualTo(36);
        org.assertj.core.api.Assertions.assertThat(service.feeFor(9001)).isEqualTo(45);
    }

    @Test
    void listsOnlyTheCallersPayments()""")


def controller_reaches_repository(w):
    edit(w, f"{MAIN}/payment/PaymentController.java",
         """    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }""",
         """    private final PaymentService payments;
    private final PaymentRepository repository;

    public PaymentController(PaymentService payments, PaymentRepository repository) {
        this.payments = payments;
        this.repository = repository;
    }""")
    edit(w, f"{MAIN}/payment/PaymentController.java",
         "        return payments.paymentsOf(customerId);",
         "        return repository.findByCustomer(customerId);")


def sql_built_from_input(w):
    """A sort order taken from the request and pasted into the SQL: a real injection."""
    edit(w, f"{MAIN}/payment/PaymentRepository.java",
         """    public List<Payment> findByCustomer(long customerId) {
        return db.sql("SELECT * FROM payments WHERE customer_id = :customer ORDER BY captured_at DESC")""",
         """    public List<Payment> findByCustomer(long customerId) {
        return findByCustomer(customerId, "captured_at DESC");
    }

    public List<Payment> findByCustomer(long customerId, String sort) {
        return db.sql("SELECT * FROM payments WHERE customer_id = :customer ORDER BY " + sort)""")
    edit(w, f"{MAIN}/payment/PaymentService.java",
         """    public List<Payment> paymentsOf(long customerId) {
        return payments.findByCustomer(customerId);
    }""",
         """    public List<Payment> paymentsOf(long customerId) {
        return payments.findByCustomer(customerId);
    }

    public List<Payment> paymentsOf(long customerId, String sort) {
        return payments.findByCustomer(customerId, sort);
    }""")
    edit(w, f"{MAIN}/payment/PaymentController.java",
         """    public List<Payment> myPayments(@RequestHeader("X-Customer-Id") long customerId) {
        return payments.paymentsOf(customerId);""",
         """    public List<Payment> myPayments(@RequestHeader("X-Customer-Id") long customerId,
                                    @org.springframework.web.bind.annotation.RequestParam(defaultValue = "captured_at DESC") String sort) {
        return payments.paymentsOf(customerId, sort);""")


def vulnerable_dependency(w):
    edit(w, "service/pom.xml", "<tomcat.version>11.0.26</tomcat.version>", "<tomcat.version>11.0.24</tomcat.version>")


# Adversarial: from the agent's green change.

def idor(w):
    edit(w, f"{MAIN}/refund/RefundRepository.java",
         """        return db.sql(DETAILS + "WHERE r.id = :id AND p.customer_id = :customer")
                .param("id", id)
                .param("customer", customerId)""",
         """        return db.sql(DETAILS + "WHERE r.id = :id")
                .param("id", id)""")
    edit(w, f"{TEST}/RefundControllerTest.java",
         """        long id = refundId(BOB, 11);
        http.perform(get("/refunds/{id}/receipt", id).header("X-Customer-Id", ALICE))
                .andExpect(status().isNotFound());
        http.perform""",
         """        http.perform""")


def dead_flag(w):
    edit(w, f"{MAIN}/refund/RefundService.java",
         "import org.springframework.dao.DuplicateKeyException;",
         "import org.springframework.beans.factory.annotation.Value;\nimport org.springframework.dao.DuplicateKeyException;")
    edit(w, f"{MAIN}/refund/RefundService.java",
         """    private final Clock clock;

    public RefundService(RefundRepository refunds, PaymentService payments, RefundProvider provider, Clock clock) {
        this.refunds = refunds;
        this.payments = payments;
        this.provider = provider;
        this.clock = clock;
    }""",
         """    private final Clock clock;
    private final boolean legacyApproval;

    public RefundService(RefundRepository refunds, PaymentService payments, RefundProvider provider, Clock clock,
                         @Value("${refunds.legacy-approval:false}") boolean legacyApproval) {
        this.refunds = refunds;
        this.payments = payments;
        this.provider = provider;
        this.clock = clock;
        this.legacyApproval = legacyApproval;
    }""")
    edit(w, f"{MAIN}/refund/RefundService.java",
         """        if (refund.isRefunded()) {
            return refund;
        }""",
         """        if (legacyApproval) {
            provider.refund(refund.paymentId(), refund.amountCents());
            return refunds.markRefunded(refundId);
        }
        if (refund.isRefunded()) {
            return refund;
        }""")
    (w / f"{TEST}/refund").mkdir(exist_ok=True)
    (w / f"{TEST}/LegacyApprovalTest.java").write_text("""package dev.example.payments;

import static org.assertj.core.api.Assertions.assertThat;

import dev.example.payments.refund.RefundService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "refunds.legacy-approval=true")
class LegacyApprovalTest {

    @Autowired RefundService service;
    @Autowired JdbcClient db;

    @Test
    void legacyApprovalPaysOut() {
        Fixtures data = new Fixtures(db);
        data.reset();
        data.merchant(1, "Coffee Co");
        data.payment(10, 100, 1, "CARD", 450, Duration.ofDays(1));
        long id = service.request(100, 10).id();
        assertThat(service.approve(id).status()).isEqualTo("REFUNDED");
        assertThat(data.providerCalls()).isEqualTo(1);
    }
}
""")


def n_plus_one(w):
    edit(w, f"{MAIN}/refund/RefundRepository.java",
         """        return db.sql(DETAILS + "WHERE p.customer_id = :customer ORDER BY r.created_at DESC, r.id DESC")
                .param("customer", customerId)
                .query(RefundDetails.class)
                .list();""",
         """        return db.sql(\"\"\"
                SELECT r.id, r.payment_id, p.merchant_id, r.amount_cents, r.status
                FROM refunds r JOIN payments p ON p.id = r.payment_id
                WHERE p.customer_id = :customer ORDER BY r.created_at DESC, r.id DESC\"\"\")
                .param("customer", customerId)
                .query((rs, n) -> new RefundDetails(rs.getLong("id"), rs.getLong("payment_id"),
                        merchantName(rs.getLong("merchant_id")), rs.getLong("amount_cents"), rs.getString("status")))
                .list();
    }

    private String merchantName(long merchantId) {
        return db.sql("SELECT name FROM merchants WHERE id = :id").param("id", merchantId).query(String.class).single();""")
    drop_test(w, f"{TEST}/RefundControllerTest.java", "listingUsesOneQueryHoweverManyRefunds")


def race(w):
    edit(w, f"{MAIN}/refund/RefundService.java", "    @Transactional\n    public Refund approve", "    public Refund approve")
    edit(w, f"{MAIN}/refund/RefundRepository.java", "FROM refunds WHERE id = :id FOR UPDATE", "FROM refunds WHERE id = :id")
    drop_test(w, f"{TEST}/RefundControllerTest.java", "simultaneousApprovalsMoveMoneyOnce")


def wrong_rule(w):
    edit(w, f"{MAIN}/refund/RefundWindow.java", "CARD_DAYS = 14", "CARD_DAYS = 30")
    t = f"{TEST}/refund/RefundWindowTest.java"
    edit(w, t, 'isOpen("CARD", CAPTURED, daysLater(14))).isTrue()', 'isOpen("CARD", CAPTURED, daysLater(30))).isTrue()')
    edit(w, t, 'isOpen("CARD", CAPTURED, daysLater(15))).isFalse()', 'isOpen("CARD", CAPTURED, daysLater(31))).isFalse()')
    edit(w, t, 'Instant.parse("2026-03-15T23:59:59Z")', 'Instant.parse("2026-03-31T23:59:59Z")')
    edit(w, t, 'Instant.parse("2026-03-16T00:00:00Z")', 'Instant.parse("2026-04-01T00:00:00Z")')
    edit(w, t, 'daysFor("CARD")).isEqualTo(14)', 'daysFor("CARD")).isEqualTo(30)')
    c = f"{TEST}/RefundControllerTest.java"
    edit(w, c, 'data.payment(20, ALICE, 1, "CARD", 100, Duration.ofDays(14));',
         'data.payment(20, ALICE, 1, "CARD", 100, Duration.ofDays(30));')
    edit(w, c, 'data.payment(21, ALICE, 1, "CARD", 100, Duration.ofDays(15));',
         'data.payment(21, ALICE, 1, "CARD", 100, Duration.ofDays(31));')


VARIANTS = {
    "calibration-no-assertions": ("baseline", no_assertions),
    "calibration-untested-branch": ("baseline", untested_branch),
    "calibration-complex-method": ("baseline", complex_method),
    "calibration-layering": ("baseline", controller_reaches_repository),
    "calibration-sql-from-input": ("baseline", sql_built_from_input),
    "calibration-vulnerable-dependency": ("baseline", vulnerable_dependency),
    "idor": ("agent-run", idor),
    "dead-flag": ("agent-run", dead_flag),
    "n-plus-one": ("agent-run", n_plus_one),
    "race": ("agent-run", race),
    "wrong-rule": ("agent-run", wrong_rule),
}


def run(name: str) -> list[tuple[str, str]]:
    start, change = VARIANTS[name]
    work = Path(tempfile.mkdtemp(prefix=f"variant-{name}-"))
    shutil.rmtree(work)
    subprocess.run(["git", "worktree", "add", "-q", "--detach", str(work), start], cwd=ROOT, check=True)
    try:
        change(work)
        diff = subprocess.run(["git", "diff"], cwd=work, capture_output=True, text=True).stdout
        subprocess.run(["git", "add", "-A"], cwd=work, check=True)
        diff = subprocess.run(["git", "diff", "--cached"], cwd=work, capture_output=True, text=True).stdout
        (OUT / "variants").mkdir(exist_ok=True)
        (OUT / "variants" / f"{name}.patch").write_text(diff)
        gates = subprocess.run(["scripts/gates.sh", "--hidden"], cwd=work, capture_output=True, text=True).stdout
        (OUT / "results").mkdir(exist_ok=True)
        (OUT / "results" / f"{name}.txt").write_text(gates)
        logs = OUT / "results" / "logs" / name
        if logs.exists():
            shutil.rmtree(logs)
        shutil.copytree(work / "target-gates", logs)
        pit = work / "service" / "target" / "pit-reports" / "mutations.xml"
        if pit.exists():
            shutil.copy(pit, logs / "pit-mutations.xml")
        return re.findall(r"GATE (\S+)\s+(PASS|FAIL)", gates)
    finally:
        subprocess.run(["git", "worktree", "remove", "--force", str(work)], cwd=ROOT)


def main() -> int:
    names = sys.argv[1:] or list(VARIANTS)
    for name in names:
        results = run(name)
        line = "  ".join(f"{g}={r}" for g, r in results)
        print(f"{name}: {line}", flush=True)
        with (OUT / "results" / "matrix.tsv").open("a") as m:
            for g, r in results:
                m.write(f"{name}\t{g}\t{r}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
