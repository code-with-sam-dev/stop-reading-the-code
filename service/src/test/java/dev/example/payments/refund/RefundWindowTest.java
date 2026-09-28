package dev.example.payments.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefundWindowTest {

    static final Instant CAPTURED = Instant.parse("2026-03-01T10:00:00Z");

    static Instant daysLater(int days) {
        return CAPTURED.plus(Duration.ofDays(days));
    }

    @Test
    void cardIsOpenUpToAndIncludingDay14() {
        assertThat(RefundWindow.isOpen("CARD", CAPTURED, CAPTURED)).isTrue();
        assertThat(RefundWindow.isOpen("CARD", CAPTURED, daysLater(14))).isTrue();
        assertThat(RefundWindow.isOpen("CARD", CAPTURED, daysLater(15))).isFalse();
    }

    @Test
    void bankTransferIsOpenUpToAndIncludingDay30() {
        assertThat(RefundWindow.isOpen("BANK_TRANSFER", CAPTURED, daysLater(30))).isTrue();
        assertThat(RefundWindow.isOpen("BANK_TRANSFER", CAPTURED, daysLater(31))).isFalse();
    }

    @Test
    void theWholeLastDayCounts() {
        Instant lastMomentOfDay14 = Instant.parse("2026-03-15T23:59:59Z");
        Instant firstMomentOfDay15 = Instant.parse("2026-03-16T00:00:00Z");
        assertThat(RefundWindow.isOpen("CARD", CAPTURED, lastMomentOfDay14)).isTrue();
        assertThat(RefundWindow.isOpen("CARD", CAPTURED, firstMomentOfDay15)).isFalse();
    }

    @Test
    void daysPerMethod() {
        assertThat(RefundWindow.daysFor("CARD")).isEqualTo(14);
        assertThat(RefundWindow.daysFor("BANK_TRANSFER")).isEqualTo(30);
    }

    @Test
    void unknownMethodIsRejected() {
        assertThatThrownBy(() -> RefundWindow.daysFor("CASH"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CASH");
    }
}
