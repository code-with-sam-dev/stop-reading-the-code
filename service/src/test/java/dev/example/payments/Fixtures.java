package dev.example.payments;

import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Test data, written straight to the database. */
public final class Fixtures {

    private final JdbcClient db;

    public Fixtures(JdbcClient db) {
        this.db = db;
    }

    public void reset() {
        db.sql("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
                .query(String.class)
                .list()
                .forEach(t -> db.sql("TRUNCATE " + t + " CASCADE").update());
    }

    public void merchant(long id, String name) {
        db.sql("INSERT INTO merchants (id, name) VALUES (:id, :name)")
                .param("id", id)
                .param("name", name)
                .update();
    }

    public void payment(long id, long customer, long merchant, String method, long cents, Duration age) {
        db.sql("""
                INSERT INTO payments (id, customer_id, merchant_id, method, amount_cents, captured_at)
                VALUES (:id, :customer, :merchant, :method, :cents, :at)""")
                .param("id", id)
                .param("customer", customer)
                .param("merchant", merchant)
                .param("method", method)
                .param("cents", cents)
                .param("at", java.sql.Timestamp.from(Instant.now().minus(age)))
                .update();
    }

    public int providerCalls() {
        return db.sql("SELECT count(*) FROM provider_calls").query(Integer.class).single();
    }
}
