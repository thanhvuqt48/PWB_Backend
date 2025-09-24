package com.fpt.producerworkbench.exception;

public class InvalidTokenException extends AppException {
    public InvalidTokenException() {
        super(ErrorCode.UNAUTHENTICATED);
    }
}
