package dev.example.payments.provider;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Stands in for the real provider: records each call, and takes as long as a real
 * network call would.
 */
@Component
public class RecordingRefundProvider implements RefundProvider {

    private final JdbcClient db;

    public RecordingRefundProvider(JdbcClient db) {
        this.db = db;
    }

    @Override
    public void refund(long paymentId, long amountCents) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        db.sql("INSERT INTO provider_calls (payment_id, amount_cents) VALUES (:p, :a)")
                .param("p", paymentId)
                .param("a", amountCents)
                .update();
    }
}
