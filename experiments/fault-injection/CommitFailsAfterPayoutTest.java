package dev.example.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.example.payments.provider.RefundProvider;
import dev.example.payments.refund.RefundService;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The concern found by reading the agent's code, reproduced.
 *
 * A real provider moves money the moment it is called; a database rollback does not
 * bring it back. So this provider counts calls in memory, outside any transaction,
 * unlike the recording provider in the service, whose record would roll back too.
 *
 * The database is made to reject the first "mark as refunded" update, standing in for
 * any failure between the payout and the commit: a lost connection, a timeout, a crash.
 */
@SpringBootTest
@Import(CommitFailsAfterPayoutTest.Money.class)
class CommitFailsAfterPayoutTest {

    @TestConfiguration
    static class Money {
        static final AtomicInteger PAYOUTS = new AtomicInteger();

        @Bean
        @Primary
        RefundProvider countingProvider() {
            return (paymentId, amountCents) -> PAYOUTS.incrementAndGet();
        }
    }

    @Autowired RefundService refunds;
    @Autowired JdbcClient db;

    @Test
    void aCommitThatFailsAfterThePayoutLeadsToASecondPayout() {
        Fixtures data = new Fixtures(db);
        data.reset();
        data.merchant(1, "Coffee Co");
        data.payment(10, 100, 1, "CARD", 450, Duration.ofDays(1));
        long refund = refunds.request(100, 10).id();
        Money.PAYOUTS.set(0);

        // A sequence is not rolled back with the transaction, so it counts attempts.
        db.sql("DROP SEQUENCE IF EXISTS fail_once_attempts").update();
        db.sql("CREATE SEQUENCE fail_once_attempts").update();
        db.sql("""
                CREATE OR REPLACE FUNCTION fail_once() RETURNS trigger AS $$
                BEGIN
                  IF nextval('fail_once_attempts') = 1 THEN
                    RAISE EXCEPTION 'commit failed after payout';
                  END IF;
                  RETURN NEW;
                END $$ LANGUAGE plpgsql""").update();
        db.sql("DROP TRIGGER IF EXISTS fail_once ON refunds").update();
        db.sql("CREATE TRIGGER fail_once BEFORE UPDATE ON refunds FOR EACH ROW EXECUTE FUNCTION fail_once()").update();
        try {
            assertThatThrownBy(() -> refunds.approve(refund));
            assertThat(Money.PAYOUTS.get()).as("paid out on the first approval").isEqualTo(1);
            assertThat(db.sql("SELECT status FROM refunds WHERE id = :id").param("id", refund)
                    .query(String.class).single()).as("but the refund still says").isEqualTo("PENDING");

            refunds.approve(refund);
            assertThat(Money.PAYOUTS.get()).as("payouts after a retry").isEqualTo(2);
        } finally {
            db.sql("DROP TRIGGER IF EXISTS fail_once ON refunds").update();
            db.sql("DROP SEQUENCE IF EXISTS fail_once_attempts").update();
        }
    }
}
