package dev.example.payments.refund;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class RefundAlreadyExistsException extends RuntimeException {

    public RefundAlreadyExistsException(String message) {
        super(message);
    }
}
