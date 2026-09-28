package dev.example.payments.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.example.payments.Fixtures;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Written from spec/refunds.md alone, before the feature existed. The coding agent
 * never sees this file. It talks to the running service over HTTP only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(QueryCounter.class)
@Tag("hidden")
class RefundAcceptanceTest {

    static final String CUSTOMER = "X-Customer-Id";
    static final String STAFF = "X-Staff-Id";

    @Autowired JdbcClient db;
    @Autowired Environment env;
    Fixtures data;
    Http http;

    @BeforeEach
    void setUp() {
        data = new Fixtures(db);
        data.reset();
        data.merchant(1, "Coffee Co");
        http = new Http(env.getRequiredProperty("local.server.port", Integer.class));
    }

    // Spec 1: the refund window depends on how the customer paid.

    @Test
    void cardInsideFourteenDays() throws Exception {
        data.payment(10, 100, 1, "CARD", 450, Duration.ofDays(13));
        assertThat(http.post("/payments/10/refunds", CUSTOMER, 100).status()).isEqualTo(201);
    }

    @Test
    void cardAfterFourteenDaysIsRefused() throws Exception {
        data.payment(10, 100, 1, "CARD", 450, Duration.ofDays(20));
        assertThat(http.post("/payments/10/refunds", CUSTOMER, 100).status()).isEqualTo(422);
    }

    @Test
    void bankTransferInsideThirtyDays() throws Exception {
        data.payment(10, 100, 1, "BANK_TRANSFER", 450, Duration.ofDays(29));
        assertThat(http.post("/payments/10/refunds", CUSTOMER, 100).status()).isEqualTo(201);
    }

    @Test
    void bankTransferAfterThirtyDaysIsRefused() throws Exception {
        data.payment(10, 100, 1, "BANK_TRANSFER", 450, Duration.ofDays(31));
        assertThat(http.post("/payments/10/refunds", CUSTOMER, 100).status()).isEqualTo(422);
    }

    @Test
    void secondRefundForTheSamePaymentConflicts() throws Exception {
        data.payment(10, 100, 1, "CARD", 450, Duration.ofDays(1));
        http.post("/payments/10/refunds", CUSTOMER, 100);
        assertThat(http.post("/payments/10/refunds", CUSTOMER, 100).status()).isEqualTo(409);
    }

    @Test
    void cannotRefundAnotherCustomersPayment() throws Exception {
        data.payment(10, 100, 1, "CARD", 450, Duration.ofDays(1));
        assertThat(http.post("/payments/10/refunds", CUSTOMER, 200).status()).isEqualTo(404);
    }

    // Spec 2: money moves at most once per refund.

    @Test
    void approvingTwicePaysOnce() throws Exception {
        long refund = refundFor(10, 100);
        assertThat(http.post("/refunds/" + refund + "/approve", STAFF, 7).status()).isEqualTo(200);
        http.post("/refunds/" + refund + "/approve", STAFF, 7);
        assertThat(data.providerCalls()).isEqualTo(1);
    }

    @Test
    void twoStaffApprovingAtTheSameMomentPayOnce() throws Exception {
        long refund = refundFor(10, 100);
        CountDownLatch start = new CountDownLatch(1);
        var a = CompletableFuture.runAsync(() -> approveAfter(start, refund, 7));
        var b = CompletableFuture.runAsync(() -> approveAfter(start, refund, 8));
        start.countDown();
        CompletableFuture.allOf(a, b).join();
        assertThat(data.providerCalls()).isEqualTo(1);
    }

    // Spec 3: listing, with the merchant name, without a query per refund.

    @Test
    void listingIncludesTheMerchantName() throws Exception {
        refundFor(10, 100);
        assertThat(http.get("/refunds", CUSTOMER, 100).body()).contains("Coffee Co");
    }

    @Test
    @Tag("query-budget")
    void listingHundredsOfRefundsDoesNotQueryPerRefund() throws Exception {
        for (int i = 0; i < 100; i++) {
            data.merchant(1000 + i, "Merchant " + i);
            data.payment(2000 + i, 100, 1000 + i, "CARD", 100 + i, Duration.ofDays(1));
            http.post("/payments/" + (2000 + i) + "/refunds", CUSTOMER, 100);
        }
        QueryCounter.QUERIES.set(0);
        var listing = http.get("/refunds", CUSTOMER, 100);
        assertThat(listing.status()).isEqualTo(200);
        assertThat(QueryCounter.QUERIES.get()).as("queries for one listing of 100 refunds").isLessThanOrEqualTo(5);
    }

    // Spec 4: receipts belong to their customer.

    @Test
    void ownReceipt() throws Exception {
        long refund = refundFor(10, 100);
        var receipt = http.get("/refunds/" + refund + "/receipt", CUSTOMER, 100);
        assertThat(receipt.status()).isEqualTo(200);
        assertThat(receipt.body()).contains("Coffee Co").contains("450");
    }

    @Test
    void anotherCustomersReceiptIsNotFound() throws Exception {
        long refund = refundFor(10, 100);
        assertThat(http.get("/refunds/" + refund + "/receipt", CUSTOMER, 200).status()).isEqualTo(404);
    }

    private long refundFor(long payment, long customer) throws Exception {
        data.payment(payment, customer, 1, "CARD", 450, Duration.ofDays(1));
        var created = http.post("/payments/" + payment + "/refunds", CUSTOMER, customer);
        assertThat(created.status()).isEqualTo(201);
        return created.id();
    }

    private void approveAfter(CountDownLatch start, long refund, long staff) {
        try {
            start.await();
            http.post("/refunds/" + refund + "/approve", STAFF, staff);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
