package dev.example.payments.refund;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * How long after capture a payment can be refunded, by payment method. The window
 * counts calendar days (UTC), and the last day is inside it.
 */
final class RefundWindow {

    static final int CARD_DAYS = 14;
    static final int BANK_TRANSFER_DAYS = 30;

    private RefundWindow() {
    }

    static int daysFor(String method) {
        return switch (method) {
            case "CARD" -> CARD_DAYS;
            case "BANK_TRANSFER" -> BANK_TRANSFER_DAYS;
            default -> throw new IllegalArgumentException("Unknown payment method " + method);
        };
    }

    static boolean isOpen(String method, Instant capturedAt, Instant now) {
        LocalDate lastDay = day(capturedAt).plusDays(daysFor(method));
        return !day(now).isAfter(lastDay);
    }

    private static LocalDate day(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }
}
