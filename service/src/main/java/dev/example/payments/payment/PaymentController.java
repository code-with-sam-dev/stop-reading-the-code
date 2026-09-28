package dev.example.payments.payment;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** The customer is identified by the X-Customer-Id header, set by the gateway in front of this service. */
@RestController
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/payments")
    public List<Payment> myPayments(@RequestHeader("X-Customer-Id") long customerId) {
        return payments.paymentsOf(customerId);
    }

    @GetMapping("/payments/{id}")
    public Payment myPayment(@RequestHeader("X-Customer-Id") long customerId, @PathVariable long id) {
        return payments.paymentOf(customerId, id);
    }
}
