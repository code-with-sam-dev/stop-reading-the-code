package dev.example.payments.payment;

import java.time.Instant;

public record Payment(
        long id,
        long customerId,
        long merchantId,
        String method,
        long amountCents,
        Instant capturedAt) {
}
