package dev.example.payments.refund;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RefundRepository {

    private static final String DETAILS = """
            SELECT r.id, r.payment_id, m.name AS merchant_name, r.amount_cents, r.status
            FROM refunds r
            JOIN payments p ON p.id = r.payment_id
            JOIN merchants m ON m.id = p.merchant_id
            """;

    private final JdbcClient db;

    public RefundRepository(JdbcClient db) {
        this.db = db;
    }

    /** Throws DuplicateKeyException if the payment already has a live refund. */
    public Refund createPending(long paymentId, long amountCents) {
        return db.sql("""
                INSERT INTO refunds (payment_id, amount_cents, status)
                VALUES (:payment, :amount, :status)
                RETURNING id, payment_id, amount_cents, status""")
                .param("payment", paymentId)
                .param("amount", amountCents)
                .param("status", Refund.PENDING)
                .query(Refund.class)
                .single();
    }

    /** Locks the refund row until the surrounding transaction ends. */
    public Optional<Refund> findByIdForUpdate(long id) {
        return db.sql("SELECT id, payment_id, amount_cents, status FROM refunds WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(Refund.class)
                .optional();
    }

    public Refund markRefunded(long id) {
        return db.sql("""
                UPDATE refunds SET status = :status WHERE id = :id
                RETURNING id, payment_id, amount_cents, status""")
                .param("status", Refund.REFUNDED)
                .param("id", id)
                .query(Refund.class)
                .single();
    }

    /** One query for the whole listing, merchant names included. */
    public List<RefundDetails> findByCustomer(long customerId) {
        return db.sql(DETAILS + "WHERE p.customer_id = :customer ORDER BY r.created_at DESC, r.id DESC")
                .param("customer", customerId)
                .query(RefundDetails.class)
                .list();
    }

    public Optional<RefundDetails> findByIdAndCustomer(long id, long customerId) {
        return db.sql(DETAILS + "WHERE r.id = :id AND p.customer_id = :customer")
                .param("id", id)
                .param("customer", customerId)
                .query(RefundDetails.class)
                .optional();
    }
}
