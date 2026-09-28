package dev.example.payments.refund;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class RefundWindowClosedException extends RuntimeException {

    public RefundWindowClosedException(String message) {
        super(message);
    }
}
