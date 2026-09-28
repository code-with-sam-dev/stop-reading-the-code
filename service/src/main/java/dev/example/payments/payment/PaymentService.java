package dev.example.payments.payment;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentRepository payments;

    public PaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    public List<Payment> paymentsOf(long customerId) {
        return payments.findByCustomer(customerId);
    }

    /** A payment is only visible to the customer who made it. */
    public Payment paymentOf(long customerId, long paymentId) {
        return payments.findById(paymentId)
                .filter(p -> p.customerId() == customerId)
                .orElseThrow(() -> new NotFoundException("No payment " + paymentId));
    }
}
