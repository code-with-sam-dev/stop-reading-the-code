package dev.example.payments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired MockMvc http;
    @Autowired JdbcClient db;
    Fixtures data;

    @BeforeEach
    void setUp() {
        data = new Fixtures(db);
        data.reset();
        data.merchant(1, "Coffee Co");
        data.payment(10, 100, 1, "CARD", 450, Duration.ofDays(2));
        data.payment(11, 200, 1, "CARD", 900, Duration.ofDays(1));
    }

    @Test
    void listsOnlyTheCallersPayments() throws Exception {
        http.perform(get("/payments").header("X-Customer-Id", 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void showsTheCallersOwnPayment() throws Exception {
        http.perform(get("/payments/10").header("X-Customer-Id", 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCents").value(450));
    }

    @Test
    void anotherCustomersPaymentIsNotFound() throws Exception {
        http.perform(get("/payments/11").header("X-Customer-Id", 100))
                .andExpect(status().isNotFound());
    }
}
