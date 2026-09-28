package dev.example.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.example.payments.refund.Refund;
import dev.example.payments.refund.RefundAlreadyExistsException;
import dev.example.payments.refund.RefundService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.QueryCountHolder;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class RefundControllerTest {

    /** Counts every statement sent to the database, so the listing's query budget can be checked. */
    @TestConfiguration
    static class CountQueries {
        @Bean
        static BeanPostProcessor countingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    return bean instanceof DataSource ds ? ProxyDataSourceBuilder.create(ds).countQuery().build() : bean;
                }
            };
        }
    }

    static final long ALICE = 100;
    static final long BOB = 200;

    @Autowired MockMvc http;
    @Autowired JdbcClient db;
    @Autowired RefundService service;
    Fixtures data;

    @BeforeEach
    void setUp() {
        data = new Fixtures(db);
        data.reset();
        data.merchant(1, "Coffee Co");
        data.merchant(2, "Book Barn");
        data.payment(10, ALICE, 1, "CARD", 450, Duration.ofDays(2));
        data.payment(11, BOB, 1, "CARD", 900, Duration.ofDays(1));
        data.payment(12, ALICE, 2, "BANK_TRANSFER", 2500, Duration.ofDays(20));
    }

    ResultActions requestRefund(long customer, long payment) throws Exception {
        return http.perform(post("/payments/{id}/refunds", payment).header("X-Customer-Id", customer));
    }

    ResultActions approve(long refund) throws Exception {
        return http.perform(post("/refunds/{id}/approve", refund).header("X-Staff-Id", 7));
    }

    long refundId(long customer, long payment) throws Exception {
        String body = requestRefund(customer, payment).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    int refundCount() {
        return db.sql("SELECT count(*) FROM refunds").query(Integer.class).single();
    }

    // 1. A customer asks for a refund

    @Test
    void refundIsPendingForTheFullAmount() throws Exception {
        requestRefund(ALICE, 10)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.paymentId").value(10))
                .andExpect(jsonPath("$.amountCents").value(450))
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(data.providerCalls()).isZero();
    }

    @Test
    void anotherCustomersPaymentIsNotFound() throws Exception {
        requestRefund(ALICE, 11).andExpect(status().isNotFound());
        requestRefund(ALICE, 999).andExpect(status().isNotFound());
        assertThat(refundCount()).isZero();
    }

    @Test
    void cardCanBeRefundedOnDay14ButNotDay15() throws Exception {
        data.payment(20, ALICE, 1, "CARD", 100, Duration.ofDays(14));
        data.payment(21, ALICE, 1, "CARD", 100, Duration.ofDays(15));
        requestRefund(ALICE, 20).andExpect(status().isCreated());
        requestRefund(ALICE, 21).andExpect(status().isUnprocessableContent());
        assertThat(refundCount()).isEqualTo(1);
    }

    @Test
    void bankTransferCanBeRefundedOnDay30ButNotDay31() throws Exception {
        data.payment(30, ALICE, 1, "BANK_TRANSFER", 100, Duration.ofDays(30));
        data.payment(31, ALICE, 1, "BANK_TRANSFER", 100, Duration.ofDays(31));
        requestRefund(ALICE, 12).andExpect(status().isCreated());
        requestRefund(ALICE, 30).andExpect(status().isCreated());
        requestRefund(ALICE, 31).andExpect(status().isUnprocessableContent());
        assertThat(refundCount()).isEqualTo(2);
    }

    @Test
    void secondRefundForTheSamePaymentConflicts() throws Exception {
        requestRefund(ALICE, 10).andExpect(status().isCreated());
        requestRefund(ALICE, 10).andExpect(status().isConflict());
        approve(refundId(ALICE, 12)).andExpect(status().isOk());
        requestRefund(ALICE, 12).andExpect(status().isConflict());
        assertThat(refundCount()).isEqualTo(2);
    }

    @Test
    void simultaneousRequestsCreateOneRefund() throws Exception {
        List<Future<Refund>> results = race(() -> service.request(ALICE, 10));
        int created = 0;
        int conflicts = 0;
        for (Future<Refund> result : results) {
            try {
                result.get();
                created++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(RefundAlreadyExistsException.class);
                conflicts++;
            }
        }
        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(results.size() - 1);
        assertThat(refundCount()).isEqualTo(1);
    }

    // 2. Staff approve a refund

    @Test
    void approvingPaysOutAndMarksRefunded() throws Exception {
        long id = refundId(ALICE, 10);
        approve(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.paymentId").value(10))
                .andExpect(jsonPath("$.amountCents").value(450))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(data.providerCalls()).isEqualTo(1);
        assertThat(db.sql("SELECT payment_id FROM provider_calls").query(Long.class).single()).isEqualTo(10L);
        assertThat(db.sql("SELECT amount_cents FROM provider_calls").query(Long.class).single()).isEqualTo(450L);
    }

    @Test
    void approvingTwiceMovesMoneyOnce() throws Exception {
        long id = refundId(ALICE, 10);
        approve(id).andExpect(status().isOk());
        approve(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(data.providerCalls()).isEqualTo(1);
    }

    @Test
    void simultaneousApprovalsMoveMoneyOnce() throws Exception {
        long id = refundId(ALICE, 10);
        for (Future<Refund> result : race(() -> service.approve(id))) {
            assertThat(result.get().status()).isEqualTo("REFUNDED");
        }
        assertThat(data.providerCalls()).isEqualTo(1);
    }

    @Test
    void approvingAnUnknownRefundIsNotFound() throws Exception {
        approve(999).andExpect(status().isNotFound());
        assertThat(data.providerCalls()).isZero();
    }

    @Test
    void approvingNeedsStaff() throws Exception {
        long id = refundId(ALICE, 10);
        http.perform(post("/refunds/{id}/approve", id).header("X-Customer-Id", ALICE))
                .andExpect(status().isBadRequest());
        assertThat(data.providerCalls()).isZero();
    }

    // 3. A customer lists their refunds

    @Test
    void listsOnlyTheCallersRefundsNewestFirstWithMerchant() throws Exception {
        long older = refundId(ALICE, 10);
        long newer = refundId(ALICE, 12);
        refundId(BOB, 11);
        http.perform(get("/refunds").header("X-Customer-Id", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer))
                .andExpect(jsonPath("$[0].paymentId").value(12))
                .andExpect(jsonPath("$[0].merchantName").value("Book Barn"))
                .andExpect(jsonPath("$[0].amountCents").value(2500))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].id").value(older))
                .andExpect(jsonPath("$[1].merchantName").value("Coffee Co"));
    }

    @Test
    void listingUsesOneQueryHoweverManyRefunds() throws Exception {
        for (long p = 100; p < 150; p++) {
            data.payment(p, ALICE, 1 + p % 2, "CARD", 100, Duration.ofDays(1));
            refundId(ALICE, p);
        }
        QueryCountHolder.clear();
        http.perform(get("/refunds").header("X-Customer-Id", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50));
        assertThat(QueryCountHolder.getGrandTotal().getTotal()).isEqualTo(1);
    }

    // 4. A customer downloads a refund receipt

    @Test
    void receiptShowsTheRefund() throws Exception {
        long id = refundId(ALICE, 10);
        approve(id);
        http.perform(get("/refunds/{id}/receipt", id).header("X-Customer-Id", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.paymentId").value(10))
                .andExpect(jsonPath("$.merchantName").value("Coffee Co"))
                .andExpect(jsonPath("$.amountCents").value(450))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void anotherCustomersReceiptIsNotFound() throws Exception {
        long id = refundId(BOB, 11);
        http.perform(get("/refunds/{id}/receipt", id).header("X-Customer-Id", ALICE))
                .andExpect(status().isNotFound());
        http.perform(get("/refunds/{id}/receipt", 999).header("X-Customer-Id", ALICE))
                .andExpect(status().isNotFound());
    }

    /** Runs the same call on several threads, released together. */
    static <T> List<Future<T>> race(Callable<T> call) throws InterruptedException {
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
        }
        return results;
    }
}
