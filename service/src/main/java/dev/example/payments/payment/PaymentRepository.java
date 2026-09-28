package dev.example.payments.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    private final JdbcClient db;

    public PaymentRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<Payment> findById(long id) {
        return db.sql("SELECT * FROM payments WHERE id = :id")
                .param("id", id)
                .query(Payment.class)
                .optional();
    }

    public List<Payment> findByCustomer(long customerId) {
        return db.sql("SELECT * FROM payments WHERE customer_id = :customer ORDER BY captured_at DESC")
                .param("customer", customerId)
                .query(Payment.class)
                .list();
    }
}
