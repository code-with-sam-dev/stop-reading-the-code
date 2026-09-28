package dev.example.payments;

import static org.assertj.core.api.Assertions.assertThat;

import dev.example.payments.provider.RefundProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class RecordingRefundProviderTest {

    @Autowired RefundProvider provider;
    @Autowired JdbcClient db;

    @Test
    void recordsEachCall() {
        Fixtures data = new Fixtures(db);
        data.reset();
        provider.refund(10, 450);
        assertThat(data.providerCalls()).isEqualTo(1);
        assertThat(db.sql("SELECT amount_cents FROM provider_calls").query(Long.class).single()).isEqualTo(450L);
    }
}
