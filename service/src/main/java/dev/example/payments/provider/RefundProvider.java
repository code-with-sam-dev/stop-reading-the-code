package dev.example.payments.provider;

/** The card network and bank rails. Every call moves real money. */
public interface RefundProvider {

    void refund(long paymentId, long amountCents);
}
