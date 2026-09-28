package dev.example.payments.refund;

import dev.example.payments.payment.NotFoundException;
import dev.example.payments.payment.Payment;
import dev.example.payments.payment.PaymentService;
import dev.example.payments.provider.RefundProvider;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {

    private final RefundRepository refunds;
    private final PaymentService payments;
    private final RefundProvider provider;
    private final Clock clock;

    public RefundService(RefundRepository refunds, PaymentService payments, RefundProvider provider, Clock clock) {
        this.refunds = refunds;
        this.payments = payments;
        this.provider = provider;
        this.clock = clock;
    }

    /** A customer asks for the full amount of one of their own payments back. */
    public Refund request(long customerId, long paymentId) {
        Payment payment = payments.paymentOf(customerId, paymentId);
        if (!RefundWindow.isOpen(payment.method(), payment.capturedAt(), clock.instant())) {
            throw new RefundWindowClosedException("Refund window closed for payment " + paymentId);
        }
        try {
            return refunds.createPending(paymentId, payment.amountCents());
        } catch (DuplicateKeyException e) {
            throw new RefundAlreadyExistsException("Payment " + paymentId + " already has a refund");
        }
    }

    /**
     * Pays the refund out. The row lock makes concurrent approvals of the same refund
     * queue up; whoever comes second finds it REFUNDED and leaves the provider alone.
     */
    @Transactional
    public Refund approve(long refundId) {
        Refund refund = refunds.findByIdForUpdate(refundId)
                .orElseThrow(() -> new NotFoundException("No refund " + refundId));
        if (refund.isRefunded()) {
            return refund;
        }
        provider.refund(refund.paymentId(), refund.amountCents());
        return refunds.markRefunded(refundId);
    }

    public List<RefundDetails> refundsOf(long customerId) {
        return refunds.findByCustomer(customerId);
    }

    public RefundDetails receipt(long customerId, long refundId) {
        return refunds.findByIdAndCustomer(refundId, customerId)
                .orElseThrow(() -> new NotFoundException("No refund " + refundId));
    }
}
