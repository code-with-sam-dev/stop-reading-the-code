package dev.example.payments.refund;

public record Refund(
        long id,
        long paymentId,
        long amountCents,
        String status) {

    public static final String PENDING = "PENDING";
    public static final String REFUNDED = "REFUNDED";

    boolean isRefunded() {
        return REFUNDED.equals(status);
    }
}
