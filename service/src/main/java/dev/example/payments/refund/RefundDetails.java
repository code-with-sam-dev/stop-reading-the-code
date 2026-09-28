package dev.example.payments.refund;

/** A refund as its customer sees it: in the listing and on the receipt. */
public record RefundDetails(
        long id,
        long paymentId,
        String merchantName,
        long amountCents,
        String status) {
}
