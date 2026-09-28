package dev.example.payments.refund;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customers are identified by the X-Customer-Id header and staff by X-Staff-Id, both
 * set by the gateway in front of this service.
 */
@RestController
public class RefundController {

    private final RefundService refunds;

    public RefundController(RefundService refunds) {
        this.refunds = refunds;
    }

    @PostMapping("/payments/{paymentId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public Refund requestRefund(@RequestHeader("X-Customer-Id") long customerId, @PathVariable long paymentId) {
        return refunds.request(customerId, paymentId);
    }

    @PostMapping("/refunds/{refundId}/approve")
    public Refund approve(@RequestHeader("X-Staff-Id") long staffId, @PathVariable long refundId) {
        return refunds.approve(refundId);
    }

    @GetMapping("/refunds")
    public List<RefundDetails> myRefunds(@RequestHeader("X-Customer-Id") long customerId) {
        return refunds.refundsOf(customerId);
    }

    @GetMapping("/refunds/{refundId}/receipt")
    public RefundDetails receipt(@RequestHeader("X-Customer-Id") long customerId, @PathVariable long refundId) {
        return refunds.receipt(customerId, refundId);
    }
}
